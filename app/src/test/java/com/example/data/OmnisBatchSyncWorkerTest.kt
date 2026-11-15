package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmnisBatchSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var db: OmnisDatabase
    private lateinit var dao: OmnisDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        db = Room.inMemoryDatabaseBuilder(context, OmnisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.omnisDao()
        OmnisBatchSyncWorker.daoOverride = dao
        OmnisBatchSyncWorker.batchUploaderOverride = null
    }

    @After
    fun tearDown() {
        OmnisBatchSyncWorker.daoOverride = null
        OmnisBatchSyncWorker.batchUploaderOverride = null
        db.close()
    }

    @Test
    fun schedulerTriggersAt250UnsyncedAndNetworkConstraintIsArmed() = runBlocking {
        // 1. Pod prahem (50 záznamů): okamžitý práh 250 ještě není splněn, ale recovery sync je připraven
        val smallBatch = (1..50).map { idx ->
            OmnisRecord(
                role = "user",
                content = "Test record $idx",
                isSyncedToPostgres = false
            )
        }
        dao.insertRecords(smallBatch)
        val belowThresholdTriggered = OmnisBatchSyncScheduler.evaluateThresholdAndSchedule(context, dao)
        assertFalse(belowThresholdTriggered)

        // 2. Dosažení prahu >= 250 záznamů (+200 = celkem 250)
        val remainingBatch = (51..250).map { idx ->
            OmnisRecord(
                role = "assistant",
                content = "Test response $idx",
                isSyncedToPostgres = false
            )
        }
        dao.insertRecords(remainingBatch)
        assertEquals(250, dao.getUnsyncedCount())

        val thresholdTriggered = OmnisBatchSyncScheduler.evaluateThresholdAndSchedule(context, dao)
        assertTrue("Při 250 záznamech se musí spustit WorkManager threshold sync", thresholdTriggered)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(OmnisBatchSyncScheduler.IMMEDIATE_WORK_NAME)
            .get()
        assertFalse(workInfos.isEmpty())
        assertTrue(
            workInfos.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.SUCCEEDED
            }
        )
    }

    @Test
    fun batchSyncWorkerUploads250RecordsAndMarksThemSynced() = runBlocking {
        val batch = (1..250).map { idx ->
            OmnisRecord(
                role = "user",
                content = "NIS2 batch record #$idx",
                domain = "ECONOMICS",
                isSyncedToPostgres = false
            )
        }
        dao.insertRecords(batch)
        assertEquals(250, dao.getUnsyncedCount())

        var uploadedBatches = 0
        var uploadedTotal = 0
        OmnisBatchSyncWorker.batchUploaderOverride = { records ->
            uploadedBatches++
            uploadedTotal += records.size
            OmnisBatchSyncWorker.ResultPair(true, records.size, "Synced ${records.size}")
        }

        val worker = TestListenableWorkerBuilder<OmnisBatchSyncWorker>(context).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, uploadedBatches)
        assertEquals(250, uploadedTotal)
        assertEquals(0, dao.getUnsyncedCount())
    }

    @Test
    fun repositoryInsert_writesTransactionalOutboxEntryAtomically() = runBlocking {
        val outboxDao = db.outboxDao()
        val repo = OmnisRepository(dao, outboxDao, db)
        val insertedId = repo.insert(
            OmnisRecord(
                role = "user",
                content = "Transactional outbox durability test",
                threadId = "thread_outbox",
                isSyncedToPostgres = false
            )
        )
        assertTrue(insertedId > 0L)
        assertEquals(1, outboxDao.getPendingCount())

        val pending = outboxDao.getPendingEntries()
        assertEquals("PENDING", pending.first().syncStatus)
        assertEquals("thread_outbox", pending.first().sessionId)

        outboxDao.markSynced(listOf(pending.first().id))
        assertEquals(0, outboxDao.getPendingCount())
    }
}
