package com.example.ui.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
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
import com.example.data.sync.PostgresPgVectorEngine
import com.example.defense.SigmaOmegaRedTeamingEngine
import com.example.engine.OmnisFivePhaseExecutor
import com.example.ui.theme.*

@Composable
fun PgVectorImpactMatrixView(
    modifier: Modifier = Modifier
) {
    var queryInput by remember { mutableStateOf("Harmonizace energetické infrastruktury a právního rámu EU AI Act") }

    val fivePhaseResult = remember(queryInput) {
        OmnisFivePhaseExecutor.executeFivePhasePipeline(queryInput)
    }

    val sigmaConfrontation = remember(queryInput, fivePhaseResult) {
        SigmaOmegaRedTeamingEngine.runSigmaConfrontation(queryInput, fivePhaseResult.teleologicalAutopoiesisCode)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("pgvector_impact_matrix_container")
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
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = OmnisCyan,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "POSTGRESQL PGVECTOR & MATICE DOPADŮ",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "HYBRID: FIRESTORE REALTIME + PGVECTOR HNSW INDEX",
                        color = OmnisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = OmnisEmerald.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald)
            ) {
                Text(
                    text = "HNSW INDEX ONLINE",
                    color = OmnisEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hybrid Architecture Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "HYBRIDNÍ ARCHITEKTURA DATABÁZE",
                            color = OmnisCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Firestore Client State:", color = OmnisTextMuted, fontSize = 10.sp)
                                Text("Real-time relace & Auth", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PostgreSQL pgvector Engine:", color = OmnisTextMuted, fontSize = 10.sp)
                                Text("64D HNSW Cosine Index", color = OmnisEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. 5-Phase Execution Cycle Tracker
            item {
                Text(
                    text = "5-FÁZOVÝ EXEKUČNÍ CYKLUS O.M.N.I.S.:",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhaseRow("Fáze I", "Holomorphic Sběr", fivePhaseResult.holomorphicCollectionSummary, OmnisCyan)
                        PhaseRow("Fáze II", "Sémantická Dekonstrukce", fivePhaseResult.semanticDeconstructionSummary, OmnisEmerald)
                        PhaseRow("Fáze III", "Transdisciplinární Křížení", fivePhaseResult.transdisciplinaryBridgeSummary, Color(0xFFA855F7))
                        PhaseRow("Fáze IV", "Synergická Konvergence", "Matice dopadů Win-Win-Win: ${(fivePhaseResult.impactMatrix.winWinWinScore * 100).toInt()}%", OmnisAmber)
                        PhaseRow("Fáze V", "Teleologická Autopoieza", fivePhaseResult.teleologicalAutopoiesisCode, OmnisCyan)
                    }
                }
            }

            // 3. Win-Win-Win Impact Matrix Card
            item {
                Text(
                    text = "MATICE DOPADŮ (WIN-WIN-WIN):",
                    color = OmnisEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmnisEmerald.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImpactBarRow("Ekonomická Životaschopnost", fivePhaseResult.impactMatrix.economicViability, OmnisCyan)
                        ImpactBarRow("Technická Elegance", fivePhaseResult.impactMatrix.technicalElegance, OmnisEmerald)
                        ImpactBarRow("Ekologicko-Sociální Regenerace", fivePhaseResult.impactMatrix.ecoSocialRegeneration, Color(0xFFA855F7))
                        ImpactBarRow("Psychologická Přijatelnost", fivePhaseResult.impactMatrix.psychologicalAcceptance, OmnisAmber)
                    }
                }
            }

            // 4. SIGMA Red-Teaming Confrontation Log
            item {
                Text(
                    text = "SIGMA RED-TEAMING & OMEGA REZONANCE:",
                    color = Color(0xFFA855F7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = sigmaConfrontation.lawyerObjection, color = Color.White, fontSize = 11.sp)
                        Text(text = sigmaConfrontation.architectObjection, color = Color.White, fontSize = 11.sp)
                        Text(text = sigmaConfrontation.gameTheoristObjection, color = Color.White, fontSize = 11.sp)
                        HorizontalDivider(color = OmnisBorderDark, thickness = 0.5.dp)
                        Text(text = sigmaConfrontation.crystalResonanceConsensus, color = OmnisEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseRow(phaseCode: String, phaseTitle: String, summary: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(1.dp, color)
        ) {
            Text(
                text = phaseCode,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = phaseTitle, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = summary, color = OmnisTextMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ImpactBarRow(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = OmnisTextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(160.dp)
        )

        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = OmnisBgDark
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${value.toInt()}%",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
