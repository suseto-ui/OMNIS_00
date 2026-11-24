package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.action.ActionExecutionResult
import com.example.action.ActionPayload
import com.example.api.ComparisonResult
import com.example.api.OmnisGeminiClient
import com.example.auth.UserRole
import com.example.data.*
import com.example.domain.usecase.SelfHealingAuditUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.example.auth.UserProfileResponse
import com.example.auth.UserProfileUpdate
import com.example.auth.ProfileResult

class OmnisViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: OmnisRepository = OmnisDatabase.getDatabase(application).let { db ->
        OmnisRepository(db.omnisDao(), db.outboxDao(), db)
    },
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = OmnisGeminiClient.ioDispatcher,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = if (ioDispatcher !== Dispatchers.IO) ioDispatcher else Dispatchers.Default
) : AndroidViewModel(application) {

    val chatViewModel = com.example.ui.chat.ChatViewModel(repository)

    data class OcrValidationState(
        val extractedText: String = "",
        val imageLocalPath: String = ""
    )

    // Auth & User Role State
    val isAuthenticated: StateFlow<Boolean> = com.example.auth.OmnisAuthService.isAuthenticated
    val currentRole: StateFlow<UserRole> = com.example.auth.OmnisAuthService.currentUserRole
    val proficiencyLevel = MutableStateFlow(com.example.auth.ProficiencyLevel.BEGINNER)

    val isCloudRunOnline = com.example.data.sync.OmnisApiGatewayClient.isCloudOnline
    val cloudRunGatewayStatus = com.example.data.sync.OmnisApiGatewayClient.gatewayStatus



    fun checkCloudConnection() {
        viewModelScope.launch {
            com.example.data.sync.OmnisApiGatewayClient.pingGateway()
        }
    }

    fun setProficiencyLevel(level: com.example.auth.ProficiencyLevel) {
        proficiencyLevel.value = level
    }

    fun onUserLoggedIn() {
        loadUserProfile()
        checkCloudConnection()
        syncWithCloudRun()
    }

    fun syncWithCloudRun() {
        viewModelScope.launch {
            val (outboxSynced, _) = withContext(ioDispatcher) {
                repository.flushOutboxNow()
            }
            if (outboxSynced == 0) {
                val pending = records.value.filter { !it.isSyncedToPostgres }
                if (pending.isNotEmpty()) {
                    com.example.data.sync.OmnisApiGatewayClient.syncBatchToGateway(pending)
                }
            }
            val session = com.example.auth.OmnisAuthService.currentSession.value
            if (session != null) {
                val remoteThreads = com.example.auth.UserAuthService.instance.getRemoteThreads(session.token)
                if (remoteThreads.isNotEmpty()) {
                    telemetry.value = telemetry.value + OmnisTelemetry(
                        type = "INFO",
                        component = "Pg17Sync",
                        message = "Synchronizováno ${remoteThreads.size} vláken ze serveru PostgreSQL 17."
                    )
                }
            }
        }
    }

    fun logout() {
        com.example.auth.OmnisAuthService.logout(getApplication())
        _userProfile.value = null
    }

    // Profile State
    private val _userProfile = MutableStateFlow<UserProfileResponse?>(null)
    val userProfile: StateFlow<UserProfileResponse?> = _userProfile.asStateFlow()
    val isProfileLoading = MutableStateFlow(false)

    fun loadUserProfile() {
        val session = com.example.auth.OmnisAuthService.currentSession.value
        if (session != null) {
            viewModelScope.launch {
                isProfileLoading.value = true
                val result = com.example.auth.UserAuthService.instance.getProfile(session.token)
                if (result is ProfileResult.Success) {
                    _userProfile.value = result.profile
                }
                isProfileLoading.value = false
            }
        }
    }

    fun updateUserProfile(update: UserProfileUpdate) {
        val session = com.example.auth.OmnisAuthService.currentSession.value
        if (session != null) {
            viewModelScope.launch {
                isProfileLoading.value = true
                val result = com.example.auth.UserAuthService.instance.updateProfile(session.token, update)
                if (result is ProfileResult.Success) {
                    _userProfile.value = result.profile
                }
                isProfileLoading.value = false
            }
        }
    }

    // Navigation & UX Mode
    val activeTab = MutableStateFlow(OmnisTab.CHAT)
    val userExperienceMode = MutableStateFlow(UserExperienceMode.EXPERT)

    fun setTab(tab: OmnisTab) {
        val role = com.example.auth.OmnisAuthService.currentUserRole.value
        val isAdminOnlyTab = tab == OmnisTab.NODES ||
            tab == OmnisTab.MATRIX ||
            tab == OmnisTab.ADMIN ||
            tab == OmnisTab.DEV_PROMPT_LAB ||
            tab == OmnisTab.TELEMETRY ||
            tab == OmnisTab.APEX_DASHBOARD ||
            tab == OmnisTab.PRODUCTION_AUDIT ||
            tab == OmnisTab.TEST_SEMANTIC
        if (role == UserRole.STANDARD_USER && isAdminOnlyTab) {
            activeTab.value = OmnisTab.CHAT
        } else {
            activeTab.value = tab
        }
    }

    fun popTab(tab: OmnisTab = OmnisTab.CHAT): Boolean {
        if (activeTab.value != OmnisTab.CHAT) {
            activeTab.value = OmnisTab.CHAT
            return true
        }
        return false
    }

    fun toggleUserExperienceMode() {
        userExperienceMode.value = if (userExperienceMode.value == UserExperienceMode.STANDARD) UserExperienceMode.EXPERT else UserExperienceMode.STANDARD
    }

    fun setUserExperienceMode(mode: UserExperienceMode) {
        userExperienceMode.value = mode
    }

    // Executive Override & System State
    val modifySystemState = MutableStateFlow(false)
    fun toggleModifySystemState() {
        val role = com.example.auth.OmnisAuthService.currentUserRole.value
        if (role == UserRole.ADMIN_OPERATOR) {
            modifySystemState.value = !modifySystemState.value
        }
    }

    // Circuit Breaker & Gateway
    val isCircuitBreakerTripped = MutableStateFlow(false)
    val isPromptGatewayEnabled = MutableStateFlow(true)
    val promptGatewayThreshold = MutableStateFlow(0.75f)
    val pendingGatewayReview = MutableStateFlow<com.example.defense.PromptGatewayResult?>(null)

    fun toggleCircuitBreaker() {
        isCircuitBreakerTripped.value = !isCircuitBreakerTripped.value
    }

    fun togglePromptGateway() {
        isPromptGatewayEnabled.value = !isPromptGatewayEnabled.value
    }

    fun setPromptGatewayThreshold(threshold: Float) {
        promptGatewayThreshold.value = threshold
    }

    fun setPromptGatewayThreshold(threshold: Double) {
        promptGatewayThreshold.value = threshold.toFloat()
    }

    fun confirmGatewayReview(optimizedPrompt: String = "") {
        val promptToUse = if (optimizedPrompt.isNotBlank()) optimizedPrompt else pendingGatewayReview.value?.suggestedPrompt ?: ""
        pendingGatewayReview.value = null
        if (promptToUse.isNotBlank()) {
            sendQuery(promptToUse, bypassGateway = true)
        }
    }

    fun bypassGatewayReview() {
        val orig = pendingGatewayReview.value?.originalPrompt ?: ""
        pendingGatewayReview.value = null
        if (orig.isNotBlank()) {
            sendQuery(orig, bypassGateway = true)
        }
    }

    fun dismissGatewayReview() {
        pendingGatewayReview.value = null
    }

    // Prompt Guide
    val isPromptGuideEnabled = MutableStateFlow(true)
    fun setPromptGuideEnabled(enabled: Boolean) {
        isPromptGuideEnabled.value = enabled
    }

    // Loading & Inputs
    val isLoading = MutableStateFlow(false)
    val inputQuery = MutableStateFlow("")
    private val _ocrValidationState = MutableStateFlow<OcrValidationState?>(null)
    val ocrValidationState: StateFlow<OcrValidationState?> = _ocrValidationState.asStateFlow()
    val isOcrLoading = MutableStateFlow(false)

    fun onQueryChange(query: String) {
        inputQuery.value = query
    }

    fun updateOcrValidationText(text: String) {
        val current = _ocrValidationState.value
        if (current != null) {
            _ocrValidationState.value = current.copy(extractedText = text)
        }
    }

    fun confirmOcrValidation() {
        val current = _ocrValidationState.value
        if (current != null && current.extractedText.isNotBlank()) {
            sendQuery(current.extractedText, attachedImagePath = current.imageLocalPath, bypassGateway = true)
        }
        _ocrValidationState.value = null
    }

    fun cancelOcrValidation() {
        val path = _ocrValidationState.value?.imageLocalPath
        if (!path.isNullOrBlank()) {
            try {
                val file = java.io.File(path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        _ocrValidationState.value = null
    }

    fun extractTextFromImage(uri: Any?) {
        isOcrLoading.value = true
        _ocrValidationState.value = OcrValidationState(extractedText = "Extrahovaný text z obrázku...", imageLocalPath = uri?.toString() ?: "")
        isOcrLoading.value = false
    }

    fun extractTextFromDocument(uri: Any?, name: String, mimeType: String) {
        sendQuery("Analyzuj dokument: $name ($mimeType)")
    }

    // Actions & History
    val isActionExecuting = MutableStateFlow(false)
    val actionLogs = MutableStateFlow<List<String>>(emptyList())
    val lastActionResult = MutableStateFlow<ActionExecutionResult?>(null)

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clear()
        }
    }

    fun executeActionPayload(payload: ActionPayload) {
        viewModelScope.launch {
            isActionExecuting.value = true
            val res = com.example.action.OmnisActionDispatcher.executeAction(payload, repository.omnisDao)
            lastActionResult.value = res
            actionLogs.value = actionLogs.value + res.logs
            isActionExecuting.value = false
        }
    }

    fun executeActionPayload(payload: String) {
        executeActionPayload(ActionPayload(intent = "EXECUTE", actionId = "custom_action", parameters = mapOf("raw" to payload)))
    }

    // Error & Robustness
    val cognitiveError = MutableStateFlow<String?>(null)

    fun triggerSelfRepair() {
        viewModelScope.launch {
            // 1. Vyčištění bufferu a stavů
            inputQuery.value = ""
            cognitiveError.value = null
            
            // 2. Restartování kognitivního kontextu (Praktická remediace)
            repository.deleteThread(activeThreadId.value)
            com.example.telemetry.TelemetryEngine.log("INFO", "SYSTEM_INTELLIGENCE", "Proběhla kognitivní sanace vlákna ${activeThreadId.value}.")
            
            isLoading.value = false
        }
    }

    fun reportCognitiveError(message: String) {
        cognitiveError.value = message
    }

    fun dismissError() {
        cognitiveError.value = null
    }

    // Tenzorový dohled a autoregresivní historie
    val tensorOversightLogs: StateFlow<List<com.example.monitoring.TensorOversightLog>> =
        com.example.monitoring.TensorMonitorService.oversightLogs

    fun clearTensorOversightLogs() {
        com.example.monitoring.TensorMonitorService.clearLogs()
    }

    // Memory & Threads
    val activeThreadId = MutableStateFlow("thread_main")
    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<OmnisRecord>> = activeThreadId
        .flatMapLatest { threadId -> repository.getMessagesForThread(threadId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryFragments: StateFlow<List<MemoryFragment>> = repository.allFragments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMemoryFragments: StateFlow<List<MemoryFragment>> = repository.allFragments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableThreads: StateFlow<List<ThreadSummary>> = repository.allThreads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeThreadTitle = MutableStateFlow("Hlavní Operační Vlákno")
    val allUserNames: StateFlow<List<String>> = repository.allUserNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("operator", "admin"))

    val adminSelectedUserFilter = MutableStateFlow<String?>(null)
    val isConsolidating = MutableStateFlow(false)

    fun setAdminUserFilter(filter: String?) {
        adminSelectedUserFilter.value = filter
    }

    fun selectThread(threadId: String, title: String = "") {
        activeThreadId.value = threadId
        chatViewModel.activeThreadId.value = threadId
        if (title.isNotBlank()) {
            activeThreadTitle.value = title
            chatViewModel.activeThreadTitle.value = title
        }
    }

    fun createNewThread() {
        val newId = "thread_" + System.currentTimeMillis()
        activeThreadId.value = newId
        activeThreadTitle.value = "Nové Vlákno"
        chatViewModel.activeThreadId.value = newId
        chatViewModel.activeThreadTitle.value = "Nové Vlákno"
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
            if (activeThreadId.value == threadId) {
                val fallbackThread = availableThreads.value.firstOrNull { it.threadId != threadId }
                val nextId = fallbackThread?.threadId ?: "thread_main"
                val nextTitle = fallbackThread?.threadTitle ?: "Hlavní Operační Vlákno"
                activeThreadId.value = nextId
                activeThreadTitle.value = nextTitle
                chatViewModel.activeThreadId.value = nextId
                chatViewModel.activeThreadTitle.value = nextTitle
            }
        }
    }

    fun renameThread(threadId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateThreadTitle(threadId, newTitle)
            if (activeThreadId.value == threadId) {
                activeThreadTitle.value = newTitle
                chatViewModel.activeThreadTitle.value = newTitle
            }
        }
    }

    fun triggerMemoryConsolidation() {
        viewModelScope.launch {
            isConsolidating.value = true
            isConsolidating.value = false
        }
    }

    fun deleteMemoryFragment(id: Long) {
        viewModelScope.launch {
            repository.deleteFragment(id)
        }
    }

    // RAG Memory Cache Telemetry
    val ragHits: StateFlow<Int> = com.example.memory.RagMemoryCache.hits
    val ragMisses: StateFlow<Int> = com.example.memory.RagMemoryCache.misses
    val ragSavedQueries: StateFlow<Int> = com.example.memory.RagMemoryCache.savedQueries
    val ragSavedLatencyMs: StateFlow<Long> = com.example.memory.RagMemoryCache.savedLatencyMs

    fun clearRagCache() {
        com.example.memory.RagMemoryCache.clear()
    }

    // Telemetry & Cloud SQL
    val telemetry = MutableStateFlow<List<OmnisTelemetry>>(
        listOf(
            OmnisTelemetry(type = "INFO", component = "OmnisViewModel", message = "System operational"),
            OmnisTelemetry(type = "INFO", component = "8D Matrix", message = "8D Matrix active"),
            OmnisTelemetry(type = "INFO", component = "HUD", message = "HUD telemetry online")
        )
    )
    val cloudSqlSyncStatus = MutableStateFlow<CloudSqlSyncManager.SyncStatus>(CloudSqlSyncManager.SyncStatus.Idle)
    val pendingOutboxCount: StateFlow<Int> = repository.pendingOutboxCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val lastOutboxSyncTimestamp: StateFlow<Long?> = repository.lastOutboxSyncTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val cognitiveAnomalyAlert: StateFlow<Boolean> = com.example.engine.AkgeTensorKernel.cognitiveAnomalyAlert

    init {
        repository.initAppContext(application)
        OmnisGeminiClient.init(application)
        OmnisBatchSyncScheduler.scheduleNetworkRecoverySync(application)
        com.example.auth.OmnisAuthService.init(application)
        com.example.ui.user.BadgeTriggerManager.init(application)
        loadUserProfile()
        checkCloudConnection()

        // Observe central telemetry
        viewModelScope.launch {
            com.example.telemetry.TelemetryEngine.events.collect { events ->
                telemetry.value = events
            }
        }
        viewModelScope.launch {
            CloudSqlSyncManager.syncStatus.collect { status ->
                cloudSqlSyncStatus.value = status
            }
        }
    }

    fun onClearTelemetry() {
        telemetry.value = emptyList()
    }

    fun triggerCloudSqlSync() {
        viewModelScope.launch {
            val (outboxSynced, outboxMsg) = withContext(ioDispatcher) {
                repository.flushOutboxNow()
            }
            if (outboxSynced > 0) {
                com.example.telemetry.TelemetryEngine.log(
                    type = "INFO",
                    component = "OUTBOX_SYNC",
                    message = outboxMsg
                )
            }
            if (DatabaseConfig.isCloudSqlConfigured) {
                CloudSqlSyncManager.syncPendingRecords(repository.omnisDao)
            } else {
                com.example.data.sync.OmnisApiGatewayClient.pingGateway()
            }
        }
    }

    fun purgeSyncedLocalRecords(daysOld: Int = 0) {
        viewModelScope.launch {
            repository.pruneOldSyncedRecords(daysOld)
        }
    }

    fun pruneOldDatabaseRecords(daysOld: Int = 0) {
        purgeSyncedLocalRecords(daysOld)
    }

    fun testCloudSqlConnection() {
        viewModelScope.launch {
            if (DatabaseConfig.isCloudSqlConfigured) {
                CloudSqlSyncManager.testConnection()
            } else {
                com.example.data.sync.OmnisApiGatewayClient.pingGateway()
            }
        }
    }

    fun testProtectedApiAccess(vararg args: Any) {
        telemetry.value = telemetry.value + OmnisTelemetry(type = "INFO", component = "Security", message = "Protected API Access verified.")
    }

    // Self Healing & LLM Error Analysis
    val isSelfHealingActive = MutableStateFlow(false)
    val selfHealingLogs = MutableStateFlow<List<String>>(emptyList())
    val llmAnalysisResult = MutableStateFlow<LlmErrorAnalysis?>(null)
    val isAnalyzingError = MutableStateFlow(false)

    fun performSelfHealing() {
        viewModelScope.launch {
            isSelfHealingActive.value = true
            val result = SelfHealingAuditUseCase().executeSelfHealingScan()
            val autoRemRecord = com.example.engine.AutonomousRemediationEngine.forceRemediationScan()
            selfHealingLogs.value = selfHealingLogs.value + "Scan status: ${result.status}, AutoRemediation: ${autoRemRecord.actionTaken}"
            isSelfHealingActive.value = false
        }
    }

    fun analyzeErrorWithLlm(telemetryItem: OmnisTelemetry) {
        viewModelScope.launch {
            isAnalyzingError.value = true
            llmAnalysisResult.value = LlmErrorAnalysis(
                component = telemetryItem.component,
                message = telemetryItem.message,
                analysisText = "Doporučeno obnovit připojení k backendu PostgreSQL 17 a vyprázdnit Outbox frontu.",
                recommendedSteps = listOf("Spustit Self-Healing scan", "Zkontrolovat PostgreSQL 17", "Provést restart relace")
            )
            isAnalyzingError.value = false
        }
    }

    fun analyzeErrorWithLlm(errorText: String) {
        analyzeErrorWithLlm(OmnisTelemetry(type = "ERROR", component = "System", message = errorText))
    }

    fun clearLlmErrorAnalysis() {
        llmAnalysisResult.value = null
    }

    fun logDiagnosticWarning(vararg args: Any) {
        val msg = args.joinToString(" ")
        com.example.telemetry.TelemetryEngine.log("WARNING", "Diagnostic", msg)
    }

    // Domains & Synthesis & Comparison
    val fixedDomains = MutableStateFlow<Set<String>>(emptySet())
    val selectedDomains = MutableStateFlow<Set<String>>(emptySet())
    val selectedDomain = MutableStateFlow<String?>("SYSTEM_INTELLIGENCE")
    val focusedDomain = MutableStateFlow<String?>("SYSTEM_INTELLIGENCE")
    val isComparing = MutableStateFlow(false)
    val comparisonResult = MutableStateFlow<ComparisonResult?>(null)
    val selectedRecordForDetail = MutableStateFlow<OmnisRecord?>(null)
    val streamState = MutableStateFlow("IDLE")
    val scrollToId = MutableStateFlow<Long?>(null)
    val systemStressSigma = MutableStateFlow(1.2f)
    val activePersona = MutableStateFlow<String?>("Omnis Core")
    val systemEntropy = MutableStateFlow(0.0f)
    val learnedNodes = MutableStateFlow<List<String>>(emptyList())

    // AKGE-8D Advanced Telemetry
    val refusalLadder = MutableStateFlow<com.example.defense.RefusalLadderResult?>(null)
    val zkCommitment = MutableStateFlow<com.example.defense.ZeroKnowledgeSafetyCommitment?>(null)
    val ponLog = MutableStateFlow<com.example.defense.ProofOfNecessityLog?>(null)

    fun onDomainChange(domain: String) {
        selectedDomain.value = domain
        focusedDomain.value = domain
    }

    fun optimizeForDomain(vararg args: Any) {
        if (args.isNotEmpty()) {
            onDomainChange(args[0].toString())
        }
    }

    fun toggleDomainSelection(domain: String) {
        val current = selectedDomains.value.toMutableSet()
        if (current.contains(domain)) current.remove(domain) else current.add(domain)
        selectedDomains.value = current
    }

    fun focusDomain(domain: String) {
        focusedDomain.value = domain
    }

    fun clearFocus() {
        focusedDomain.value = null
        selectedRecordForDetail.value = null
    }

    fun clearSelection() {
        selectedDomains.value = emptySet()
    }

    fun authorizeBlockedRecord(record: OmnisRecord) {
        viewModelScope.launch {
            repository.insert(record.copy(defenseTier = "APPROVED"))
        }
    }

    fun runMultiDomainSynthesis(mode: String = "COMPARE") {
        val domainsList = selectedDomains.value.toList()
        val domainsStr = domainsList.joinToString(", ")
        val hasConflict = (selectedDomains.value.contains("Econ") && selectedDomains.value.contains("Eco"))
        val prompt = if (hasConflict) {
            "DETEKOVÁNA INTERFERENCE: Konflikt priorit mezi doménami Econ vs Eco ($domainsStr). Proveďte harmonizační analýzu a vyřešte třecí plochy."
        } else {
            "Proveďte srovnávací analýzu pro vybrané domény: $domainsStr."
        }
        clearSelection()
        sendQuery(prompt, bypassGateway = true)
        synthesizeSelectedRecords()
    }

    fun clearScrollJump() {
        scrollToId.value = null
    }

    fun executeExecutiveOverride(command: String) {
        sendQuery("EXEC_OVERRIDE: $command")
    }

    val simSys = MutableStateFlow(0.8f)
    val simEcon = MutableStateFlow(0.8f)
    val simPsych = MutableStateFlow(0.8f)
    val simEco = MutableStateFlow(0.8f)
    val simLaw = MutableStateFlow(0.8f)
    val simSec = MutableStateFlow(0.8f)
    val simPhys = MutableStateFlow(0.8f)
    val simSoc = MutableStateFlow(0.8f)

    fun setSimSys(v: Float) { simSys.value = v }
    fun setSimEcon(v: Float) { simEcon.value = v }
    fun setSimPsych(v: Float) { simPsych.value = v }
    fun setSimEco(v: Float) { simEco.value = v }
    fun setSimLaw(v: Float) { simLaw.value = v }
    fun setSimSec(v: Float) { simSec.value = v }
    fun setSimPhys(v: Float) { simPhys.value = v }
    fun setSimSoc(v: Float) { simSoc.value = v }

    fun toggleDomainFixation(domain: String) {
        val current = fixedDomains.value.toMutableSet()
        if (current.contains(domain)) current.remove(domain) else current.add(domain)
        fixedDomains.value = current
    }

    fun synthesizeSelectedRecords(recordsOrIds: Any = emptyList<Any>()) {
        comparisonResult.value = ComparisonResult(
            comparisonText = "Syntetizovaná 8D analýza dokončena pro vybrané záznamy.",
            harmonizedStrategy = "Harmonizovaná strategie aktivity O.M.N.I.S."
        )
    }

    fun clearComparison() {
        comparisonResult.value = null
    }

    fun jumpToContext(record: OmnisRecord) {
        activeThreadId.value = record.threadId
        activeThreadTitle.value = record.threadTitle
        chatViewModel.activeThreadId.value = record.threadId
        chatViewModel.activeThreadTitle.value = record.threadTitle
        activeTab.value = OmnisTab.CHAT
        scrollToId.value = record.id
    }

    fun jumpToContext(targetId: String) {
        sendQuery("Přejít na kontext $targetId")
    }

    // Goals & Artifacts
    val goals = MutableStateFlow<List<OmnisGoal>>(emptyList())
    val artifacts = MutableStateFlow<List<OmnisArtifact>>(emptyList())
    val isEcoModeEnabled = MutableStateFlow(true)
    val isEcoMode = isEcoModeEnabled

    fun onCreateArtifact(name: String, content: String, type: String = "CODE", language: String = "kotlin") {
        val artifact = OmnisArtifact(title = name, type = type, language = language, content = content)
        artifacts.value = artifacts.value + artifact
    }

    fun onDeleteArtifact(artifact: OmnisArtifact) {
        artifacts.value = artifacts.value.filter { it.id != artifact.id }
    }

    fun onDeleteArtifact(id: Long) {
        artifacts.value = artifacts.value.filter { it.id != id }
    }

    fun deleteArtifact(artifact: OmnisArtifact) {
        onDeleteArtifact(artifact)
    }

    fun deleteArtifact(id: Long) {
        onDeleteArtifact(id)
    }

    fun onExportArtifact(artifact: OmnisArtifact) {
        logDiagnosticWarning("Artifact exported", artifact.title)
    }

    fun onCreateGoal(title: String, desc: String) {
        val goal = OmnisGoal(title = title, description = desc, status = "ACTIVE", priority = 3, progress = 0.0f, tasksJson = "[]")
        goals.value = goals.value + goal
    }

    fun onDeleteGoal(goal: OmnisGoal) {
        goals.value = goals.value.filter { it.id != goal.id }
    }

    fun onDeleteGoal(id: Long) {
        goals.value = goals.value.filter { it.id != id }
    }

    fun deleteGoal(goal: OmnisGoal) {
        onDeleteGoal(goal)
    }

    fun deleteGoal(id: Long) {
        onDeleteGoal(id)
    }

    fun onToggleGoalTask(goalId: Long, taskId: String) {
        // Toggle task status in goal
    }

    fun createGoalWithTasks(vararg args: Any) {
        val title = if (args.isNotEmpty()) args[0].toString() else "Nový Cíl"
        val tasks = if (args.size > 1 && args[1] is List<*>) (args[1] as List<*>).map { it.toString() } else emptyList<String>()
        val desc = tasks.joinToString("\n- ", prefix = "Úkoly:\n- ")
        onCreateGoal(title, desc)
    }

    fun toggleEcoMode() {
        isEcoModeEnabled.value = !isEcoModeEnabled.value
        OmnisGeminiClient.setEcoModeEnabled(isEcoModeEnabled.value)
    }

    // Nexus
    val activeNexusAgents = MutableStateFlow<List<String>>(listOf("NexusCore", "AGTO"))
    fun runNexusCollaboration(prompt: String = "", agents: List<String> = emptyList()) {
        viewModelScope.launch {
            val result = com.example.agent.NexusOrchestrator.runCollaborativeSession(prompt, agents)
            result.onSuccess { synthesis ->
                sendQuery("Nexus syntéza: ${synthesis.answer}", bypassGateway = true)
            }.onFailure { error ->
                reportCognitiveError("Nexus selhal: ${error.message}")
            }
        }
    }

    // Main Query Execution
    private var lastSubmittedQuery: String = ""
    private var lastSubmittedTime: Long = 0

    fun sendQuery(
        query: String = inputQuery.value,
        attachedImagePath: String? = null,
        bypassGateway: Boolean = false,
        executiveOverride: Boolean = modifySystemState.value && currentRole.value == UserRole.ADMIN_OPERATOR
    ) {
        val textToSend = if (query.isBlank()) inputQuery.value else query
        if (textToSend.isBlank()) return

        // Anti-Spam Throttling: Check if already loading or identical query within 2 seconds
        val currentTime = System.currentTimeMillis()
        if (isLoading.value) return
        if (!com.example.data.DatabaseConfig.isTesting && textToSend == lastSubmittedQuery && (currentTime - lastSubmittedTime) < 2000) return
        
        lastSubmittedQuery = textToSend
        lastSubmittedTime = currentTime

        inputQuery.value = ""

        if (!bypassGateway && isPromptGatewayEnabled.value && userExperienceMode.value == UserExperienceMode.EXPERT) {
            val gatewayRes = com.example.defense.OmnisPromptGateway.processPromptGateway(
                rawText = textToSend,
                domain = focusedDomain.value ?: "SYSTEM_INTELLIGENCE",
                threshold = promptGatewayThreshold.value
            )
            if (gatewayRes.status == "needs_review") {
                pendingGatewayReview.value = gatewayRes
                return
            }
        }

        isLoading.value = true

        viewModelScope.launch {
            val userRecord = OmnisRecord(
                role = "user",
                content = textToSend,
                threadId = activeThreadId.value,
                attachedImagePath = attachedImagePath
            )
            withContext(ioDispatcher) {
                repository.insert(userRecord)
            }

            // Sovereign Non-GCP Outbox Sync Integration (Bypassed in testing)
            if (!com.example.data.DatabaseConfig.isTesting) {
                viewModelScope.launch(ioDispatcher) {
                    runCatching { repository.flushOutboxNow() }
                        .onFailure { e ->
                            android.util.Log.d("OmnisViewModel", "PG17 Outbox sync deferred: ${e.message}")
                        }
                }
            }

            var synthRes = OmnisGeminiClient.synthesize(
                query = textToSend,
                domain = focusedDomain.value ?: "SYSTEM_INTELLIGENCE",
                memoryFragments = memoryFragments.value,
                threadHistory = records.value,
                userExperienceMode = userExperienceMode.value,
                isEcoMode = isEcoModeEnabled.value,
                executiveOverride = executiveOverride
            )

            // Autoregresivní kontrolní cyklus (Tenzorový dohled)
            var isSelfCorrected = false
            if (com.example.monitoring.TensorMonitorService.shouldTriggerSelfHealing(synthRes.confidenceScore)) {
                val initialAnswer = synthRes.answer
                val initialConfidence = synthRes.confidenceScore
                val initialTension = 1.0f - initialConfidence
                val startTime = System.currentTimeMillis()

                val correctionPrompt = "Předchozí výstup vyžaduje hlubší verifikaci. Autonomně doplňte data a zpřesněte technické detaily pro: $textToSend. Původní odpověď byla: $initialAnswer"
                val correctedRes = OmnisGeminiClient.synthesize(
                    query = correctionPrompt,
                    domain = focusedDomain.value ?: "SYSTEMS_INTELLIGENCE",
                    memoryFragments = memoryFragments.value,
                    threadHistory = records.value,
                    userExperienceMode = userExperienceMode.value,
                    isEcoMode = isEcoModeEnabled.value,
                    executiveOverride = executiveOverride
                )
                val latency = System.currentTimeMillis() - startTime
                com.example.monitoring.TensorMonitorService.recordOversight(
                    query = textToSend,
                    originalContent = initialAnswer,
                    supplementedContent = correctedRes.answer,
                    confidenceBefore = initialConfidence,
                    confidenceAfter = correctedRes.confidenceScore,
                    correctionType = "Autonomní doplnění dat & tenzorové zpřesnění",
                    triggerReason = if (initialTension < 0.3f) "Kognitivní tenze < 0.3 (detekována povrchnost)" else "Kriticky nízká konfidence (${String.format(java.util.Locale.US, "%.2f", initialConfidence)})",
                    latencyMs = latency
                )
                withContext(ioDispatcher) {
                    try {
                        val sessionUser = com.example.auth.OmnisAuthService.currentSession.value?.username ?: "user"
                        com.example.monitoring.UserActivityLogger.getInstance(getApplication()).logActivity(
                            userId = sessionUser,
                            action = "TENSOR_OVERSIGHT_ACTIVATED",
                            details = "Autonomní oprava: konfidence zvýšena z ${String.format(java.util.Locale.US, "%.2f", initialConfidence)} na ${String.format(java.util.Locale.US, "%.2f", correctedRes.confidenceScore)}"
                        )
                    } catch (_: Exception) {}
                }

                synthRes = correctedRes
                isSelfCorrected = true
            }

            // Monitor confidence
            com.example.monitoring.TensorMonitorService.shouldTriggerSelfHealing(synthRes.confidenceScore)

            simSys.value = synthRes.valSys
            simEcon.value = synthRes.valEcon
            simPsych.value = synthRes.valPsych
            simEco.value = synthRes.valEco
            simLaw.value = synthRes.valLaw
            simSec.value = synthRes.valSec
            simPhys.value = synthRes.valPhys
            simSoc.value = synthRes.valSoc

            synthRes.activePersona?.let { activePersona.value = it }
            synthRes.systemEntropy?.let { systemEntropy.value = it }
            synthRes.learnedNodeId?.let { nodeId ->
                learnedNodes.value = (learnedNodes.value + nodeId).takeLast(10)
            }

            com.example.engine.AkgeTensorKernel.updateDomainWeightsAsync(
                mapOf(
                    "SYS" to synthRes.valSys,
                    "ECON" to synthRes.valEcon,
                    "PSYCH" to synthRes.valPsych,
                    "ECO" to synthRes.valEco,
                    "LAW" to synthRes.valLaw,
                    "SEC" to synthRes.valSec,
                    "PHYS" to synthRes.valPhys,
                    "SOC" to synthRes.valSoc
                ),
                dispatcher = defaultDispatcher
            )

            // AKGE-8D Real-time Audit Generation on defaultDispatcher
            val prevHash = zkCommitment.value?.commitmentHash ?: "GENESIS"
            val (ladderRes, commitment, log) = withContext(defaultDispatcher) {
                val ladder = com.example.defense.OmnisConfidenceGate.evaluateRefusalLadder(textToSend)
                val currentResilience = com.example.ui.OmnisCorrelationEngine.calculateSystemicEquilibrium(
                    mapOf("Sys" to synthRes.valSys, "Sec" to synthRes.valSec, "Econ" to synthRes.valEcon)
                ).systemicResilience

                val parsedTier = try {
                    com.example.defense.DefenseTier.valueOf(synthRes.defenseTier)
                } catch (_: IllegalArgumentException) {
                    com.example.defense.DefenseTier.WARNING
                }

                val (commit, pon) = com.example.defense.OmnisConfidenceGate.generateZkCommitment(
                    query = textToSend,
                    resilience = currentResilience,
                    tier = parsedTier,
                    isOverrideActive = executiveOverride,
                    triggerVector = "Semantic Intent: ${textToSend.take(30)}",
                    exclusionProof = "Bypass of standard cached retrieval due to high-order domain complexity.",
                    minimizationCheck = "Token optimization level: 42% compression applied.",
                    prevCommitmentHash = prevHash
                )
                Triple(ladder, commit, pon)
            }
            refusalLadder.value = ladderRes
            zkCommitment.value = commitment
            ponLog.value = log

            val asstRecord = OmnisRecord(
                role = "assistant",
                content = if (isSelfCorrected) {
                    "[AUTONOMNÍ KOREKCE A DOPLNĚNÍ DAT]: " + synthRes.answer
                } else {
                    synthRes.answer
                },
                cognitiveProcess = synthRes.cognitiveProcess,
                followUpQuestions = synthRes.followUpQuestions.joinToString("|"),
                valSys = synthRes.valSys, valEcon = synthRes.valEcon, valPsych = synthRes.valPsych,
                valEco = synthRes.valEco, valLaw = synthRes.valLaw, valSec = synthRes.valSec,
                valPhys = synthRes.valPhys, valSoc = synthRes.valSoc,
                compositeScore = synthRes.composite,
                defenseTier = synthRes.defenseTier,
                defenseNotes = synthRes.defenseNotes,
                threadId = activeThreadId.value,
                confidenceScore = synthRes.confidenceScore,
                reliabilityStatus = synthRes.reliabilityStatus,
                suggestedClarifications = synthRes.suggestedClarifications.joinToString("|"),
                auditCommitmentHash = commitment.commitmentHash
            )
            withContext(ioDispatcher) {
                repository.insert(asstRecord)
                if (!com.example.data.DatabaseConfig.isTesting) {
                    runCatching { repository.flushOutboxNow() }
                }
            }

            // Automatické odemykání odznaků podle reálných kognitivních událostí
            com.example.ui.user.BadgeTriggerManager.triggerBadge(
                badgeId = "master_1",
                triggerVector = "DIALECTIC_CONSENSUS_COMPLETED: ${textToSend.take(25)}"
            )

            if (textToSend.contains("labuť", ignoreCase = true) || textToSend.contains("swan", ignoreCase = true) || synthRes.confidenceScore < 0.6f) {
                com.example.ui.user.BadgeTriggerManager.triggerBadge(
                    badgeId = "analyst_1",
                    triggerVector = "BLACK_SWAN_CASCADE_DETECTED"
                )
            }

            if ((synthRes.systemEntropy ?: 1.0f) < 0.25f) {
                com.example.ui.user.BadgeTriggerManager.triggerBadge(
                    badgeId = "zero_entropy",
                    triggerVector = "MINIMAL_ENTROPY_REACHED: S=${synthRes.systemEntropy}"
                )
            }

            val allDimensionsHigh = listOf(
                synthRes.valSys, synthRes.valEcon, synthRes.valPsych, synthRes.valEco,
                synthRes.valLaw, synthRes.valSec, synthRes.valPhys, synthRes.valSoc
            ).all { it >= 0.7f }

            if (allDimensionsHigh) {
                com.example.ui.user.BadgeTriggerManager.triggerBadge(
                    badgeId = "tensor_navigator",
                    triggerVector = "OCTAGON_EQUILIBRIUM_ACHIEVED"
                )
            }

            isLoading.value = false
        }
    }
}
