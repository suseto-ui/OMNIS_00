package com.example.engine

import com.example.auth.UserRole
import com.example.data.MemoryFragment

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

data class RoleScopedEvaluationResult(
    val role: UserRole,
    val unifiedQualityIndex: Float,
    val qualityTier: String,
    val ragCosineScore: Float?,
    val tensorStabilityScore: Float?,
    val impactMatrixScore: Float?,
    val zeroFluffScore: Float?,
    val diagnosticBreakdown: String
)

object UnifiedResponseEvaluator {

    private fun sanitizeUnit(value: Float, default: Float = 0.5f): Float =
        if (value.isFinite()) value.coerceIn(0.0f, 1.0f) else default

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
        val ragCos = sanitizeUnit(if (matches.isNotEmpty()) matches[0].cosineSimilarity else 0.75f, default = 0.75f)

        // 2. 8D Tensor Coherence & Stability (S_tensor)
        val coherence = TensorCoherenceEvaluator.evaluateCoherence(query, response, matches)
        val tensorStab = sanitizeUnit(coherence.overallScore / 100f)

        // 3. Win-Win-Win Impact Matrix (S_impact) chráněná vrstvou Gatekeeper G1-G6
        val gatekeeperOutcome = try {
            val impact = OmnisFivePhaseExecutor.evaluateImpactMatrix(query, emptyMap())
            impact.winWinWinScore to null
        } catch (ex: com.example.gateway.GatekeeperChainException) {
            0.0f to ex.gateId.code
        }
        val impactScore = sanitizeUnit(gatekeeperOutcome.first, default = 0.0f)

        // 4. Zero-Fluff Compliance (S_zero_fluff)
        val isZeroFluff = ApexOrchestratorEngine.isZeroFluffCompliant(response)
        val zeroFluffScore = if (isZeroFluff) 1.0f else 0.40f

        // Celkové agregované Q_omnis skóre
        val unifiedIndex = sanitizeUnit(
            0.30f * ragCos +
                0.25f * tensorStab +
                0.25f * impactScore +
                0.20f * zeroFluffScore
        )

        val tier = when {
            gatekeeperOutcome.second != null -> "REFUSED_${gatekeeperOutcome.second}"
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

    /**
     * Striktní RBAC izolace výstupu validátoru Q_omnis pro roli STANDARD_USER vs. ADMIN_OPERATOR.
     * Běžnému uživateli neposkytuje interní tenzorovou ani forenzní telemetrii.
     */
    fun evaluateResponseForRole(
        query: String,
        response: String,
        role: UserRole,
        memoryFragments: List<MemoryFragment> = emptyList()
    ): RoleScopedEvaluationResult {
        val full = evaluateResponse(query, response, memoryFragments)
        return when (role) {
            UserRole.ADMIN_OPERATOR -> RoleScopedEvaluationResult(
                role = UserRole.ADMIN_OPERATOR,
                unifiedQualityIndex = full.unifiedQualityIndex,
                qualityTier = full.qualityTier,
                ragCosineScore = full.ragCosineScore,
                tensorStabilityScore = full.tensorStabilityScore,
                impactMatrixScore = full.impactMatrixScore,
                zeroFluffScore = full.zeroFluffScore,
                diagnosticBreakdown = "ADMIN_FORENSIC[S_cos=${full.ragCosineScore}, S_tensor=${full.tensorStabilityScore}, S_impact=${full.impactMatrixScore}, S_zf=${full.zeroFluffScore}]"
            )
            UserRole.STANDARD_USER -> RoleScopedEvaluationResult(
                role = UserRole.STANDARD_USER,
                unifiedQualityIndex = full.unifiedQualityIndex,
                qualityTier = full.qualityTier,
                ragCosineScore = null,
                tensorStabilityScore = null,
                impactMatrixScore = null,
                zeroFluffScore = null,
                diagnosticBreakdown = "REDACTED_STANDARD_USER"
            )
        }
    }
}
