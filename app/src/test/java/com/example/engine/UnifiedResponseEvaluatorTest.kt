package com.example.engine

import com.example.data.MemoryFragment
import org.junit.Assert.*
import org.junit.Test

class UnifiedResponseEvaluatorTest {

    @Test
    fun testUnifiedEvaluationScoreCalculation() {
        val query = "Optimalizace systémové stability a paměti"
        val response = "[ARTEFAKT: OmnisEngine.kt]\nfun optimizeSystem() { return true }"
        val memories = listOf(
            MemoryFragment(
                id = 1L,
                title = "Optimalizace systémové stability a paměti",
                summary = "Optimalizace systémové stability a paměti O.M.N.I.S.",
                timestamp = System.currentTimeMillis(),
                sourceRecordCount = 10,
                tags = "paměť, stability"
            )
        )

        val evalScore = UnifiedResponseEvaluator.evaluateResponse(query, response, memories)

        assertNotNull(evalScore)
        assertEquals(query, evalScore.query)
        assertEquals(response, evalScore.response)
        assertTrue("RAG Cosine score should be > 0", evalScore.ragCosineScore > 0.0f)
        assertTrue("Tensor stability score should be between 0 and 1", evalScore.tensorStabilityScore in 0.0f..1.0f)
        assertTrue("Impact matrix score should be between 0 and 1", evalScore.impactMatrixScore in 0.0f..1.0f)
        assertEquals(1.0f, evalScore.zeroFluffScore, 0.001f)
        assertTrue("Unified quality index should be > 0", evalScore.unifiedQualityIndex > 0.0f)
        assertNotNull(evalScore.qualityTier)
    }

    @Test
    fun testFluffResponseReducesZeroFluffScore() {
        val query = "Vygeneruj architekturu"
        val response = "Dobrý den, rád vám pomohu s návrhem architektury!"

        val evalScore = UnifiedResponseEvaluator.evaluateResponse(query, response)

        assertEquals(0.40f, evalScore.zeroFluffScore, 0.001f)
        assertTrue(evalScore.unifiedQualityIndex <= 1.0f)
    }
}
