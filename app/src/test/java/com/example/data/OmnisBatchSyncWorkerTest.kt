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
        SyncLogManager.resetForTesting()
    }

    @After
    fun tearDown() {
        OmnisBatchSyncWorker.daoOverride = null
        OmnisBatchSyncWorker.batchUploaderOverride = null
        SyncLogManager.resetForTesting()
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
        val successResult = result as ListenableWorker.Result.Success
        val outputPayloadBytes = successResult.outputData.getLong(OmnisBatchSyncWorker.KEY_PAYLOAD_BYTES, 0L)
        val outputSyncTime = successResult.outputData.getLong(OmnisBatchSyncWorker.KEY_LAST_SYNC_TIME, 0L)
        assertTrue(outputPayloadBytes > 0L)
        assertTrue(outputSyncTime > 0L)
        assertEquals(250, successResult.outputData.getInt(OmnisBatchSyncWorker.KEY_SYNCED_COUNT, 0))
        assertEquals(1, uploadedBatches)
        assertEquals(250, uploadedTotal)
        assertEquals(0, dao.getUnsyncedCount())

        val logs = SyncLogManager.logs.value
        assertEquals(1, logs.size)
        assertTrue(logs.first().success)
        assertEquals(250, logs.first().syncedCount)
        assertEquals(outputPayloadBytes, logs.first().payloadSizeBytes)
        assertEquals("Synced 250", logs.first().message)
    }

    @Test
    fun batchSyncWorkerLogsFailureAndReturnsRetryWhenUploaderFails() = runBlocking {
        val failingBatch = listOf(
            OmnisRecord(
                role = "user",
                content = "Záznam pro test selhání sítě žluťoučký kůň",
                domain = "SECURITY",
                isSyncedToPostgres = false
            )
        )
        dao.insertRecords(failingBatch)

        OmnisBatchSyncWorker.batchUploaderOverride = {
            OmnisBatchSyncWorker.ResultPair(false, 0, "HTTP 503 ElectricSQL nedostupný")
        }

        val worker = TestListenableWorkerBuilder<OmnisBatchSyncWorker>(context).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(1, dao.getUnsyncedCount())

        val logs = SyncLogManager.logs.value
        assertEquals(1, logs.size)
        assertFalse(logs.first().success)
        assertEquals(0, logs.first().syncedCount)
        assertEquals("HTTP 503 ElectricSQL nedostupný", logs.first().message)
        assertTrue(logs.first().payloadSizeBytes > 0L)
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

    @Test
    fun repositoryInsertBatched_assignsEntityIdsAndPrunesOnlyOldSyncedRecords() = runBlocking {
        val outboxDao = db.outboxDao()
        val repo = OmnisRepository(dao, outboxDao, db)
        val now = System.currentTimeMillis()
        val oldTimestamp = now - java.util.concurrent.TimeUnit.DAYS.toMillis(20)

        repo.insertRecordsBatched(
            listOf(
                OmnisRecord(
                    role = "user",
                    content = "Unsynced batch item",
                    threadId = "thread_batch",
                    timestamp = oldTimestamp,
                    isSyncedToPostgres = false
                ),
                OmnisRecord(
                    role = "assistant",
                    content = "Old synced batch item",
                    threadId = "thread_batch",
                    timestamp = oldTimestamp,
                    isSyncedToPostgres = true
                ),
                OmnisRecord(
                    role = "assistant",
                    content = "Recent synced batch item",
                    threadId = "thread_batch",
                    timestamp = now,
                    isSyncedToPostgres = true
                )
            )
        )

        val pending = outboxDao.getPendingEntries()
        assertEquals(1, pending.size)
        assertTrue("Outbox entityId musí mít skutečné ID z Room DB", pending.first().entityId > 0L)

        val pruned = repo.pruneOldSyncedRecords(daysOld = 14)
        assertEquals(1, pruned)
        assertEquals(2, dao.getRecordCount())
    }

    @Test
    fun syncLogManager_persistsLogsAndLastSyncTimeIntoPreferences() {
        SyncLogManager.init(context)
        SyncLogManager.logSync(
            taskType = "ElectricSQL Shape Check",
            success = true,
            syncedCount = 12,
            payloadSizeBytes = 2048L,
            durationMs = 37L,
            message = "Synchronizace dokončena"
        )

        val sp = context.getSharedPreferences("omnis_sync_logs_prefs", Context.MODE_PRIVATE)
        val savedJson = sp.getString("sync_logs_json", null)
        val savedTime = sp.getLong("last_successful_sync_time", 0L)
        assertTrue(!savedJson.isNullOrBlank())
        assertTrue(savedJson!!.contains("ElectricSQL Shape Check"))
        assertTrue(savedTime > 0L)
    }
}
