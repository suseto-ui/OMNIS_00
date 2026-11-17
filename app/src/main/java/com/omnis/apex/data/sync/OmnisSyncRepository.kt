package com.omnis.apex.data.sync

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.OmnisDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

class OmnisSyncRepository(private val context: Context) {
    private val TAG = "OmnisSyncRepository"
    private val backendUrl = BuildConfig.OMNIS_BACKEND_URL.trimEnd('/')

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .retryOnConnectionFailure(true)
            .build()
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

        var syncedCount = 0
        for (item in pendingItems) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = item.payloadJson.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("$backendUrl/api/v1/sync/batch")
                    .post(body)
                    .header("X-Idempotency-Key", item.idempotencyKey)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        db.outboxDao().markSynced(listOf(item.id))
                        syncedCount++
                    } else {
                        db.outboxDao().markFailed(item.id, "HTTP status ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync outbox item ${item.id}: ${e.message}")
                db.outboxDao().markFailed(item.id, e.message ?: "Unknown error")
            }
        }
        syncedCount
    }
}
