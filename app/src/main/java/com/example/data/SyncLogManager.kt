package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SyncLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val taskType: String,
    val success: Boolean,
    val syncedCount: Int,
    val payloadSizeBytes: Long,
    val message: String
)

object SyncLogManager {
    private val _logs = MutableStateFlow<List<SyncLogEntry>>(
        listOf(
            SyncLogEntry(
                taskType = "Initialization",
                success = true,
                syncedCount = 0,
                payloadSizeBytes = 128L,
                message = "ElectricSQL / PostgreSQL 17 Outbox Sync pipeline initialized successfully."
            )
        )
    )
    val logs: StateFlow<List<SyncLogEntry>> = _logs.asStateFlow()

    fun logSync(
        taskType: String,
        success: Boolean,
        syncedCount: Int,
        payloadSizeBytes: Long,
        message: String
    ) {
        val entry = SyncLogEntry(
            taskType = taskType,
            success = success,
            syncedCount = syncedCount,
            payloadSizeBytes = payloadSizeBytes,
            message = message
        )
        val updated = listOf(entry) + _logs.value
        _logs.value = updated.take(300)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
