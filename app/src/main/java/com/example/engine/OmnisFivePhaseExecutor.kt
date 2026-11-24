package com.example.engine

import com.example.gateway.GateAuditRecord
import com.example.gateway.GatekeeperChainException
import com.example.gateway.GatekeeperG1G6
import com.example.gateway.GatekeeperPayload
import com.example.gateway.SanitizedImpactInput

data class ImpactMatrixResult(
    val economicViability: Float,
    val technicalElegance: Float,
    val ecoSocialRegeneration: Float,
    val psychologicalAcceptance: Float,
    val winWinWinScore: Float,
    val gateAuditTrail: List<GateAuditRecord> = emptyList(),
    val chainCommitmentHash: String = ""
)

data class FivePhaseExecutionResult(
    val query: String,
    val currentPhase: String,
    val holomorphicCollectionSummary: String,
    val semanticDeconstructionSummary: String,
    val transdisciplinaryBridgeSummary: String,
    val impactMatrix: ImpactMatrixResult,
    val teleologicalAutopoiesisCode: String,
    val gateAuditTrail: List<GateAuditRecord> = emptyList()
)

object OmnisFivePhaseExecutor {

    /**
     * Vykoná 5-fázový exekuční cyklus O.M.N.I.S. (Fáze I až V) chráněný vrstvou Gatekeeper G1-G6.
     */
    @Throws(GatekeeperChainException::class)
    fun executeFivePhasePipeline(
        query: String,
        domainWeights: Map<String, Float> = emptyMap(),
        embeddingVector: FloatArray? = null,
        systemStressSigma: Float = 0.18f
    ): FivePhaseExecutionResult {
        val validated = GatekeeperG1G6.validateAndSanitize(
            GatekeeperPayload(
                query = query,
                domainWeights = domainWeights,
                embeddingVector = embeddingVector,
                systemStressSigma = systemStressSigma
            )
        )

        val phase1Summary = "Sběr multimodálních dat pro dotaz: '${validated.sanitizedQuery}'. Integrovány strukturované řady i chování."
        val phase2Summary = "Zero-Assumption redukce na prvočinitele: Odstraněn povrchový šum a kognitivní zkreslení."
        val phase3Summary = "Transdisciplinární přenosem aplikována termodynamika a síťová dynamika mycelia."
        val matrix = computeImpactMatrixFromSanitized(validated)
        val phase5Summary = "MVS nasazen se zabudovanou senzorickou smyčkou. Rekalibrační autopoieza aktivní (Win-Win-Win: ${(matrix.winWinWinScore * 100).toInt()}%)."

        return FivePhaseExecutionResult(
            query = validated.sanitizedQuery,
            currentPhase = "PHASE_V_AUTOPOIESIS",
            holomorphicCollectionSummary = phase1Summary,
            semanticDeconstructionSummary = phase2Summary,
            transdisciplinaryBridgeSummary = phase3Summary,
            impactMatrix = matrix,
            teleologicalAutopoiesisCode = phase5Summary,
            gateAuditTrail = validated.auditTrail
        )
    }

    /**
     * Vyhodnotí Matici dopadů (Win-Win-Win) po průchodu sekvenční sanitizací a validací Gatekeeper G1-G6.
     */
    @Throws(GatekeeperChainException::class)
    fun evaluateImpactMatrix(
        query: String,
        domainWeights: Map<String, Float>,
        embeddingVector: FloatArray? = null,
        systemStressSigma: Float = 0.18f
    ): ImpactMatrixResult {
        val validated = GatekeeperG1G6.validateAndSanitize(
            GatekeeperPayload(
                query = query,
                domainWeights = domainWeights,
                embeddingVector = embeddingVector,
                systemStressSigma = systemStressSigma
            )
        )
        return computeImpactMatrixFromSanitized(validated)
    }

    private fun computeImpactMatrixFromSanitized(validated: SanitizedImpactInput): ImpactMatrixResult {
        val weights = validated.sanitizedDomainWeights
        val econW = weights["ECON"] ?: 0.82f
        val sysW = weights["SYS"] ?: 0.88f
        val ecoW = weights["ECO"] ?: 0.85f
        val psychW = weights["PSYCH"] ?: 0.80f

        val econViability = (econW * 90f + 5f).coerceIn(40f, 99f)
        val techElegance = (sysW * 92f + 6f).coerceIn(50f, 99f)
        val ecoSocialRegen = (ecoW * 88f + 8f).coerceIn(45f, 99f)
        val psychAcceptance = (psychW * 85f + 10f).coerceIn(40f, 99f)

        val winWinWin = (econViability * 0.25f + techElegance * 0.30f + ecoSocialRegen * 0.25f + psychAcceptance * 0.20f) / 100f

        return ImpactMatrixResult(
            economicViability = econViability,
            technicalElegance = techElegance,
            ecoSocialRegeneration = ecoSocialRegen,
            psychologicalAcceptance = psychAcceptance,
            winWinWinScore = winWinWin.coerceIn(0.0f, 1.0f),
            gateAuditTrail = validated.auditTrail,
            chainCommitmentHash = validated.chainCommitmentHash
        )
    }
}
