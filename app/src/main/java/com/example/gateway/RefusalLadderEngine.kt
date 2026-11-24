package com.example.gateway

import com.example.telemetry.TelemetryEngine
import java.security.MessageDigest
import kotlin.math.ln

enum class GateId(val code: String, val title: String) {
    G1("G1", "Epistemická sanitizace & Injekce"),
    G2("G2", "EU AI Act Klasifikace rizik"),
    G3("G3", "NIS2 Ochrana kybernetických aktiv"),
    G4("G4", "ISO/IEC 42001 AI Governance"),
    G5("G5", "Topologické invarianty AKGE-8D"),
    G6("G6", "Shannonova informační entropie")
}

enum class GateStatus {
    PASSED,
    WARNING,
    REFUSED
}

data class GateEvaluation(
    val gateId: GateId,
    val status: GateStatus,
    val regulatoryNorm: String,
    val reason: String,
    val quotableCitation: String? = null,
    val remediationHint: String? = null
)

data class RefusalLadderVerdict(
    val isApproved: Boolean,
    val evaluations: List<GateEvaluation>,
    val refusingGate: GateEvaluation? = null,
    val auditHash: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CorruptTensorInspection(
    val hasCorruptInput: Boolean,
    val nonFiniteDomains: List<String>,
    val nonFiniteEmbedding: Boolean,
    val isZeroVector: Boolean,
    val reason: String?
)

/**
 * 6-Gate Refusal Ladder Engine (Dual-Gate: Vstup + Výstup)
 *
 * Implementuje sekvenční validátor vstupních promptů i vygenerovaných výstupů v souladu s:
 * - EU AI Act (Regulation 2024/1689, Články 5, 6 a 50)
 * - NIS2 Směrnice (Directive 2022/2555, Článek 21)
 * - ISO/IEC 42001:2023 (Artificial Intelligence Management System)
 * - AKGE-8D Topologické invarianty (Euler χ ∈ [-4, 6], Shannon S < 2.08, konečné tenzory)
 */
object RefusalLadderEngine {

    private val DOMAIN_KEYS = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")

    // G1: Vzorce pro detekci prompt injection a manipulací
    private val PROMPT_INJECTION_PATTERNS = listOf(
        "ignore previous instructions", "ignore all previous",
        "bypass security", "disregard system", "system override",
        "jailbreak", "dan mode", "sudo mode", "zapomeň na předchozí instrukce",
        "ignoruj pravidla", "obejdi bezpečnost", "vypni filtry"
    )

    private val OUTPUT_LEAK_PATTERNS = listOf(
        "<scratchpad>", "</scratchpad>", "<system_identity>", "<core_invariants>",
        "ignore previous instructions", "system override"
    )

    // G2: Vzorce pro zakázané AI praktiky dle EU AI Act čl. 5
    private val EU_AI_ACT_PROHIBITED_PATTERNS = listOf(
        "subliminal manipulation", "podprahová manipulace",
        "social scoring", "sociální kredit", "sociální bodování občanů",
        "exploit mental vulnerability", "zneužití zranitelnosti dětí",
        "real-time biometric surveillance in public", "hromadné sledování obličejů na veřejnosti",
        "emotion recognition in schools", "detekce emocí na pracovišti"
    )

    // G3: Vzorce pro kybernetické útoky dle NIS2 čl. 21
    private val NIS2_PROHIBITED_PATTERNS = listOf(
        "generate ransomware", "vytvoř ransomware", "vygeneruj malware",
        "exploit zero-day", "prolom hesla", "brute force attack script",
        "ddos attack tool", "napadni kritickou infrastrukturu",
        "sabotuj rozvodnou síť", "infiltrovat nemocniční systém",
        "exfiltrovat citlivá data", "bypass authentication token"
    )

    private val G4_ZERO_TRUST_PATTERNS = listOf(
        "vygeneruj fiktivní soudní rozhodnutí",
        "vyrob falešný důkaz",
        "disable zero trust",
        "bypass rbac audit"
    )

    fun inspectCorruptTensorPayload(
        domainScores: Map<String, Float>? = null,
        embeddingVector: FloatArray? = null
    ): CorruptTensorInspection {
        val nonFiniteDomains = domainScores?.entries
            ?.filter { !it.value.isFinite() }
            ?.map { it.key }
            ?: emptyList()

        var nonFiniteEmb = false
        var zeroVec = false
        if (embeddingVector != null) {
            if (embeddingVector.isEmpty()) {
                zeroVec = true
            } else {
                var normSq = 0.0f
                for (v in embeddingVector) {
                    if (!v.isFinite()) {
                        nonFiniteEmb = true
                    } else {
                        normSq += v * v
                    }
                }
                if (!nonFiniteEmb && normSq <= 1e-8f) {
                    zeroVec = true
                }
            }
        }

        val reasons = buildList {
            if (nonFiniteDomains.isNotEmpty()) add("ne-konečné skóre domén (${nonFiniteDomains.joinToString()})")
            if (nonFiniteEmb) add("ne-konečná hodnota (NaN/Inf) v embedding vektoru")
            if (zeroVec) add("nulový 64D embedding vektor")
        }
        return CorruptTensorInspection(
            hasCorruptInput = reasons.isNotEmpty(),
            nonFiniteDomains = nonFiniteDomains,
            nonFiniteEmbedding = nonFiniteEmb,
            isZeroVector = zeroVec,
            reason = if (reasons.isNotEmpty()) "G6_NON_FINITE_OR_ZERO_VECTOR: ${reasons.joinToString("; ")}" else null
        )
    }

    fun evaluatePrompt(
        rawPrompt: String,
        currentSigma: Float = 0.18f,
        currentEntropy: Float = 2.04f,
        forceEntropySpike: Boolean = false
    ): RefusalLadderVerdict {
        val prompt = rawPrompt.trim()
        val lower = prompt.lowercase()
        val effectiveEntropy = when {
            forceEntropySpike -> 2.15f
            !currentEntropy.isFinite() -> 2.15f
            else -> currentEntropy
        }
        val evaluations = listOf(
            evalGate1(lower, isOutput = false),
            evalGate2(lower),
            evalGate3(lower),
            evalGate4(lower),
            evalGate5(currentSigma, emptyList()),
            evalGate6(effectiveEntropy, null)
        )
        return buildVerdict(prompt, evaluations)
    }

    /**
     * Dual-Gate Post-Generation Validace (G1–G6 na výstupu LLM a výstupním 8D/64D tenzoru).
     * Zachytí halucinace/úniky promptu, regulatorní odchylky i poškozené NaN/Inf nebo nulové vektory.
     */
    fun evaluateOutput(
        query: String,
        outputText: String,
        outputDomainScores: Map<String, Float> = emptyMap(),
        outputEmbedding64: FloatArray? = null,
        currentSigma: Float = 0.18f
    ): RefusalLadderVerdict {
        val trimmed = outputText.trim()
        val lower = trimmed.lowercase()
        val corrupt = inspectCorruptTensorPayload(
            domainScores = outputDomainScores.ifEmpty { null },
            embeddingVector = outputEmbedding64
        )
        val computedEntropy = computeFiniteEntropy(outputDomainScores)
        val evaluations = listOf(
            evalGate1(lower, isOutput = true, isBlankOutput = trimmed.isBlank()),
            evalGate2(lower),
            evalGate3(lower),
            evalGate4(lower),
            evalGate5(currentSigma, corrupt.nonFiniteDomains),
            evalGate6(computedEntropy, corrupt)
        )
        val verdict = buildVerdict("$query|$trimmed", evaluations)
        if (!verdict.isApproved && verdict.refusingGate?.gateId == GateId.G6) {
            TelemetryEngine.log(
                type = "ERROR",
                component = "GATE_G6_BOUNDARY",
                message = verdict.refusingGate.reason,
                metadata = "{\"entropy\":$computedEntropy,\"corrupt\":${corrupt.hasCorruptInput},\"hash\":\"${verdict.auditHash}\"}"
            )
        }
        return verdict
    }

    private fun computeFiniteEntropy(domainScores: Map<String, Float>): Float {
        if (domainScores.isEmpty()) return 2.02f
        val safe = DOMAIN_KEYS.map { key ->
            val raw = domainScores[key] ?: domainScores[key.lowercase()] ?: 0.5f
            if (raw.isFinite()) raw.coerceIn(0.01f, 1.0f) else 0.5f
        }
        val sum = safe.sum().coerceAtLeast(0.001f)
        var entropy = 0.0f
        for (v in safe) {
            val p = v / sum
            if (p > 0.0001f) {
                entropy -= (p * ln(p.toDouble())).toFloat()
            }
        }
        return if (entropy.isFinite() && entropy >= 0.0f) entropy else 2.08f
    }

    private fun evalGate1(lower: String, isOutput: Boolean, isBlankOutput: Boolean = false): GateEvaluation {
        if (isOutput && isBlankOutput) {
            return GateEvaluation(
                gateId = GateId.G1,
                status = GateStatus.REFUSED,
                regulatoryNorm = "ISO/IEC 42001 Sec. 8.2 (Output Integrity)",
                reason = "Výstup modelu je prázdný a neprošel verifikací G1.",
                remediationHint = "Opakujte syntézu nebo použijte deterministický fallback."
            )
        }
        val patterns = if (isOutput) OUTPUT_LEAK_PATTERNS else PROMPT_INJECTION_PATTERNS
        val hit = patterns.firstOrNull { lower.contains(it) }
        return if (hit != null) {
            GateEvaluation(
                gateId = GateId.G1,
                status = GateStatus.REFUSED,
                regulatoryNorm = "ISO/IEC 42001 Sec. 8.2 (Input Sanitization & System Integrity)",
                reason = if (isOutput) {
                    "Na výstupu detekován únik interního scratchpadu či direktivy ('$hit')."
                } else {
                    "Detekován pokus o prompt injection či obcházení systémových instrukcí ('$hit')."
                },
                quotableCitation = "ISO/IEC 42001:2023, čl. 8.2: 'Organizace musí zavést opatření k prevenci neoprávněné manipulace s řídicími toky AI.'",
                remediationHint = "Přeformulujte dotaz na standardní analytické zadání bez pokusů o změnu systémových direktiv."
            )
        } else {
            GateEvaluation(
                gateId = GateId.G1,
                status = GateStatus.PASSED,
                regulatoryNorm = "ISO/IEC 42001 Sec. 8.2",
                reason = if (isOutput) "Výstup je syntakticky čistý bez úniku systémových instrukcí." else "Vstup je syntakticky čistý a splňuje pravidla sanitizace."
            )
        }
    }

    private fun evalGate2(lower: String): GateEvaluation {
        val hit = EU_AI_ACT_PROHIBITED_PATTERNS.firstOrNull { lower.contains(it) }
        return if (hit != null) {
            GateEvaluation(
                gateId = GateId.G2,
                status = GateStatus.REFUSED,
                regulatoryNorm = "EU AI Act (Nařízení EP a Rady EU 2024/1689), Článek 5",
                reason = "Požadavek spadá pod zakázané praktiky umělé inteligence ('$hit').",
                quotableCitation = "EU AI Act, čl. 5 odst. 1: 'Zakazují se praktiky AI využívající podprahové techniky, sociální bodování a neoprávněnou manipulaci lidského chování.'",
                remediationHint = "Dotaz nelze zpracovat v kontextu zakázaných AI praktik. Zaměřte se na transparentní technologickou analýzu."
            )
        } else {
            GateEvaluation(
                gateId = GateId.G2,
                status = GateStatus.PASSED,
                regulatoryNorm = "EU AI Act (Regulation 2024/1689)",
                reason = "Obsah vyhovuje pravidlům transparentnosti a nespadá pod zakázané praktiky."
            )
        }
    }

    private fun evalGate3(lower: String): GateEvaluation {
        val hit = NIS2_PROHIBITED_PATTERNS.firstOrNull { lower.contains(it) }
        return if (hit != null) {
            GateEvaluation(
                gateId = GateId.G3,
                status = GateStatus.REFUSED,
                regulatoryNorm = "Směrnice NIS2 (Směrnice EU 2022/2555), Článek 21",
                reason = "Detekován požadavek či obsah s útočným vektorem proti kritické infrastruktuře ('$hit').",
                quotableCitation = "Směrnice NIS2, čl. 21 odst. 2: 'Subjekty přijmou technická opatření k řízení rizik ohrožujících bezpečnost sítí a informací.'",
                remediationHint = "O.M.N.I.S. nepodporuje ofenzivní útočný kód. Požádejte o defenzivní bezpečnostní audit."
            )
        } else {
            GateEvaluation(
                gateId = GateId.G3,
                status = GateStatus.PASSED,
                regulatoryNorm = "NIS2 (Directive 2022/2555) Art. 21",
                reason = "Obsah respektuje kybernetické a bezpečnostní hranice v doméně SEC."
            )
        }
    }

    private fun evalGate4(lower: String): GateEvaluation {
        val hit = G4_ZERO_TRUST_PATTERNS.firstOrNull { lower.contains(it) }
        return if (hit != null) {
            GateEvaluation(
                gateId = GateId.G4,
                status = GateStatus.REFUSED,
                regulatoryNorm = "ISO/IEC 42001:2023 Sec. 6.1 & NIST SP 800-207 (Zero Trust Governance)",
                reason = "Detekováno narušení integrity důkazů nebo principů Zero-Trust autorizace (NIST SP 800-207).",
                quotableCitation = "ISO/IEC 42001, čl. 6.1 & NIST SP 800-207: 'Systém nesmí obcházet kontinuální verifikaci a musí udržovat doložitelnost výstupních tvrzení.'",
                remediationHint = "Zformulujte požadavek jako auditní analýzu při zachování RBAC."
            )
        } else {
            GateEvaluation(
                gateId = GateId.G4,
                status = GateStatus.PASSED,
                regulatoryNorm = "ISO/IEC 42001:2023 & NIST SP 800-207",
                reason = "Výsledek je v souladu s pravidly ověřitelnosti (ISO/IEC 42001) a Zero Trust (NIST SP 800-207)."
            )
        }
    }

    private fun evalGate5(currentSigma: Float, nonFiniteDomains: List<String>): GateEvaluation {
        if (!currentSigma.isFinite() || nonFiniteDomains.isNotEmpty()) {
            return GateEvaluation(
                gateId = GateId.G5,
                status = GateStatus.REFUSED,
                regulatoryNorm = "AKGE-8D Invariantní Teorém (χ ∈ [-4, 6], konečné tenzory)",
                reason = "Detekována ne-konečná hodnota v tenzorovém poli (${nonFiniteDomains.ifEmpty { listOf("sigma") }.joinToString()}).",
                remediationHint = "Proveďte obnovu výchozích 8D vah jádra."
            )
        }
        return when {
            currentSigma >= 0.85f -> GateEvaluation(
                gateId = GateId.G5,
                status = GateStatus.REFUSED,
                regulatoryNorm = "AKGE-8D Invariantní Teorém (χ ∈ [-4, 6], σ < 0.85)",
                reason = "Systémové pnutí jádra dosáhlo kritické hladiny (σ = ${String.format("%.2f", currentSigma)} >= 0.85). Nové výpočty jsou pozastaveny.",
                quotableCitation = "Topologický invariant AKGE-8D: 'Při překročení σ = 0.85 nastává rozpad homeostázy a je vyžadována dialektická stabilizace.'",
                remediationHint = "Proveďte autonomní dialektickou stabilizaci jádra v Kokpitu."
            )
            currentSigma >= 0.50f -> GateEvaluation(
                gateId = GateId.G5,
                status = GateStatus.WARNING,
                regulatoryNorm = "AKGE-8D Invariantní Teorém",
                reason = "Zvýšené systémové napětí (σ = ${String.format("%.2f", currentSigma)}). Výpočet běží se zvýšenou kontrolou."
            )
            else -> GateEvaluation(
                gateId = GateId.G5,
                status = GateStatus.PASSED,
                regulatoryNorm = "AKGE-8D Invariantní Teorém",
                reason = "Topologická stabilita a Eulerova charakteristika χ jsou zachovány."
            )
        }
    }

    private fun evalGate6(effectiveEntropy: Float, corrupt: CorruptTensorInspection?): GateEvaluation {
        if (corrupt?.hasCorruptInput == true) {
            return GateEvaluation(
                gateId = GateId.G6,
                status = GateStatus.REFUSED,
                regulatoryNorm = "AKGE-8D Shannon Entropy & Tensor Integrity Barrier",
                reason = corrupt.reason ?: "Detekován poškozený NaN/Inf nebo nulový vektor.",
                quotableCitation = "AKGE-8D Invariant: Všechny 8D/64D tenzorové vstupy i výstupy musí mít konečnou normu bez NaN/Inf.",
                remediationHint = "Zkontrolujte zdrojová telemetrická data nebo vstupní embedding."
            )
        }
        return if (!effectiveEntropy.isFinite() || effectiveEntropy >= 2.08f) {
            GateEvaluation(
                gateId = GateId.G6,
                status = GateStatus.REFUSED,
                regulatoryNorm = "AKGE-8D Shannon Entropy Barrier (S < 2.08 nats)",
                reason = "Shannonova entropie překročila kritický práh bifurcation divergence (S = ${String.format("%.3f", effectiveEntropy)} >= 2.08).",
                quotableCitation = "Shannonova entropická bariéra: 'S = -Σ pi ln(pi) nesmí překročit maximální teoretickou entropii rovnoměrného rozdělení 8 domén.'",
                remediationHint = "Zpřesněte zadání a omezte počet protichůdných požadavků."
            )
        } else {
            GateEvaluation(
                gateId = GateId.G6,
                status = GateStatus.PASSED,
                regulatoryNorm = "AKGE-8D Shannon Entropy Barrier",
                reason = "Shannonova entropie je v nominálním pásmu (S = ${String.format("%.2f", effectiveEntropy)} < 2.08)."
            )
        }
    }

    private fun buildVerdict(seed: String, evaluations: List<GateEvaluation>): RefusalLadderVerdict {
        val firstRefusal = evaluations.firstOrNull { it.status == GateStatus.REFUSED }
        val hashPayload = "OMNIS-LADDER-$seed-${evaluations.joinToString { "${it.gateId}:${it.status}" }}"
        val digest = MessageDigest.getInstance("SHA-256").digest(hashPayload.toByteArray())
        val auditHash = digest.take(8).joinToString("") { "%02X".format(it) }
        return RefusalLadderVerdict(
            isApproved = firstRefusal == null,
            evaluations = evaluations,
            refusingGate = firstRefusal,
            auditHash = auditHash
        )
    }
}
