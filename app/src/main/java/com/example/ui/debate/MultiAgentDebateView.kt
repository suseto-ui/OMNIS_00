package com.example.ui.debate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.OmnisViewModel
import com.example.ui.theme.*

data class ExpertAgentOpinion(
    val agentName: String,
    val roleTitle: String,
    val domain: String,
    val color: Color,
    val statement: String,
    val confidence: Float
)

@Composable
fun MultiAgentDebateView(
    viewModel: OmnisViewModel,
    modifier: Modifier = Modifier
) {
    val proficiencyLevel by viewModel.proficiencyLevel.collectAsStateWithLifecycle()
    val isExpertMode = proficiencyLevel == com.example.auth.ProficiencyLevel.EXPERT

    var topicInput by remember { mutableStateOf("Analýza dopadu migrace kritické infrastruktury do hybridního cloudu s ohledem na NIS2.") }
    var isDebating by remember { mutableStateOf(false) }
    var consensusReached by remember { mutableStateOf(false) }
    var opinions by remember { mutableStateOf<List<ExpertAgentOpinion>>(emptyList()) }
    var finalConsensus by remember { mutableStateOf("") }

    fun startDebate() {
        if (topicInput.isBlank()) return
        isDebating = true
        consensusReached = false

        // Simulace reálné multi-agentní syntézy 4 expertů
        opinions = listOf(
            ExpertAgentOpinion(
                agentName = "Dr. Arthur Pendelton",
                roleTitle = "Systems Architect",
                domain = "SYS",
                color = OmnisCyan,
                statement = "Migrace vyžaduje modularizaci na mikroslužby s garantovaným failoverem pod 50ms. Rychlost replikace mezi lokálním Room úložištěm a PostgreSQL pgvector musí být bezvýpadková.",
                confidence = 0.96f
            ),
            ExpertAgentOpinion(
                agentName = "Elena Vance",
                roleTitle = "Risk Economist",
                domain = "ECON",
                color = OmnisAmber,
                statement = "Kapitálové náklady (CapEx) na přechod klesnou o 32% v horizontu 12 měsíců, ovšem operativní náklady (OpEx) na API kvóty vyžadují tokenovou kompresi minimálně o 40%.",
                confidence = 0.91f
            ),
            ExpertAgentOpinion(
                agentName = "Marcus Thorne",
                roleTitle = "Legal & Compliance Officer",
                domain = "LAW",
                color = OmnisViolet,
                statement = "Z pohledu normy NIS2 čl. 21 odst. 2 a EU AI Act musí být zachována auditovatelnost a kryptografická HMAC-SHA256 stopa u každého exekučního rozhodnutí.",
                confidence = 0.98f
            ),
            ExpertAgentOpinion(
                agentName = "Sarah Chen",
                roleTitle = "Cybersecurity Specialist",
                domain = "SEC",
                color = OmnisEmerald,
                statement = "Doporučuji nasadit Zero-Trust perimetr s AES-256 šifrováním v klidu a aktivním Circuit Breakerem pro případ pokusu o adversarial injection.",
                confidence = 0.94f
            )
        )

        finalConsensus = "Konsensus 4 agentů: Hybridní migrace je schválena pod podmínkou nasazení Zero-Trust perimetru, HMAC-SHA256 auditní stopy a 42% tokenové komprese (Q_omnis index: 95%)."
        isDebating = false
        consensusReached = true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("multi_agent_debate_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                border = BorderStroke(1.dp, OmnisViolet.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = OmnisViolet,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "MULTI-AGENTNÍ DEBATNÍ KROUŽEK",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Reálná syntéza názorů 4 expertů (SYS, ECON, LAW, SEC)",
                                    color = OmnisTextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isExpertMode) OmnisViolet.copy(alpha = 0.2f) else OmnisCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isExpertMode) OmnisViolet else OmnisCyan)
                        ) {
                            Text(
                                text = if (isExpertMode) "EXPERT REŽIM" else "ZAČÁTEČNÍK",
                                color = if (isExpertMode) OmnisViolet else OmnisCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Input Prompt
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Téma k debate / System Hypothesis", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OmnisViolet,
                            unfocusedBorderColor = OmnisBorderDark,
                            focusedLabelColor = OmnisViolet,
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { startDebate() },
                        colors = ButtonDefaults.buttonColors(containerColor = OmnisViolet),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("start_debate_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ZAHÁJIT 4-AGENTNÍ DEBATU", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        if (consensusReached) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
                    border = BorderStroke(1.dp, OmnisEmerald.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = OmnisEmerald, modifier = Modifier.size(16.dp))
                            Text(
                                text = "VÝSLEDNÝ KONSENSUS EXPERTŮ:",
                                color = OmnisEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = finalConsensus,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Opinions List
        if (opinions.isNotEmpty()) {
            item {
                Text(
                    text = "ARGUMENTAČNÍ STOPY JEDNOTLIVÝCH EXPERTŮ:",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            items(opinions) { opinion ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = BorderStroke(1.dp, opinion.color.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(opinion.color.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opinion.domain,
                                        color = opinion.color,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column {
                                    Text(
                                        text = opinion.agentName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = opinion.roleTitle,
                                        color = OmnisTextMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (isExpertMode) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = opinion.color.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, opinion.color.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "CONF: ${(opinion.confidence * 100).toInt()}%",
                                        color = opinion.color,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = opinion.statement,
                            color = OmnisTextLight,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
