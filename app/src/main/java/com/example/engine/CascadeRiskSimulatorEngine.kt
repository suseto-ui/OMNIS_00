package com.example.engine

import kotlin.math.sqrt

data class DomainTensionState(
    val domainWeights: Map<String, Float>,
    val systemicTensionIndex: Float,
    val riskLevel: String,
    val isCircuitBreakerTripped: Boolean,
    val isForensicModeActive: Boolean,
    val rebalancingActions: List<String>,
    val recommendedWeights: Map<String, Float>
)

object CascadeRiskSimulatorEngine {

    val DOMAINS = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")

    /**
     * Vypočítá systémový index tenze T_sys napříč 8 doménami:
     * T_sys = sqrt( (1/8) * sum(tau_i^2) )
     */
    fun calculateSystemicTension(weights: Map<String, Float>): Float {
        var sumSquares = 0.0f
        DOMAINS.forEach { domain ->
            val w = weights[domain] ?: 0.5f
            sumSquares += w * w
        }
        return sqrt(sumSquares / DOMAINS.size.toFloat()).coerceIn(0.0f, 1.0f)
    }

    /**
     * Vyhodnotí stav kaskádového rizika a navrhne stabilizační rebalanční vektor.
     */
    fun evaluateRiskState(weights: Map<String, Float>): DomainTensionState {
        val tensionIndex = calculateSystemicTension(weights)

        val isCircuitBreaker = tensionIndex >= 0.85f || DOMAINS.any { (weights[it] ?: 0f) >= 0.92f }
        val isForensic = tensionIndex >= 0.70f || DOMAINS.any { (weights[it] ?: 0f) >= 0.78f }

        val riskLevel = when {
            isCircuitBreaker -> "CIRCUIT BREAKER ACTIVATED"
            isForensic -> "KRITICKÉ KASKÁDOVÉ RIZIKO"
            tensionIndex >= 0.45f -> "ZVÝŠENÉ SYSTEMICKÉ PNUTÍ"
            else -> "STABILNÍ ROVNOVÁHA"
        }

        val actions = mutableListOf<String>()
        val recommended = mutableMapOf<String, Float>()

        DOMAINS.forEach { domain ->
            val currentW = weights[domain] ?: 0.5f
            if (currentW > 0.75f) {
                actions.add("Aktivovat tlumení tenze pro doménu $domain (${(currentW * 100).toInt()}%)")
                recommended[domain] = 0.55f
            } else if (currentW < 0.25f) {
                actions.add("Posílit kapacitu domény $domain (${(currentW * 100).toInt()}%)")
                recommended[domain] = 0.50f
            } else {
                recommended[domain] = currentW
            }
        }

        if (actions.isEmpty()) {
            actions.add("Systém je v ideální 8D rovnováze. Žádné rebalanční zásahy nejsou vyžadovány.")
        }

        return DomainTensionState(
            domainWeights = weights,
            systemicTensionIndex = tensionIndex,
            riskLevel = riskLevel,
            isCircuitBreakerTripped = isCircuitBreaker,
            isForensicModeActive = isForensic,
            rebalancingActions = actions,
            recommendedWeights = recommended
        )
    }

    /**
     * Spustí automatické vyrovnání 8D tenzorového pole do rovnovážného stavu.
     */
    fun computeStabilizationVector(weights: Map<String, Float>): Map<String, Float> {
        val stabilized = mutableMapOf<String, Float>()
        DOMAINS.forEach { domain ->
            val current = weights[domain] ?: 0.5f
            // Přitažlivost k barycentru 0.52f
            val delta = (0.52f - current) * 0.65f
            stabilized[domain] = (current + delta).coerceIn(0.15f, 0.85f)
        }
        return stabilized
    }
}
