package com.example.ui.dialectics

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dialectics.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class ArenaMode {
    FOUR_AGENTS,
    TWO_AGENTS
}

@Composable
fun MultiAgentArenaView(
    viewModel: ArenaViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onSendToChat: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val currentArenaMode by viewModel.currentArenaMode.collectAsState()
    
    // 4-Agent Debate State
    val fourAgentProblemText by viewModel.fourAgentProblemText.collectAsState()
    val isDebating4A by viewModel.isDebating4A.collectAsState()
    val liveTurns4A by viewModel.liveTurns4A.collectAsState()
    val fourAgentResult by viewModel.fourAgentResult.collectAsState()

    // 2-Agent Duel State
    val selectedAgentA by viewModel.selectedAgentA.collectAsState()
    val selectedAgentB by viewModel.selectedAgentB.collectAsState()
    val problemText2A by viewModel.problemText2A.collectAsState()
    val isDebating2A by viewModel.isDebating2A.collectAsState()
    val debateResult2A by viewModel.debateResult2A.collectAsState()
    val liveTurns2A by viewModel.liveTurns2A.collectAsState()
    val comparePair by viewModel.comparePair.collectAsState()
    val overrideFlagInput by viewModel.overrideFlagInput.collectAsState()
    val showOverrideConfirmDialog by viewModel.showOverrideConfirmDialog.collectAsState()
    val historyList2A by viewModel.historyList2A.collectAsState()
    
    var showAgentASelector by remember { mutableStateOf(false) }
    var showAgentBSelector by remember { mutableStateOf(false) }

    fun startFourAgentDebate() {
        viewModel.startFourAgentDebate(fourAgentProblemText)
    }

    fun startTwoAgentDebate() {
        viewModel.startTwoAgentDebate(problemText2A, selectedAgentA, selectedAgentB)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(14.dp)
            .testTag("multi_agent_arena_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Title Banner with Arena Mode Switcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                border = BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OmnisCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = "Multi-Agent Arena",
                                    tint = OmnisCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "MULTI-AGENTNÍ DEBATA",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = if (currentArenaMode == ArenaMode.FOUR_AGENTS)
                                        "4 Agenti (Architekt, Skeptik, Regulátor, Inženýr) • Veto & Override"
                                    else
                                        "2 Agenti • Přímý dialektický duel",
                                    color = OmnisTextLight,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Switch Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentArenaMode == ArenaMode.FOUR_AGENTS) OmnisCyan else OmnisBgDark,
                            border = BorderStroke(1.dp, OmnisCyan),
                            modifier = Modifier
                                .weight(1.3f)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setArenaMode(ArenaMode.FOUR_AGENTS)
                                }
                        ) {
                            Text(
                                text = "4 AGENTI (KONSENZUS)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (currentArenaMode == ArenaMode.FOUR_AGENTS) Color.Black else OmnisCyan,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentArenaMode == ArenaMode.TWO_AGENTS) OmnisViolet else OmnisBgDark,
                            border = BorderStroke(1.dp, OmnisViolet),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setArenaMode(ArenaMode.TWO_AGENTS)
                                }
                        ) {
                            Text(
                                text = "2 AGENTI (DUEL)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (currentArenaMode == ArenaMode.TWO_AGENTS) Color.White else OmnisViolet,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // ==================== MODE 1: 4-AGENT CONCENSUS ====================
        if (currentArenaMode == ArenaMode.FOUR_AGENTS) {
            // Canonical 4 Agents Overview Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                    border = BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "KOGNITIVNÍ RADY 4 AGENTŮ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OmnisCyan,
                            fontSize = 11.5.sp
                        )

                        // 4 Agents Grid / Rows
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AgentArenaLibrary.canonicalFourAgents.forEach { agent ->
                                val color = Color(agent.colorHex)
                                val hasVeto = agent.id == "regulator"

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = OmnisBgDark,
                                    border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
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
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(color.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = agent.name.take(1),
                                                    color = color,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = agent.name,
                                                        color = color,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    if (hasVeto) {
                                                        Surface(
                                                            shape = RoundedCornerShape(3.dp),
                                                            color = Color.Red.copy(alpha = 0.25f),
                                                            border = BorderStroke(0.5.dp, Color.Red)
                                                        ) {
                                                            Text(
                                                                text = "PRÁVO VETA",
                                                                color = Color.Red,
                                                                fontSize = 7.5.sp,
                                                                fontWeight = FontWeight.Black,
                                                                fontFamily = FontFamily.Monospace,
                                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = agent.roleTitle,
                                                    color = OmnisTextLight,
                                                    fontSize = 9.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            agent.primaryDomains.forEach { dom ->
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = color.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = dom,
                                                        color = color,
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Problem Input
                        Text(
                            text = "TÉMA DEBATY NEBO SYSTÉMOVÝ ZÁMĚR:",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OmnisTextLight,
                            fontSize = 10.5.sp
                        )

                        OutlinedTextField(
                            value = fourAgentProblemText,
                            onValueChange = { viewModel.setFourAgentProblemText(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OmnisCyan,
                                unfocusedBorderColor = OmnisBorderDark,
                                focusedContainerColor = OmnisBgDark,
                                unfocusedContainerColor = OmnisBgDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 2
                        )

                        // Quick presets chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AgentArenaLibrary.presetDebateTopics.forEach { topic ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = OmnisBgDark,
                                    border = BorderStroke(0.5.dp, OmnisBorderDark),
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.setFourAgentProblemText(topic)
                                    }
                                ) {
                                    Text(
                                        text = topic.take(28) + "...",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OmnisTextLight,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Launch Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                startFourAgentDebate()
                            },
                            enabled = !isDebating4A && fourAgentProblemText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isDebating4A) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PROBÍHÁ DEBATA 4 AGENTŮ...", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SPUSTIT DEBATU 4 AGENTŮ", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // Live Turns Stream (4 Agents)
            if (liveTurns4A.isNotEmpty()) {
                item {
                    Text(
                        text = "💬 PRŮBĚH DEBATY A NÁMITEK",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = OmnisCyan,
                        fontSize = 12.sp
                    )
                }

                items(liveTurns4A) { turn ->
                    val agentArchetype = AgentArenaLibrary.canonicalFourAgents.find { it.id == turn.agentId }
                    val color = Color(agentArchetype?.colorHex ?: 0xFF00E5FF)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = turn.agentName.take(1), fontWeight = FontWeight.Bold, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Text(text = turn.agentName, fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                                Text(text = turn.roundName, color = OmnisTextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = turn.argumentText, color = Color.White, fontSize = 11.5.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            // Voting Result & Executive Override Board
            fourAgentResult?.let { result ->
                // Voting Matrix Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                        border = BorderStroke(1.dp, OmnisCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🗳️ HLASOVACÍ PROTOKOL AGENTŮ",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = OmnisCyan,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "KONSENZUS: ${(result.consensusIndex * 100).toInt()}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (result.isVetoActive && !result.isOverrideActive) Color.Red else OmnisEmerald,
                                    fontSize = 11.sp
                                )
                            }

                            // Votes rows
                            result.votes.forEach { vote ->
                                val (badgeColor, badgeText) = when (vote.voteType) {
                                    VoteType.APPROVE -> Pair(OmnisEmerald, "SCHVÁLENO")
                                    VoteType.CONDITIONAL -> Pair(OmnisAmber, "S VÝHRADOU")
                                    VoteType.VETO -> Pair(Color.Red, "VETO")
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = OmnisBgDark,
                                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = vote.agentName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = badgeColor.copy(alpha = 0.2f),
                                                border = BorderStroke(0.5.dp, badgeColor)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    color = badgeColor,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(text = vote.justification, color = OmnisTextLight, fontSize = 10.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // VETO / OVERRIDE Banner
                item {
                    if (result.isVetoActive && !result.isOverrideActive) {
                        // VETO BLOCKED
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                            border = BorderStroke(1.5.dp, Color.Red),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("veto_blocked_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "VETO AGENTA REGULÁTORA AKTIVNÍ",
                                        color = Color.Red,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "Zápis do systému O.M.N.I.S. byl zablokován pro nesoulad s normou (EU AI Act čl. 14 / NIS2). Pro provedení změn v produkčním stavu musí ADMIN_OPERATOR zadat flag 'MODIFY_SYSTEM_STATE'.",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp
                                )

                                OutlinedTextField(
                                    value = overrideFlagInput,
                                    onValueChange = { viewModel.setOverrideFlagInput(it) },
                                    placeholder = { Text("Zadejte flag: MODIFY_SYSTEM_STATE", color = Color.Gray, fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Red,
                                        unfocusedBorderColor = OmnisBorderDark,
                                        focusedContainerColor = OmnisBgDark,
                                        unfocusedContainerColor = OmnisBgDark
                                    ),
                                    singleLine = true
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                    onClick = { viewModel.setOverrideFlagInput("MODIFY_SYSTEM_STATE") },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, OmnisCyan),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("Předvyplnit flag", color = OmnisCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }

                                    Button(
                                        onClick = { viewModel.setShowOverrideConfirmDialog(true) },
                                        enabled = overrideFlagInput.trim() == "MODIFY_SYSTEM_STATE",
                                        modifier = Modifier.weight(1.3f).height(34.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("EXECUTIVE OVERRIDE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    } else if (result.isOverrideActive) {
                        // OVERRIDE_ACTIVE Banner
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OmnisCardDark,
                            border = BorderStroke(1.dp, OmnisViolet),
                            modifier = Modifier.fillMaxWidth().testTag("override_active_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OmnisViolet, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "OVERRIDE_ACTIVE (VETO PŘEHLASOVÁNO)",
                                        color = OmnisViolet,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = result.auditLogEntry,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        // FULL CONSENSUS ACHIEVED
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OmnisCardDark,
                            border = BorderStroke(1.dp, OmnisEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OmnisEmerald, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "PLNÝ KONSENZUS: Všech 4 kognitivních agentů schválilo harmonizaci tenzorového pole.",
                                    color = OmnisEmerald,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // 8D Synthesized Scores Grid
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                        border = BorderStroke(0.5.dp, OmnisBorderDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "8D SYNTETIZOVANÉ HODNOTY DO KERNELU:",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = OmnisCyan,
                                fontSize = 11.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                result.synthesized8DScores.forEach { (dom, score) ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = OmnisBgDark,
                                        border = BorderStroke(0.5.dp, OmnisCyan.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "$dom: ${String.format("%.2f", score)}",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Actionable guidelines
                            result.actionableGuidelines.forEach { g ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("•", color = OmnisCyan, fontSize = 10.sp)
                                    Text(g, color = OmnisTextLight, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Send to Chat Button
                            OutlinedButton(
                                onClick = {
                                    onSendToChat("4-Agentní Debata u tématu '${result.problemStatement}':\n${result.synthesisSummary}\nAudit: ${result.auditLogEntry}")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, OmnisCyan),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = OmnisCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Odeslat syntézu do chatu O.M.N.I.S.", color = OmnisCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // ==================== MODE 2: 2-AGENT DUEL ====================
        if (currentArenaMode == ArenaMode.TWO_AGENTS) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                    border = BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "VÝBĚR PROTICHŮDNÝCH AGENTŮ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OmnisCyan,
                            fontSize = 11.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AgentCardButton(label = "AGENT A (TEZE)", agent = selectedAgentA, onClick = { showAgentASelector = true })
                            }
                            Text(text = "VS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.Red, fontSize = 14.sp)
                            Box(modifier = Modifier.weight(1f)) {
                                AgentCardButton(label = "AGENT B (ANTITEZE)", agent = selectedAgentB, onClick = { showAgentBSelector = true })
                            }
                        }

                        OutlinedTextField(
                            value = problemText2A,
                            onValueChange = { viewModel.setProblemText2A(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OmnisCyan,
                                unfocusedBorderColor = OmnisBorderDark,
                                focusedContainerColor = OmnisBgDark,
                                unfocusedContainerColor = OmnisBgDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 2
                        )

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                startTwoAgentDebate()
                            },
                            enabled = !isDebating2A && problemText2A.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OmnisViolet, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isDebating2A) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PROBÍHÁ DUEL AGENTŮ...", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SPUSTIT 2-AGENTNÍ DUEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Live 2-Agent turns
            if (liveTurns2A.isNotEmpty()) {
                items(liveTurns2A) { turn ->
                    val isAgentA = turn.agentId == selectedAgentA.id
                    val color = Color(if (isAgentA) selectedAgentA.colorHex else selectedAgentB.colorHex)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "${turn.agentName} (${turn.roundName})", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = turn.argumentText, color = Color.White, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }

            // 2-Agent History
            item {
                ArenaHistoryPanel(
                    historyList = historyList2A,
                    onLoadDebate = { debate ->
                        viewModel.setSelectedAgentA(debate.agentA)
                        viewModel.setSelectedAgentB(debate.agentB)
                        viewModel.setProblemText2A(debate.problemStatement)
                        viewModel.setLiveTurns2A(debate.turns)
                        viewModel.setDebateResult2A(debate)
                    },
                    onCompareDebates = { debate1, debate2 ->
                        viewModel.setComparePair(Pair(debate1, debate2))
                    }
                )
            }
        }
    }

    // Confirmation Dialog for Executive Override
    if (showOverrideConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowOverrideConfirmDialog(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                    Text("Potvrzení Executive Override", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            text = {
                Text(
                    text = "Opravdu si přejete přehlasovat Veto Agenta Regulátora? Tato akce nevratně zapíše značku 'OVERRIDE_ACTIVE' do kryptografického auditního logu O.M.N.I.S. a aplikuje syntetizované 8D hodnoty do systémového kernelu.",
                    fontSize = 11.sp,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setShowOverrideConfirmDialog(false)
                        fourAgentResult?.let { res ->
                            viewModel.applyExecutiveOverride(res.debateId, "MODIFY_SYSTEM_STATE")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text("PŘEHLASOVAT VETO", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowOverrideConfirmDialog(false) }) {
                    Text("ZRUŠIT", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = OmnisCardDark
        )
    }

    // Comparison Modal Dialog
    comparePair?.let { pair ->
        Arena8DComparisonModal(
            debateA = pair.first,
            debateB = pair.second,
            onDismiss = { viewModel.setComparePair(null) }
        )
    }

    // Agent Selection Dialogs (for 2-Agent Duel)
    if (showAgentASelector) {
        AgentSelectionDialog(
            title = "ZVOLTE AGENTA A (TEZE)",
            selectedAgent = selectedAgentA,
            onAgentSelected = {
                viewModel.setSelectedAgentA(it)
                showAgentASelector = false
            },
            onDismiss = { showAgentASelector = false }
        )
    }

    if (showAgentBSelector) {
        AgentSelectionDialog(
            title = "ZVOLTE AGENTA B (ANTITEZE)",
            selectedAgent = selectedAgentB,
            onAgentSelected = {
                viewModel.setSelectedAgentB(it)
                showAgentBSelector = false
            },
            onDismiss = { showAgentBSelector = false }
        )
    }
}

@Composable
private fun AgentCardButton(
    label: String,
    agent: AgentArchetype,
    onClick: () -> Unit
) {
    val agentColor = Color(agent.colorHex)

    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisBgDark),
        border = BorderStroke(1.dp, agentColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, color = OmnisTextLight, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
            Text(text = agent.name, color = agentColor, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
            Text(text = agent.roleTitle, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AgentSelectionDialog(
    title: String,
    selectedAgent: AgentArchetype,
    onAgentSelected: (AgentArchetype) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OmnisCardDark,
        title = {
            Text(text = title, color = OmnisCyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentArenaLibrary.presetArchetypes.forEach { archetype ->
                    val color = Color(archetype.colorHex)
                    val isSelected = archetype.id == selectedAgent.id

                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) color.copy(alpha = 0.2f) else OmnisBgDark),
                        border = BorderStroke(1.dp, if (isSelected) color else OmnisBorderDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onAgentSelected(archetype) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = archetype.name, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(text = archetype.roleTitle, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ZRUŠIT", color = OmnisTextLight, fontFamily = FontFamily.Monospace)
            }
        }
    )
}
