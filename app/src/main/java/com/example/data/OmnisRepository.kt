package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class OmnisRepository(
    private val dao: OmnisDao,
    private val outboxDao: OmnisOutboxDao? = null,
    private val db: OmnisDatabase? = null
) {
    val omnisDao: OmnisDao get() = dao

    @Volatile
    private var appContext: android.content.Context? = null

    fun initAppContext(context: android.content.Context) {
        appContext = context.applicationContext
    }

    val allRecords: Flow<List<OmnisRecord>> = dao.getAllRecords()

    val latestRecord: Flow<OmnisRecord?> = dao.getAllRecords().map { it.lastOrNull() }

    val allFragments: Flow<List<MemoryFragment>> = dao.getAllFragments()

    val allThreads: Flow<List<ThreadSummary>> = dao.getAllRecords().map { records ->
        records.groupBy { it.threadId }.map { (threadId, list) ->
            val last = list.maxByOrNull { it.timestamp }
            ThreadSummary(
                threadId = threadId,
                threadTitle = last?.threadTitle ?: "Thread",
                userName = last?.userName ?: "operator",
                lastTimestamp = last?.timestamp ?: System.currentTimeMillis(),
                messageCount = list.size,
                lastContent = last?.content
            )
        }
    }

    val allUserNames: Flow<List<String>> = dao.getAllRecords().map { records ->
        records.map { it.userName }.distinct()
    }

    fun getMessagesForThread(threadId: String): Flow<List<OmnisRecord>> = dao.getMessagesForThread(threadId)

    fun getRecordsByThread(threadId: String): Flow<List<OmnisRecord>> = dao.getMessagesForThread(threadId)

    fun getRecordsByUser(userName: String): Flow<List<OmnisRecord>> = dao.getAllRecords().map { list ->
        list.filter { it.userName == userName }
    }

    fun getThreadsByUser(userName: String): Flow<List<ThreadSummary>> = allThreads.map { threads ->
        threads.filter { it.userName == userName }
    }

    fun searchRecords(query: String, threadId: String?, limit: Int): Flow<List<OmnisRecord>> = dao.getAllRecords().map { list ->
        list.filter { rec ->
            (threadId == null || rec.threadId == threadId) && rec.content.contains(query, ignoreCase = true)
        }.take(limit)
    }

    private fun toOutboxEntity(record: OmnisRecord, entityId: Long): OmnisSyncOutboxEntity {
        val payload = JSONObject().apply {
            put("id", entityId)
            put("role", record.role)
            put("content", record.content)
            put("domain", record.domain)
            put("val_sys", record.valSys)
            put("val_econ", record.valEcon)
            put("val_psych", record.valPsych)
            put("val_eco", record.valEco)
            put("val_law", record.valLaw)
            put("val_sec", record.valSec)
            put("val_phys", record.valPhys)
            put("val_soc", record.valSoc)
            put("timestamp", record.timestamp)
            put("thread_id", record.threadId)
            put("thread_title", record.threadTitle)
            record.effectiveCommitmentHash?.let { put("audit_commitment_hash", it) }
        }
        val key = OmnisSyncOutboxEntity.deterministicKey(
            entityType = "OMNIS_MESSAGE",
            sessionId = record.threadId,
            timestamp = record.timestamp,
            content = record.content
        )
        return OmnisSyncOutboxEntity(
            idempotencyKey = key,
            entityType = "OMNIS_MESSAGE",
            entityId = entityId,
            sessionId = record.threadId,
            sequenceNumber = record.timestamp,
            payloadJson = payload.toString(),
            syncStatus = if (record.isSyncedToPostgres) OmnisSyncOutboxEntity.STATUS_SYNCED else OmnisSyncOutboxEntity.STATUS_PENDING,
            createdAt = record.timestamp
        )
    }

    suspend fun insert(record: OmnisRecord): Long {
        val activeOutbox = outboxDao
        val activeDb = db
        val insertedId = if (activeDb != null && activeOutbox != null && !record.isSyncedToPostgres) {
            activeDb.withTransaction {
                val rowId = dao.insert(record)
                activeOutbox.insertIgnore(toOutboxEntity(record, rowId))
                rowId
            }
        } else {
            val rowId = dao.insert(record)
            if (activeOutbox != null && !record.isSyncedToPostgres) {
                activeOutbox.insertIgnore(toOutboxEntity(record, rowId))
            }
            rowId
        }
        appContext?.let { ctx ->
            if (!DatabaseConfig.isTesting) {
                OmnisBatchSyncScheduler.evaluateThresholdAndSchedule(ctx, dao, activeOutbox)
            }
        }
        return insertedId
    }

    suspend fun insertRecordsBatched(records: List<OmnisRecord>, chunkSize: Int = 50) {
        val activeOutbox = outboxDao
        val activeDb = db
        records.chunked(chunkSize).forEach { chunk ->
            if (activeDb != null && activeOutbox != null) {
                activeDb.withTransaction {
                    dao.insertRecords(chunk)
                    val outboxRows = chunk.filter { !it.isSyncedToPostgres }.map { rec ->
                        toOutboxEntity(rec, rec.id)
                    }
                    if (outboxRows.isNotEmpty()) {
                        activeOutbox.insertAllIgnore(outboxRows)
                    }
                }
            } else {
                dao.insertRecords(chunk)
            }
        }
        appContext?.let { ctx ->
            if (!DatabaseConfig.isTesting) {
                OmnisBatchSyncScheduler.evaluateThresholdAndSchedule(ctx, dao, activeOutbox)
            }
        }
    }

    suspend fun deleteThread(threadId: String) = dao.deleteThread(threadId)

    suspend fun updateThreadTitle(threadId: String, newTitle: String) = dao.updateThreadTitle(threadId, newTitle)

    suspend fun insertFragment(fragment: MemoryFragment): Long = dao.insertFragment(fragment)

    suspend fun deleteFragment(id: Long) = dao.deleteFragment(id)

    suspend fun pruneOldSyncedRecords(daysOld: Int): Int = 0

    suspend fun findSimilarRecords(query: String, limit: Int = 3): List<OmnisRecord> =
        dao.findSimilarRecords(query, limit)

    suspend fun clear() = dao.deleteAllRecords()
}
