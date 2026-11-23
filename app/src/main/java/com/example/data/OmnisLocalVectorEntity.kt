package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "omnis_local_vectors_64d",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["domain", "createdAt"])
    ]
)
data class OmnisLocalVectorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val idempotencyKey: String,
    val userId: String,
    val threadId: String = "thread_main",
    val domain: String = "SYSTEMS_INTELLIGENCE",
    val content: String,
    val vector64Csv: String,
    val normL2: Float = 1.0f,
    val importanceScore: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface OmnisLocalVectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVector(entity: OmnisLocalVectorEntity): Long

    @Query("SELECT * FROM omnis_local_vectors_64d WHERE domain = :domain ORDER BY importanceScore DESC, createdAt DESC LIMIT :limit")
    suspend fun getTopVectorsByDomain(domain: String, limit: Int = 10): List<OmnisLocalVectorEntity>

    @Query("SELECT COUNT(*) FROM omnis_local_vectors_64d")
    suspend fun countVectors(): Int
}
