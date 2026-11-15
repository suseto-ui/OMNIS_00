package com.example.ui.apex

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
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
import com.example.engine.ApexExecutionStep
import com.example.engine.ApexOrchestratorEngine
import com.example.ui.theme.*

@Composable
fun ApexOrchestratorView(
    onRunApexQuery: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var queryInput by remember {
        mutableStateOf("Syntéza kognitivního enginu a rozhraní NIS2 pro automatizované nasazení")
    }
    var rawInputText by remember { mutableStateOf(queryInput) }

    val apexResult = remember(rawInputText) {
        ApexOrchestratorEngine.executeMasterProtocol(rawInputText)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("apex_orchestrator_container")
    ) {
        // Header Row
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
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = OmnisCyan,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "SIGMA-OMEGA APEX ORCHESTRATOR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "AUTONOMNÍ TRANSDISCIPLINÁRNÍ EXEKUČNÍ ENGEL",
                        color = OmnisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (apexResult.isZeroFluffCompliant) OmnisEmerald.copy(alpha = 0.15f) else OmnisAmber.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (apexResult.isZeroFluffCompliant) OmnisEmerald else OmnisAmber),
                modifier = Modifier.testTag("zero_fluff_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = if (apexResult.isZeroFluffCompliant) OmnisEmerald else OmnisAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (apexResult.isZeroFluffCompliant) "ZERO-FLUFF NORMA" else "NON-COMPLIANT",
                        color = if (apexResult.isZeroFluffCompliant) OmnisEmerald else OmnisAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Query Scratchpad Input Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ZADÁNÍ PRO APEX KŘÍŽOVOU FÚZI",
                            color = OmnisCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = queryInput,
                            onValueChange = { queryInput = it },
                            label = { Text("Zadejte zadání pro APEX Master Protokol", color = OmnisTextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("apex_query_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = OmnisCyan,
                                unfocusedBorderColor = OmnisBorderDark,
                                focusedContainerColor = OmnisBgDark,
                                unfocusedContainerColor = OmnisBgDark
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                rawInputText = queryInput
                                onRunApexQuery(queryInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("run_apex_execution_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SPUSTIT 4-KROKOVÝ APEX PROTOKOL",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 2. 4-Step Execution Protocol Progress Tracker
            item {
                Text(
                    text = "4-KROKOVÝ EXEKUČNÍ PROTOKOL APEX:",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            items(apexResult.steps) { step ->
                ApexStepCard(step = step)
            }

            // 3. Final Deterministic Artifact Result
            item {
                Text(
                    text = "DETERMINISTICKÝ EXEKUČNÍ ARTEFAKT (ZERO-FLUFF SANITIZED):",
                    color = OmnisEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PŘÍMÝ VYSTUPOVÝ ARTEFAKT (BEZ VATY)",
                                color = OmnisEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Resilience: ${(apexResult.adversarialResilienceScore * 100).toInt()}%",
                                color = OmnisCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = apexResult.finalArtifact,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // 4. Unified Response Quality Evaluator Q_omnis Index Card
            item {
                val evalScore = remember(rawInputText, apexResult) {
                    com.example.engine.UnifiedResponseEvaluator.evaluateResponse(rawInputText, apexResult.finalArtifact)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("q_omnis_evaluator_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = OmnisCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "JEDNOTNÝ INDEX KVALITY Q_OMNIS",
                                    color = OmnisCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (evalScore.qualityTier) {
                                    "EXCELLENT" -> OmnisEmerald.copy(alpha = 0.2f)
                                    "OPTIMAL" -> OmnisCyan.copy(alpha = 0.2f)
                                    else -> OmnisAmber.copy(alpha = 0.2f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (evalScore.qualityTier) {
                                        "EXCELLENT" -> OmnisEmerald
                                        "OPTIMAL" -> OmnisCyan
                                        else -> OmnisAmber
                                    }
                                )
                            ) {
                                Text(
                                    text = "Q_omnis: ${(evalScore.unifiedQualityIndex * 100).toInt()}% [${evalScore.qualityTier}]",
                                    color = when (evalScore.qualityTier) {
                                        "EXCELLENT" -> OmnisEmerald
                                        "OPTIMAL" -> OmnisCyan
                                        else -> OmnisAmber
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4 Pillars Progress Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PillarMetricBox(
                                label = "RAG Cosine",
                                score = evalScore.ragCosineScore,
                                weight = "30%",
                                modifier = Modifier.weight(1f)
                            )
                            PillarMetricBox(
                                label = "8D Tensor",
                                score = evalScore.tensorStabilityScore,
                                weight = "25%",
                                modifier = Modifier.weight(1f)
                            )
                            PillarMetricBox(
                                label = "Impact Matrix",
                                score = evalScore.impactMatrixScore,
                                weight = "25%",
                                modifier = Modifier.weight(1f)
                            )
                            PillarMetricBox(
                                label = "Zero-Fluff",
                                score = evalScore.zeroFluffScore,
                                weight = "20%",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillarMetricBox(
    label: String,
    score: Float,
    weight: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = OmnisBgDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = OmnisTextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${(score * 100).toInt()}%",
                color = OmnisCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { score.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = OmnisCyan,
                trackColor = OmnisBorderDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = weight,
                color = OmnisTextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ApexStepCard(step: ApexExecutionStep) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = OmnisCyan.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan)
            ) {
                Text(
                    text = "KROK ${step.stepNumber}",
                    color = OmnisCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.stepName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = step.summary,
                    color = OmnisTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = OmnisEmerald,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
