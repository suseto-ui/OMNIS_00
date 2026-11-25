package com.example.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.util.UUID

/**
 * Transactional Outbox pro garantovanou idempotentní synchronizaci lokálních záznamů
 * do PostgreSQL 17 / ElectricSQL (/api/v1/sync/batch) bez ztráty dat při výpadku sítě.
 */
@Immutable
@Entity(
    tableName = "omnis_sync_outbox",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["syncStatus", "createdAt"])
    ]
)
data class OmnisSyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val idempotencyKey: String,
    val entityType: String = "OMNIS_MESSAGE",
    val entityId: Long = 0L,
    val sessionId: String = "thread_main",
    val sequenceNumber: Long = 0L,
    val payloadJson: String,
    val syncStatus: String = STATUS_PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"

        fun deterministicKey(
            entityType: String,
            sessionId: String,
            timestamp: Long,
            content: String
        ): String {
            val raw = "$entityType|$sessionId|$timestamp|${content.take(256)}"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
            return "outbox_${entityType.lowercase()}_$hex"
        }

        fun randomKey(entityType: String): String =
            "outbox_${entityType.lowercase()}_${UUID.randomUUID().toString().replace("-", "")}"
    }
}
