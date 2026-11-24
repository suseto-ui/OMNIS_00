package com.omnis.apex.data.sync

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.auth.OmnisAuthService
import com.example.data.DatabaseConfig
import com.example.data.OmnisDatabase
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .build()
    }

    private suspend fun resolveBearerToken(): String? {
        val omnisToken = OmnisAuthService.currentSession.value?.token?.takeIf { it.isNotBlank() }
        if (omnisToken != null) return omnisToken
        return runCatching {
            Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
        }.getOrNull()?.takeIf { it.isNotBlank() }
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

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = batchPayload.toString().toRequestBody(mediaType)
            val reqBuilder = Request.Builder()
                .url("$backendUrl/api/v1/sync/batch")
                .post(body)
            bearer?.let { reqBuilder.header("Authorization", "Bearer $it") }
            syncApiKey?.let { reqBuilder.header("X-OMNIS-API-KEY", it) }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    db.outboxDao().markSynced(pendingItems.map { it.id })
                    val messageIds = pendingItems
                        .filter { it.entityType.equals("OMNIS_MESSAGE", ignoreCase = true) && it.entityId > 0L }
                        .map { it.entityId }
                    if (messageIds.isNotEmpty()) {
                        db.omnisDao().markRecordsAsSynced(messageIds)
                    }
                    pendingItems.size
                } else {
                    pendingItems.forEach { db.outboxDao().markFailed(it.id, "HTTP status ${response.code}") }
                    0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync outbox batch: ${e.message}")
            pendingItems.forEach { db.outboxDao().markFailed(it.id, e.message ?: "Unknown error") }
            0
        }
    }
}
