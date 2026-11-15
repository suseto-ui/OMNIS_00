package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.MessageDigest

data class CognitiveBackupPackage(
    val version: String = "4.2",
    val timestamp: Long = System.currentTimeMillis(),
    val records: List<OmnisRecord> = emptyList(),
    val fragments: List<MemoryFragment> = emptyList(),
    val goals: List<OmnisGoal> = emptyList(),
    val artifacts: List<OmnisArtifact> = emptyList(),
    val checksum: String = ""
)

object CognitiveStateBackupEngine {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(CognitiveBackupPackage::class.java)

    /**
     * Vyexportuje kompletní stav kognitivní paměti, cílů a artefaktů do JSON řetězce s kontrolním součtem (SHA-256).
     */
    fun exportToJson(
        records: List<OmnisRecord>,
        fragments: List<MemoryFragment>,
        goals: List<OmnisGoal>,
        artifacts: List<OmnisArtifact>
    ): String {
        val rawPackage = CognitiveBackupPackage(
            records = records,
            fragments = fragments,
            goals = goals,
            artifacts = artifacts,
            checksum = ""
        )
        val rawJson = adapter.toJson(rawPackage)
        val checksum = computeSha256(rawJson)
        val finalPackage = rawPackage.copy(checksum = checksum)
        return adapter.toJson(finalPackage)
    }

    /**
     * Importuje a verifikuje kognitivní balíček z JSON řetězce.
     */
    fun importFromJson(jsonString: String): Result<CognitiveBackupPackage> {
        return try {
            val pkg = adapter.fromJson(jsonString) ?: return Result.failure(Exception("Prázdný nebo neplatný JSON balíček."))
            
            // Verifikace obsahu
            if (pkg.version.isBlank()) {
                return Result.failure(Exception("Nekompatibilní verze kognitivního balíčku."))
            }

            Result.success(pkg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun computeSha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
