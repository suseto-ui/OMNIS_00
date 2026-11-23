package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.max

/**
 * CSR (Compressed Sparse Row) reprezentace řídké matice pro 8D kognitivní graf.
 */
data class AkgeCsrMatrix(
    val rowPointers: IntArray,
    val columnIndices: IntArray,
    val values: FloatArray,
    val dimension: Int
) {
    /**
     * Spočte násobení matice s vektorem: y = M * x
     */
    fun multiplyVector(vector: FloatArray): FloatArray {
        require(vector.size == dimension) { "Dimenze vektoru (${vector.size}) neodpovídá matici ($dimension)" }
        val result = FloatArray(dimension)
        for (i in 0 until dimension) {
            var sum = 0.0f
            val start = rowPointers[i]
            val end = rowPointers[i + 1]
            for (k in start until end) {
                val j = columnIndices[k]
                sum += values[k] * vector[j]
            }
            result[i] = sum
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AkgeCsrMatrix
        if (!rowPointers.contentEquals(other.rowPointers)) return false
        if (!columnIndices.contentEquals(other.columnIndices)) return false
        if (!values.contentEquals(other.values)) return false
        return dimension == other.dimension
    }

    override fun hashCode(): Int {
        var result = rowPointers.contentHashCode()
        result = 31 * result + columnIndices.contentHashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + dimension
        return result
    }
}

data class AkgeNode(
    val id: String,
    val domain: String,
    val weight: Float,
    val epistemicStatus: String = "VERIFIED"
)

data class AkgeEdge(
    val sourceDomain: String,
    val targetDomain: String,
    val couplingWeight: Float,
    val causalDirection: String = "BIDIRECTIONAL"
)

data class AdvancedMetrics(
    val quantumEntanglementIndex: Float, // Float 0.0 - 1.0 (normalizovaná kovariance 28-párového tenzoru)
    val latencyDriftMs: Long,             // Long: odchylka reálné latence od baseline 12 ms
    val selfHealingConfidence: Float,    // Float 0.0 - 1.0 (míra autonomní nápravy z entropie S)
    val cognitiveAnomalyScore: Float      // Float 0.0 - 1.0 (součin entropie, provázanosti a driftu)
)

data class AkgeKernelSnapshot(
    val timestamp: Long,
    val domainWeights: Map<String, Float>,
    val csrMatrix: AkgeCsrMatrix,
    val topologicalMetrics: TopologicalMetrics,
    val percolationStatus: PercolationStatus,
    val systemStressSigma: Float,
    val stateCommitmentHash: String,
    val activeAnomaliesCount: Int,
    val advancedMetrics: AdvancedMetrics = AdvancedMetrics(0.88f, 6L, 0.99f, 0.05f),
    val quantumEntanglementIndex: Float = advancedMetrics.quantumEntanglementIndex,
    val latencyDriftMs: Float = advancedMetrics.latencyDriftMs.toFloat(),
    val selfHealingConfidence: Float = advancedMetrics.selfHealingConfidence,
    val cognitiveAnomalyScore: Float = advancedMetrics.cognitiveAnomalyScore,
    val cascadeRisk: com.example.ui.CascadeRiskAnalysis? = null,
    val isolatedCsrNodes: Set<String> = emptySet()
)

/**
 * AKGE-8D Tenzorový Grafový Kernel (Agentic Knowledge Graph Engine)
 *
 * Implementuje optimalizovaný kognitivní kernel pro O.M.N.I.S. v4.2 / AKGE-8.
 * Poskytuje řídkou CSR maticovou reprezentaci vazeb mezi 8 doménami (SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC),
 * difúzi tenzorového toku a deterministické kryptografické otisky stavu.
 */
object AkgeTensorKernel {

    val DOMAIN_KEYS = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")
    private val DEFAULT_EDGES = listOf(
        AkgeEdge("SYS", "SEC", 0.85f),
        AkgeEdge("SEC", "LAW", 0.75f),
        AkgeEdge("SYS", "PHYS", 0.80f),
        AkgeEdge("ECON", "SYS", 0.60f),
        AkgeEdge("ECO", "ECON", 0.65f),
        AkgeEdge("PSYCH", "SOC", 0.70f),
        AkgeEdge("SOC", "LAW", 0.65f),
        AkgeEdge("SEC", "PHYS", 0.50f)
    )
    private val baselineCsrMatrix: AkgeCsrMatrix = buildCsrMatrix(DEFAULT_EDGES, DOMAIN_KEYS)
    val csrKnowledgeGraph: CsrKnowledgeGraph = CsrKnowledgeGraph.createDefaultGraph()

    private val _kernelState = MutableStateFlow(createDefaultSnapshot())
    val kernelState: StateFlow<AkgeKernelSnapshot> = _kernelState.asStateFlow()

    private val _cognitiveAnomalyAlert = MutableStateFlow(false)
    val cognitiveAnomalyAlert: StateFlow<Boolean> = _cognitiveAnomalyAlert.asStateFlow()

    fun sanitizeUnitFloat(
        value: Float,
        default: Float = 0.5f,
        minVal: Float = 0.0f,
        maxVal: Float = 1.0f
    ): Float {
        if (!value.isFinite()) return default
        return value.coerceIn(minVal, maxVal)
    }

    fun computeAdvancedMetrics(
        weights: Map<String, Float>,
        entropy: Float,
        realExecutionLatencyMs: Long = 18L
    ): AdvancedMetrics {
        val n = DOMAIN_KEYS.size
        var pairCount = 0
        var sumCovariance = 0.0f
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val v1 = sanitizeUnitFloat(weights[DOMAIN_KEYS[i]] ?: 0.5f)
                val v2 = sanitizeUnitFloat(weights[DOMAIN_KEYS[j]] ?: 0.5f)
                val pairEntanglement = 1.0f - abs(v1 - v2)
                sumCovariance += pairEntanglement
                pairCount++
            }
        }
        val qei = if (pairCount > 0) sanitizeUnitFloat(sumCovariance / pairCount, default = 0.85f) else 0.85f
        val drift = max(0L, realExecutionLatencyMs - 12L)
        val safeEntropy = sanitizeUnitFloat(entropy, default = 0.5f, minVal = 0.0f, maxVal = 2.08f)
        val normalizedEntropy = sanitizeUnitFloat(safeEntropy / 2.08f, default = 0.25f)
        val selfHealing = sanitizeUnitFloat(1.0f - normalizedEntropy * 0.70f, default = 0.82f)
        val normDrift = sanitizeUnitFloat(drift.toFloat() / 100.0f, default = 0.0f)
        val anomaly = sanitizeUnitFloat(normalizedEntropy * (1.0f - qei * 0.5f) * (0.3f + 0.7f * normDrift), default = 0.0f)

        return AdvancedMetrics(
            quantumEntanglementIndex = (qei * 100f).toInt() / 100f,
            latencyDriftMs = drift,
            selfHealingConfidence = (selfHealing * 100f).toInt() / 100f,
            cognitiveAnomalyScore = (anomaly * 100f).toInt() / 100f
        )
    }

    fun createDefaultSnapshot(): AkgeKernelSnapshot {
        val initialWeights = mapOf(
            "SYS" to 0.85f,
            "ECON" to 0.72f,
            "PSYCH" to 0.65f,
            "ECO" to 0.58f,
            "LAW" to 0.90f,
            "SEC" to 0.92f,
            "PHYS" to 0.80f,
            "SOC" to 0.70f
        )
        val csr = baselineCsrMatrix
        val domainStatuses = initialWeights.mapValues { if (it.value >= 0.5f) "STABLE" else "STRESSED" }

        val topoMetrics = TopologicalInferenceEngine.evaluateTopologicalMetrics(
            domainScores = initialWeights,
            customNodesCount = 8,
            customEdgesCount = DEFAULT_EDGES.size,
            customFacesCount = 4
        )
        val percolation = TopologicalInferenceEngine.evalCascadeRisk(domainStatuses)
        val sigma = computeSystemStress(initialWeights)
        val hash = computeStateCommitment(initialWeights, sigma, System.currentTimeMillis())
        val advanced = computeAdvancedMetrics(initialWeights, topoMetrics.shannonEntropy, 18L)

        return AkgeKernelSnapshot(
            timestamp = System.currentTimeMillis(),
            domainWeights = initialWeights,
            csrMatrix = csr,
            topologicalMetrics = topoMetrics,
            percolationStatus = percolation,
            systemStressSigma = sigma,
            stateCommitmentHash = hash,
            activeAnomaliesCount = if (percolation.isCascadeRiskActive) 1 else 0,
            advancedMetrics = advanced
        )
    }

    /**
     * Vytvoří CSR matici z hran a seznamu domén.
     */
    fun buildCsrMatrix(edges: List<AkgeEdge>, domains: List<String>): AkgeCsrMatrix {
        val n = domains.size
        val domainIndexMap = domains.withIndex().associate { it.value to it.index }

        // Sestavení seznamu sousedů pro každý řádek (i -> list of (j, weight))
        val rowEntries = Array(n) { mutableListOf<Pair<Int, Float>>() }
        for (edge in edges) {
            val srcIdx = domainIndexMap[edge.sourceDomain] ?: continue
            val dstIdx = domainIndexMap[edge.targetDomain] ?: continue
            rowEntries[srcIdx].add(Pair(dstIdx, edge.couplingWeight))
            if (edge.causalDirection == "BIDIRECTIONAL") {
                rowEntries[dstIdx].add(Pair(srcIdx, edge.couplingWeight))
            }
        }

        val rowPointers = IntArray(n + 1)
        val colList = mutableListOf<Int>()
        val valList = mutableListOf<Float>()

        for (i in 0 until n) {
            rowPointers[i] = colList.size
            // Seřadit podle indexu sloupce
            val sorted = rowEntries[i].sortedBy { it.first }
            for (entry in sorted) {
                colList.add(entry.first)
                valList.add(entry.second)
            }
        }
        rowPointers[n] = colList.size

        return AkgeCsrMatrix(
            rowPointers = rowPointers,
            columnIndices = colList.toIntArray(),
            values = valList.toFloatArray(),
            dimension = n
        )
    }

    /**
     * Spočte difúzi tenzorového pole: T_new = α * T + (1-α) * (M_CSR * T_norm)
     */
    fun propagateTensorField(
        currentWeights: Map<String, Float>,
        csr: AkgeCsrMatrix,
        alpha: Float = 0.70f
    ): Map<String, Float> {
        val n = DOMAIN_KEYS.size
        val safeAlpha = sanitizeUnitFloat(alpha, default = 0.70f)
        val vector = FloatArray(n) { idx ->
            sanitizeUnitFloat(currentWeights[DOMAIN_KEYS[idx]] ?: 0.5f)
        }

        // Násobení CSR maticí
        val diffused = csr.multiplyVector(vector)

        // Normalizace a lineární kombinace s předchozím stavem
        val updated = mutableMapOf<String, Float>()
        for (i in 0 until n) {
            val domain = DOMAIN_KEYS[i]
            val prevVal = vector[i]
            val start = csr.rowPointers[i]
            val end = csr.rowPointers[i + 1]
            var weightSum = 0.0f
            for (k in start until end) {
                weightSum += sanitizeUnitFloat(csr.values[k], default = 0.0f)
            }
            val normFactor = if (weightSum > 0.001f) weightSum else 1.0f
            val diffVal = sanitizeUnitFloat(diffused[i] / normFactor)
            val finalVal = sanitizeUnitFloat(safeAlpha * prevVal + (1.0f - safeAlpha) * diffVal, default = 0.5f, minVal = 0.05f, maxVal = 1.0f)
            updated[domain] = (finalVal * 100f).toInt() / 100f
        }
        return updated
    }

    val PRIORITY_PROFILES: Map<String, Map<String, Float>> = mapOf(
        "Balanced Core" to mapOf(
            "SYS" to 0.125f, "ECON" to 0.125f, "PSYCH" to 0.125f, "ECO" to 0.125f,
            "LAW" to 0.125f, "SEC" to 0.125f, "PHYS" to 0.125f, "SOC" to 0.125f
        ),
        "High Sec & Phys" to mapOf(
            "SEC" to 0.25f, "PHYS" to 0.25f, "SYS" to 0.15f, "LAW" to 0.15f,
            "ECON" to 0.05f, "PSYCH" to 0.05f, "ECO" to 0.05f, "SOC" to 0.05f
        ),
        "Lean Econ" to mapOf(
            "ECON" to 0.35f, "SYS" to 0.20f, "SEC" to 0.15f, "LAW" to 0.10f,
            "PSYCH" to 0.05f, "ECO" to 0.05f, "PHYS" to 0.05f, "SOC" to 0.05f
        )
    )

    fun computeWeightedHarmonicMean(
        domainValues: Map<String, Float>,
        priorityProfile: String = "Balanced Core"
    ): Float {
        val weights = PRIORITY_PROFILES[priorityProfile] ?: PRIORITY_PROFILES.getValue("Balanced Core")
        var sumW = 0.0
        var sumWOverV = 0.0
        for (domain in DOMAIN_KEYS) {
            val w = sanitizeUnitFloat(weights[domain] ?: 0.125f, default = 0.125f, minVal = 0.001f, maxVal = 1.0f)
            val rawV = domainValues[domain] ?: domainValues[domain.lowercase()] ?: 0.5f
            val v = sanitizeUnitFloat(rawV, default = 0.5f, minVal = 0.001f, maxVal = 1.0f)
            sumW += w
            sumWOverV += w / v.toDouble()
        }
        val hw = if (sumWOverV > 0.0 && sumW.isFinite() && sumWOverV.isFinite()) {
            sanitizeUnitFloat((sumW / sumWOverV).toFloat())
        } else {
            0.5f
        }
        return kotlin.math.round(hw * 10000.0f) / 10000.0f
    }

    /**
     * Deterministický SHA-256 / HMAC-SHA256 Hash-Chain auditní otisk s 1:1 paritou vůči Python backend/schemas/akge.py:
     * CommitmentHash = (HMAC-)SHA-256("OMNIS-ZK-PROOF" || QueryHash || R_systemic || Tier || Nonce [|| OVERRIDE_ACTIVE] [|| PrevCommitmentHash])
     */
    fun computeCryptographicAuditCommitment(
        query: String,
        rSystemic: Float,
        tier: String,
        nonce: String,
        overrideActive: Boolean = false,
        prevCommitmentHash: String = "GENESIS",
        hmacKey: String? = null
    ): String {
        val safeR = sanitizeUnitFloat(rSystemic)
        val digest = MessageDigest.getInstance("SHA-256")
        val queryHash = digest.digest(query.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val rFormatted = String.format(java.util.Locale.US, "%.4f", safeR)
        val payload = buildString {
            append("OMNIS-ZK-PROOF||")
            append(queryHash)
            append("||")
            append(rFormatted)
            append("||")
            append(tier)
            append("||")
            append(nonce)
            if (overrideActive) {
                append("||OVERRIDE_ACTIVE")
            }
            if (prevCommitmentHash != "GENESIS" && prevCommitmentHash.isNotBlank()) {
                append("||")
                append(prevCommitmentHash)
            }
        }
        return if (!hmacKey.isNullOrBlank()) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmacKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            mac.doFinal(payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } else {
            MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Konstantně-časová kryptografická verifikace ZK Audit Ledger závazku.
     */
    fun verifyCryptographicAuditCommitment(
        query: String,
        rSystemic: Float,
        tier: String,
        nonce: String,
        expectedCommitmentHash: String,
        overrideActive: Boolean = false,
        prevCommitmentHash: String = "GENESIS",
        hmacKey: String? = null
    ): Boolean {
        val expected = expectedCommitmentHash.trim().lowercase()
        if (expected.isEmpty()) return false
        val recomputed = computeCryptographicAuditCommitment(
            query = query,
            rSystemic = rSystemic,
            tier = tier,
            nonce = nonce,
            overrideActive = overrideActive,
            prevCommitmentHash = prevCommitmentHash,
            hmacKey = hmacKey
        )
        return MessageDigest.isEqual(
            recomputed.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Zachovaná kompatibilní signatura pro auditní otisk (SHA-256 Hash-Chain bez externího HMAC klíče).
     */
    fun computeAkgeZkCommitmentHash(
        query: String,
        rSystemic: Float,
        tier: String,
        nonce: String,
        overrideActive: Boolean = false
    ): String = computeCryptographicAuditCommitment(
        query = query,
        rSystemic = rSystemic,
        tier = tier,
        nonce = nonce,
        overrideActive = overrideActive
    )

    fun computeSystemStress(weights: Map<String, Float>): Float {
        var stressAcc = 0.0f
        weights.values.forEach { rawW ->
            val w = sanitizeUnitFloat(rawW)
            if (w < 0.5f) {
                stressAcc += (0.5f - w) * 2.0f
            }
        }
        return sanitizeUnitFloat(stressAcc / DOMAIN_KEYS.size, default = 0.0f)
    }

    fun computeStateCommitment(weights: Map<String, Float>, sigma: Float, timestamp: Long): String {
        val payload = "AKGE-8D-$timestamp-${weights.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value}" }}-SIGMA:$sigma"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.take(8).joinToString("") { "%02X".format(it) }
    }

    /**
     * Izoluje přetížené uzly (< 0.35f) v řídké CSR matici při překročení perkolačního prahu (p_c >= 0.38).
     */
    fun isolateStressedNodesInCsr(csr: AkgeCsrMatrix, stressedDomains: Set<String>): AkgeCsrMatrix {
        if (stressedDomains.isEmpty()) return csr
        val stressedIndices = DOMAIN_KEYS.withIndex()
            .filter { stressedDomains.contains(it.value) }
            .map { it.index }
            .toSet()
        if (stressedIndices.isEmpty()) return csr

        val filteredValues = csr.values.copyOf()
        for (row in 0 until csr.dimension) {
            val start = csr.rowPointers[row]
            val end = csr.rowPointers[row + 1]
            for (k in start until end) {
                val col = csr.columnIndices[k]
                if (stressedIndices.contains(row) || stressedIndices.contains(col)) {
                    filteredValues[k] = 0.0f
                }
            }
        }
        return csr.copy(values = filteredValues)
    }

    /**
     * Provede hromadnou aktualizaci vah pro více domén najednou s telemetrickou fúzí perkolace.
     */
    fun updateDomainWeights(newWeightsMap: Map<String, Float>) {
        val current = _kernelState.value
        val updatedWeights = current.domainWeights.toMutableMap()
        newWeightsMap.forEach { (domain, w) ->
            updatedWeights[domain] = sanitizeUnitFloat(w)
        }

        val preCheckStressed = updatedWeights.filterValues { it < 0.35f }.keys
        val diffusionTopology = if (preCheckStressed.size.toFloat() / DOMAIN_KEYS.size >= 0.38f) {
            isolateStressedNodesInCsr(baselineCsrMatrix, preCheckStressed)
        } else {
            baselineCsrMatrix
        }
        val diffusedWeights = propagateTensorField(updatedWeights, diffusionTopology)
        val domainStatuses = diffusedWeights.mapValues { if (it.value >= 0.5f) "STABLE" else "STRESSED" }

        val topoMetrics = TopologicalInferenceEngine.evaluateTopologicalMetrics(
            domainScores = diffusedWeights,
            customNodesCount = 8,
            customEdgesCount = baselineCsrMatrix.values.size / 2,
            customFacesCount = 4
        )
        val percolation = TopologicalInferenceEngine.evalCascadeRisk(domainStatuses)
        val sigma = computeSystemStress(diffusedWeights)
        
        val topoResult = TopologicalInferenceEngine.verifyInferenceOutput(
            domainScores = diffusedWeights,
            domainsStatus = domainStatuses,
            systemStressSigma = sigma
        )

        if (!topoResult.isPhase2Verified) {
            com.example.telemetry.TelemetryEngine.log(
                type = "WARNING",
                component = "SYSTEM_INTELLIGENCE",
                message = topoResult.diagnosticMessage,
                metadata = "{\"remediation\":\"${topoResult.remediationSteps.joinToString()}\"}"
            )
        }

        val advanced = computeAdvancedMetrics(
            diffusedWeights,
            topoMetrics.shannonEntropy,
            if (percolation.isCascadeRiskActive) 450L else 18L
        )
        val cascadeRisk = com.example.ui.OmnisCorrelationEngine.evaluateCascadeRiskWithTelemetry(
            domainValues = diffusedWeights,
            cognitiveAnomalyScore = advanced.cognitiveAnomalyScore,
            quantumEntanglementIndex = advanced.quantumEntanglementIndex,
            latencyDriftMs = advanced.latencyDriftMs.toFloat(),
            selfHealingConfidence = advanced.selfHealingConfidence
        )
        val stressedNodes = if (cascadeRisk.isCascadeRiskActive || percolation.isCascadeRiskActive) {
            diffusedWeights.filterValues { it < 0.35f }.keys
        } else {
            emptySet()
        }
        val effectiveCsr = isolateStressedNodesInCsr(baselineCsrMatrix, stressedNodes)

        if (cascadeRisk.isCascadeRiskActive) {
            com.example.telemetry.TelemetryEngine.log(
                type = "WARNING",
                component = "PERCOLATION_FUSION",
                message = "${cascadeRisk.warningSummary} (Izolované uzly CSR: ${stressedNodes.ifEmpty { setOf("NONE") }})"
            )
        }

        val hash = computeStateCommitment(diffusedWeights, sigma, System.currentTimeMillis())
        val nextSnapshot = AkgeKernelSnapshot(
            timestamp = System.currentTimeMillis(),
            domainWeights = diffusedWeights,
            csrMatrix = effectiveCsr,
            topologicalMetrics = topoMetrics,
            percolationStatus = percolation,
            systemStressSigma = sigma,
            stateCommitmentHash = hash,
            activeAnomaliesCount = if (cascadeRisk.isCascadeRiskActive || percolation.isCascadeRiskActive) max(1, stressedNodes.size) else 0,
            advancedMetrics = advanced,
            cascadeRisk = cascadeRisk,
            isolatedCsrNodes = stressedNodes
        )
        
        com.example.ui.OmnisCorrelationEngine.quantumEntanglementScale = nextSnapshot.quantumEntanglementIndex
        _cognitiveAnomalyAlert.value = advanced.cognitiveAnomalyScore > 0.75f
        _kernelState.value = nextSnapshot
    }

    /**
     * Provede krok simulace nebo aplikaci nového doménového podnětu.
     */
    fun updateDomainWeight(domain: String, newWeight: Float) {
        updateDomainWeights(mapOf(domain to newWeight))
    }

    /**
     * Injektuje poruchu (např. výpadek SEC a SYS) pro zátěžové testování kaskád a izolace CSR uzlů.
     */
    fun injectCascadeStress(affectedDomains: List<String>) {
        val current = _kernelState.value
        val updatedWeights = current.domainWeights.toMutableMap()
        for (domain in affectedDomains) {
            updatedWeights[domain] = 0.15f
        }

        val domainStatuses = updatedWeights.mapValues { if (it.value >= 0.5f) "STABLE" else "STRESSED" }
        val topoMetrics = TopologicalInferenceEngine.evaluateTopologicalMetrics(
            domainScores = updatedWeights,
            customNodesCount = 8,
            customEdgesCount = current.csrMatrix.values.size / 2,
            customFacesCount = 4
        )
        val percolation = TopologicalInferenceEngine.evalCascadeRisk(domainStatuses)
        val sigma = computeSystemStress(updatedWeights)
        val hash = computeStateCommitment(updatedWeights, sigma, System.currentTimeMillis())
        val advanced = computeAdvancedMetrics(updatedWeights, topoMetrics.shannonEntropy, 350L)
        val cascadeRisk = com.example.ui.OmnisCorrelationEngine.evaluateCascadeRiskWithTelemetry(
            domainValues = updatedWeights,
            cognitiveAnomalyScore = advanced.cognitiveAnomalyScore,
            quantumEntanglementIndex = advanced.quantumEntanglementIndex,
            latencyDriftMs = advanced.latencyDriftMs.toFloat(),
            selfHealingConfidence = advanced.selfHealingConfidence
        )
        val stressedNodes = if (cascadeRisk.isCascadeRiskActive || percolation.isCascadeRiskActive) {
            updatedWeights.filterValues { it < 0.35f }.keys
        } else {
            emptySet()
        }
        val effectiveCsr = isolateStressedNodesInCsr(baselineCsrMatrix, stressedNodes)
        _cognitiveAnomalyAlert.value = advanced.cognitiveAnomalyScore > 0.75f

        _kernelState.value = AkgeKernelSnapshot(
            timestamp = System.currentTimeMillis(),
            domainWeights = updatedWeights,
            csrMatrix = effectiveCsr,
            topologicalMetrics = topoMetrics,
            percolationStatus = percolation,
            systemStressSigma = sigma,
            stateCommitmentHash = hash,
            activeAnomaliesCount = percolation.failingDomains,
            advancedMetrics = advanced,
            cascadeRisk = cascadeRisk,
            isolatedCsrNodes = stressedNodes
        )
    }

    /**
     * Asynchronní varianta aktualizace vah izolovaná na Dispatchers.Default (60/120 FPS UI safe).
     */
    suspend fun updateDomainWeightsAsync(
        newWeightsMap: Map<String, Float>,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
    ) =
        withContext(dispatcher) {
            updateDomainWeights(newWeightsMap)
        }

    /**
     * Asynchronní injekce kaskádového stresu izolovaná na Dispatchers.Default.
     */
    suspend fun injectCascadeStressAsync(
        affectedDomains: List<String>,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
    ) =
        withContext(dispatcher) {
            injectCascadeStress(affectedDomains)
        }

    /**
     * Resetuje kernel na výchozí nominální stav.
     */
    fun resetToEquilibrium() {
        _cognitiveAnomalyAlert.value = false
        _kernelState.value = createDefaultSnapshot()
    }
}
