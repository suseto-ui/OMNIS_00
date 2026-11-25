package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SyncLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val taskType: String,
    val success: Boolean,
    val syncedCount: Int,
    val payloadSizeBytes: Long,
    val durationMs: Long = 0L,
    val message: String
)

object SyncLogManager {
    private const val PREFS_NAME = "omnis_sync_logs_prefs"
    private const val KEY_LOGS_JSON = "sync_logs_json"
    private const val KEY_LAST_SUCCESS_TIME = "last_successful_sync_time"
    private const val MAX_IN_MEMORY_LOGS = 300
    private const val MAX_PERSISTED_LOGS = 100

    private val _logs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val logs: StateFlow<List<SyncLogEntry>> = _logs.asStateFlow()

    private val _lastSuccessfulSyncTime = MutableStateFlow<Long?>(null)
    val lastSuccessfulSyncTime: StateFlow<Long?> = _lastSuccessfulSyncTime.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            val savedTime = sp.getLong(KEY_LAST_SUCCESS_TIME, 0L)
            if (savedTime > 0L && (_lastSuccessfulSyncTime.value == null || savedTime > (_lastSuccessfulSyncTime.value ?: 0L))) {
                _lastSuccessfulSyncTime.value = savedTime
            }
            val rawJson = sp.getString(KEY_LOGS_JSON, null)
            if (!rawJson.isNullOrBlank()) {
                val restored = parseLogsJson(rawJson)
                if (restored.isNotEmpty()) {
                    _logs.update { current ->
                        val merged = (current + restored)
                            .distinctBy { it.id }
                            .sortedByDescending { it.timestamp }
                            .take(MAX_IN_MEMORY_LOGS)
                        merged
                    }
                }
            }
        }
    }

    fun logSync(
        taskType: String,
        success: Boolean,
        syncedCount: Int,
        payloadSizeBytes: Long,
        message: String,
        durationMs: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        val entry = SyncLogEntry(
            timestamp = now,
            taskType = taskType,
            success = success,
            syncedCount = syncedCount,
            payloadSizeBytes = payloadSizeBytes.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            message = message
        )
        var snapshotForPersist: List<SyncLogEntry> = emptyList()
        _logs.update { current ->
            val updated = (listOf(entry) + current).take(MAX_IN_MEMORY_LOGS)
            snapshotForPersist = updated
            updated
        }
        if (success) {
            _lastSuccessfulSyncTime.value = now
        }
        persistAsyncSafe(snapshotForPersist, if (success) now else null)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        prefs?.edit()?.remove(KEY_LOGS_JSON)?.apply()
    }

    internal fun resetForTesting() {
        _logs.value = emptyList()
        _lastSuccessfulSyncTime.value = null
        prefs?.edit()?.clear()?.commit()
        prefs = null
    }

    private fun persistAsyncSafe(entries: List<SyncLogEntry>, lastSuccessTime: Long?) {
        val sp = prefs ?: return
        runCatching {
            val arr = JSONArray()
            entries.take(MAX_PERSISTED_LOGS).forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("timestamp", entry.timestamp)
                        put("taskType", entry.taskType)
                        put("success", entry.success)
                        put("syncedCount", entry.syncedCount)
                        put("payloadSizeBytes", entry.payloadSizeBytes)
                        put("durationMs", entry.durationMs)
                        put("message", entry.message)
                    }
                )
            }
            sp.edit().apply {
                putString(KEY_LOGS_JSON, arr.toString())
                if (lastSuccessTime != null) {
                    putLong(KEY_LAST_SUCCESS_TIME, lastSuccessTime)
                }
                apply()
            }
        }
    }

    private fun parseLogsJson(raw: String): List<SyncLogEntry> {
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        SyncLogEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            taskType = obj.optString("taskType", "Sync"),
                            success = obj.optBoolean("success", false),
                            syncedCount = obj.optInt("syncedCount", 0),
                            payloadSizeBytes = obj.optLong("payloadSizeBytes", 0L),
                            durationMs = obj.optLong("durationMs", 0L),
                            message = obj.optString("message", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
