package com.example.ui.telemetry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.TestSemanticChatView
import com.example.api.OmnisGeminiClient
import com.example.auth.UserRole
import com.example.data.OmnisRecord
import com.example.gateway.OmnisCircuitBreaker
import com.example.ui.CognitiveNodesView
import com.example.ui.OctagonDashboard
import com.example.ui.OmnisTab
import com.example.ui.OmnisViewModel
import com.example.ui.admin.DevPromptLab
import com.example.ui.apex.ApexTelemetryDashboardView
import com.example.ui.audit.ProductionAuditView
import com.example.ui.audit.TensorOversightView
import com.example.ui.causal.CausalSimulatorView
import com.example.ui.components.SyncLogScreen
import com.example.ui.debate.MultiAgentDebateView
import com.example.ui.dialectics.DialecticEngineView
import com.example.ui.theme.*

/**
 * Sjednocený diagnostický a analytický hub (Core / Telemetry).
 * Soustřeďuje živou telemetrii kaskády modelů, jističe, red-teaming, audit
 * i 8D kauzální moduly na jedno místo, aby běžné denní rozhraní zůstalo čisté.
 */
@Composable
fun CoreTelemetryHubView(
    viewModel: OmnisViewModel,
    userRole: UserRole,
    records: List<OmnisRecord>,
    isLoading: Boolean,
    inputQuery: String,
    modifier: Modifier = Modifier
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var telemetrySubTool by rememberSaveable { mutableIntStateOf(0) }
    var redTeamSubTool by rememberSaveable { mutableIntStateOf(0) }
    var matrixSubTool by rememberSaveable { mutableIntStateOf(0) }

    val isAdmin = userRole == UserRole.ADMIN_OPERATOR

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .testTag("core_telemetry_hub")
    ) {
        // Hlavní 3-sekční přepínač (Telemetrie & Jističe / Red-Teaming & Audit / 8D & Kauzální analýza)
        val sections = listOf(
            Triple(0, "Telemetrie & Jističe", Icons.Default.Speed),
            Triple(1, "Red-Teaming & Audit", Icons.Default.Shield),
            Triple(2, "8D & Kauzální analýza", Icons.Default.Analytics)
        )

        Surface(
            color = OmnisPanelDark,
            border = BorderStroke(1.dp, OmnisBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { (index, label, icon) ->
                    val isSelected = selectedSection == index
                    val accent = when (index) {
                        0 -> OmnisCyan
                        1 -> OmnisViolet
                        else -> OmnisEmerald
                    }
                    HubFilterChip(
                        label = label,
                        icon = icon,
                        selected = isSelected,
                        accentColor = accent,
                        testTag = "core_hub_section_$index",
                        onClick = { selectedSection = index }
                    )
                }
            }
        }

        when (selectedSection) {
            0 -> {
                val telemetryLogs by viewModel.telemetry.collectAsStateWithLifecycle()
                Column(modifier = Modifier.fillMaxSize()) {
                    LiveCascadeTelemetryCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HubSubToolChip(
                            label = "Systémové logy",
                            selected = telemetrySubTool == 0,
                            accent = OmnisCyan,
                            testTag = "core_sub_telemetry_logs",
                            onClick = { telemetrySubTool = 0 }
                        )
                        HubSubToolChip(
                            label = "APEX Metriky",
                            selected = telemetrySubTool == 1,
                            accent = OmnisCyan,
                            testTag = "core_sub_apex_metrics",
                            onClick = { telemetrySubTool = 1 }
                        )
                        HubSubToolChip(
                            label = "Sync Logy",
                            selected = telemetrySubTool == 2,
                            accent = OmnisEmerald,
                            testTag = "core_sub_sync_logs",
                            onClick = { telemetrySubTool = 2 }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (telemetrySubTool) {
                            0 -> TelemetryDashboardView(
                                logs = telemetryLogs,
                                onClearLogs = viewModel::onClearTelemetry
                            )
                            1 -> ApexTelemetryDashboardView(
                                viewModel = viewModel,
                                records = records
                            )
                            else -> SyncLogScreen(
                                onBack = { telemetrySubTool = 0 }
                            )
                        }
                    }
                }
            }

            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HubSubToolChip(
                            label = "Dialektika",
                            selected = redTeamSubTool == 0,
                            accent = OmnisViolet,
                            testTag = "core_sub_dialectics",
                            onClick = { redTeamSubTool = 0 }
                        )
                        HubSubToolChip(
                            label = "Multi-Agent Debata",
                            selected = redTeamSubTool == 1,
                            accent = OmnisCyan,
                            testTag = "core_sub_multi_agent",
                            onClick = { redTeamSubTool = 1 }
                        )
                        if (isAdmin) {
                            HubSubToolChip(
                                label = "Produkční Audit",
                                selected = redTeamSubTool == 2,
                                accent = OmnisEmerald,
                                testTag = "core_sub_prod_audit",
                                onClick = { redTeamSubTool = 2 }
                            )
                            HubSubToolChip(
                                label = "Dev Prompt Lab",
                                selected = redTeamSubTool == 3,
                                accent = OmnisAmber,
                                testTag = "core_sub_dev_prompt_lab",
                                onClick = { redTeamSubTool = 3 }
                            )
                            HubSubToolChip(
                                label = "Sémantický Test",
                                selected = redTeamSubTool == 4,
                                accent = OmnisCyan,
                                testTag = "core_sub_semantic_test",
                                onClick = { redTeamSubTool = 4 }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val effectiveTool = if (!isAdmin && redTeamSubTool > 1) 0 else redTeamSubTool
                        when (effectiveTool) {
                            0 -> DialecticEngineView(
                                onSendToChat = { prompt ->
                                    viewModel.onQueryChange(prompt)
                                    viewModel.setTab(OmnisTab.CHAT)
                                    viewModel.sendQuery(prompt)
                                }
                            )
                            1 -> MultiAgentDebateView(viewModel = viewModel)
                            2 -> ProductionAuditView(
                                onPruneOldData = {
                                    viewModel.pruneOldDatabaseRecords(daysOld = 14)
                                }
                            )
                            3 -> {
                                val telemetryLogs by viewModel.telemetry.collectAsStateWithLifecycle()
                                val llmAnalysisResult by viewModel.llmAnalysisResult.collectAsStateWithLifecycle()
                                val isAnalyzingError by viewModel.isAnalyzingError.collectAsStateWithLifecycle()
                                val isPromptGatewayEnabled by viewModel.isPromptGatewayEnabled.collectAsStateWithLifecycle()
                                val promptGatewayThreshold by viewModel.promptGatewayThreshold.collectAsStateWithLifecycle()
                                DevPromptLab(
                                    telemetryLogs = telemetryLogs,
                                    llmAnalysisResult = llmAnalysisResult,
                                    isAnalyzingError = isAnalyzingError,
                                    isPromptGatewayEnabled = isPromptGatewayEnabled,
                                    promptGatewayThreshold = promptGatewayThreshold,
                                    onTogglePromptGateway = viewModel::togglePromptGateway,
                                    onThresholdChange = viewModel::setPromptGatewayThreshold,
                                    onClearTelemetry = viewModel::onClearTelemetry,
                                    onSimulateDiagnosticError = viewModel::logDiagnosticWarning,
                                    onAnalyzeErrorWithLlm = viewModel::analyzeErrorWithLlm,
                                    onClearLlmAnalysis = viewModel::clearLlmErrorAnalysis
                                )
                            }
                            else -> TestSemanticChatView(
                                records = emptyList(),
                                isLoading = isLoading,
                                inputQuery = inputQuery,
                                onQueryChange = viewModel::onQueryChange,
                                onSend = { viewModel.sendQuery() },
                                onWeightChange = { _, _, _, _ -> },
                                onReSynthesize = { _, _ -> }
                            )
                        }
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HubSubToolChip(
                            label = "8D Matice",
                            selected = matrixSubTool == 0,
                            accent = OmnisViolet,
                            testTag = "core_sub_8d_matrix",
                            onClick = { matrixSubTool = 0 }
                        )
                        HubSubToolChip(
                            label = "Kauzální Simulátor",
                            selected = matrixSubTool == 1,
                            accent = OmnisCyan,
                            testTag = "core_sub_causal",
                            onClick = { matrixSubTool = 1 }
                        )
                        HubSubToolChip(
                            label = "Tenzorový Dohled",
                            selected = matrixSubTool == 2,
                            accent = OmnisEmerald,
                            testTag = "core_sub_tensor_oversight",
                            onClick = { matrixSubTool = 2 }
                        )
                        if (isAdmin) {
                            HubSubToolChip(
                                label = "Kognitivní Uzly",
                                selected = matrixSubTool == 3,
                                accent = OmnisAmber,
                                testTag = "core_sub_cognitive_nodes",
                                onClick = { matrixSubTool = 3 }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val effectiveMatrix = if (!isAdmin && matrixSubTool > 2) 0 else matrixSubTool
                        when (effectiveMatrix) {
                            0 -> {
                                val fixedDomains by viewModel.fixedDomains.collectAsStateWithLifecycle()
                                val isComparing by viewModel.isComparing.collectAsStateWithLifecycle()
                                val comparisonResult by viewModel.comparisonResult.collectAsStateWithLifecycle()
                                OctagonDashboard(
                                    records = records,
                                    latestRecord = records.lastOrNull { it.role == "assistant" },
                                    simSys = viewModel.simSys.collectAsStateWithLifecycle().value,
                                    simEcon = viewModel.simEcon.collectAsStateWithLifecycle().value,
                                    simPsych = viewModel.simPsych.collectAsStateWithLifecycle().value,
                                    simEco = viewModel.simEco.collectAsStateWithLifecycle().value,
                                    simLaw = viewModel.simLaw.collectAsStateWithLifecycle().value,
                                    simSec = viewModel.simSec.collectAsStateWithLifecycle().value,
                                    simPhys = viewModel.simPhys.collectAsStateWithLifecycle().value,
                                    simSoc = viewModel.simSoc.collectAsStateWithLifecycle().value,
                                    fixedDomains = fixedDomains,
                                    onToggleFix = viewModel::toggleDomainFixation,
                                    onSimChange = { sys, econ, psych, eco, law, sec, phys, soc ->
                                        viewModel.setSimSys(sys)
                                        viewModel.setSimEcon(econ)
                                        viewModel.setSimPsych(psych)
                                        viewModel.setSimEco(eco)
                                        viewModel.setSimLaw(law)
                                        viewModel.setSimSec(sec)
                                        viewModel.setSimPhys(phys)
                                        viewModel.setSimSoc(soc)
                                    },
                                    isComparing = isComparing,
                                    comparisonResult = comparisonResult,
                                    onSynthesize = { selectedIds -> viewModel.synthesizeSelectedRecords(selectedIds) },
                                    onClearComparison = { viewModel.clearComparison() },
                                    onDirectMitigate = { prompt ->
                                        viewModel.setTab(OmnisTab.CHAT)
                                        viewModel.sendQuery(prompt)
                                    },
                                    onCreateCompensatoryGoal = viewModel::createGoalWithTasks
                                )
                            }
                            1 -> CausalSimulatorView(
                                baselineRecord = records.lastOrNull { it.role == "assistant" },
                                onSaveArtifact = viewModel::onCreateArtifact,
                                onCreateGoal = viewModel::onCreateGoal,
                                onSendToChat = { prompt ->
                                    viewModel.onQueryChange(prompt)
                                    viewModel.setTab(OmnisTab.CHAT)
                                    viewModel.sendQuery(prompt)
                                }
                            )
                            2 -> TensorOversightView(
                                viewModel = viewModel,
                                onSendToChat = { prompt ->
                                    viewModel.onQueryChange(prompt)
                                    viewModel.setTab(OmnisTab.CHAT)
                                    viewModel.sendQuery(prompt)
                                }
                            )
                            else -> {
                                val learnedNodes by viewModel.learnedNodes.collectAsStateWithLifecycle()
                                CognitiveNodesView(
                                    records = records,
                                    learnedNodes = learnedNodes,
                                    viewModel = viewModel,
                                    onExecuteAction = { payload ->
                                        viewModel.setTab(OmnisTab.CHAT)
                                        viewModel.executeActionPayload(payload)
                                    },
                                    onNavigateToChat = { prompt ->
                                        viewModel.setTab(OmnisTab.CHAT)
                                        viewModel.sendQuery(prompt)
                                    },
                                    onSaveArtifact = viewModel::onCreateArtifact,
                                    onPurgeSyncedRecords = { count -> viewModel.purgeSyncedLocalRecords(count) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCascadeTelemetryCard(modifier: Modifier = Modifier) {
    val activeModel by OmnisGeminiClient.activeModel.collectAsStateWithLifecycle()
    val activeKeyName by OmnisGeminiClient.activeKeyName.collectAsStateWithLifecycle()
    val rotationStats by OmnisGeminiClient.rotationStats.collectAsStateWithLifecycle()
    val isEcoMode by OmnisGeminiClient.isEcoModeEnabled.collectAsStateWithLifecycle()
    val switchLogs by OmnisGeminiClient.modelSwitchLog.collectAsStateWithLifecycle()
    val keyPoolLogs by OmnisGeminiClient.keyPoolLog.collectAsStateWithLifecycle()
    val lastError by OmnisGeminiClient.lastOnlineError.collectAsStateWithLifecycle()
    val breaker by OmnisCircuitBreaker.telemetry.collectAsStateWithLifecycle()

    val isDegraded = breaker.isDegraded || !lastError.isNullOrBlank()
    val statusColor = if (isDegraded) OmnisAmber else OmnisEmerald

    Surface(
        modifier = modifier.testTag("live_cascade_telemetry_card"),
        shape = RoundedCornerShape(12.dp),
        color = OmnisPanelDark,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Živá kaskáda modelů",
                        tint = OmnisCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AI KASKÁDA: $activeModel",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = (if (isEcoMode) OmnisEmerald else OmnisViolet).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, if (isEcoMode) OmnisEmerald else OmnisViolet)
                    ) {
                        Text(
                            text = if (isEcoMode) "ECO" else "FULL",
                            color = if (isEcoMode) OmnisEmerald else OmnisViolet,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                if (breaker.isDegraded) {
                    OutlinedButton(
                        onClick = { OmnisCircuitBreaker.reset() },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("core_hub_breaker_reset"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, OmnisEmerald)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resetovat jistič",
                            tint = OmnisEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", color = OmnisEmerald, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Text(
                text = "Zdroj: $activeKeyName • Breaker: ${breaker.state.name} (Selhání: ${breaker.consecutiveFailures})",
                color = OmnisCyan,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = rotationStats,
                color = OmnisTextLight,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!lastError.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Poslední chyba kaskády",
                        tint = OmnisAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = lastError.orEmpty(),
                        color = OmnisAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val recentLines = (switchLogs.takeLast(2) + keyPoolLogs.takeLast(2)).takeLast(3)
            if (recentLines.isNotEmpty()) {
                recentLines.forEach { entry ->
                    Text(
                        text = "• $entry",
                        color = OmnisTextMuted,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HubFilterChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.20f) else OmnisBgDark,
        border = BorderStroke(1.dp, if (selected) accentColor else OmnisBorderDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accentColor else OmnisTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (selected) Color.White else OmnisTextMuted,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HubSubToolChip(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else OmnisPanelDark,
        border = BorderStroke(1.dp, if (selected) accent else OmnisBorderDark)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = if (selected) accent else OmnisTextLight,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
