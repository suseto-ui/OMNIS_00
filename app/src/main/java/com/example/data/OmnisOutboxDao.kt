package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OmnisOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: OmnisSyncOutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entries: List<OmnisSyncOutboxEntity>): List<Long>

    @Query(
        "SELECT * FROM omnis_sync_outbox " +
            "WHERE syncStatus = 'PENDING' OR (syncStatus = 'FAILED' AND retryCount < :maxRetries) " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun getPendingEntries(limit: Int = 250, maxRetries: Int = 5): List<OmnisSyncOutboxEntity>

    @Query(
        "SELECT COUNT(*) FROM omnis_sync_outbox " +
            "WHERE syncStatus = 'PENDING' OR (syncStatus = 'FAILED' AND retryCount < :maxRetries)"
    )
    suspend fun getPendingCount(maxRetries: Int = 5): Int

    @Query(
        "UPDATE omnis_sync_outbox SET syncStatus = 'SYNCED', syncedAt = :syncedAt, lastError = NULL " +
            "WHERE id IN (:ids)"
    )
    suspend fun markSynced(ids: List<Long>, syncedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE omnis_sync_outbox SET syncStatus = 'FAILED', retryCount = retryCount + 1, lastError = :error " +
            "WHERE id = :id"
    )
    suspend fun markFailed(id: Long, error: String)
}
