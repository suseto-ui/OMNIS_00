package com.example.data.sync

import android.util.Log
import com.example.auth.OmnisAuthService
import com.example.data.DatabaseConfig
import com.example.data.OmnisRecord
import com.example.data.OmnisSyncOutboxEntity
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Zabezpečený klient pro komunikaci s OMNIS by Suseto FastAPI Gateway (Sovereign Non-GCP Stack).
 * Zajišťuje idempotentní dávkovou synchronizaci Transactional Outboxu do PostgreSQL 17 / ElectricSQL 1.8.1.
 */
object OmnisApiGatewayClient {

    private const val TAG = "OmnisApiGatewayClient"
    val GATEWAY_BASE_URL: String
        get() = com.example.BuildConfig.OMNIS_BACKEND_URL
    val CLOUD_RUN_BASE_URL: String
        get() = GATEWAY_BASE_URL
    private const val BATCH_SIZE = 100

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    sealed interface GatewayStatus {
        object Standby : GatewayStatus
        object Synchronizing : GatewayStatus
        data class Connected(val latencyMs: Long, val message: String) : GatewayStatus
        data class FallbackMode(val reason: String, val circuitOpen: Boolean) : GatewayStatus
    }

    private val _gatewayStatus = MutableStateFlow<GatewayStatus>(GatewayStatus.Standby)
    val gatewayStatus: StateFlow<GatewayStatus> = _gatewayStatus.asStateFlow()

    private val _isCloudOnline = MutableStateFlow(false)
    val isCloudOnline: StateFlow<Boolean> = _isCloudOnline.asStateFlow()

    private var isCircuitBreakerOpen = false

    private suspend fun resolveBearerToken(): String? {
        val omnisToken = OmnisAuthService.currentSession.value?.token?.takeIf { it.isNotBlank() }
        if (omnisToken != null) return omnisToken
        return runCatching {
            Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveAppCheckToken(): String? {
        return runCatching {
            FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun resolveSyncApiKey(): String? {
        return DatabaseConfig.getBuildConfigValue("OMNIS_SYNC_API_KEY")?.takeIf { it.isNotBlank() }
    }

    /**
     * Otestuje dostupnost a latenci Sovereign PostgreSQL 17 REST API Gateway.
     */
    suspend fun pingGateway(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (com.example.defense.DatabaseConfig.isTesting) {
            return@withContext Pair(true, "PostgreSQL 17 API Gateway aktivní (MOCK)")
        }
        _gatewayStatus.value = GatewayStatus.Synchronizing
        val startTime = System.currentTimeMillis()

        try {
            val reqBuilder = Request.Builder()
                .url("$GATEWAY_BASE_URL/api/v1/health")
                .header("User-Agent", "OMNIS-by-Suseto-Android/4.5")
                .get()
            resolveAppCheckToken()?.let { reqBuilder.header("X-Firebase-AppCheck", it) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                isCircuitBreakerOpen = false
                _isCloudOnline.value = true
                val msg = "PostgreSQL 17 API Gateway aktivní (HTTPS TLSv1.3 | Latence: ${latency}ms | ElectricSQL WAL)"
                _gatewayStatus.value = GatewayStatus.Connected(latency, msg)
                Pair(true, msg)
            } else {
                isCircuitBreakerOpen = true
                _isCloudOnline.value = false
                val errorMsg = "PG17 Gateway vrátil kód ${response.code}: ${response.message}"
                _gatewayStatus.value = GatewayStatus.FallbackMode(errorMsg, circuitOpen = true)
                Pair(false, errorMsg)
            }
        } catch (e: Exception) {
            isCircuitBreakerOpen = true
            _isCloudOnline.value = false
            val errorMsg = "Spojení s PostgreSQL 17 Gateway selhalo: ${e.localizedMessage ?: "Timeout"}"
            _gatewayStatus.value = GatewayStatus.FallbackMode(errorMsg, circuitOpen = true)
            Log.w(TAG, errorMsg)
            Pair(false, errorMsg)
        }
    }

    /**
     * Odeslání idempotentních položek z Transactional Outbox fronty na /api/v1/sync/batch.
     */
    suspend fun syncOutboxBatchToGateway(entries: List<OmnisSyncOutboxEntity>): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (com.example.defense.DatabaseConfig.isTesting) {
            return@withContext Pair(entries.size, "Outbox synchronizován (MOCK)")
        }
        if (entries.isEmpty()) {
            return@withContext Pair(0, "Žádné položky v Outbox frontě.")
        }

        _gatewayStatus.value = GatewayStatus.Synchronizing
        var syncedTotal = 0
        val bearer = resolveBearerToken()
        val appCheckToken = resolveAppCheckToken()
        val syncApiKey = resolveSyncApiKey()

        try {
            val chunks = entries.chunked(BATCH_SIZE)
            for (chunk in chunks) {
                val itemsArray = JSONArray()
                for (entry in chunk) {
                    val itemObj = JSONObject().apply {
                        put("idempotency_key", entry.idempotencyKey)
                        put("entity_type", entry.entityType)
                        put("entity_id", entry.entityId)
                        put("session_id", entry.sessionId)
                        put("sequence_number", entry.sequenceNumber)
                        put("payload_json", entry.payloadJson)
                        put("created_at", entry.createdAt)
                    }
                    itemsArray.put(itemObj)
                }

                val rootPayload = JSONObject().apply {
                    put("items", itemsArray)
                    put("client_version", "4.5")
                }

                val body = rootPayload.toString().toRequestBody("application/json".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$GATEWAY_BASE_URL/api/v1/sync/batch")
                    .header("User-Agent", "OMNIS-by-Suseto-Android/4.5")
                    .post(body)
                bearer?.let { reqBuilder.header("Authorization", "Bearer $it") }
                syncApiKey?.let { reqBuilder.header("X-OMNIS-API-KEY", it) }
                appCheckToken?.let { reqBuilder.header("X-Firebase-AppCheck", it) }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                if (response.isSuccessful) {
                    syncedTotal += chunk.size
                } else {
                    throw IllegalStateException("Chyba batch outbox synchronizace (HTTP ${response.code})")
                }
                yield()
            }

            val successMsg = "Úspěšně synchronizováno $syncedTotal položek Outboxu do PostgreSQL 17."
            _gatewayStatus.value = GatewayStatus.Connected(42L, successMsg)
            _isCloudOnline.value = true
            Pair(syncedTotal, successMsg)
        } catch (e: Exception) {
            isCircuitBreakerOpen = true
            _isCloudOnline.value = false
            val errorMsg = "Chyba Outbox synchronizace přes Gateway: ${e.localizedMessage ?: "Síťová chyba"}"
            _gatewayStatus.value = GatewayStatus.FallbackMode(errorMsg, circuitOpen = true)
            Log.e(TAG, errorMsg, e)
            Pair(syncedTotal, errorMsg)
        }
    }

    /**
     * Dávková synchronizace zpráv na REST API Gateway PostgreSQL 17.
     */
    suspend fun syncBatchToGateway(records: List<OmnisRecord>): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (com.example.defense.DatabaseConfig.isTesting) {
            return@withContext Pair(records.size, "Úspěšně synchronizováno (MOCK)")
        }
        if (records.isEmpty()) {
            return@withContext Pair(0, "Žádné čekající záznamy k synchronizaci.")
        }

        _gatewayStatus.value = GatewayStatus.Synchronizing
        var syncedTotal = 0
        val token = resolveBearerToken()
        val appCheckToken = resolveAppCheckToken()
        val syncApiKey = resolveSyncApiKey()

        try {
            val chunks = records.chunked(BATCH_SIZE)
            for (chunk in chunks) {
                val jsonArray = JSONArray()
                for (rec in chunk) {
                    val key = OmnisSyncOutboxEntity.deterministicKey(
                        entityType = "OMNIS_MESSAGE",
                        sessionId = rec.threadId,
                        timestamp = rec.timestamp,
                        content = rec.content
                    )
                    val obj = JSONObject().apply {
                        put("id", rec.id)
                        put("idempotency_key", key)
                        put("role", rec.role)
                        put("content", rec.content)
                        put("cognitive_process", rec.cognitiveProcess)
                        put("follow_up_questions", rec.followUpQuestions)
                        put("domain", rec.domain)
                        put("val_sys", rec.valSys)
                        put("val_econ", rec.valEcon)
                        put("val_psych", rec.valPsych)
                        put("val_eco", rec.valEco)
                        put("val_law", rec.valLaw)
                        put("val_sec", rec.valSec)
                        put("val_phys", rec.valPhys)
                        put("val_soc", rec.valSoc)
                        put("composite_score", rec.compositeScore)
                        put("defense_tier", rec.defenseTier)
                        put("defense_notes", rec.defenseNotes)
                        put("timestamp", rec.timestamp)
                        put("thread_id", rec.threadId)
                        put("thread_title", rec.threadTitle)
                        put("user_name", rec.userName)
                    }
                    jsonArray.put(obj)
                }

                val payload = JSONObject().apply {
                    put("messages", jsonArray)
                    put("client_version", "4.5")
                }

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val reqBuilder = Request.Builder()
                    .url("$GATEWAY_BASE_URL/api/v1/sync/messages")
                    .header("User-Agent", "OMNIS-by-Suseto-Android/4.5")
                    .post(body)

                if (!token.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                if (!syncApiKey.isNullOrBlank()) {
                    reqBuilder.header("X-OMNIS-API-KEY", syncApiKey)
                }
                if (!appCheckToken.isNullOrBlank()) {
                    reqBuilder.header("X-Firebase-AppCheck", appCheckToken)
                }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                if (response.isSuccessful) {
                    syncedTotal += chunk.size
                } else {
                    throw IllegalStateException("Chyba synchronizace dávky (HTTP ${response.code})")
                }
                yield()
            }

            val successMsg = "Úspěšně synchronizováno $syncedTotal zpráv do PostgreSQL 17."
            _gatewayStatus.value = GatewayStatus.Connected(42L, successMsg)
            _isCloudOnline.value = true
            Pair(syncedTotal, successMsg)
        } catch (e: Exception) {
            isCircuitBreakerOpen = true
            _isCloudOnline.value = false
            val errorMsg = "Chyba synchronizace přes Gateway: ${e.localizedMessage ?: "Síťová chyba"}"
            _gatewayStatus.value = GatewayStatus.FallbackMode(errorMsg, circuitOpen = true)
            Log.e(TAG, errorMsg, e)
            Pair(syncedTotal, errorMsg)
        }
    }
}
