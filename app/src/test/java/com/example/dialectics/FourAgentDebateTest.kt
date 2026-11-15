package com.example.dialectics

import com.example.engine.AkgeTensorKernel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FourAgentDebateTest {

    @Test
    fun `canonical 4 agents exist with distinct roles and regulator has veto mandate`() {
        val canonical = AgentArenaLibrary.canonicalFourAgents
        assertEquals(4, canonical.size)

        val architect = canonical.find { it.id == "architect" }
        assertNotNull(architect)
        assertEquals("Architekt", architect!!.name)
        assertTrue(architect.primaryDomains.contains("SYS"))

        val skeptic = canonical.find { it.id == "skeptic" }
        assertNotNull(skeptic)
        assertEquals("Skeptik", skeptic!!.name)
        assertTrue(skeptic.primaryDomains.contains("PSYCH"))

        val regulator = canonical.find { it.id == "regulator" }
        assertNotNull(regulator)
        assertEquals("Regulátor", regulator!!.name)
        assertTrue(regulator.primaryDomains.contains("LAW"))
        assertTrue(regulator.roleTitle.contains("VETA"))

        val engineer = canonical.find { it.id == "engineer" }
        assertNotNull(engineer)
        assertEquals("Inženýr", engineer!!.name)
        assertTrue(engineer.primaryDomains.contains("PHYS"))
    }

    @Test
    fun `executeFourAgentDebate generates 4 turns and 4 votes`() = runBlocking {
        val recordedTurns = mutableListOf<ArenaDebateTurn>()
        val result = AgentArenaEngine.executeFourAgentDebate(
            problemStatement = "Optimalizace tenzorového toku v distribuované síti",
            onProgressUpdate = { turn -> recordedTurns.add(turn) }
        )

        assertNotNull(result)
        assertEquals(4, result.turns.size)
        assertEquals(4, recordedTurns.size)
        assertEquals(4, result.votes.size)

        // Verify votes cover all 4 agents
        val agentIds = result.votes.map { it.agentId }
        assertTrue(agentIds.contains("architect"))
        assertTrue(agentIds.contains("skeptic"))
        assertTrue(agentIds.contains("regulator"))
        assertTrue(agentIds.contains("engineer"))

        // Verify ZK Commitment Hash is present
        assertNotNull(result.zkCommitmentHash)
        assertEquals(16, result.zkCommitmentHash.length) // 8 bytes hex
    }

    @Test
    fun `regulator veto blocks execution and executive override unblocks with OVERRIDE_ACTIVE`() = runBlocking {
        // Problem triggering regulator veto (contains keyword 'letov')
        val vetoProblem = "Autonomní AI v řízení letového provozu bez lidského zásahu"

        val initialResult = AgentArenaEngine.executeFourAgentDebate(
            problemStatement = vetoProblem
        )

        assertTrue("Veto mělo být aktivní", initialResult.isVetoActive)
        assertFalse("Override by neměl být aktivní před zadáním", initialResult.isOverrideActive)
        assertTrue(initialResult.auditLogEntry.contains("VETO_BLOCKED"))

        // Try invalid override flag
        val invalidAttempt = AgentArenaEngine.applyExecutiveOverride(initialResult.debateId, "INVALID_FLAG")
        assertNull(invalidAttempt)

        // Apply valid executive override with flag MODIFY_SYSTEM_STATE
        val overriddenResult = AgentArenaEngine.applyExecutiveOverride(initialResult.debateId, "MODIFY_SYSTEM_STATE")
        assertNotNull(overriddenResult)
        assertTrue("Override musí být aktivní", overriddenResult!!.isOverrideActive)
        assertTrue("Audit musí obsahovat OVERRIDE_ACTIVE", overriddenResult.auditLogEntry.contains("OVERRIDE_ACTIVE"))
        assertTrue(overriddenResult.consensusIndex > 0.7f)

        // Verify state was written to AkgeTensorKernel
        val kernelState = AkgeTensorKernel.kernelState.value
        assertNotNull(kernelState)
        assertTrue(kernelState.domainWeights.isNotEmpty())
    }
}
