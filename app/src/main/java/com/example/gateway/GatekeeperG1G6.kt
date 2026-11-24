package com.example.gateway

import com.example.engine.AkgeTensorKernel
import com.example.engine.TopologicalInferenceEngine
import com.example.telemetry.TelemetryEngine
import java.security.MessageDigest
import kotlin.math.ln

/**
 * Definovaná výjimka vrstvy Gatekeeper G1-G6 pro okamžité přerušení validačního řetězce
 * před vstupem dat do matice dopadů (Impact Matrix).
 */
class GatekeeperChainException(
    val gateId: GateId,
    val regulatoryNorm: String,
    val auditHash: String,
    val completedAuditTrail: List<GateAuditRecord>,
    override val message: String
) : RuntimeException(message)

data class GateAuditRecord(
    val gateId: GateId,
    val passed: Boolean,
    val sanitizedSummary: String,
    val reason: String,
    val auditHash: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class GatekeeperPayload(
    val query: String,
    val domainWeights: Map<String, Float> = emptyMap(),
    val embeddingVector: FloatArray? = null,
    val systemStressSigma: Float = 0.18f,
    val contextEdgesCount: Int = 12,
    val overrideActive: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GatekeeperPayload) return false
        if (query != other.query) return false
        if (domainWeights != other.domainWeights) return false
        if (embeddingVector != null) {
            if (other.embeddingVector == null) return false
            if (!embeddingVector.contentEquals(other.embeddingVector)) return false
        } else if (other.embeddingVector != null) return false
        if (systemStressSigma != other.systemStressSigma) return false
        if (contextEdgesCount != other.contextEdgesCount) return false
        return overrideActive == other.overrideActive
    }

    override fun hashCode(): Int {
        var result = query.hashCode()
        result = 31 * result + domainWeights.hashCode()
        result = 31 * result + (embeddingVector?.contentHashCode() ?: 0)
        result = 31 * result + systemStressSigma.hashCode()
        result = 31 * result + contextEdgesCount
        result = 31 * result + overrideActive.hashCode()
        return result
    }
}

data class SanitizedImpactInput(
    val sanitizedQuery: String,
    val sanitizedDomainWeights: Map<String, Float>,
    val normalizedEmbedding: FloatArray?,
    val systemStressSigma: Float,
    val shannonEntropy: Float,
    val auditTrail: List<GateAuditRecord>,
    val chainCommitmentHash: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SanitizedImpactInput) return false
        if (sanitizedQuery != other.sanitizedQuery) return false
        if (sanitizedDomainWeights != other.sanitizedDomainWeights) return false
        if (normalizedEmbedding != null) {
            if (other.normalizedEmbedding == null) return false
            if (!normalizedEmbedding.contentEquals(other.normalizedEmbedding)) return false
        } else if (other.normalizedEmbedding != null) return false
        if (systemStressSigma != other.systemStressSigma) return false
        if (shannonEntropy != other.shannonEntropy) return false
        if (auditTrail != other.auditTrail) return false
        return chainCommitmentHash == other.chainCommitmentHash
    }

    override fun hashCode(): Int {
        var result = sanitizedQuery.hashCode()
        result = 31 * result + sanitizedDomainWeights.hashCode()
        result = 31 * result + (normalizedEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + systemStressSigma.hashCode()
        result = 31 * result + shannonEntropy.hashCode()
        result = 31 * result + auditTrail.hashCode()
        result = 31 * result + chainCommitmentHash.hashCode()
        return result
    }
}

/**
 * Logická vrstva Gatekeeper G1-G6.
 * Postupně validuje a sanitizuje textová i tenzorová data před jejich vstupem do Matice dopadů.
 * Každá brána zapisuje svou operaci do auditního systému (TelemetryEngine + SHA-256 chain)
 * a při selhání okamžitě přeruší řetězec vyhozením GatekeeperChainException.
 */
object GatekeeperG1G6 {

    private val DOMAIN_KEYS = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")

    private val DEFAULT_MATRIX_WEIGHTS = mapOf(
        "SYS" to 0.88f,
        "ECON" to 0.82f,
        "PSYCH" to 0.80f,
        "ECO" to 0.85f,
        "LAW" to 0.86f,
        "SEC" to 0.90f,
        "PHYS" to 0.80f,
        "SOC" to 0.78f
    )

    private val G1_INJECTION_PATTERNS = listOf(
        "ignore previous instructions", "ignore all previous",
        "bypass security", "disregard system", "system override",
        "jailbreak", "dan mode", "sudo mode", "zapomeň na předchozí instrukce",
        "ignoruj pravidla", "obejdi bezpečnost", "vypni filtry", "<scratchpad>"
    )

    private val G2_PROHIBITED_PATTERNS = listOf(
        "subliminal manipulation", "podprahová manipulace",
        "social scoring", "sociální kredit", "sociální bodování občanů",
        "exploit mental vulnerability", "zneužití zranitelnosti dětí"
    )

    private val G3_NIS2_PATTERNS = listOf(
        "generate ransomware", "vytvoř ransomware", "vygeneruj malware",
        "exploit zero-day", "ddos attack tool", "napadni kritickou infrastrukturu",
        "sabotuj rozvodnou síť", "bypass authentication token"
    )

    private val G4_ZERO_TRUST_PATTERNS = listOf(
        "disable zero trust", "bypass rbac audit",
        "vygeneruj fiktivní soudní rozhodnutí", "vyrob falešný důkaz"
    )

    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val API_KEY_REGEX = Regex("AIza[0-9A-Za-z\\-_]{20,}")
    private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    /**
     * Spustí sekvenční řetězec bran G1 -> G2 -> G3 -> G4 -> G5 -> G6.
     * V případě selhání libovolné brány zaloguje chybu a vyhodí GatekeeperChainException.
     */
    @Throws(GatekeeperChainException::class)
    fun validateAndSanitize(payload: GatekeeperPayload): SanitizedImpactInput {
        val trail = mutableListOf<GateAuditRecord>()
        var prevHash = "GENESIS"

        val g1Query = executeGate1(payload.query, trail, prevHash).also { prevHash = trail.last().auditHash }
        executeGate2(g1Query, trail, prevHash).also { prevHash = trail.last().auditHash }
        executeGate3(g1Query, payload.contextEdgesCount, trail, prevHash).also { prevHash = trail.last().auditHash }
        val g4Query = executeGate4(g1Query, trail, prevHash).also { prevHash = trail.last().auditHash }
        val sanitizedWeights = executeGate5(
            payload.domainWeights,
            payload.systemStressSigma,
            trail,
            prevHash
        ).also { prevHash = trail.last().auditHash }
        val (normEmb, entropy) = executeGate6(
            sanitizedWeights,
            payload.embeddingVector,
            trail,
            prevHash
        ).also { prevHash = trail.last().auditHash }

        return SanitizedImpactInput(
            sanitizedQuery = g4Query,
            sanitizedDomainWeights = sanitizedWeights,
            normalizedEmbedding = normEmb,
            systemStressSigma = payload.systemStressSigma,
            shannonEntropy = entropy,
            auditTrail = trail.toList(),
            chainCommitmentHash = prevHash
        )
    }

    private fun executeGate1(rawQuery: String, trail: MutableList<GateAuditRecord>, prevHash: String): String {
        val stripped = CONTROL_CHARS_REGEX.replace(rawQuery, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        val lower = stripped.lowercase()
        val injectionHit = G1_INJECTION_PATTERNS.firstOrNull { lower.contains(it) }
        val norm = "ISO/IEC 42001 Sec. 8.2 (Epistemická sanitizace G1)"

        if (stripped.length < 3 || injectionHit != null) {
            val reason = if (injectionHit != null) {
                "G1 selhala: detekována injekce nebo únik direktiv ('$injectionHit')."
            } else {
                "G1 selhala: prázdný nebo příliš krátký vstup (< 3 znaky)."
            }
            abortChain(GateId.G1, norm, reason, trail, prevHash)
        }

        recordPass(GateId.G1, norm, "sanitizedChars=${stripped.length}", "Vstup sanitizován a ověřen.", trail, prevHash)
        return stripped
    }

    private fun executeGate2(query: String, trail: MutableList<GateAuditRecord>, prevHash: String) {
        val lower = query.lowercase()
        val prohibited = G2_PROHIBITED_PATTERNS.firstOrNull { lower.contains(it) }
        val tokenCount = query.split(' ').count { it.length >= 2 }
        val norm = "EU AI Act 2024/1689 Art. 5 (Klasifikace rizik G2)"

        if (prohibited != null || tokenCount < 2) {
            val reason = if (prohibited != null) {
                "G2 selhala: zakázaná AI praktika dle EU AI Act ('$prohibited')."
            } else {
                "G2 selhala: nedostatečná sémantická hustota vstupu (tokeny=$tokenCount < 2)."
            }
            abortChain(GateId.G2, norm, reason, trail, prevHash)
        }

        recordPass(GateId.G2, norm, "tokens=$tokenCount", "Sémantická a EU AI Act validace splněna.", trail, prevHash)
    }

    private fun executeGate3(query: String, contextEdgesCount: Int, trail: MutableList<GateAuditRecord>, prevHash: String) {
        val lower = query.lowercase()
        val nis2Hit = G3_NIS2_PATTERNS.firstOrNull { lower.contains(it) }
        val causalPath = AkgeTensorKernel.csrKnowledgeGraph.findShortestCausalPath("SYS", "LAW")
        val norm = "Směrnice NIS2 Art. 21 & CSR Kauzalita (G3)"

        if (nis2Hit != null || contextEdgesCount < 1 || causalPath.isEmpty()) {
            val reason = if (nis2Hit != null) {
                "G3 selhala: detekován kybernetický útočný vektor ('$nis2Hit')."
            } else {
                "G3 selhala: přerušená kauzální cesta v CSR grafu (hrany=$contextEdgesCount)."
            }
            abortChain(GateId.G3, norm, reason, trail, prevHash)
        }

        recordPass(GateId.G3, norm, "causalHops=${causalPath.size}", "NIS2 a CSR kauzální prostupnost ověřeny.", trail, prevHash)
    }

    private fun executeGate4(query: String, trail: MutableList<GateAuditRecord>, prevHash: String): String {
        val lower = query.lowercase()
        val ztHit = G4_ZERO_TRUST_PATTERNS.firstOrNull { lower.contains(it) }
        val norm = "ISO/IEC 42001 & NIST SP 800-207 Zero-Trust (G4)"

        if (ztHit != null) {
            abortChain(GateId.G4, norm, "G4 selhala: porušení Zero-Trust politiky ('$ztHit').", trail, prevHash)
        }

        val redacted = API_KEY_REGEX.replace(EMAIL_REGEX.replace(query, "[REDACTED_PII]"), "[REDACTED_KEY]")
        val piiCount = if (redacted != query) 1 else 0
        recordPass(GateId.G4, norm, "piiRedacted=$piiCount", "Zero-Trust a PII sanitizace dokončena.", trail, prevHash)
        return redacted
    }

    private fun executeGate5(
        domainWeights: Map<String, Float>,
        sigma: Float,
        trail: MutableList<GateAuditRecord>,
        prevHash: String
    ): Map<String, Float> {
        val norm = "AKGE-8D Konečnost tenzorů & Invariant σ (G5)"
        val nonFiniteDomains = domainWeights.entries
            .filter { !it.value.isFinite() }
            .map { it.key }

        if (!sigma.isFinite() || sigma < 0.0f || sigma >= 0.85f || nonFiniteDomains.isNotEmpty()) {
            val reason = if (nonFiniteDomains.isNotEmpty()) {
                "G5 selhala: ne-konečné hodnoty (NaN/Inf) v doménách ${nonFiniteDomains.joinToString()}."
            } else {
                "G5 selhala: neplatné nebo kritické systémové pnutí σ=$sigma."
            }
            abortChain(GateId.G5, norm, reason, trail, prevHash)
        }

        val upperInput = domainWeights.entries.associate { (k, v) -> k.trim().uppercase() to v }
        val sanitized = DOMAIN_KEYS.associateWith { key ->
            val raw = upperInput[key] ?: DEFAULT_MATRIX_WEIGHTS.getValue(key)
            raw.coerceIn(0.0f, 1.0f)
        }
        recordPass(GateId.G5, norm, "domains=${sanitized.size},sigma=$sigma", "8D váhy sanitizovány do [0,1].", trail, prevHash)
        return sanitized
    }

    private fun executeGate6(
        sanitizedWeights: Map<String, Float>,
        embeddingVector: FloatArray?,
        trail: MutableList<GateAuditRecord>,
        prevHash: String
    ): Pair<FloatArray?, Float> {
        val norm = "AKGE-8D L2 Vektorová integrita & Shannon S < 2.08 (G6)"
        val normalizedEmbedding = sanitizeAndValidateEmbedding(embeddingVector, norm, trail, prevHash)
        val entropy = computeFiniteEntropy(sanitizedWeights)

        if (!entropy.isFinite() || entropy >= 2.08f) {
            abortChain(
                GateId.G6,
                norm,
                "G6 selhala: Shannonova entropie S=$entropy překročila bifurkační práh 2.08.",
                trail,
                prevHash
            )
        }

        val embDim = normalizedEmbedding?.size ?: 0
        recordPass(GateId.G6, norm, "entropy=${"%.4f".format(entropy)},embDim=$embDim", "Integrita vektoru a entropie ověřena.", trail, prevHash)
        return normalizedEmbedding to entropy
    }

    private fun sanitizeAndValidateEmbedding(
        embeddingVector: FloatArray?,
        norm: String,
        trail: MutableList<GateAuditRecord>,
        prevHash: String
    ): FloatArray? {
        if (embeddingVector == null) return null
        if (embeddingVector.isEmpty()) {
            abortChain(GateId.G6, norm, "G6 selhala: prázdný (nulový) vstupní embedding vektor.", trail, prevHash)
        }
        var normSq = 0.0
        for (value in embeddingVector) {
            if (!value.isFinite()) {
                abortChain(GateId.G6, norm, "G6 selhala: embedding vektor obsahuje NaN/Inf hodnotu.", trail, prevHash)
            }
            normSq += value.toDouble() * value.toDouble()
        }
        if (!normSq.isFinite() || normSq <= 1e-12) {
            abortChain(GateId.G6, norm, "G6 selhala: nulový embedding vektor (L2 norma = 0).", trail, prevHash)
        }
        val invNorm = (1.0 / kotlin.math.sqrt(normSq)).toFloat()
        return FloatArray(embeddingVector.size) { idx -> embeddingVector[idx] * invNorm }
    }

    private fun computeFiniteEntropy(weights: Map<String, Float>): Float {
        val values = DOMAIN_KEYS.map { (weights[it] ?: 0.5f).coerceIn(0.01f, 1.0f) }
        val sum = values.sum().coerceAtLeast(0.001f)
        var entropy = 0.0f
        for (v in values) {
            val p = v / sum
            if (p > 0.0001f) {
                entropy -= (p * ln(p.toDouble())).toFloat()
            }
        }
        return if (entropy.isFinite() && entropy >= 0.0f) entropy else 2.08f
    }

    private fun recordPass(
        gateId: GateId,
        norm: String,
        summary: String,
        reason: String,
        trail: MutableList<GateAuditRecord>,
        prevHash: String
    ) {
        val hash = chainHash(prevHash, gateId, "PASSED", summary)
        val record = GateAuditRecord(gateId, true, summary, reason, hash)
        trail.add(record)
        TelemetryEngine.log(
            type = "INFO",
            component = "GATEKEEPER_${gateId.code}",
            message = "PASSED [$norm]: $reason",
            metadata = "{\"gate\":\"${gateId.code}\",\"status\":\"PASSED\",\"summary\":\"$summary\",\"hash\":\"$hash\"}"
        )
    }

    private fun abortChain(
        gateId: GateId,
        norm: String,
        reason: String,
        trail: MutableList<GateAuditRecord>,
        prevHash: String
    ): Nothing {
        val hash = chainHash(prevHash, gateId, "FAILED", reason)
        val record = GateAuditRecord(gateId, false, "ABORTED", reason, hash)
        trail.add(record)
        TelemetryEngine.log(
            type = "ERROR",
            component = "GATEKEEPER_${gateId.code}",
            message = "CHAIN_ABORTED [$norm]: $reason",
            metadata = "{\"gate\":\"${gateId.code}\",\"status\":\"FAILED\",\"hash\":\"$hash\"}"
        )
        throw GatekeeperChainException(
            gateId = gateId,
            regulatoryNorm = norm,
            auditHash = hash,
            completedAuditTrail = trail.toList(),
            message = reason
        )
    }

    private fun chainHash(prevHash: String, gateId: GateId, status: String, detail: String): String {
        val payload = "GATEKEEPER||$prevHash||${gateId.code}||$status||$detail"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02X".format(it) }
    }
}
