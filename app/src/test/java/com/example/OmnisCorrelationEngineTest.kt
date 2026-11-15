package com.example

import com.example.ui.OmnisCorrelationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmnisCorrelationEngineTest {

    private val allDomains = listOf("Sys", "Econ", "Psych", "Eco", "Law", "Sec", "Phys", "Soc")

    @Test
    fun `test identity correlation returns 100 percent`() {
        allDomains.forEach { domain ->
            val corr = OmnisCorrelationEngine.getCorrelation(domain, domain)
            assertEquals("Identity correlation for $domain must be 1.0", 1.0f, corr.correlation, 0.001f)
        }
    }

    @Test
    fun `test symmetric correlation lookup for all 28 distinct pairs`() {
        var testedPairs = 0
        for (i in 0 until allDomains.size) {
            for (j in i + 1 until allDomains.size) {
                val dA = allDomains[i]
                val dB = allDomains[j]
                val corrAB = OmnisCorrelationEngine.getCorrelation(dA, dB)
                val corrBA = OmnisCorrelationEngine.getCorrelation(dB, dA)

                assertEquals("Correlation between $dA and $dB must be symmetric", corrAB.correlation, corrBA.correlation, 0.001f)
                assertEquals("Description between $dA and $dB must match", corrAB.impactDescription, corrBA.impactDescription)
                assertNotNull("Description must not be null", corrAB.impactDescription)
                assertTrue("Impact description must be informative", corrAB.impactDescription.isNotBlank())
                testedPairs++
            }
        }
        assertEquals("Total number of distinct domain pairs must be exactly 28", 28, testedPairs)
    }

    @Test
    fun `test known high synergy and critical friction values`() {
        // High Synergies
        val lawSec = OmnisCorrelationEngine.getCorrelation("Law", "Sec")
        assertTrue("Law-Sec must be strong synergy (> 0.7)", lawSec.correlation >= 0.70f)

        val psychSoc = OmnisCorrelationEngine.getCorrelation("Psych", "Soc")
        assertTrue("Psych-Soc must be strong synergy (> 0.8)", psychSoc.correlation >= 0.80f)

        val ecoPhys = OmnisCorrelationEngine.getCorrelation("Eco", "Phys")
        assertTrue("Eco-Phys must be strong thermodynamic synergy (> 0.7)", ecoPhys.correlation >= 0.70f)

        // Critical Frictions
        val econEco = OmnisCorrelationEngine.getCorrelation("Econ", "Eco")
        assertTrue("Econ-Eco must exhibit negative friction (< -0.4)", econEco.correlation <= -0.40f)

        val secPsych = OmnisCorrelationEngine.getCorrelation("Sec", "Psych")
        assertTrue("Sec-Psych must exhibit tension (< -0.4)", secPsych.correlation <= -0.40f)
    }

    @Test
    fun `test cluster evaluation detects friction and summarizes pair`() {
        val cluster = OmnisCorrelationEngine.evaluateCluster(setOf("Econ", "Eco"))
        assertTrue("Cluster with Econ and Eco must report friction", cluster.hasFriction)
        assertNotNull("Primary pair must be reported", cluster.primaryPair)
        assertEquals("Econ", cluster.primaryPair?.domainA)
        assertEquals("Eco", cluster.primaryPair?.domainB)
    }

    @Test
    fun `test calculateSystemicEquilibrium detects bottleneck and critical failure`() {
        // Balanced vector
        val balancedVector = allDomains.associateWith { 0.8f }
        val balancedResult = OmnisCorrelationEngine.calculateSystemicEquilibrium(balancedVector)
        assertFalse("Balanced vector must not trigger critical failure", balancedResult.isCriticalFailure)
        assertEquals(0.8f, balancedResult.arithmeticMean, 0.01f)
        assertEquals(0.8f, balancedResult.harmonicMean, 0.01f)
        assertEquals(0.8f, balancedResult.systemicResilience, 0.01f)

        // Vector with single critical collapse (Sec = 0.05)
        val compromisedVector = mapOf(
            "Sys" to 0.9f,
            "Econ" to 0.9f,
            "Psych" to 0.9f,
            "Eco" to 0.9f,
            "Law" to 0.9f,
            "Sec" to 0.05f, // Critical security failure
            "Phys" to 0.9f,
            "Soc" to 0.9f
        )
        val compromisedResult = OmnisCorrelationEngine.calculateSystemicEquilibrium(compromisedVector)

        assertTrue("Vector with Sec=0.05 must flag critical failure", compromisedResult.isCriticalFailure)
        assertEquals("Sec", compromisedResult.bottleneckDomain)
        assertEquals(0.05f, compromisedResult.bottleneckValue, 0.001f)

        // Arithmetic mean stays deceptively high (~0.79), but Harmonic mean drops severely (< 0.40)
        assertTrue("Arithmetic mean remains high (> 0.75)", compromisedResult.arithmeticMean > 0.75f)
        assertTrue("Harmonic mean must severely penalize single-point failure (< 0.45)", compromisedResult.harmonicMean < 0.45f)
        assertTrue("Overall systemic resilience is suppressed", compromisedResult.systemicResilience < compromisedResult.arithmeticMean)
    }

    @Test
    fun `test Donella Meadows leverage point identification`() {
        // System with low security and moderate law
        val vector = mapOf(
            "Sys" to 0.6f,
            "Econ" to 0.7f,
            "Psych" to 0.5f,
            "Eco" to 0.5f,
            "Law" to 0.3f,
            "Sec" to 0.25f,
            "Phys" to 0.6f,
            "Soc" to 0.5f
        )
        val analysis = OmnisCorrelationEngine.calculateSystemicEquilibrium(vector)

        assertNotNull("Leverage domain must be identified", analysis.leverageDomain)
        assertTrue("Leverage domain must be one of the 8 valid domains", allDomains.contains(analysis.leverageDomain))
        // Verify that the bottleneck is correctly tracked
        assertEquals("Sec", analysis.bottleneckDomain)
    }

    @Test
    fun `test topological invariants and hypergraph resilience`() {
        // Balanced stable system
        val balancedVector = allDomains.associateWith { 0.8f }
        val topology = OmnisCorrelationEngine.calculateHypergraphTopology(balancedVector)

        assertNotNull("Hypergraph topology analysis must not be null", topology)
        assertTrue("Euler characteristic must be calculated", topology.eulerCharacteristic != 0)
        assertEquals("Betti0 must be 1 (single connected component)", 1, topology.betti0)
        assertTrue("Betti1 must be non-negative", topology.betti1 >= 0)
        assertTrue("Systemic entropy must be positive", topology.systemicEntropy > 0f)
        assertTrue("Topological resilience should be non-minimal for balanced system", topology.topologicalResilience > 0.1f)

        // Collapsed system (high tension)
        val stressedVector = mapOf(
            "Sys" to 0.9f, "Econ" to 0.1f, "Psych" to 0.1f, "Eco" to 0.9f,
            "Law" to 0.9f, "Sec" to 0.1f, "Phys" to 0.9f, "Soc" to 0.1f
        )
        val stressedTopology = OmnisCorrelationEngine.calculateHypergraphTopology(stressedVector)
        
        // Resilience should be lower due to higher entropy or Betti impacts
        assertTrue("Stressed system should have lower or equal resilience than balanced", 
            stressedTopology.topologicalResilience <= topology.topologicalResilience)
    }

    @Test
    fun `test Euler characteristic formula V - E + F`() {
        val vector = allDomains.associateWith { 0.5f }
        val topology = OmnisCorrelationEngine.calculateHypergraphTopology(vector)
        
        // Vertices = 8
        // activeEdges = count of pairs with corr > 0.35
        // faces = count of hyperEdges with cohesion > 0.5
        // euler = V - E + F
        
        val vertices = 8
        var activeEdges = 0
        for (i in 0 until allDomains.size) {
            for (j in i + 1 until allDomains.size) {
                if (OmnisCorrelationEngine.getCorrelation(allDomains[i], allDomains[j]).correlation > 0.35f) {
                    activeEdges++
                }
            }
        }
        val faces = topology.hyperEdges.count { it.cohesionScore > 0.50f }
        val expectedEuler = vertices - activeEdges + faces
        
        assertEquals("Euler characteristic must follow V - E + F", expectedEuler, topology.eulerCharacteristic)
    }
}
