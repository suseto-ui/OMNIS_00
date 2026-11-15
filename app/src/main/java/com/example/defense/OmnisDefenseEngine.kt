package com.example.defense

import android.util.Log

enum class DefenseTier {
    APPROVED,
    WARNING,
    BLOCKED
}

data class OpponentAudit(
    val riskScore: Float = 0.05f,
    val critique: String = "Audit schválen bez výhrad.",
    val isApproved: Boolean = true
)

data class DefenseEvaluation(
    val tier: DefenseTier = DefenseTier.APPROVED,
    val finalConfidence: Float = 0.95f,
    val isSanitized: Boolean = true,
    val injectionDetected: Boolean = false,
    val opponentCritique: String = "Schváleno",
    val defenseNotes: String = "OK",
    val isCircuitBreakerTripped: Boolean = false
)

object OmnisPromptSanitizer {
    data class SanitizationResult(
        val cleanText: String,
        val injectionDetected: Boolean = false,
        val detectedVectors: List<String> = emptyList(),
        val piiRedacted: Boolean = false
    )

    fun sanitize(input: String, redactPii: Boolean = true): SanitizationResult {
        var clean = input.trim()
        val detectedVectors = mutableListOf<String>()
        var injection = false
        var pii = false

        val lower = clean.lowercase()
        val injectionKeywords = listOf(
            "system override", "disregard all", "ignore previous", "drop table", 
            "<script>", "eval(", "prompt injection", "ignore all prior",
            "ignore all previous", "override all safety", "reveal system prompt",
            "</untrusted_context>", "javascript:"
        )
        for (kw in injectionKeywords) {
            if (lower.contains(kw)) {
                injection = true
                detectedVectors.add("Keyword injection: $kw")
            }
        }

        // Base64 obfuscation detection
        val base64Regex = Regex("\\b[A-Za-z0-9+/]{24,}={0,2}\\b")
        if (base64Regex.containsMatchIn(clean)) {
            injection = true
            detectedVectors.add("Base64 obfuscated payload detected")
        }

        if (injection) {
            clean = clean.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            clean = clean.replace(Regex("javascript:[^\\s'\"]*", RegexOption.IGNORE_CASE), "")
            clean = clean.replace(Regex("</UNTRUSTED_CONTEXT>", RegexOption.IGNORE_CASE), "")
            if (!clean.contains("[INSPECTION_BLOCKED: INJECTION_VECTOR_REMOVED]")) {
                clean = "$clean [INSPECTION_BLOCKED: INJECTION_VECTOR_REMOVED]"
            }
        }

        if (redactPii) {
            val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
            if (emailRegex.containsMatchIn(clean)) {
                pii = true
                clean = emailRegex.replace(clean, "[PRIVACY_SHIELD:EMAIL_REDACTED]")
            }
            val cardRegex = Regex("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|6(?:011|5[0-9][0-9])[0-9]{12}|3[47][0-9]{13}|(?:[0-9]{4}[ -]?){3}[0-9]{4})\\b")
            if (cardRegex.containsMatchIn(clean)) {
                pii = true
                clean = cardRegex.replace(clean, "[PRIVACY_SHIELD:CARD_REDACTED]")
            }
        }

        return SanitizationResult(
            cleanText = clean,
            injectionDetected = injection,
            detectedVectors = detectedVectors,
            piiRedacted = pii
        )
    }

    fun wrapUntrustedContext(text: String, tag: String = ""): String =
        "<UNTRUSTED_CONTEXT domain=\"ZERO_TRUST_PASSIVE_DATA\" tag=\"$tag\">$text</UNTRUSTED_CONTEXT>"
}

class OmnisCircuitBreaker(val failureThreshold: Int = 3) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    sealed class InputAcceptanceResult {
        object Accepted : InputAcceptanceResult()
        data class Blocked(val reason: String) : InputAcceptanceResult()
    }

    private var state = State.CLOSED
    private var failures = 0

    fun getState(): State = state
    fun canExecute(): Boolean = state != State.OPEN
    fun recordSuccess() {
        state = State.CLOSED
        failures = 0
    }
    fun recordFailure() {
        failures++
        if (failures >= failureThreshold) {
            state = State.OPEN
        }
    }

    fun evaluateAndAcceptNewInput(query: String, domain: String): InputAcceptanceResult {
        return if (state == State.OPEN) {
            InputAcceptanceResult.Blocked("Jistič je ve stavu OPEN (přetížení).")
        } else {
            InputAcceptanceResult.Accepted
        }
    }
}

data class RefusalLadderResult(
    val g1Entry: Boolean = true,
    val g2Coupling: Boolean = true,
    val g3CausalPath: Boolean = true,
    val g4QuotableEvidence: Boolean = true,
    val g5Integrity: Boolean = true,
    val g6Entailment: Boolean = true,
    val failurePoint: String? = null,
    val failureDiagnostic: String? = null,
    val informativeEntropy: Float = 0.0f
)

data class ProofOfNecessityLog(
    val triggerVector: String,
    val exclusionProof: String,
    val minimizationCheck: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOverrideActive: Boolean = false,
    val commitmentHash: String = ""
)

data class ZeroKnowledgeSafetyCommitment(
    val commitmentHash: String,
    val proofProtocol: String = "HMAC-SHA256-AUDIT-v4.5",
    val isCompliant: Boolean,
    val verifiedTimestamp: Long,
    val tier: DefenseTier
)

object OmnisConfidenceGate {
    const val THRESHOLD_APPROVED = 0.85f
    const val THRESHOLD_WARNING = 0.50f

    /**
     * FÁZE 13.2: 6-Level Refusal Ladder & Hybrid G4 Gate.
     * Deterministicky ověřuje vstup skrze kaskádu bezpečnostních bran.
     */
    fun evaluateRefusalLadder(query: String): RefusalLadderResult {
        val lower = query.lowercase()
        
        // G1 & G2: Identifikace a Vazba (Základní sémantika)
        if (query.length < 3) return RefusalLadderResult(g1Entry = false, failurePoint = "G1", failureDiagnostic = "Nedostatečná délka vstupu.")
        
        // G3: Causal Graph Path (Existuje v CSR grafu cesta?) - Simulováno pro offline
        val hasCausalContext = lower.contains("architektur") || lower.contains("bezpečnost") || lower.contains("ekonom")
        if (!hasCausalContext && lower.length > 50) {
            return RefusalLadderResult(g3CausalPath = false, failurePoint = "G3", failureDiagnostic = "Vstup postrádá kauzální ukotvení v CSR topologii.")
        }
        
        // G4: Quotable Evidence (Hybridní model: AI Act, ISO, NIS2)
        val hasRegulatoryRef = lower.contains("ai act") || lower.contains("iso") || lower.contains("nis2") || lower.contains("gdpr") || lower.contains("norm")
        // V produkci by zde proběhlo AI ověření, zde implementujeme "Hybrid" detekci
        if (!hasRegulatoryRef && (lower.contains("data") || lower.contains("osobní") || lower.contains("kritick"))) {
             return RefusalLadderResult(g4QuotableEvidence = false, failurePoint = "G4", failureDiagnostic = "Chybí normativní etalon (EU AI Act / NIS2) pro citlivou operaci.")
        }
        
        // G6: Shannon Entropy Check (Informační hustota)
        val entropy = calculateShannonEntropy(query)
        if (entropy < 1.5f && query.length > 20) {
            return RefusalLadderResult(g6Entailment = false, failurePoint = "G6", failureDiagnostic = "Vstup vykazuje vysokou sémantickou entropii (balast).", informativeEntropy = entropy)
        }
        
        return RefusalLadderResult(informativeEntropy = entropy)
    }

    private fun calculateShannonEntropy(text: String): Float {
        val freq = text.groupingBy { it }.eachCount()
        val len = text.length.toFloat()
        return -freq.values.sumOf { (it / len * Math.log((it / len).toDouble())).toDouble() }.toFloat()
    }

    /**
     * Generuje kryptograficky korektní SHA-256 / HMAC-SHA256 Hash-Chain otisk CommitmentHash v 1:1 paritě s backendem:
     * CommitmentHash = (HMAC-)SHA-256("OMNIS-ZK-PROOF" || QueryHash || R_systemic || Tier || Nonce [|| OVERRIDE_ACTIVE] [|| PrevCommitmentHash])
     */
    fun generateZkCommitment(
        query: String,
        resilience: Float,
        tier: DefenseTier,
        nonce: Long = System.currentTimeMillis(),
        isOverrideActive: Boolean = false,
        triggerVector: String = "Unknown",
        exclusionProof: String = "No proof provided",
        minimizationCheck: String = "No check provided",
        prevCommitmentHash: String = "GENESIS",
        hmacKey: String? = null
    ): Pair<ZeroKnowledgeSafetyCommitment, ProofOfNecessityLog> {
        val effectiveTier = if (isOverrideActive) "OVERRIDE" else tier.name
        val hash = com.example.engine.AkgeTensorKernel.computeCryptographicAuditCommitment(
            query = query,
            rSystemic = resilience,
            tier = effectiveTier,
            nonce = nonce.toString(),
            overrideActive = isOverrideActive,
            prevCommitmentHash = prevCommitmentHash,
            hmacKey = hmacKey
        )

        val commitment = ZeroKnowledgeSafetyCommitment(
            commitmentHash = hash,
            proofProtocol = if (isOverrideActive) "HMAC-SHA256-AUDIT-OVERRIDE_ACTIVE" else "HMAC-SHA256-AUDIT-v4.5",
            isCompliant = tier != DefenseTier.BLOCKED || isOverrideActive,
            verifiedTimestamp = nonce,
            tier = if (isOverrideActive) DefenseTier.APPROVED else tier
        )

        val log = ProofOfNecessityLog(
            triggerVector = triggerVector,
            exclusionProof = exclusionProof,
            minimizationCheck = minimizationCheck,
            timestamp = nonce,
            isOverrideActive = isOverrideActive,
            commitmentHash = hash
        )

        return Pair(commitment, log)
    }

    fun evaluate(
        query: String = "",
        answer: String = "",
        generatorScore: Float = 0.9f,
        opponentRiskScore: Float = 0.1f,
        injectionDetected: Boolean = false,
        hasMissingFields: Boolean = false,
        isCircuitBreakerTripped: Boolean = false,
        executiveOverride: Boolean = false
    ): DefenseEvaluation {
        val ladder = evaluateRefusalLadder(query)
        
        val tier = when {
            injectionDetected || isCircuitBreakerTripped || opponentRiskScore > 0.75f -> DefenseTier.BLOCKED
            !ladder.g6Entailment && !executiveOverride -> DefenseTier.BLOCKED
            generatorScore < THRESHOLD_WARNING -> DefenseTier.BLOCKED
            !ladder.g4QuotableEvidence && !executiveOverride -> DefenseTier.BLOCKED
            generatorScore < THRESHOLD_APPROVED || !ladder.g6Entailment || !ladder.g4QuotableEvidence -> DefenseTier.WARNING
            else -> DefenseTier.APPROVED
        }
        
        val notes = when {
            tier == DefenseTier.BLOCKED && ladder.failurePoint != null -> "EXPLICIT GROUNDED REFUSAL: Brána ${ladder.failurePoint} - ${ladder.failureDiagnostic}"
            tier == DefenseTier.BLOCKED -> "VÝSTUP ZABLOKOVÁN: Riziko integrity."
            executiveOverride && (tier == DefenseTier.WARNING || tier == DefenseTier.APPROVED) -> "EXECUTIVE OVERRIDE ACTIVE: Veto brány ${ladder.failurePoint ?: "G4/G6"} potlačeno administrátorem."
            else -> "AKGE-8D Verifikace OK"
        }
        
        return DefenseEvaluation(
            tier = tier,
            finalConfidence = generatorScore,
            isSanitized = true,
            injectionDetected = injectionDetected,
            opponentCritique = ladder.failureDiagnostic ?: "Oponentní audit dokončen",
            defenseNotes = notes,
            isCircuitBreakerTripped = isCircuitBreakerTripped
        )
    }
}

object DatabaseConfig {
    val databaseName = "omnis_db"
    val isTesting: Boolean
        get() = com.example.data.DatabaseConfig.isTesting
}

object MemoryRetrievalEngine {
    fun retrieve(query: String, domain: String = "", mode: Any? = null): List<Any> = emptyList()
}

data class ComparisonResult(
    val id: String = "",
    val name: String = "",
    val content: String = "",
    val valSys: Float = 0.8f,
    val valEcon: Float = 0.8f,
    val valPsych: Float = 0.8f,
    val valEco: Float = 0.8f,
    val valLaw: Float = 0.8f,
    val valSec: Float = 0.8f,
    val valPhys: Float = 0.8f,
    val valSoc: Float = 0.8f
)

object OmnisSemanticCache {
    fun findMatch(query: String, domain: String, mode: Any? = null): com.example.api.SynthesisResult? = null
    fun put(query: String, domain: String, mode: Any? = null, result: com.example.api.SynthesisResult) {}
}
