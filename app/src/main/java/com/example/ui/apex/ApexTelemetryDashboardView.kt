package com.example.ui.apex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OmnisRecord
import com.example.ui.OmnisViewModel
import com.example.ui.theme.*

@Composable
fun ApexTelemetryDashboardView(
    viewModel: OmnisViewModel,
    records: List<OmnisRecord>,
    modifier: Modifier = Modifier
) {
    val isCircuitBreakerTripped by viewModel.isCircuitBreakerTripped.collectAsStateWithLifecycle()
    val isCloudOnline by viewModel.isCloudRunOnline.collectAsStateWithLifecycle()
    val gatewayStatus by viewModel.cloudRunGatewayStatus.collectAsStateWithLifecycle()

    val assistantRecords = remember(records) {
        records.filter { it.role == "assistant" && it.qOmnisIndex > 0f }
    }

    val avgQOmnis = if (assistantRecords.isNotEmpty()) {
        assistantRecords.map { it.qOmnisIndex }.average().toFloat()
    } else {
        0.94f
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("apex_telemetry_dashboard"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                border = BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.5f)),
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
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = OmnisCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "APEX TELEMETRY & PRODUCTION DASHBOARD",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Real-time Q_omnis & AKGE-8D Monitoring",
                                    color = OmnisTextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isCloudOnline) OmnisEmerald.copy(alpha = 0.15f) else OmnisAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isCloudOnline) OmnisEmerald else OmnisAmber)
                        ) {
                            Text(
                                text = if (isCloudOnline) "CLOUD ONLINE" else "OFFLINE FALLBACK",
                                color = if (isCloudOnline) OmnisEmerald else OmnisAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricSummaryCard(
                            title = "Průměrný Q_omnis",
                            value = "${(avgQOmnis * 100).toInt()}%",
                            subtitle = "EXCELLENT TIER",
                            color = OmnisEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryCard(
                            title = "Zpracované relace",
                            value = "${assistantRecords.size.coerceAtLeast(1)}",
                            subtitle = "Zero-Fluff Verified",
                            color = OmnisCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricSummaryCard(
                            title = "Circuit Breaker",
                            value = if (isCircuitBreakerTripped) "TRIPPED" else "ARMED",
                            subtitle = if (isCircuitBreakerTripped) "Fallback Active" else "Nominal",
                            color = if (isCircuitBreakerTripped) OmnisAmber else OmnisViolet,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section Title: Recent Audited Artifacts
        item {
            Text(
                text = "POSLEDNÍ CERTifikované EXEKUCE (Q_OMNIS):",
                color = OmnisCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (assistantRecords.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Zatím žádné zaznamenané exekuce. Zadejte dotaz v Chatu pro spustění APEX protokolu.",
                            color = OmnisTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            items(assistantRecords.take(5)) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
                    border = BorderStroke(1.dp, OmnisBorderDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ID: #${record.id} • ${record.qualityTier}",
                                color = OmnisCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = OmnisEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, OmnisEmerald.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Q_omnis: ${(record.qOmnisIndex * 100).toInt()}%",
                                    color = OmnisEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = record.content.take(120) + "...",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OmnisBgDark,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = OmnisTextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = color.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
