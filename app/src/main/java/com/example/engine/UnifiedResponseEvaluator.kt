package com.example.engine

import com.example.data.MemoryFragment
import com.example.defense.SigmaOmegaRedTeamingEngine

data class UnifiedEvaluationScore(
    val query: String,
    val response: String,
    val ragCosineScore: Float,
    val tensorStabilityScore: Float,
    val impactMatrixScore: Float,
    val zeroFluffScore: Float,
    val unifiedQualityIndex: Float,
    val qualityTier: String
)

object UnifiedResponseEvaluator {

    /**
     * Vypočítá Jednotný Index Kvality Q_omnis napříč všemi 4 vyhodnocovacími pilíři:
     * Q_omnis = 0.30 * S_cos + 0.25 * S_tensor + 0.25 * S_impact + 0.20 * S_zero_fluff
     */
    fun evaluateResponse(
        query: String,
        response: String,
        memoryFragments: List<MemoryFragment> = emptyList()
    ): UnifiedEvaluationScore {
        // 1. 64D RAG Cosine Match (S_cos)
        val matches = VectorRagEngine.searchVectorMemory(query, memoryFragments, topK = 1)
        val ragCos = if (matches.isNotEmpty()) matches[0].cosineSimilarity else 0.75f

        // 2. 8D Tensor Coherence & Stability (S_tensor)
        val coherence = TensorCoherenceEvaluator.evaluateCoherence(query, response, matches)
        val tensorStab = coherence.overallScore / 100f

        // 3. Win-Win-Win Impact Matrix (S_impact)
        val impactResult = OmnisFivePhaseExecutor.evaluateImpactMatrix(query, emptyMap())
        val impactScore = impactResult.winWinWinScore

        // 4. Zero-Fluff Compliance (S_zero_fluff)
        val isZeroFluff = ApexOrchestratorEngine.isZeroFluffCompliant(response)
        val zeroFluffScore = if (isZeroFluff) 1.0f else 0.40f

        // Celkové agregované Q_omnis skóre
        val unifiedIndex = (
            0.30f * ragCos +
            0.25f * tensorStab +
            0.25f * impactScore +
            0.20f * zeroFluffScore
        ).coerceIn(0.0f, 1.0f)

        val tier = when {
            unifiedIndex >= 0.90f -> "EXCELLENT"
            unifiedIndex >= 0.70f -> "OPTIMAL"
            unifiedIndex >= 0.45f -> "MODERATE"
            else -> "NEEDS_RESTORATION"
        }

        return UnifiedEvaluationScore(
            query = query,
            response = response,
            ragCosineScore = ragCos,
            tensorStabilityScore = tensorStab,
            impactMatrixScore = impactScore,
            zeroFluffScore = zeroFluffScore,
            unifiedQualityIndex = unifiedIndex,
            qualityTier = tier
        )
    }
}
