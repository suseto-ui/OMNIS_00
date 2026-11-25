package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.sync.OmnisApiGatewayClient
import com.example.gateway.OmnisCircuitBreaker
import com.example.telemetry.TelemetryEngine
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker pro automatickou idempotentní dávkovou synchronizaci
 * z Room SQLite (Transactional Outbox + messages) do PostgreSQL 17 / ElectricSQL 1.8.1.
 */
class OmnisBatchSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        SyncLogManager.init(applicationContext)
        val db = if (daoOverride == null) OmnisDatabase.getDatabase(applicationContext) else null
        val dao = daoOverride ?: db!!.omnisDao()
        val outboxDao = outboxDaoOverride ?: db?.outboxDao()
        var totalSynced = 0
        var totalPayloadBytes = 0L
        val runStartMs = System.currentTimeMillis()

        return try {
            // 1. Primární cesta: Idempotentní Transactional Outbox fronta (/api/v1/sync/batch)
            if (batchUploaderOverride == null && outboxDao != null) {
                for (batchIteration in 0 until MAX_BATCHES_PER_RUN) {
                    if (isStopped) break
                    val pending = outboxDao.getPendingEntries(OmnisCircuitBreaker.SYNC_BATCH_SIZE)
                    if (pending.isEmpty()) break

                    val batchStartMs = System.currentTimeMillis()
                    val batchPayloadBytes = pending.sumOf {
                        (it.idempotencyKey.length + it.entityType.length + it.sessionId.length +
                            it.payloadJson.toByteArray(Charsets.UTF_8).size).toLong()
                    }
                    val (syncedCount, msg) = OmnisApiGatewayClient.syncOutboxBatchToGateway(pending)
                    val batchDurationMs = System.currentTimeMillis() - batchStartMs

                    if (syncedCount <= 0) {
                        pending.forEach { entry -> outboxDao.markFailed(entry.id, msg.take(250)) }
                        SyncLogManager.logSync(
                            taskType = "Outbox Batch #${batchIteration + 1}",
                            success = false,
                            syncedCount = 0,
                            payloadSizeBytes = batchPayloadBytes,
                            durationMs = batchDurationMs,
                            message = msg
                        )
                        TelemetryEngine.log(
                            type = "WARNING",
                            component = "BATCH_SYNC_WORKER",
                            message = "Outbox dávka #${batchIteration + 1} selhala ($msg); položky zůstávají v Room."
                        )
                        return if (totalSynced > 0) {
                            Result.success(
                                workDataOf(
                                    KEY_SYNCED_COUNT to totalSynced,
                                    KEY_PAYLOAD_BYTES to totalPayloadBytes,
                                    KEY_LAST_SYNC_TIME to System.currentTimeMillis(),
                                    KEY_PARTIAL to true
                                )
                            )
                        } else {
                            Result.retry()
                        }
                    }

                    val completedEntries = pending.take(syncedCount)
                    val completedBytes = completedEntries.sumOf {
                        (it.idempotencyKey.length + it.entityType.length + it.sessionId.length +
                            it.payloadJson.toByteArray(Charsets.UTF_8).size).toLong()
                    }
                    outboxDao.markSynced(completedEntries.map { it.id })
                    val messageIds = completedEntries
                        .filter { it.entityType == "OMNIS_MESSAGE" && it.entityId > 0L }
                        .map { it.entityId }
                    if (messageIds.isNotEmpty()) {
                        dao.markRecordsAsSynced(messageIds)
                    }
                    totalSynced += completedEntries.size
                    totalPayloadBytes += completedBytes
                    SyncLogManager.logSync(
                        taskType = "Outbox Batch #${batchIteration + 1}",
                        success = true,
                        syncedCount = completedEntries.size,
                        payloadSizeBytes = completedBytes,
                        durationMs = batchDurationMs,
                        message = msg
                    )
                    if (pending.size < OmnisCircuitBreaker.SYNC_BATCH_SIZE) break
                }
            }

            // 2. Zbytkové nebo přímé záznamy bez outbox záznamu (zpětná kompatibilita & test harness)
            for (batchIteration in 0 until MAX_BATCHES_PER_RUN) {
                if (isStopped) break
                val unsynced = dao.getUnsyncedRecords(OmnisCircuitBreaker.SYNC_BATCH_SIZE)
                if (unsynced.isEmpty()) break

                val batchStartMs = System.currentTimeMillis()
                val batchPayloadBytes = unsynced.sumOf { rec ->
                    (rec.role.length + rec.domain.length + rec.threadId.length + rec.threadTitle.length +
                        (rec.content + rec.cognitiveProcess + rec.followUpQuestions + rec.defenseNotes)
                            .toByteArray(Charsets.UTF_8).size).toLong()
                }

                val uploadResult = batchUploaderOverride?.invoke(unsynced) ?: run {
                    val (count, msg) = OmnisApiGatewayClient.syncBatchToGateway(unsynced)
                    if (count > 0) ResultPair(true, count, msg)
                    else ResultPair(false, 0, msg)
                }
                val batchDurationMs = System.currentTimeMillis() - batchStartMs

                if (!uploadResult.success || uploadResult.syncedCount <= 0) {
                    TelemetryEngine.log(
                        type = "WARNING",
                        component = "BATCH_SYNC_WORKER",
                        message = "Dávková synchronizace pozastavena (${uploadResult.message}); záznamy zůstávají bezpečně v Room."
                    )
                    SyncLogManager.logSync(
                        taskType = "Direct Batch #${batchIteration + 1}",
                        success = false,
                        syncedCount = 0,
                        payloadSizeBytes = batchPayloadBytes,
                        durationMs = batchDurationMs,
                        message = uploadResult.message
                    )
                    return if (totalSynced > 0) {
                        Result.success(
                            workDataOf(
                                KEY_SYNCED_COUNT to totalSynced,
                                KEY_PAYLOAD_BYTES to totalPayloadBytes,
                                KEY_LAST_SYNC_TIME to System.currentTimeMillis(),
                                KEY_PARTIAL to true
                            )
                        )
                    } else {
                        Result.retry()
                    }
                }

                val completedRecords = unsynced.take(uploadResult.syncedCount)
                val completedIds = completedRecords.map { it.id }
                val completedBytes = completedRecords.sumOf { rec ->
                    (rec.role.length + rec.domain.length + rec.threadId.length + rec.threadTitle.length +
                        (rec.content + rec.cognitiveProcess + rec.followUpQuestions + rec.defenseNotes)
                            .toByteArray(Charsets.UTF_8).size).toLong()
                }
                if (completedIds.isNotEmpty()) {
                    dao.markRecordsAsSynced(completedIds)
                    totalSynced += completedIds.size
                    totalPayloadBytes += completedBytes
                    SyncLogManager.logSync(
                        taskType = "Direct Batch #${batchIteration + 1}",
                        success = true,
                        syncedCount = completedIds.size,
                        payloadSizeBytes = completedBytes,
                        durationMs = batchDurationMs,
                        message = uploadResult.message
                    )
                }
                if (unsynced.size < OmnisCircuitBreaker.SYNC_BATCH_SIZE) {
                    break
                }
            }

            if (totalSynced > 0) {
                TelemetryEngine.log(
                    type = "INFO",
                    component = "BATCH_SYNC_WORKER",
                    message = "WorkManager dávková synchronizace úspěšně uložila $totalSynced záznamů ($totalPayloadBytes B) do PostgreSQL 17."
                )
            } else if (batchUploaderOverride == null) {
                val checkStartMs = System.currentTimeMillis()
                val (shapeOk, shapeBytes, shapeMsg) = OmnisApiGatewayClient.verifyElectricShapeConsistency()
                val checkDurationMs = System.currentTimeMillis() - checkStartMs
                SyncLogManager.logSync(
                    taskType = "ElectricSQL Consistency Check",
                    success = shapeOk,
                    syncedCount = 0,
                    payloadSizeBytes = shapeBytes,
                    durationMs = checkDurationMs,
                    message = shapeMsg
                )
                if (!shapeOk) {
                    return Result.retry()
                }
            }
            val completedAt = System.currentTimeMillis()
            Result.success(
                workDataOf(
                    KEY_SYNCED_COUNT to totalSynced,
                    KEY_PAYLOAD_BYTES to totalPayloadBytes,
                    KEY_LAST_SYNC_TIME to completedAt,
                    KEY_PARTIAL to false
                )
            )
        } catch (e: Exception) {
            val totalDurationMs = System.currentTimeMillis() - runStartMs
            Log.w(TAG, "Výjimka při WorkManager synchronizaci: ${e.message}", e)
            TelemetryEngine.log(
                type = "WARNING",
                component = "BATCH_SYNC_WORKER",
                message = "Výjimka synchronizace (naplánován retry): ${e.localizedMessage ?: "Unknown"}"
            )
            SyncLogManager.logSync(
                taskType = "WorkManager Sync Exception",
                success = false,
                syncedCount = totalSynced,
                payloadSizeBytes = totalPayloadBytes,
                durationMs = totalDurationMs,
                message = "Chyba synchronizace: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
            Result.retry()
        }
    }

    data class ResultPair(
        val success: Boolean,
        val syncedCount: Int,
        val message: String
    )

    companion object {
        private const val TAG = "OmnisBatchSyncWorker"
        private const val MAX_BATCHES_PER_RUN = 10
        const val KEY_SYNCED_COUNT = "synced_count"
        const val KEY_PAYLOAD_BYTES = "payload_bytes"
        const val KEY_LAST_SYNC_TIME = "last_sync_time"
        const val KEY_PARTIAL = "partial_sync"

        @Volatile
        internal var daoOverride: OmnisDao? = null

        @Volatile
        internal var outboxDaoOverride: OmnisOutboxDao? = null

        @Volatile
        internal var batchUploaderOverride: (suspend (List<OmnisRecord>) -> ResultPair)? = null
    }
}

object OmnisBatchSyncScheduler {

    const val SYNC_THRESHOLD = OmnisCircuitBreaker.SYNC_BATCH_SIZE // 250 záznamů
    const val IMMEDIATE_WORK_NAME = "omnis_immediate_batch_sync"
    const val NETWORK_RECOVERY_WORK_NAME = "omnis_network_recovery_sync"
    const val PERIODIC_WORK_NAME = "omnis_periodic_batch_sync"

    private fun connectedConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleNetworkRecoverySync(context: Context) {
        val appCtx = context.applicationContext
        val workManager = runCatching { WorkManager.getInstance(appCtx) }.getOrNull() ?: return

        val recoveryRequest = OneTimeWorkRequestBuilder<OmnisBatchSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("omnis_network_recovery")
            .build()

        workManager.enqueueUniqueWork(
            NETWORK_RECOVERY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            recoveryRequest
        )

        val periodicRequest = PeriodicWorkRequestBuilder<OmnisBatchSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .addTag("omnis_periodic_sync")
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    suspend fun evaluateThresholdAndSchedule(
        context: Context,
        dao: OmnisDao,
        outboxDao: OmnisOutboxDao? = null
    ): Boolean {
        val unsyncedCount = maxOf(dao.getUnsyncedCount(), outboxDao?.getPendingCount() ?: 0)
        if (unsyncedCount <= 0) return false

        val appCtx = context.applicationContext
        val workManager = runCatching { WorkManager.getInstance(appCtx) }.getOrNull()

        if (unsyncedCount >= SYNC_THRESHOLD) {
            if (workManager != null) {
                val batchRequest = OneTimeWorkRequestBuilder<OmnisBatchSyncWorker>()
                    .setConstraints(connectedConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .addTag("omnis_threshold_250")
                    .build()

                workManager.enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    batchRequest
                )
            }
            TelemetryEngine.log(
                type = "INFO",
                component = "BATCH_SYNC_SCHEDULER",
                message = "Dosažen práh dávky ($unsyncedCount >= $SYNC_THRESHOLD): naplánována WorkManager synchronizace."
            )
            return true
        }

        if (workManager != null) {
            scheduleNetworkRecoverySync(appCtx)
        }
        return false
    }
}
