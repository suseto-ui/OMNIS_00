package vault.omnis.praxeological

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * [NODE-PRX-001] O.M.N.I.S. v7.0 Praxeological Vault Reference Orchestrator.
 * Canonical reference blueprint synchronized with `com.example.engine.ApexOrchestratorEngine`
 * and `com.example.engine.OmnisFivePhaseExecutor`.
 *
 * Enforces:
 * 1. Mutex-serialized state transitions preventing coroutine race conditions [ERR-001 / FIX-001].
 * 2. Zero-Fluff output sanitization stripping internal `<scratchpad>` reasoning.
 * 3. Deterministic SHA-256 canonical commitment sealing matching `zk_audit_ledger`.
 */
object OmnisOrchestrator {

    private val orchestrationMutex = Mutex()
    private val scratchpadPattern = Regex("(?s)<scratchpad>.*?</scratchpad>")
    private val prohibitedIntros = listOf(
        "dobrý den", "zdravím", "zde je", "rád vám pomohu",
        "na základě vašeho dotazu", "předkládám vám", "jako ai model"
    )

    data class OrchestrationTrace(
        val queryHash: String,
        val sanitizedArtifact: String,
        val winWinWinScore: Float,
        val commitmentHash: String,
        val zeroFluffCompliant: Boolean
    )

    suspend fun executeGuardedPipeline(
        query: String,
        rawResponse: String,
        domainScores: Map<String, Float>,
        prevHash: String = "GENESIS",
        nonce: String
    ): OrchestrationTrace = orchestrationMutex.withLock {
        val cleaned = sanitizeZeroFluff(rawResponse)
        val winScore = computeWinWinWinScore(domainScores)
        val queryHash = sha256Hex(query.trim())
        val rSystemic = computeHarmonicMean(domainScores.values.toList())
        val canonicalPayload = buildString {
            append("OMNIS-ZK-PROOF||")
            append(prevHash).append("||")
            append(queryHash).append("||")
            append(String.format(java.util.Locale.US, "%.4f", rSystemic)).append("||")
            append("APPROVED||")
            append(nonce)
        }

        OrchestrationTrace(
            queryHash = queryHash,
            sanitizedArtifact = cleaned,
            winWinWinScore = winScore,
            commitmentHash = sha256Hex(canonicalPayload),
            zeroFluffCompliant = isZeroFluffValid(cleaned)
        )
    }

    fun sanitizeZeroFluff(input: String): String {
        var result = input.replace(scratchpadPattern, "").trim()
        val lower = result.lowercase()
        for (intro in prohibitedIntros) {
            if (lower.startsWith(intro)) {
                val sepIdx = result.indexOfAny(charArrayOf('.', '\n', ':'))
                result = if (sepIdx in 0 until result.length - 1) {
                    result.substring(sepIdx + 1).trim()
                } else {
                    result.removePrefix(intro).trim()
                }
                break
            }
        }
        return result
    }

    fun isZeroFluffValid(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains("<scratchpad>", ignoreCase = true)) return false
        val lower = text.lowercase()
        return prohibitedIntros.none { lower.startsWith(it) }
    }

    fun computeWinWinWinScore(weights: Map<String, Float>): Float {
        val econ = ((weights["ECON"] ?: 0.82f) * 90f + 5f).coerceIn(40f, 99f)
        val sys = ((weights["SYS"] ?: 0.88f) * 92f + 6f).coerceIn(50f, 99f)
        val eco = ((weights["ECO"] ?: 0.85f) * 88f + 8f).coerceIn(45f, 99f)
        val psych = ((weights["PSYCH"] ?: 0.80f) * 85f + 10f).coerceIn(40f, 99f)
        return ((econ * 0.25f + sys * 0.30f + eco * 0.25f + psych * 0.20f) / 100f).coerceIn(0f, 1f)
    }

    fun computeHarmonicMean(values: List<Float>): Float {
        if (values.isEmpty()) return 0.5f
        val safeSum = values.sumOf { 1.0 / it.coerceIn(0.01f, 1.0f).toDouble() }
        return (values.size / safeSum).toFloat().coerceIn(0f, 1f)
    }

    private fun sha256Hex(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
