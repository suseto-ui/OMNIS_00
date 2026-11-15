package com.example.ui.audit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.engine.AkgeTensorKernel
import com.example.engine.AutoRemediationRecord
import com.example.engine.AutonomousRemediationEngine
import com.example.engine.TopologicalInferenceEngine
import com.example.ui.theme.*

@Composable
fun AutonomousDiagnosticView(
    modifier: Modifier = Modifier
) {
    val kernelSnapshot by AkgeTensorKernel.kernelState.collectAsStateWithLifecycle()
    val isAutoEnabled by AutonomousRemediationEngine.isAutoRemediationEnabled.collectAsStateWithLifecycle()
    val remediationHistory by AutonomousRemediationEngine.remediationHistory.collectAsStateWithLifecycle()

    val topoResult = remember(kernelSnapshot) {
        TopologicalInferenceEngine.verifyInferenceOutput(
            domainScores = kernelSnapshot.domainWeights,
            domainsStatus = kernelSnapshot.domainWeights.mapValues { if (it.value < 0.35f) "FAILED" else "STABLE" },
            systemStressSigma = kernelSnapshot.systemStressSigma
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(14.dp)
            .testTag("autonomous_diagnostic_view_root"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Bar & Control Switch
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = OmnisPanelDark,
            border = BorderStroke(1.dp, OmnisBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Autonomní Diagnostika",
                        tint = OmnisCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "AUTONOMNÍ DIAGNOSTIKA & REMEDIACE",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isAutoEnabled) "Aktivní autonomní dohled nad 8D grafikou" else "Autonomní remediace pozastavena",
                            color = if (isAutoEnabled) OmnisEmerald else OmnisAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Switch(
                    checked = isAutoEnabled,
                    onCheckedChange = { AutonomousRemediationEngine.setAutoRemediationEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OmnisBgDark,
                        checkedTrackColor = OmnisCyan,
                        uncheckedThumbColor = OmnisTextMuted,
                        uncheckedTrackColor = OmnisPanelDark
                    ),
                    modifier = Modifier.testTag("auto_remediation_switch")
                )
            }
        }

        // 2. Topological Radar & Health Metrics
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = OmnisPanelDark,
            border = BorderStroke(1.dp, if (topoResult.metrics.isValid) OmnisBorderDark else OmnisRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "STATUS TOPOLOCICKÉ INTEGRITY (AKGE-8D)",
                    color = if (topoResult.metrics.isValid) OmnisCyan else OmnisRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DiagnosticMetricChip("Euler χ", topoResult.metrics.eulerCharacteristic.toString(), topoResult.metrics.eulerCharacteristic in -4..6)
                    DiagnosticMetricChip("Betti β1", topoResult.metrics.betti1.toString(), topoResult.metrics.betti1 in 0..7)
                    DiagnosticMetricChip("Entropie S", String.format("%.2f", topoResult.metrics.shannonEntropy), topoResult.metrics.shannonEntropy < 2.08f)
                    DiagnosticMetricChip("Pnutí σ", String.format("%.2f", kernelSnapshot.systemStressSigma), kernelSnapshot.systemStressSigma < 0.40f)
                }

                if (topoResult.remediationSteps.isNotEmpty()) {
                    HorizontalDivider(color = OmnisBorderDark, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "DETEKOVÁNO DŮVODŮ K REMEDIACI:",
                        color = OmnisAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    topoResult.remediationSteps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(OmnisAmber))
                            Text(text = step, color = OmnisTextLight, fontSize = 10.5.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }

        // 3. Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { AutonomousRemediationEngine.forceRemediationScan() },
                colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("force_remediation_button")
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = OmnisBgDark, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Vynutit Opravu", color = OmnisBgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { AkgeTensorKernel.injectCascadeStress(listOf("SYS", "SEC")) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OmnisRed),
                border = BorderStroke(1.dp, OmnisRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("inject_stress_button")
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = OmnisRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulovat Šok", color = OmnisRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = { AutonomousRemediationEngine.clearRemediationHistory() },
                modifier = Modifier
                    .size(42.dp)
                    .background(OmnisPanelDark, RoundedCornerShape(8.dp))
                    .border(1.dp, OmnisBorderDark, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Vyčistit logy", tint = OmnisTextMuted, modifier = Modifier.size(18.dp))
            }
        }

        // 4. Remediation Stream History
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = OmnisPanelDark,
            border = BorderStroke(1.dp, OmnisBorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = "PRODĚLANÉ AUTONOMNÍ OPRAVY (${remediationHistory.size})",
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (remediationHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Žádná remediace zatím nebola vyžádána. Systém je v nominálním ekvilibriu.",
                            color = OmnisTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(remediationHistory.reversed(), key = { it.id }) { record ->
                            RemediationHistoryItem(record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricChip(label: String, value: String, isOk: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = OmnisTextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = value,
            color = if (isOk) OmnisEmerald else OmnisRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RemediationHistoryItem(record: AutoRemediationRecord) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OmnisBgDark,
        border = BorderStroke(0.5.dp, OmnisEmerald.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OmnisEmerald, modifier = Modifier.size(14.dp))
                    Text(text = "REMEDIACE DOKONČENA", color = OmnisEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Text(text = record.formattedTime, color = OmnisTextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }

            Text(text = record.actionTaken, color = Color.White, fontSize = 11.sp, lineHeight = 15.sp)
            Text(text = "Důvod: ${record.triggerReason}", color = OmnisTextMuted, fontSize = 9.5.sp, lineHeight = 13.sp)
        }
    }
}
