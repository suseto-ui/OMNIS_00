package com.example.engine

import org.junit.Assert.*
import org.junit.Test

class CascadeRiskSimulatorEngineTest {

    @Test
    fun testSystemicTensionCalculationAndEquilibrium() {
        val lowTensionWeights = mapOf(
            "SYS" to 0.35f, "ECON" to 0.35f, "PSYCH" to 0.35f, "ECO" to 0.35f,
            "LAW" to 0.35f, "SEC" to 0.35f, "PHYS" to 0.35f, "SOC" to 0.35f
        )

        val tension = CascadeRiskSimulatorEngine.calculateSystemicTension(lowTensionWeights)
        assertEquals(0.35f, tension, 0.01f)

        val state = CascadeRiskSimulatorEngine.evaluateRiskState(lowTensionWeights)
        assertEquals("STABILNÍ ROVNOVÁHA", state.riskLevel)
        assertFalse(state.isCircuitBreakerTripped)
        assertFalse(state.isForensicModeActive)

        val moderateWeights = mapOf(
            "SYS" to 0.50f, "ECON" to 0.50f, "PSYCH" to 0.50f, "ECO" to 0.50f,
            "LAW" to 0.50f, "SEC" to 0.50f, "PHYS" to 0.50f, "SOC" to 0.50f
        )
        val modState = CascadeRiskSimulatorEngine.evaluateRiskState(moderateWeights)
        assertEquals("ZVÝŠENÉ SYSTEMICKÉ PNUTÍ", modState.riskLevel)
    }

    @Test
    fun testCriticalCascadeRiskTriggering() {
        val stressedWeights = mapOf(
            "SYS" to 0.90f, "ECON" to 0.85f, "PSYCH" to 0.80f, "ECO" to 0.85f,
            "LAW" to 0.90f, "SEC" to 0.95f, "PHYS" to 0.70f, "SOC" to 0.80f
        )

        val tension = CascadeRiskSimulatorEngine.calculateSystemicTension(stressedWeights)
        assertTrue("Tenze při vysoké zátěži má být > 0.80: $tension", tension > 0.80f)

        val state = CascadeRiskSimulatorEngine.evaluateRiskState(stressedWeights)
        assertTrue("Circuit breaker se má aktivovat", state.isCircuitBreakerTripped)
        assertTrue("Forenzní režim má být aktivní", state.isForensicModeActive)
        assertTrue("Seznam rebalančních akcí není prázdný", state.rebalancingActions.isNotEmpty())
    }

    @Test
    fun testStabilizationVectorCalculation() {
        val stressedWeights = mapOf(
            "SYS" to 0.95f, "ECON" to 0.10f, "PSYCH" to 0.80f, "ECO" to 0.50f,
            "LAW" to 0.85f, "SEC" to 0.90f, "PHYS" to 0.30f, "SOC" to 0.50f
        )

        val stabilized = CascadeRiskSimulatorEngine.computeStabilizationVector(stressedWeights)
        assertNotNull(stabilized)

        val sysStabilized = stabilized["SYS"] ?: 0f
        assertTrue("Přetížená doména SYS se má posunout blíže k barycentru (0.52)", sysStabilized < 0.95f)

        val econStabilized = stabilized["ECON"] ?: 0f
        assertTrue("Oslabená doména ECON se má posílit směrem k barycentru", econStabilized > 0.10f)
    }
}
