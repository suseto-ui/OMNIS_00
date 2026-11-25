package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OmnisRecord
import com.example.data.OmnisRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: OmnisRepository) : ViewModel() {
    val inputQuery = MutableStateFlow("")
    val isLoading = MutableStateFlow(false)
    val ocrValidationState = MutableStateFlow<com.example.ui.OmnisViewModel.OcrValidationState?>(null)
    val isOcrLoading = MutableStateFlow(false)
    val activeThreadId = MutableStateFlow("thread_main")
    
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val records = activeThreadId.flatMapLatest { threadId: String ->
        repository.getMessagesForThread(threadId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<com.example.data.OmnisRecord>())

    val activeThreadTitle = MutableStateFlow("Hlavní Operační Vlákno")

    fun onQueryChange(query: String) {
        inputQuery.value = query
    }

    fun sendQuery(query: String) {
        val textToSend = query.ifBlank { inputQuery.value }
        if (textToSend.isBlank()) return

        viewModelScope.launch {
            isLoading.value = true
            inputQuery.value = ""

            // 1. Uložení dotazu uživatele
            val userRecord = com.example.data.OmnisRecord(
                role = "user",
                content = textToSend,
                threadId = activeThreadId.value
            )
            repository.insert(userRecord)

            // 2. RAG: Vyhledání relevantní historie
            val similarRecords = repository.findSimilarRecords(textToSend.take(20), limit = 3)
            val ragContext = similarRecords.joinToString("\n") { 
                "[HISTORIE ${it.role}]: ${it.content.take(200)}"
            }

            // 3. Načtení 8D metrik z Jádra a vyhodnocení kaskádového rizika
            val kernelState = com.example.engine.AkgeTensorKernel.kernelState.value
            val cascadeRisk = com.example.ui.OmnisCorrelationEngine.evaluateCascadeRiskWithTelemetry(
                domainValues = kernelState.domainWeights,
                cognitiveAnomalyScore = kernelState.cognitiveAnomalyScore,
                quantumEntanglementIndex = kernelState.quantumEntanglementIndex,
                latencyDriftMs = kernelState.latencyDriftMs,
                selfHealingConfidence = kernelState.selfHealingConfidence
            )

            val systemMetricsContext = kernelState.domainWeights.entries.joinToString(", ") { 
                "${it.key}:${it.value}"
            }

            // 4. Formátování Expert Promptu s ohledem na stav systému
            val tokenModeMsg = if (cascadeRisk.tokenRebalancingMode == "FULL_FORENSIC") {
                "SYSTÉMOVÉ PNUTÍ VYSOKÉ: Aktivován FORENZNÍ REŽIM (Nulová komprese). Analyzuj do nejmenších detailů."
            } else {
                "Režim tokenové optimalizace: Komprese 42% aktivní."
            }

            val systemInstruction = """
                Jsi O.M.N.I.S. v4.5, KYBER-AI EXPERT. Tvé odpovědi jsou technicky precizní a analytické.
                Používej terminologii: tenzory, kognitivní grafy, entropie.
                Zachovávej chladný, profesionální tón. Komunikuj v češtině.
                $tokenModeMsg
            """.trimIndent()

            val expertPrompt = """
                $systemInstruction
                
                [SYSTÉMOVÝ STAV 8D]: $systemMetricsContext
                [KASKÁDOVÉ RIZIKO]: ${cascadeRisk.warningSummary}
                [RELEVANTNÍ HISTORIE (RAG)]:
                $ragContext
                
                [DOTAZ OPERÁTORA]: $textToSend
                
                Proveď hloubkovou technickou analýzu a navrhni řešení v souladu s protokolem O.M.N.I.S.
            """.trimIndent()

            // 5. Volání AI Služby s APEX Master Protokolem
            val result = com.example.api.FirebaseAiService.generateContent(expertPrompt)
            
            result.onSuccess { rawResponseText ->
                // APEX Orchestrátor: Spuštění 4-krokového protokolu a sanitizace
                val apexResult = com.example.engine.ApexOrchestratorEngine.executeMasterProtocol(
                    query = textToSend,
                    rawModelResponse = rawResponseText
                )

                // Dual-Gate Post-Generation Validace (G1–G6 na výstupu)
                val outputVerdict = com.example.gateway.RefusalLadderEngine.evaluateOutput(
                    query = textToSend,
                    outputText = apexResult.finalArtifact,
                    outputDomainScores = kernelState.domainWeights,
                    currentSigma = kernelState.systemStressSigma
                )

                val verifiedContent = if (outputVerdict.isApproved) {
                    apexResult.finalArtifact
                } else {
                    val gate = outputVerdict.refusingGate
                    com.example.telemetry.TelemetryEngine.log(
                        type = "SECURITY",
                        component = "OUTPUT_REFUSAL_LADDER",
                        message = "Výstup v ChatViewModel zablokován na bráně ${gate?.gateId?.code}: ${gate?.reason}"
                    )
                    "⛔ **EXPLICIT GROUNDED REFUSAL NA VÝSTUPU (${gate?.gateId?.code ?: "G1-G6"})**\n\n" +
                        "${gate?.reason ?: "Vygenerovaný výstup neprošel post-generační verifikací."}\n\n" +
                        "Norma: ${gate?.regulatoryNorm ?: "EU AI Act / NIS2 / AKGE-8D"}"
                }

                // Vyhodnocení kvality napříč 4 pilíři (Q_omnis)
                val evalScore = com.example.engine.UnifiedResponseEvaluator.evaluateResponse(
                    query = textToSend,
                    response = verifiedContent
                )

                val assistantRecord = com.example.data.OmnisRecord(
                    role = "assistant",
                    content = verifiedContent,
                    threadId = activeThreadId.value,
                    defenseTier = if (outputVerdict.isApproved) "APPROVED" else "REFUSED_OUTPUT_${outputVerdict.refusingGate?.gateId?.code ?: "G6"}",
                    cognitiveProcess = "RAG context integrated. Cascade risk: ${cascadeRisk.isCascadeRiskActive}. Mode: ${cascadeRisk.tokenRebalancingMode}",
                    valSys = kernelState.domainWeights["SYS"] ?: 0.5f,
                    valSec = kernelState.domainWeights["SEC"] ?: 0.5f,
                    topologicalDiagnostic = if (outputVerdict.isApproved) cascadeRisk.warningSummary else (outputVerdict.refusingGate?.reason ?: cascadeRisk.warningSummary),
                    confidenceScore = if (outputVerdict.isApproved) apexResult.adversarialResilienceScore else 0.25f,
                    reliabilityStatus = if (outputVerdict.isApproved) evalScore.qualityTier else "REFUSED",
                    hiddenScratchpad = apexResult.hiddenScratchpad.ifBlank { null },
                    qOmnisIndex = evalScore.unifiedQualityIndex,
                    qualityTier = evalScore.qualityTier
                )
                repository.insert(assistantRecord)
            }.onFailure { error ->
                // Ochranný jistič (Circuit Breaker) & Lokální Deterministický Fallback
                com.example.telemetry.TelemetryEngine.log(
                    type = "WARNING",
                    component = "CIRCUIT_BREAKER",
                    message = "ChatViewModel fallback aktivován: ${error.message ?: "Provider unreachable"}"
                )
                val localFallbackResult = com.example.engine.ApexOrchestratorEngine.executeMasterProtocol(
                    query = textToSend
                )
                val fallbackVerdict = com.example.gateway.RefusalLadderEngine.evaluateOutput(
                    query = textToSend,
                    outputText = localFallbackResult.finalArtifact,
                    outputDomainScores = kernelState.domainWeights,
                    currentSigma = kernelState.systemStressSigma
                )
                val verifiedFallback = if (fallbackVerdict.isApproved) {
                    localFallbackResult.finalArtifact
                } else {
                    "⛔ **EXPLICIT GROUNDED REFUSAL NA VÝSTUPU (${fallbackVerdict.refusingGate?.gateId?.code ?: "G6"})**\n\n" +
                        "${fallbackVerdict.refusingGate?.reason ?: "Výstup neprošel verifikací."}"
                }
                val fallbackEval = com.example.engine.UnifiedResponseEvaluator.evaluateResponse(
                    query = textToSend,
                    response = verifiedFallback
                )

                val fallbackRecord = com.example.data.OmnisRecord(
                    role = "assistant",
                    content = verifiedFallback,
                    threadId = activeThreadId.value,
                    defenseTier = if (fallbackVerdict.isApproved) "FALLBACK_RECOVERY" else "REFUSED_OUTPUT_${fallbackVerdict.refusingGate?.gateId?.code ?: "G6"}",
                    topologicalDiagnostic = "Circuit Breaker aktivován (${error.message}). Spuštěna lokální deterministická simulace.",
                    confidenceScore = if (fallbackVerdict.isApproved) 0.92f else 0.25f,
                    reliabilityStatus = if (fallbackVerdict.isApproved) fallbackEval.qualityTier else "REFUSED",
                    hiddenScratchpad = localFallbackResult.hiddenScratchpad.ifBlank { null },
                    qOmnisIndex = fallbackEval.unifiedQualityIndex,
                    qualityTier = fallbackEval.qualityTier
                )
                repository.insert(fallbackRecord)
            }

            isLoading.value = false
        }
    }
}
