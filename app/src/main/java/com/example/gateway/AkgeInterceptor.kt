package com.example.gateway

import android.content.Context
import com.example.api.OmnisSemanticCache
import com.example.engine.AkgeTensorKernel
import com.example.telemetry.TelemetryEngine
import com.example.ui.UserExperienceMode

data class InterceptionResult(
    val isProceedAllowed: Boolean,
    val tokenCompressionRate: Float,
    val isCacheHit: Boolean,
    val cachedAnswer: String? = null,
    val refusalVerdict: RefusalLadderVerdict? = null,
    val executionMode: String = "CLOUD_INFERENCE", // "CACHE_HIT", "CLOUD_INFERENCE", "OFFLINE_SIMULATION", "REFUSED"
    val diagnosticNote: String = ""
)

/**
 * Centrální Middle-Tier AkgeInterceptor pro modul PromptGateway.
 * - Sémantická cache (shoda > 0.95 -> okamžitý návrat bez spotřeby tokenů)
 * - Autonomní rebalancování tokenového rozpočtu dle systémového pnutí sigma (až 70% komprese)
 * - Paralelní 6-úrovňový Refusal Ladder s EU AI Act / NIS2 validací
 * - Spojení s 3-fázovým Circuit Breakerem pro offline simulaci
 */
object AkgeInterceptor {

    suspend fun interceptPrompt(
        context: Context,
        prompt: String,
        domain: String = "SYSTEMS_INTELLIGENCE",
        userMode: UserExperienceMode = UserExperienceMode.STANDARD,
        forceBypassCircuit: Boolean = false
    ): InterceptionResult {
        val kernelState = AkgeTensorKernel.kernelState.value
        val sigma = kernelState.systemStressSigma
        val entropy = kernelState.topologicalMetrics.shannonEntropy
        val cascadeActive = kernelState.percolationStatus.isCascadeRiskActive ||
            (kernelState.cascadeRisk?.isCascadeRiskActive == true)
        val anomalyScore = kernelState.advancedMetrics.cognitiveAnomalyScore

        // 1. Kontrola Circuit Breakeru
        val circuitState = OmnisCircuitBreaker.getCurrentState()
        if (circuitState == CircuitState.OPEN && !forceBypassCircuit) {
            val offlineRes = OmnisCircuitBreaker.executeOfflineSimulation(context, prompt, domain)
            return InterceptionResult(
                isProceedAllowed = true,
                tokenCompressionRate = 0.0f,
                isCacheHit = false,
                cachedAnswer = offlineRes.answer,
                executionMode = "OFFLINE_SIMULATION",
                diagnosticNote = offlineRes.diagnosticMessage
            )
        }

        // 2. Sémantická cache (práh shody > 0.95f)
        val cached = OmnisSemanticCache.findMatch(prompt, domain, userMode)
        if (cached != null && !cascadeActive) {
            TelemetryEngine.log(
                type = "INFO",
                component = "AKGE_INTERCEPTOR",
                message = "Sémantická cache HIT (shoda > 0.95): Požadavek odbaven bez spotřeby tokenů."
            )
            return InterceptionResult(
                isProceedAllowed = true,
                tokenCompressionRate = 0.0f,
                isCacheHit = true,
                cachedAnswer = cached.answer,
                executionMode = "CACHE_HIT",
                diagnosticNote = "Cached response (Zero token expenditure)"
            )
        }

        // 3. Výpočet komprese tokenového rozpočtu f(sigma) + Perkolace (pc >= 0.38 => FULL_FORENSIC 0%)
        val compressionRate = when {
            cascadeActive || sigma >= 0.60f -> 0.00f // Perkolace / vysoké pnutí: Plný forenzní rozpočet s 0% kompresí
            sigma < 0.20f -> 0.70f                    // Nízké pnutí: až 70% komprese pro rutinní dotazy
            else -> 0.30f                             // Střední pnutí: 30% komprese
        }

        if (anomalyScore > 0.75f) {
            TelemetryEngine.log(
                type = "SECURITY",
                component = "AKGE_TELEMETRY_FUSION",
                message = "Kritická kognitivní anomálie (${String.format(java.util.Locale.US, "%.2f", anomalyScore)} > 0.75): Vyžadována kontrola ADMIN_OPERATOR Executive Override (MODIFY_SYSTEM_STATE)."
            )
        }

        // 4. Vyhodnocení 6-Gate Refusal Ladderu
        val verdict = RefusalLadderEngine.evaluatePrompt(
            rawPrompt = prompt,
            currentSigma = sigma,
            currentEntropy = entropy,
            forceEntropySpike = false
        )

        if (!verdict.isApproved) {
            val refusingGate = verdict.refusingGate
            TelemetryEngine.log(
                type = "SECURITY",
                component = "REFUSAL_LADDER",
                message = "Explicit Grounded Refusal na bráně ${refusingGate?.gateId?.code}: ${refusingGate?.reason}",
                metadata = "{\"gate\":\"${refusingGate?.gateId?.code}\",\"norm\":\"${refusingGate?.regulatoryNorm}\"}"
            )
            return InterceptionResult(
                isProceedAllowed = false,
                tokenCompressionRate = compressionRate,
                isCacheHit = false,
                refusalVerdict = verdict,
                executionMode = "REFUSED",
                diagnosticNote = refusingGate?.reason ?: "Zadání neprošlo 6-Gate ověřením."
            )
        }

        val anomalyNote = if (anomalyScore > 0.75f) " [ANOMALY_ALERT: Executive Override review]" else ""
        return InterceptionResult(
            isProceedAllowed = true,
            tokenCompressionRate = compressionRate,
            isCacheHit = false,
            refusalVerdict = verdict,
            executionMode = "CLOUD_INFERENCE",
            diagnosticNote = "Všechny brány G1-G6 schváleny. Komprese tokenů: ${(compressionRate * 100).toInt()}%.$anomalyNote"
        )
    }
}
