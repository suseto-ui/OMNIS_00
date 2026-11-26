package com.omnis.apex.data.sync

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.auth.OmnisAuthService
import com.example.data.DatabaseConfig
import com.example.data.OmnisDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject

class OmnisSyncRepository(private val context: Context) {
    private val TAG = "OmnisSyncRepository"
    private val backendUrl = BuildConfig.OMNIS_BACKEND_URL.trimEnd('/')

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("X-OMNIS-API-KEY")
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun resolveBearerToken(): String? {
        return OmnisAuthService.currentSession.value?.token?.takeIf { it.isNotBlank() }
    }

    suspend fun pingSovereignBackend(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$backendUrl/healthz")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sovereign backend ping failed: ${e.message}")
            false
        }
    }

    suspend fun syncLocalOutboxBatch(): Int = withContext(Dispatchers.IO) {
        val db = OmnisDatabase.getDatabase(context)
        val pendingItems = db.outboxDao().getPendingEntries(50, 5)
        if (pendingItems.isEmpty()) return@withContext 0

        val bearer = resolveBearerToken()
        val syncApiKey = DatabaseConfig.getBuildConfigValue("OMNIS_SYNC_API_KEY")?.takeIf { it.isNotBlank() }

        var attemptPayloadBytes = 0L
        val startMs = System.currentTimeMillis()
        return@withContext try {
            val itemsArray = JSONArray()
            for (item in pendingItems) {
                itemsArray.put(
                    JSONObject().apply {
                        put("idempotency_key", item.idempotencyKey)
                        put("entity_type", item.entityType)
                        put("entity_id", item.entityId)
                        put("session_id", item.sessionId)
                        put("sequence_number", item.sequenceNumber)
                        put("payload_json", item.payloadJson)
                        put("created_at", item.createdAt)
                    }
                )
            }
            val batchPayload = JSONObject().apply {
                put("items", itemsArray)
                put("client_version", "4.5")
            }

            val payloadBytes = batchPayload.toString().toByteArray(Charsets.UTF_8)
            attemptPayloadBytes = payloadBytes.size.toLong()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = payloadBytes.toRequestBody(mediaType)
            val reqBuilder = Request.Builder()
                .url("$backendUrl/api/v1/sync/batch")
                .post(body)
            bearer?.let { reqBuilder.header("Authorization", "Bearer $it") }
            syncApiKey?.let { reqBuilder.header("X-OMNIS-API-KEY", it) }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val durationMs = System.currentTimeMillis() - startMs
                if (response.isSuccessful) {
                    db.outboxDao().markSynced(pendingItems.map { it.id })
                    val messageIds = pendingItems
                        .filter { it.entityType.equals("OMNIS_MESSAGE", ignoreCase = true) && it.entityId > 0L }
                        .map { it.entityId }
                    if (messageIds.isNotEmpty()) {
                        db.omnisDao().markRecordsAsSynced(messageIds)
                    }
                    com.example.data.SyncLogManager.logSync(
                        taskType = "ElectricSQL / Outbox Repository",
                        success = true,
                        syncedCount = pendingItems.size,
                        payloadSizeBytes = attemptPayloadBytes,
                        durationMs = durationMs,
                        message = "Odesláno ${pendingItems.size} položek do PostgreSQL 17 / ElectricSQL (HTTP ${response.code})."
                    )
                    pendingItems.size
                } else {
                    val failMsg = "HTTP status ${response.code}: ${response.message}"
                    pendingItems.forEach { db.outboxDao().markFailed(it.id, failMsg.take(250)) }
                    com.example.data.SyncLogManager.logSync(
                        taskType = "ElectricSQL / Outbox Repository",
                        success = false,
                        syncedCount = 0,
                        payloadSizeBytes = attemptPayloadBytes,
                        durationMs = durationMs,
                        message = failMsg
                    )
                    0
                }
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            val errMsg = e.message ?: "Unknown error"
            Log.e(TAG, "Failed to sync outbox batch: $errMsg")
            pendingItems.forEach { db.outboxDao().markFailed(it.id, errMsg.take(250)) }
            com.example.data.SyncLogManager.logSync(
                taskType = "ElectricSQL / Outbox Repository",
                success = false,
                syncedCount = 0,
                payloadSizeBytes = attemptPayloadBytes,
                durationMs = durationMs,
                message = "Výjimka při přenosu: $errMsg"
            )
            0
        }
    }
}
