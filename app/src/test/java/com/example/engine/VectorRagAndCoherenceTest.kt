package com.example.engine

import com.example.data.MemoryFragment
import org.junit.Assert.*
import org.junit.Test

class VectorRagAndCoherenceTest {

    @Test
    fun testVectorEncodingAndCosineSimilarity() {
        val text1 = "Analýza tenzorového jádra a perkolace topologie"
        val text2 = "Analýza tenzorové stabilizace a topologické perkolace"
        val text3 = "Smažený sýr a hranolky s tatarkou a kečupem"

        val vec1 = VectorRagEngine.encodeTo64DVector(text1)
        val vec2 = VectorRagEngine.encodeTo64DVector(text2)
        val vec3 = VectorRagEngine.encodeTo64DVector(text3)

        assertEquals(64, vec1.size)
        assertEquals(64, vec2.size)
        assertEquals(64, vec3.size)

        val sim12 = VectorRagEngine.calculateCosineSimilarity(vec1, vec2)
        val sim13 = VectorRagEngine.calculateCosineSimilarity(vec1, vec3)

        assertTrue("Technické dotazy mají mít vyšší kosinovou shodu: sim12=$sim12, sim13=$sim13", sim12 > sim13)
        assertTrue("Shoda pro podobné texty má být kódována s rozumnou hodnotou: sim12=$sim12", sim12 > 0.15f)
    }

    @Test
    fun testVectorRagSearch() {
        val fragments = listOf(
            MemoryFragment(1L, "Tenzorové jádro AKGE", "Topologie 8D tenzoru a perkolace tenzor", 1000L, 5, "TENZOR"),
            MemoryFragment(2L, "Kuchařský Recept", "Pečení chleba a mouka sýr", 2000L, 2, "RECEPT")
        )

        val query = "Hledám informace o perkolaci tenzorového jádra"
        val matches = VectorRagEngine.searchVectorMemory(query, fragments, topK = 2)

        assertEquals(2, matches.size)
        assertEquals("Tenzorové jádro AKGE", matches[0].fragment.title)
        assertTrue("Nejlepší shoda má mít vyšší sémantickou shodu než nesouvisející recept", matches[0].cosineSimilarity > matches[1].cosineSimilarity)
    }

    @Test
    fun testTensorCoherenceEvaluator() {
        val query = "Jaký je stav tenzorové perkolace?"
        val response = "Tenzorové jádro AKGE vykazuje perkolaci p_c = 0.65 a nulovou entropii s vysokou sémantickou koherencí a topologickým invariantem."

        val metrics = TensorCoherenceEvaluator.evaluateCoherence(
            query = query,
            response = response,
            eulerCharacteristic = 2,
            entropy = 0.30f,
            percolationThreshold = 0.70f
        )

        assertTrue("Celkové skóre koherence má být > 50%: ${metrics.overallScore}", metrics.overallScore > 50f)
        assertTrue("Relevance má být kladná: ${metrics.relevance}", metrics.relevance > 20f)
        assertEquals(64, metrics.queryVector.size)
        assertEquals(64, metrics.responseVector.size)
    }
}
