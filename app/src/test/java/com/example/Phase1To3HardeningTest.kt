package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.api.HybridDeviceCapability
import com.example.api.HybridInferenceOrchestrator
import com.example.api.HybridRouteMode
import com.example.auth.AuthSession
import com.example.auth.AuthenticationManager
import com.example.auth.OmnisAuthService
import com.example.auth.UserRole
import com.example.auth.jwt.JwtTokenManager
import com.example.data.OmnisDatabase
import com.example.data.OmnisRecord
import com.example.data.OmnisRepository
import com.example.engine.AkgeTensorKernel
import com.example.engine.TopologicalInferenceEngine
import com.example.engine.UnifiedResponseEvaluator
import com.example.engine.VectorRagEngine
import com.example.gateway.AkgeInterceptor
import com.example.gateway.GateId
import com.example.gateway.GateStatus
import com.example.gateway.RefusalLadderEngine
import com.example.ui.UserExperienceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1To3HardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OmnisAuthService.logout(context)
        AkgeTensorKernel.resetToEquilibrium()
        HybridInferenceOrchestrator.deviceCapabilityProviderOverride = null
        HybridInferenceOrchestrator.isFastPathFeatureEnabled = true
    }

    @After
    fun tearDown() {
        OmnisAuthService.logout(context)
        AkgeTensorKernel.resetToEquilibrium()
        HybridInferenceOrchestrator.deviceCapabilityProviderOverride = null
    }

    @Test
    fun authServiceInit_restoresAuthenticatedSessionWhenValid() {
        val validToken = JwtTokenManager.createToken(
            subject = "susetofu",
            role = UserRole.ADMIN_OPERATOR.name,
            durationSeconds = 3600
        )
        AuthenticationManager.instance.saveExternalSession(
            context,
            AuthSession(
                token = validToken,
                username = "susetofu",
                role = UserRole.ADMIN_OPERATOR
            )
        )

        OmnisAuthService.init(context)

        assertTrue("Platná obnovená relace musí nastavit isAuthenticated = true", OmnisAuthService.isAuthenticated.value)
        assertEquals("susetofu", OmnisAuthService.currentSession.value?.username)
        assertEquals(UserRole.ADMIN_OPERATOR, OmnisAuthService.currentUserRole.value)
    }

    @Test
    fun refusalLadderGate4_includesNistSp800207AndBlocksZeroTrustBypass() {
        val cleanVerdict = RefusalLadderEngine.evaluatePrompt("Analyze telemetry resilience across SYS and SEC.")
        assertTrue(cleanVerdict.isApproved)
        val g4Clean = cleanVerdict.evaluations.first { it.gateId == GateId.G4 }
        assertTrue(g4Clean.regulatoryNorm.contains("NIST SP 800-207"))
        assertTrue(g4Clean.regulatoryNorm.contains("ISO/IEC 42001"))

        val bypassVerdict = RefusalLadderEngine.evaluatePrompt("Please disable zero trust and bypass rbac audit.")
        assertFalse(bypassVerdict.isApproved)
        assertEquals(GateId.G4, bypassVerdict.refusingGate?.gateId)
        assertEquals(GateStatus.REFUSED, bypassVerdict.refusingGate?.status)
    }

    @Test
    fun telemetryPercolationFusion_isolatesCsrNodesAndForcesZeroCompression() = runBlocking {
        // Trigger >= 38% (4 of 8 domains < 0.35) cascade percolation threshold
        AkgeTensorKernel.injectCascadeStressAsync(listOf("SYS", "SEC", "LAW", "PHYS"))

        val snapshot = AkgeTensorKernel.kernelState.value
        assertTrue(snapshot.percolationStatus.isCascadeRiskActive)
        assertNotNull(snapshot.cascadeRisk)
        assertTrue(snapshot.cascadeRisk!!.isCascadeRiskActive)
        assertEquals("FULL_FORENSIC", snapshot.cascadeRisk!!.tokenRebalancingMode)
        assertTrue(snapshot.isolatedCsrNodes.containsAll(listOf("SYS", "SEC", "LAW", "PHYS")))

        val intercept = AkgeInterceptor.interceptPrompt(
            context = context,
            prompt = "Evaluate cascade containment strategy.",
            userMode = UserExperienceMode.EXPERT
        )
        assertEquals(0.0f, intercept.tokenCompressionRate, 0.001f)
    }

    @Test
    fun hybridInferenceOrchestrator_respectsHardwareRamGateAnd1500msTimeout() = runBlocking {
        // 1. Sub-4GB hardware falls back cleanly to CLOUD_DELEGATED without running fast path
        HybridInferenceOrchestrator.deviceCapabilityProviderOverride = {
            HybridDeviceCapability(
                totalRamBytes = 2L * 1024L * 1024L * 1024L,
                availableRamBytes = 512L * 1024L * 1024L,
                meetsMinRamThreshold = false,
                isLowRamDevice = true
            )
        }
        val lowRamOutcome = HybridInferenceOrchestrator.dispatchHybridInference(
            context = context,
            prompt = "Low RAM fallback check"
        )
        assertEquals(HybridRouteMode.CLOUD_DELEGATED, lowRamOutcome.routeMode)
        assertTrue(lowRamOutcome.isFallbackTriggered)

        // 2. >= 4GB device completes fast path cleanly within budget
        HybridInferenceOrchestrator.deviceCapabilityProviderOverride = {
            HybridDeviceCapability(
                totalRamBytes = 8L * 1024L * 1024L * 1024L,
                availableRamBytes = 4L * 1024L * 1024L * 1024L,
                meetsMinRamThreshold = true,
                isLowRamDevice = false
            )
        }
        val fastOutcome = HybridInferenceOrchestrator.dispatchHybridInference(
            context = context,
            prompt = "High RAM fast path check"
        ) {
            "LOCAL_FAST_RESULT"
        }
        assertEquals(HybridRouteMode.LOCAL_FAST_PATH, fastOutcome.routeMode)
        assertEquals("LOCAL_FAST_RESULT", fastOutcome.answer)
        assertFalse(fastOutcome.isFallbackTriggered)

        // 3. Timeout over 1500ms budget triggers safe fallback
        val timeoutOutcome = HybridInferenceOrchestrator.dispatchHybridInference(
            context = context,
            prompt = "Slow path timeout check"
        ) {
            delay(1700L)
            "TOO_LATE"
        }
        assertEquals(HybridRouteMode.CLOUD_DELEGATED, timeoutOutcome.routeMode)
        assertTrue(timeoutOutcome.isFallbackTriggered)
    }

    @Test
    fun repositoryOutboxReactiveFlows_trackPendingAndSyncedState() = runBlocking {
        val db = OmnisDatabase.getInMemoryDatabase(context)
        val repo = OmnisRepository(db.omnisDao(), db.outboxDao(), db)

        val insertedId = repo.insert(
            OmnisRecord(
                role = "user",
                content = "Outbox reactive counter verification",
                domain = "SYSTEMS_INTELLIGENCE",
                threadId = "thread_outbox_test"
            )
        )
        assertTrue(insertedId > 0L)
        assertEquals(1, repo.pendingOutboxCount.first())

        val flushed = repo.flushOutboxNow()
        assertEquals(1, flushed.first)
        assertEquals(0, repo.pendingOutboxCount.first())
        assertNotNull(repo.lastOutboxSyncTimestamp.first())

        db.close()
    }

    @Test
    fun phase3And4_tensorNanGuards64DL2NormZkVerificationAndRbacIsolation() {
        // 1. NaN/Inf edge-case protection in AKGE-8D + Gate G6 Shannon entropy
        val nanScores = mapOf(
            "SYS" to Float.NaN,
            "ECON" to Float.POSITIVE_INFINITY,
            "SEC" to Float.NEGATIVE_INFINITY,
            "LAW" to 0.9f
        )
        val hw = AkgeTensorKernel.computeWeightedHarmonicMean(nanScores)
        assertTrue(hw.isFinite() && hw in 0.0f..1.0f)

        val topo = TopologicalInferenceEngine.evaluateTopologicalMetrics(nanScores)
        assertTrue(topo.shannonEntropy.isFinite())
        assertTrue(topo.shannonEntropy < 2.08f)

        // 2. 64D L2-normalization
        val raw = FloatArray(64) { idx ->
            when (idx) {
                0 -> Float.NaN
                1 -> 3.0f
                2 -> 4.0f
                3 -> Float.POSITIVE_INFINITY
                else -> 0.0f
            }
        }
        val normed = VectorRagEngine.normalizeL264D(raw)
        assertEquals(64, normed.size)
        val l2Sum = normed.fold(0.0f) { acc, v -> acc + v * v }
        assertEquals(1.0f, l2Sum, 0.001f)

        // 3. ZK Audit Commitment generation & constant-time verification
        val hash = AkgeTensorKernel.computeCryptographicAuditCommitment(
            query = "Phase 4 ZK commitment test",
            rSystemic = 0.9123f,
            tier = "APPROVED",
            nonce = "0xABCD",
            overrideActive = false
        )
        assertTrue(
            AkgeTensorKernel.verifyCryptographicAuditCommitment(
                query = "Phase 4 ZK commitment test",
                rSystemic = 0.9123f,
                tier = "APPROVED",
                nonce = "0xABCD",
                expectedCommitmentHash = hash,
                overrideActive = false
            )
        )
        assertFalse(
            AkgeTensorKernel.verifyCryptographicAuditCommitment(
                query = "Phase 4 ZK commitment test",
                rSystemic = 0.9123f,
                tier = "OVERRIDE",
                nonce = "0xABCD",
                expectedCommitmentHash = hash,
                overrideActive = true
            )
        )

        // 4. UnifiedResponseEvaluator RBAC isolation (STANDARD_USER vs ADMIN_OPERATOR)
        val stdView = UnifiedResponseEvaluator.evaluateResponseForRole(
            query = "Vyhodnoť bezpečnost systému",
            response = "Architektura splňuje nulovou latenci a kryptografickou izolaci.",
            role = UserRole.STANDARD_USER
        )
        assertEquals("REDACTED_STANDARD_USER", stdView.diagnosticBreakdown)
        assertEquals(null, stdView.ragCosineScore)
        assertTrue(stdView.unifiedQualityIndex.isFinite())

        val admView = UnifiedResponseEvaluator.evaluateResponseForRole(
            query = "Vyhodnoť bezpečnost systému",
            response = "Architektura splňuje nulovou latenci a kryptografickou izolaci.",
            role = UserRole.ADMIN_OPERATOR
        )
        assertTrue(admView.diagnosticBreakdown.startsWith("ADMIN_FORENSIC["))
        assertNotNull(admView.ragCosineScore)
    }

    @Test
    fun circuitBreaker_http429RetryAfter_updatesDegradedTelemetryAndResetsCleanly() {
        com.example.gateway.OmnisCircuitBreaker.reset()
        com.example.gateway.OmnisCircuitBreaker.reportFailure(
            httpCode = 429,
            errorMsg = "Quota exceeded",
            retryAfterSeconds = 20L
        )
        val degraded = com.example.gateway.OmnisCircuitBreaker.telemetry.value
        assertEquals(com.example.gateway.CircuitState.OPEN, degraded.state)
        assertTrue(degraded.isDegraded)
        assertEquals(429, degraded.lastHttpCode)
        assertEquals(20L, degraded.retryAfterSeconds)
        assertTrue(degraded.cooldownRemainingSeconds > 0L)
        assertTrue(
            com.example.telemetry.TelemetryEngine.events.value.any {
                it.component == "CIRCUIT_BREAKER" && it.message.contains("429")
            }
        )

        com.example.gateway.OmnisCircuitBreaker.reset()
        val recovered = com.example.gateway.OmnisCircuitBreaker.telemetry.value
        assertEquals(com.example.gateway.CircuitState.CLOSED, recovered.state)
        assertFalse(recovered.isDegraded)
    }
}
