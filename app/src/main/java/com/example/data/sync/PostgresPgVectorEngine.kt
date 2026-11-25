package com.example.data.sync

import com.example.engine.VectorRagEngine
import kotlin.math.sqrt

data class PgVectorEntity(
    val id: String,
    val entityName: String,
    val vector64: FloatArray,
    val impactMatrixJson: String = "{}",
    val phaseState: String = "PHASE_I_COLLECTION",
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PgVectorEntity
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class PgVectorSearchResult(
    val entity: PgVectorEntity,
    val cosineSimilarity: Float,
    val hnswDistance: Float
)

object PostgresPgVectorEngine {

    const val DDL_CREATE_EXTENSION = "CREATE EXTENSION IF NOT EXISTS vector;"

    const val DDL_CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS omnis_memory_embeddings (
            id VARCHAR(64) PRIMARY KEY,
            entity_name VARCHAR(128) NOT NULL,
            embedding vector(64) NOT NULL,
            impact_matrix_json TEXT,
            phase_state VARCHAR(32) DEFAULT 'PHASE_I_COLLECTION',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """

    const val DDL_CREATE_HNSW_INDEX = """
        CREATE INDEX IF NOT EXISTS omnis_memory_hnsw_idx 
        ON omnis_memory_embeddings 
        USING hnsw (embedding vector_cosine_ops)
        WITH (m = 16, ef_construction = 64);
    """

    private val SAFE_ID_REGEX = Regex("^[A-Za-z0-9_.\\-:]{1,64}$")
    private val SAFE_PHASE_REGEX = Regex("^[A-Za-z0-9_\\-]{1,32}$")

    private fun formatSafeVectorLiteral(vector: FloatArray): String {
        require(vector.isNotEmpty()) { "Vector must not be empty" }
        return "[" + vector.joinToString(",") { v ->
            require(v.isFinite()) { "Vector component must be finite" }
            String.format(java.util.Locale.US, "%.5f", v)
        } + "]"
    }

    private fun escapeSqlStringLiteral(value: String): String {
        return value
            .replace("\u0000", "")
            .replace("'", "''")
    }

    /**
     * Generuje SQL INSERT dotaz s formátovaným pgvector polem `[v1,v2,...,v64]`.
     */
    fun generatePgVectorInsertQuery(entity: PgVectorEntity): String {
        require(SAFE_ID_REGEX.matches(entity.id)) { "Invalid PgVectorEntity id format" }
        require(SAFE_PHASE_REGEX.matches(entity.phaseState)) { "Invalid PgVectorEntity phaseState format" }
        val vectorStr = formatSafeVectorLiteral(entity.vector64)
        val escapedName = escapeSqlStringLiteral(entity.entityName)
        val escapedMatrix = escapeSqlStringLiteral(entity.impactMatrixJson)
        return "INSERT INTO omnis_memory_embeddings (id, entity_name, embedding, impact_matrix_json, phase_state) VALUES ('${entity.id}', '$escapedName', '$vectorStr'::vector, '$escapedMatrix', '${entity.phaseState}') ON CONFLICT (id) DO UPDATE SET embedding = EXCLUDED.embedding, impact_matrix_json = EXCLUDED.impact_matrix_json;"
    }

    /**
     * Generuje pgvector HNSW kosinový vyhladávací SQL dotaz.
     */
    fun generateHnswSearchQuery(queryVector: FloatArray, topK: Int = 3): String {
        val safeTopK = topK.coerceIn(1, 500)
        val vectorStr = formatSafeVectorLiteral(queryVector)
        return "SELECT id, entity_name, impact_matrix_json, phase_state, (1 - (embedding <=> '$vectorStr'::vector)) AS cosine_similarity FROM omnis_memory_embeddings ORDER BY embedding <=> '$vectorStr'::vector LIMIT $safeTopK;"
    }

    /**
     * Provede HNSW kosinové vyhledávání v lokální pgvector paměťové struktuře.
     */
    fun searchInMemoryPgVector(
        queryVector: FloatArray,
        entities: List<PgVectorEntity>,
        topK: Int = 3
    ): List<PgVectorSearchResult> {
        return entities.map { entity ->
            val sim = VectorRagEngine.calculateCosineSimilarity(queryVector, entity.vector64)
            val hnswDist = (1.0f - sim).coerceIn(0.0f, 2.0f)
            PgVectorSearchResult(
                entity = entity,
                cosineSimilarity = sim,
                hnswDistance = hnswDist
            )
        }
        .sortedByDescending { it.cosineSimilarity }
        .take(topK)
    }
}
