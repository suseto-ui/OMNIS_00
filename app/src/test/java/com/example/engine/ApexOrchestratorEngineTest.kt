package com.example.engine

import org.junit.Assert.*
import org.junit.Test

class ApexOrchestratorEngineTest {

    @Test
    fun testSanitizeAndValidateZeroFluffRemovesIntros() {
        val fluffyInput = "Dobrý den! Rád vám pomohu. Zde je výsledek: ARCHITEKTURA Byla schválena."
        val sanitized = ApexOrchestratorEngine.sanitizeAndValidateZeroFluff(fluffyInput)

        assertNotNull(sanitized)
        assertFalse("Úvodní pozdrav musel být odstraněn", sanitized.lowercase().contains("dobrý den"))
        assertFalse("Úvodní fráze musela být odstraněna", sanitized.lowercase().contains("zde je výsledek"))
        assertTrue("Čistá odpověď začíná přímo výsledkem", sanitized.startsWith("ARCHITEKTURA"))
    }

    @Test
    fun testScratchpadIsExtractedAndHiddenFromFinalArtifact() {
        val rawResponse = """
<scratchpad>
[FIRST_PRINCIPLES]
Entities: [Memory, HNSW]
Risk: Low
</scratchpad>
[ARTEFAKT: postgres_hnsw.sql]
CREATE INDEX idx_vector ON memories USING hnsw (embedding vector_cosine_ops);
""".trimIndent()

        val extractedScratchpad = ApexOrchestratorEngine.extractScratchpad(rawResponse)
        val sanitizedArtifact = ApexOrchestratorEngine.sanitizeAndValidateZeroFluff(rawResponse)

        assertTrue("Scratchpad byl úspěšně extrahován", extractedScratchpad.contains("Entities: [Memory, HNSW]"))
        assertFalse("Finální výstup nesmí obsahovat tag <scratchpad>", sanitizedArtifact.contains("<scratchpad>"))
        assertFalse("Finální výstup nesmí obsahovat interní dekonstrukci", sanitizedArtifact.contains("[FIRST_PRINCIPLES]"))
        assertTrue("Výstup začíná přímo artefaktem", sanitizedArtifact.startsWith("[ARTEFAKT: postgres_hnsw.sql]"))
    }

    @Test
    fun testExecuteMasterProtocolReturnsFourStepsAndHiddenScratchpad() {
        val query = "Optimalizovat databázové indexy HNSW"
        val result = ApexOrchestratorEngine.executeMasterProtocol(query)

        assertNotNull(result)
        assertEquals(4, result.steps.size)
        assertTrue("Výsledek je Zero-Fluff compliant", result.isZeroFluffCompliant)
        assertTrue("Resilience skóre je > 0.90", result.adversarialResilienceScore > 0.90f)
        assertTrue("Hidden scratchpad byl vygenerován", result.hiddenScratchpad.isNotBlank())
        assertTrue("Scratchpad obsahuje dekonstrukci", result.hiddenScratchpad.contains("[FIRST_PRINCIPLES_DECONSTRUCTION]"))
        assertFalse("Finální artefakt nesmí obsahovat tag scratchpad", result.finalArtifact.contains("<scratchpad>"))
    }

    @Test
    fun testIsZeroFluffCompliantValidation() {
        val cleanOutput = "ROZHODNUTÍ O.M.N.I.S.: Nasadit PostgreSQL s pgvector."
        val conversationalOutput = "Dobrý den, rád vám pomohu s výběrem databáze."
        val scratchpadOutput = "<scratchpad>test</scratchpad>ROZHODNUTÍ: OK"

        assertTrue("Technický přímý výstup je compliant", ApexOrchestratorEngine.isZeroFluffCompliant(cleanOutput))
        assertFalse("Konverzační úvod není compliant", ApexOrchestratorEngine.isZeroFluffCompliant(conversationalOutput))
        assertFalse("Výstup se zbytkem scratchpadu není compliant", ApexOrchestratorEngine.isZeroFluffCompliant(scratchpadOutput))
    }
}
