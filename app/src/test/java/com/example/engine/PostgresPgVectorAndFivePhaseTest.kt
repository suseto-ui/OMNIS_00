package com.example.engine

import com.example.data.sync.PgVectorEntity
import com.example.data.sync.PostgresPgVectorEngine
import com.example.defense.SigmaOmegaRedTeamingEngine
import org.junit.Assert.*
import org.junit.Test

class PostgresPgVectorAndFivePhaseTest {

    @Test
    fun testPgVectorDdlAndInsertGeneration() {
        val entity = PgVectorEntity(
            id = "ent_123",
            entityName = "Tenzorové Jádro AKGE",
            vector64 = FloatArray(64) { 0.1f * it },
            impactMatrixJson = "{\"winWinWin\": 0.95}",
            phaseState = "PHASE_V_AUTOPOIESIS"
        )

        val sqlInsert = PostgresPgVectorEngine.generatePgVectorInsertQuery(entity)
        assertNotNull(sqlInsert)
        assertTrue("SQL obsahuje vector přetypování", sqlInsert.contains("::vector"))
        assertTrue("SQL obsahuje ID ent_123", sqlInsert.contains("ent_123"))

        val searchQuery = PostgresPgVectorEngine.generateHnswSearchQuery(FloatArray(64) { 0.1f }, topK = 3)
        assertTrue("HNSW dotaz obsahuje op <=> pro kosinovou vzdálenost", searchQuery.contains("<=>"))
    }

    @Test
    fun testFivePhaseExecutionAndImpactMatrix() {
        val query = "Harmonizace právního prostředí EU AI Act a energetické sítě"
        val result = OmnisFivePhaseExecutor.executeFivePhasePipeline(query)

        assertNotNull(result)
        assertEquals("PHASE_V_AUTOPOIESIS", result.currentPhase)
        assertTrue("Win-Win-Win skóre má být > 50%", result.impactMatrix.winWinWinScore > 0.50f)
        assertTrue("Technická elegance je vysoká", result.impactMatrix.technicalElegance > 60f)
    }

    @Test
    fun testSigmaRedTeamingConfrontation() {
        val query = "Optimalizace kritické infrastruktury"
        val response = "Dobrý den, předkládám vám výslednou analýzu. Tenzorové jádro je v rovnováze."

        val confrontation = SigmaOmegaRedTeamingEngine.runSigmaConfrontation(query, response)
        assertNotNull(confrontation)
        assertTrue("Právní posudek je přítomen", confrontation.lawyerObjection.contains("NIS2"))
        assertTrue("Krystal rezonance OMEGA je aktivní", confrontation.crystalResonanceConsensus.contains("OMEGA"))
    }
}
