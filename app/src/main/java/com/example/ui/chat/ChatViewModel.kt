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
                Jsi O.M.N.I.S. v4.2, KYBER-AI EXPERT. Tvé odpovědi jsou technicky precizní a analytické.
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

                // Vyhodnocení kvality napříč 4 pilíři (Q_omnis)
                val evalScore = com.example.engine.UnifiedResponseEvaluator.evaluateResponse(
                    query = textToSend,
                    response = apexResult.finalArtifact
                )

                val assistantRecord = com.example.data.OmnisRecord(
                    role = "assistant",
                    content = apexResult.finalArtifact,
                    threadId = activeThreadId.value,
                    cognitiveProcess = "RAG context integrated. Cascade risk: ${cascadeRisk.isCascadeRiskActive}. Mode: ${cascadeRisk.tokenRebalancingMode}",
                    valSys = kernelState.domainWeights["SYS"] ?: 0.5f,
                    valSec = kernelState.domainWeights["SEC"] ?: 0.5f,
                    topologicalDiagnostic = cascadeRisk.warningSummary,
                    confidenceScore = apexResult.adversarialResilienceScore,
                    reliabilityStatus = evalScore.qualityTier,
                    hiddenScratchpad = apexResult.hiddenScratchpad.ifBlank { null },
                    qOmnisIndex = evalScore.unifiedQualityIndex,
                    qualityTier = evalScore.qualityTier
                )
                repository.insert(assistantRecord)
            }.onFailure { error ->
                // Ochranný jistič (Circuit Breaker) & Lokální Deterministický Fallback
                val localFallbackResult = com.example.engine.ApexOrchestratorEngine.executeMasterProtocol(
                    query = textToSend
                )
                val fallbackEval = com.example.engine.UnifiedResponseEvaluator.evaluateResponse(
                    query = textToSend,
                    response = localFallbackResult.finalArtifact
                )

                val fallbackRecord = com.example.data.OmnisRecord(
                    role = "assistant",
                    content = localFallbackResult.finalArtifact,
                    threadId = activeThreadId.value,
                    defenseTier = "FALLBACK_RECOVERY",
                    topologicalDiagnostic = "Circuit Breaker aktivován (${error.message}). Spuštěna lokální deterministická simulace.",
                    confidenceScore = 0.92f,
                    reliabilityStatus = fallbackEval.qualityTier,
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
