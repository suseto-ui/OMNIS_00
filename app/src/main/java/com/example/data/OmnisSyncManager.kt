package com.example.data

import android.content.Context
import android.util.Log
import com.example.api.OmnisGeminiClient
import com.example.auth.OmnisAuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Centrální synchronizační manažer pro obousměrnou synchronizaci konverzací (Room DB <-> Backend API).
 * Zajišťuje plnou podporu offline režimu a automatické sloučení stavů při obnovení připojení.
 */
object OmnisSyncManager {
    private const val TAG = "OmnisSyncManager"
    private val syncScope = CoroutineScope(Dispatchers.IO)
    private var isSyncing = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun resolveBearerToken(): String? {
        return OmnisAuthService.currentSession.value?.token?.takeIf { it.isNotBlank() }
    }

    /**
     * Spustí asynchronní obousměrnou synchronizaci s backendem.
     */
    fun triggerSync(context: Context, repository: OmnisRepository, onComplete: ((Boolean) -> Unit)? = null) {
        if (isSyncing) {
            onComplete?.invoke(false)
            return
        }
        isSyncing = true
        syncScope.launch {
            var success = false
            try {
                Log.i(TAG, "Zahajování obousměrné synchronizace konverzací...")

                // 0. Dávková synchronizace neodeslaných zpráv (/api/v1/sync/messages, max 250 ks)
                val unsyncedRecords = repository.omnisDao.getUnsyncedRecords(
                    com.example.gateway.OmnisCircuitBreaker.SYNC_BATCH_SIZE
                )
                if (unsyncedRecords.isNotEmpty()) {
                    val batchStartMs = System.currentTimeMillis()
                    val payloadBytes = unsyncedRecords.sumOf { rec ->
                        (rec.content + rec.cognitiveProcess + rec.followUpQuestions + rec.defenseNotes)
                            .toByteArray(Charsets.UTF_8).size.toLong()
                    }
                    val (syncedCount, syncMsg) = com.example.data.sync.OmnisApiGatewayClient.syncBatchToGateway(unsyncedRecords)
                    val durationMs = System.currentTimeMillis() - batchStartMs
                    if (syncedCount > 0) {
                        repository.omnisDao.markRecordsAsSynced(unsyncedRecords.take(syncedCount).map { it.id })
                        Log.i(TAG, "Dávkový sync $syncedCount zpráv dokončen: $syncMsg")
                    }
                    SyncLogManager.logSync(
                        taskType = "Bidirectional Push",
                        success = syncedCount > 0,
                        syncedCount = syncedCount,
                        payloadSizeBytes = payloadBytes,
                        durationMs = durationMs,
                        message = syncMsg
                    )
                }

                val bearerToken = resolveBearerToken()

                // 1. Synchronizace lokálních vláken na backend (/api/memory/sync)
                val localThreads = repository.allThreads.first()
                if (localThreads.isNotEmpty() && !bearerToken.isNullOrBlank()) {
                    val syncUrl = OmnisGeminiClient.baseUrl + "api/memory/sync"
                    val threadsArray = JSONArray()
                    for (t in localThreads) {
                        val threadObj = JSONObject().apply {
                            put("id", t.threadId)
                            put("title", t.threadTitle)
                            put("ontologyDomain", "SYSTEMS_INTELLIGENCE")
                        }
                        threadsArray.put(threadObj)
                    }
                    val payload = JSONObject().apply {
                        put("threads", threadsArray)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
                    val requestBody = payloadBytes.toRequestBody(mediaType)
                    val postRequest = Request.Builder()
                        .url(syncUrl)
                        .header("Authorization", "Bearer $bearerToken")
                        .post(requestBody)
                        .build()

                    val pushStartMs = System.currentTimeMillis()
                    httpClient.newCall(postRequest).execute().use { syncResponse ->
                        val syncContentType = syncResponse.header("Content-Type") ?: ""
                        val isJson = syncContentType.contains("application/json", ignoreCase = true)
                        val durationMs = System.currentTimeMillis() - pushStartMs
                        if (syncResponse.isSuccessful && isJson) {
                            Log.i(TAG, "Lokální konverzační vlákna úspěšně nahrána na backend.")
                        } else {
                            val errMsg = "Nahrávání vláken selhalo (HTTP ${syncResponse.code}, Content-Type: $syncContentType)"
                            Log.w(TAG, errMsg)
                            SyncLogManager.logSync(
                                taskType = "Thread Metadata Push",
                                success = false,
                                syncedCount = 0,
                                payloadSizeBytes = payloadBytes.size.toLong(),
                                durationMs = durationMs,
                                message = errMsg
                            )
                        }
                    }
                }

                // 2. Stažení vláken a zpráv z backendu (/api/threads) a jejich uložení do Room DB
                if (bearerToken.isNullOrBlank()) {
                    Log.i(TAG, "Synchronizace vláken přeskočena: uživatel není přihlášen.")
                    return@launch
                }
                val getUrl = OmnisGeminiClient.baseUrl + "api/threads"
                val getRequest = Request.Builder()
                    .url(getUrl)
                    .header("Authorization", "Bearer $bearerToken")
                    .get()
                    .build()

                val pullStartMs = System.currentTimeMillis()
                httpClient.newCall(getRequest).execute().use { getResponse ->
                    val contentType = getResponse.header("Content-Type") ?: ""
                    val isJson = contentType.contains("application/json", ignoreCase = true)
                    val durationMs = System.currentTimeMillis() - pullStartMs
                    if (getResponse.isSuccessful) {
                        val bodyString = getResponse.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val trimmed = bodyString.trim()
                            val responseBytes = trimmed.toByteArray(Charsets.UTF_8).size.toLong()
                            if (isJson && trimmed.startsWith("[")) {
                                try {
                                    val jsonArray = JSONArray(trimmed)
                                    Log.i(TAG, "Načteno ${jsonArray.length()} vláken z backendu k integraci.")

                                    val incomingRecords = mutableListOf<OmnisRecord>()
                                    for (i in 0 until jsonArray.length()) {
                                        val threadObj = jsonArray.getJSONObject(i)
                                        val threadId = threadObj.getString("id")
                                        val threadTitle = threadObj.optString("title", "Synchronizované Vlákno")
                                        val domain = threadObj.optString("ontology_domain", "SYSTEMS_INTELLIGENCE")
                                        val messagesArray = threadObj.optJSONArray("messages") ?: JSONArray()

                                        for (j in 0 until messagesArray.length()) {
                                            val msgObj = messagesArray.getJSONObject(j)
                                            val msgId = msgObj.optString("id")
                                            val role = msgObj.getString("role")
                                            val content = msgObj.getString("content")
                                            val thoughts = msgObj.optString("cognitive_thoughts", "")

                                            val followUpsArray = msgObj.optJSONArray("follow_up_questions")
                                            val followUpsStr = if (followUpsArray != null) {
                                                val list = mutableListOf<String>()
                                                for (k in 0 until followUpsArray.length()) {
                                                    list.add(followUpsArray.getString(k))
                                                }
                                                list.joinToString("|")
                                            } else ""

                                            // Bezpečný převod ID zprávy (UUID -> Long) pro Room DB primární klíč
                                            val parsedId = try {
                                                UUID.fromString(msgId).mostSignificantBits and Long.MAX_VALUE
                                            } catch (e: Exception) {
                                                (msgId.hashCode().toLong() and 0xFFFFFFFFL) + (j * 1000000)
                                            }

                                            val localRecord = OmnisRecord(
                                                id = parsedId,
                                                role = role,
                                                content = content,
                                                cognitiveProcess = thoughts,
                                                followUpQuestions = followUpsStr,
                                                threadId = threadId,
                                                threadTitle = threadTitle,
                                                domain = domain,
                                                isSyncedToPostgres = true
                                            )
                                            incomingRecords.add(localRecord)
                                        }
                                    }
                                    if (incomingRecords.isNotEmpty()) {
                                        repository.insertRecordsBatched(incomingRecords)
                                    }
                                    val prunedCount = repository.pruneOldSyncedRecords(daysOld = 14)
                                    val msg = "Obousměrná integrace dokončena (${incomingRecords.size} zpráv uloženo, $prunedCount starých záznamů pročištěno)."
                                    Log.i(TAG, msg)
                                    SyncLogManager.logSync(
                                        taskType = "Bidirectional Pull",
                                        success = true,
                                        syncedCount = incomingRecords.size,
                                        payloadSizeBytes = responseBytes,
                                        durationMs = durationMs,
                                        message = msg
                                    )
                                    success = true
                                } catch (e: org.json.JSONException) {
                                    val err = "Chyba při parsování JSON odpovědi z backendu: ${e.message}"
                                    Log.e(TAG, err)
                                    SyncLogManager.logSync(
                                        taskType = "Bidirectional Pull",
                                        success = false,
                                        syncedCount = 0,
                                        payloadSizeBytes = responseBytes,
                                        durationMs = durationMs,
                                        message = err
                                    )
                                }
                            } else {
                                Log.w(TAG, "Ignorována odpověď z backendu, protože to není platný JSONArray. Začátek odpovědi: ${trimmed.take(100)}")
                            }
                        }
                    } else {
                        val err = "Načtení vláken z backendu selhalo (HTTP ${getResponse.code}, Content-Type: $contentType)"
                        Log.w(TAG, err)
                        SyncLogManager.logSync(
                            taskType = "Bidirectional Pull",
                            success = false,
                            syncedCount = 0,
                            payloadSizeBytes = 0L,
                            durationMs = durationMs,
                            message = err
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chyba při obousměrné synchronizaci konverzací: ${e.message}", e)
                SyncLogManager.logSync(
                    taskType = "Bidirectional Sync Exception",
                    success = false,
                    syncedCount = 0,
                    payloadSizeBytes = 0L,
                    message = "Výjimka při obousměrné synchronizaci: ${e.localizedMessage ?: "Unknown"}"
                )
            } finally {
                isSyncing = false
                onComplete?.invoke(success)
            }
        }
    }
}
