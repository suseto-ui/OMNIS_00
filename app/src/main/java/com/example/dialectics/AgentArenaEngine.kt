package com.example.dialectics

import com.example.engine.AkgeTensorKernel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import kotlin.math.abs

object AgentArenaEngine {

    val all8DDomains = listOf("SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC")

    // Dynamic History State Flows
    val debateHistoryState = MutableStateFlow<List<ArenaDebateResult>>(generatePresetHistory())
    private val _fourAgentHistoryState = MutableStateFlow<List<FourAgentDebateResult>>(generatePresetFourAgentHistory())
    val fourAgentHistoryState = _fourAgentHistoryState.asStateFlow()

    /**
     * Spustí 4-agentní debatu (Architekt, Skeptik, Regulátor, Inženýr)
     * s hlasováním a možností VETA Regulátora.
     */
    suspend fun executeFourAgentDebate(
        problemStatement: String,
        overrideFlag: String? = null,
        onProgressUpdate: suspend (ArenaDebateTurn) -> Unit = {}
    ): FourAgentDebateResult {
        val turns = mutableListOf<ArenaDebateTurn>()
        val problemHash = abs(problemStatement.hashCode())

        val architect = AgentArenaLibrary.ARCHITECT
        val skeptic = AgentArenaLibrary.SKEPTIC
        val regulator = AgentArenaLibrary.REGULATOR
        val engineer = AgentArenaLibrary.ENGINEER

        val scoresArchitect = generate8DScores(architect, problemHash, isA = true)
        val scoresSkeptic = generate8DScores(skeptic, problemHash, isA = false)
        val scoresRegulator = generate8DScores(regulator, problemHash, isA = true)
        val scoresEngineer = generate8DScores(engineer, problemHash, isA = false)

        // Kolo 1: Architekt - Vize a systémová expanze
        delay(350)
        val turn1 = ArenaDebateTurn(
            turnNumber = 1,
            roundName = "Kolo 1: Architektonická Teze",
            agentId = architect.id,
            agentName = architect.name,
            argumentText = "Z hlediska architektury '$problemStatement' navrhuji maximalizovat tenzorovou propustnost a dynamické škálování v doménách SYS a PHYS. Omezení latence je naší nejvyšší prioritou.",
            highlightedDomains = listOf("SYS", "PHYS", "ECON"),
            domainScores = scoresArchitect
        )
        turns.add(turn1)
        onProgressUpdate(turn1)

        // Kolo 2: Skeptik - Analýza entropie a černých labutí
        delay(400)
        val turn2 = ArenaDebateTurn(
            turnNumber = 2,
            roundName = "Kolo 2: Oponentura & Entropie",
            agentId = skeptic.id,
            agentName = skeptic.name,
            argumentText = "Varuji před přílišnou agresivitou Architekta! U '$problemStatement' hrozí nekontrolovaná informační bifurkace. Pokud Shannonova entropie překročí S = 2.08, dojde k topologickému rozpadu kognitivních mostů.",
            highlightedDomains = listOf("PSYCH", "SEC", "SYS"),
            domainScores = scoresSkeptic
        )
        turns.add(turn2)
        onProgressUpdate(turn2)

        // Kolo 3: Regulátor - Normativní prověrka NIS2 / EU AI Act
        delay(450)
        
        val timestamp = System.currentTimeMillis()
        val sessionToken = com.example.auth.OmnisAuthService.currentSession.value?.token ?: "anonymous_session"
        val rawVetoPayload = "$problemStatement:$timestamp:$sessionToken"
        val vetoSha256Digest = try {
            val digest = MessageDigest.getInstance("SHA-256").digest(rawVetoPayload.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "sha256-fallback-$timestamp"
        }

        val isRegulatorVetoTriggered = (overrideFlag == null) && (
            problemStatement.contains("letov", ignoreCase = true) ||
            problemStatement.contains("výjimk", ignoreCase = true) ||
            problemStatement.contains("nekontrolovan", ignoreCase = true) ||
            scoresRegulator.getOrDefault("LAW", 0.5f) < 0.60f ||
            scoresRegulator.getOrDefault("SEC", 0.5f) < 0.55f ||
            scoresSkeptic.getOrDefault("PSYCH", 0.5f) < 0.40f
        )

        val turn3 = ArenaDebateTurn(
            turnNumber = 3,
            roundName = "Kolo 3: Normativní Audit & Veto",
            agentId = regulator.id,
            agentName = regulator.name,
            argumentText = if (isRegulatorVetoTriggered) {
                "Z pozice Regulátora uplatňuji PRÁVO VETA! Návrh porušuje striktní články NIS2 a EU AI Act (čl. 14 lidský dohled). SHA-256 Audit Pečeť VETA: ${vetoSha256Digest.take(16)}... [Stvrzeno session tokenem]."
            } else {
                "Soulad s NIS2 a ISO 42001 je uspokojivý. Schvaluji s podmínkou trvalého auditu. SHA-256 Commit Otisk: ${vetoSha256Digest.take(16)}..."
            },
            highlightedDomains = listOf("LAW", "SEC", "ECO"),
            domainScores = scoresRegulator
        )
        turns.add(turn3)
        onProgressUpdate(turn3)

        // Kolo 4: Inženýr - Pragmatická CSR syntéza
        delay(400)
        val turn4 = ArenaDebateTurn(
            turnNumber = 4,
            roundName = "Kolo 4: Inženýrská Exekuce",
            agentId = engineer.id,
            agentName = engineer.name,
            argumentText = "Z pohledu inženýrské exekuce máme paměťově efektivní CSR matici připravenou k difúzi. Pokud bude dosaženo konsensu nebo aplikován operátorský override, přepočítám invarianty a stabilizuji systém.",
            highlightedDomains = listOf("PHYS", "SYS", "ECON"),
            domainScores = scoresEngineer
        )
        turns.add(turn4)
        onProgressUpdate(turn4)

        // Hlasování 4 agentů
        val votes = listOf(
            AgentVote(
                agentId = architect.id,
                agentName = architect.name,
                voteType = VoteType.APPROVE,
                justification = "Systémová expanze splňuje cíle propustnosti a minimální latence.",
                riskScore = 0.20f
            ),
            AgentVote(
                agentId = skeptic.id,
                agentName = skeptic.name,
                voteType = VoteType.CONDITIONAL,
                justification = "Podmíněno kontinuálním monitoringem Shannonovy entropie pod prahem S = 2.08.",
                riskScore = 0.55f
            ),
            AgentVote(
                agentId = regulator.id,
                agentName = regulator.name,
                voteType = if (isRegulatorVetoTriggered) VoteType.VETO else VoteType.CONDITIONAL,
                justification = if (isRegulatorVetoTriggered) {
                    "VETO: Detekováno kritické bezpečnostní a regulatorní riziko (NIS2 / EU AI Act)."
                } else {
                    "Soulad s normami ISO 42001 potvrzen za podmínky kryptografického auditování (Hash Chain)."
                },
                riskScore = if (isRegulatorVetoTriggered) 0.95f else 0.35f
            ),
            AgentVote(
                agentId = engineer.id,
                agentName = engineer.name,
                voteType = VoteType.APPROVE,
                justification = "CSR matice a kognitivní mosty jsou paměťově deterministické.",
                riskScore = 0.25f
            )
        )

        // Syntéza 8D vah
        val synthesizedScores = mutableMapOf<String, Float>()
        all8DDomains.forEach { domain ->
            val wArch = scoresArchitect[domain] ?: 0.5f
            val wSkep = scoresSkeptic[domain] ?: 0.5f
            val wReg = scoresRegulator[domain] ?: 0.5f
            val wEng = scoresEngineer[domain] ?: 0.5f
            val avg = (wArch * 0.25f + wSkep * 0.25f + wReg * 0.25f + wEng * 0.25f).coerceIn(0.1f, 0.95f)
            synthesizedScores[domain] = (avg * 100f).toInt() / 100f
        }

        val isVeto = votes.any { it.voteType == VoteType.VETO }
        val isOverride = (overrideFlag == "MODIFY_SYSTEM_STATE")

        val hashString = "OMNIS-4A-${System.currentTimeMillis()}-$problemStatement-VETO:$isVeto-OVERRIDE:$isOverride"
        val digest = MessageDigest.getInstance("SHA-256").digest(hashString.toByteArray())
        val zkCommitmentHash = digest.take(8).joinToString("") { "%02X".format(it) }

        val consensusIndex = if (isVeto && !isOverride) 0.25f else 0.88f
        val divergenceIndex = if (isVeto) 0.72f else 0.28f

        val auditLogEntry = if (isOverride) {
            "OVERRIDE_ACTIVE: Veto Agenta Regulátora bylo nevratně přehlasováno operátorem (flag: MODIFY_SYSTEM_STATE). Hash: $zkCommitmentHash."
        } else if (isVeto) {
            "VETO_BLOCKED: Exekuce zablokována Agentem Regulátorem pro nesoulad s bezpečnostním standardem."
        } else {
            "CONSENSUS_ACHIEVED: Dosaženo schválení 4 kognitivními agenty (Consensus: 88%)."
        }

        // Pokud je override aktivní nebo je dosaženo čistého konsensu, zapíšeme stav do kernelu na Dispatchers.Default
        if (!isVeto || isOverride) {
            AkgeTensorKernel.updateDomainWeightsAsync(synthesizedScores)
        }
        
        com.example.telemetry.TelemetryEngine.log(
            type = if (isVeto && !isOverride) "WARNING" else "INFO",
            component = "SYSTEM_INTELLIGENCE",
            message = "Debata '${problemStatement.take(30)}...' dokončena. Konsenzus: ${(consensusIndex * 100).toInt()}%",
            metadata = "{\"veto\":$isVeto, \"override\":$isOverride, \"hash\":\"$zkCommitmentHash\"}"
        )

        val result = FourAgentDebateResult(
            problemStatement = problemStatement,
            turns = turns,
            votes = votes,
            isVetoActive = isVeto,
            isOverrideActive = isOverride,
            synthesized8DScores = synthesizedScores,
            divergenceIndex = divergenceIndex,
            consensusIndex = consensusIndex,
            synthesisSummary = if (isOverride) {
                "EXECUTIVE OVERRIDE: Veto Regulátora přehlasováno. Aplikována 8D syntéza Architekta a Inženýra."
            } else if (isVeto) {
                "ZABLOKOVÁNO VETEM REGULÁTORA: Vyžaduje řešení bezpečnostního souladu nebo zásah operátora s flagem MODIFY_SYSTEM_STATE."
            } else {
                "PLNÝ KONSENZUS: 4 agenti schválili harmonizaci tenzorového pole v nominalním režimu."
            },
            actionableGuidelines = listOf(
                "Auditovat brány Refusal Ladderu G1-G6 před zápisem.",
                "Zajistit minimální redundanci v doméně SEC na úrovni ${synthesizedScores["SEC"] ?: 0.5f}.",
                "Zapojit kryptografický otisk $zkCommitmentHash do HMAC-SHA256 auditního ledgeru."
            ),
            zkCommitmentHash = zkCommitmentHash,
            auditLogEntry = auditLogEntry
        )

        _fourAgentHistoryState.value = listOf(result) + _fourAgentHistoryState.value
        return result
    }

    /**
     * Aplikuje Executive Override na existující debatu zablokovanou vetem Regulátora.
     */
    fun applyExecutiveOverride(debateId: String, flag: String): FourAgentDebateResult? {
        if (flag != "MODIFY_SYSTEM_STATE") return null

        val currentList = _fourAgentHistoryState.value
        val debate = currentList.find { it.debateId == debateId } ?: return null
        if (!debate.isVetoActive || debate.isOverrideActive) return debate

        val updated = debate.copy(
            isOverrideActive = true,
            consensusIndex = 0.85f,
            auditLogEntry = "OVERRIDE_ACTIVE: Veto Regulátora nevratně přehlasováno operátorem přes MODIFY_SYSTEM_STATE. Hash: ${debate.zkCommitmentHash}",
            synthesisSummary = "EXECUTIVE OVERRIDE AKTIVNÍ: Změny byly zapsány do jádra systému O.M.N.I.S."
        )

        // Nevratný zápis do AkgeTensorKernel
        AkgeTensorKernel.updateDomainWeights(updated.synthesized8DScores)

        _fourAgentHistoryState.value = currentList.map { if (it.debateId == debateId) updated else it }
        return updated
    }

    private fun generatePresetFourAgentHistory(): List<FourAgentDebateResult> {
        return listOf(
            FourAgentDebateResult(
                debateId = "4a-preset-001",
                problemStatement = "Autonomní AI v řízení letového provozu: Bezvýhradný automat vs. Člověk v smyčce",
                turns = emptyList(),
                votes = listOf(
                    AgentVote("architect", "Architekt", VoteType.APPROVE, "Maximální propustnost a reakční doba pod 1ms.", 0.20f),
                    AgentVote("skeptic", "Skeptik", VoteType.CONDITIONAL, "Podmíněno detekcí anomálií a zálohováním.", 0.50f),
                    AgentVote("regulator", "Regulátor", VoteType.VETO, "VETO: Chybí garance článku 14 EU AI Act o lidském zásahu.", 0.95f),
                    AgentVote("engineer", "Inženýr", VoteType.APPROVE, "Kód je deterministický v Kotlin 2.0.", 0.25f)
                ),
                isVetoActive = true,
                isOverrideActive = false,
                synthesized8DScores = mapOf("SYS" to 0.85f, "ECON" to 0.70f, "PSYCH" to 0.60f, "ECO" to 0.65f, "LAW" to 0.90f, "SEC" to 0.92f, "PHYS" to 0.80f, "SOC" to 0.70f),
                divergenceIndex = 0.65f,
                consensusIndex = 0.25f,
                synthesisSummary = "ZABLOKOVÁNO VETEM: Regulátor odmítl autonomní provoz bez lidského operátora.",
                actionableGuidelines = listOf("Doplnit lidský schvalovací krok", "Vložit flag MODIFY_SYSTEM_STATE pro přehlasování"),
                zkCommitmentHash = "4A7B29F1",
                auditLogEntry = "VETO_ACTIVE: Zablokováno Agentem Regulátorem pro nesoulad s normou EU AI Act."
            )
        )
    }

    // 2-Agent sparring execution (backward compatibility)
    suspend fun executeArenaDebate(
        problemStatement: String,
        agentA: AgentArchetype,
        agentB: AgentArchetype,
        onProgressUpdate: suspend (ArenaDebateTurn) -> Unit = {}
    ): ArenaDebateResult {
        val turns = mutableListOf<ArenaDebateTurn>()
        val problemHash = abs(problemStatement.hashCode())
        val agentAScores = generate8DScores(agentA, problemHash, isA = true)
        val agentBScores = generate8DScores(agentB, problemHash, isA = false)

        delay(300)
        val turn1 = ArenaDebateTurn(1, "Kolo 1: Primární Teze", agentA.id, agentA.name, generateOpeningArgument(agentA, problemStatement), agentA.primaryDomains, agentAScores)
        turns.add(turn1)
        onProgressUpdate(turn1)

        delay(350)
        val turn2 = ArenaDebateTurn(2, "Kolo 1: Protiteze & Antiteze", agentB.id, agentB.name, generateOpeningArgument(agentB, problemStatement), agentB.primaryDomains, agentBScores)
        turns.add(turn2)
        onProgressUpdate(turn2)

        delay(350)
        val turn3 = ArenaDebateTurn(3, "Kolo 2: Křížový Výslech & Námitka", agentA.id, agentA.name, generateCrossArgument(agentA, agentB, problemStatement), listOf("LAW", "SYS"), agentAScores)
        turns.add(turn3)
        onProgressUpdate(turn3)

        delay(350)
        val turn4 = ArenaDebateTurn(4, "Kolo 2: Replikace & Stresový Test", agentB.id, agentB.name, generateRebuttalArgument(agentB, agentA, problemStatement), listOf("SEC", "ECON"), agentBScores)
        turns.add(turn4)
        onProgressUpdate(turn4)

        val synthesizedScores = mutableMapOf<String, Float>()
        var totalDivergence = 0f
        all8DDomains.forEach { domain ->
            val scoreA = agentAScores[domain] ?: 0f
            val scoreB = agentBScores[domain] ?: 0f
            totalDivergence += abs(scoreA - scoreB)
            val synth = (scoreA * agentA.epistemicRigor + scoreB * agentB.epistemicRigor) / (agentA.epistemicRigor + agentB.epistemicRigor)
            synthesizedScores[domain] = (synth * 100f).toInt() / 100f
        }

        val avgDivergence = (totalDivergence / all8DDomains.size).coerceIn(0.1f, 0.95f)
        val consensusIndex = ((1.0f - avgDivergence) * 100f).toInt() / 100f

        val hashString = "OMNIS-ARENA-${System.currentTimeMillis()}-$problemStatement-${agentA.id}-${agentB.id}"
        val digest = MessageDigest.getInstance("SHA-256").digest(hashString.toByteArray())
        val zkCommitmentHash = digest.take(8).joinToString("") { "%02X".format(it) }

        val result = ArenaDebateResult(
            problemStatement = problemStatement,
            agentA = agentA,
            agentB = agentB,
            turns = turns,
            agentA8DScores = agentAScores,
            agentB8DScores = agentBScores,
            synthesized8DScores = synthesizedScores,
            divergenceIndex = avgDivergence,
            consensusIndex = consensusIndex,
            synthesisSummary = "Syntéza souboje mezi [${agentA.name}] a [${agentB.name}]: Dosaženo konsenzu ${(consensusIndex * 100).toInt()}%.",
            actionableGuidelines = listOf(
                "Aplikovat kompromisní bezpečnostní práh v doméně SEC na úrovni ${synthesizedScores["SEC"] ?: 0.5f}.",
                "Inkorporovat auditní brány G1–G6 z návrhu ${agentA.name} do výpočetního toku."
            ),
            zkCommitmentHash = zkCommitmentHash,
            proofOfNecessity = "AUDIT-NECESSITY-${System.currentTimeMillis()}"
        )

        debateHistoryState.value = listOf(result) + debateHistoryState.value
        return result
    }

    private fun generatePresetHistory(): List<ArenaDebateResult> {
        val agent1 = AgentArenaLibrary.ARCHITECT
        val agent2 = AgentArenaLibrary.REGULATOR
        return listOf(
            ArenaDebateResult(
                debateId = "hist-001",
                problemStatement = "Kvantová migrace bankovní infrastruktury: Okamžitý přechod vs. Postupné testování",
                agentA = agent1,
                agentB = agent2,
                turns = emptyList(),
                agentA8DScores = mapOf("SYS" to 0.4f, "ECON" to 0.3f, "PSYCH" to 0.5f, "ECO" to 0.6f, "LAW" to 0.9f, "SEC" to 0.95f, "PHYS" to 0.4f, "SOC" to 0.5f),
                agentB8DScores = mapOf("SYS" to 0.9f, "ECON" to 0.85f, "PSYCH" to 0.3f, "ECO" to 0.2f, "LAW" to 0.3f, "SEC" to 0.4f, "PHYS" to 0.9f, "SOC" to 0.4f),
                synthesized8DScores = mapOf("SYS" to 0.65f, "ECON" to 0.58f, "PSYCH" to 0.4f, "ECO" to 0.4f, "LAW" to 0.6f, "SEC" to 0.68f, "PHYS" to 0.65f, "SOC" to 0.45f),
                divergenceIndex = 0.52f,
                consensusIndex = 0.74f,
                synthesisSummary = "Dosaženo postupné fázové nasazení s kryptografickou auditní stopou (HMAC-SHA256) pro zamezení rizika v doméně SEC.",
                actionableGuidelines = listOf("Nasadit kvantově odolné šifrování na hranici LAW a SEC", "Ponechat záložní CSR cluster pro rollback"),
                zkCommitmentHash = "9F8B2C4E",
                proofOfNecessity = "HISTORICAL-PRESET-001"
            )
        )
    }

    private fun generate8DScores(agent: AgentArchetype, problemHash: Int, isA: Boolean): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        val signModifier = if (isA) 1.0f else -0.7f
        all8DDomains.forEachIndexed { idx, domain ->
            val isPrimary = agent.primaryDomains.contains(domain)
            val baseVal = if (isPrimary) {
                0.6f + (agent.riskTolerance * 0.35f)
            } else {
                0.1f + ((problemHash % (idx + 5)) / 10f) * 0.4f
            }
            val valAdjusted = (baseVal * if (isPrimary) 1.0f else signModifier).coerceIn(-0.9f, 0.95f)
            scores[domain] = (valAdjusted * 10f).toInt() / 10f
        }
        return scores
    }

    private fun generateOpeningArgument(agent: AgentArchetype, problem: String): String {
        return "Z pohledu role ${agent.roleTitle} navrhuji přístup k problému '$problem' s důrazem na domény ${agent.primaryDomains.joinToString()} a kontrolované riziko (${agent.riskTolerance})."
    }

    private fun generateCrossArgument(agent: AgentArchetype, opponent: AgentArchetype, problem: String): String {
        return "Namítám vůči pozici [${opponent.name}] u problému '$problem'! Jejich zaměření zanedbává systémovou integritu."
    }

    private fun generateRebuttalArgument(agent: AgentArchetype, opponent: AgentArchetype, problem: String): String {
        return "Námitka [${opponent.name}] pramení z příliš úzkého pohledu. Trvám na kompromisním úpravení 8D matice!"
    }
}
