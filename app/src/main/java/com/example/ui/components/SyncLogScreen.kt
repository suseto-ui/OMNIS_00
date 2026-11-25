package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Error
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
import com.example.data.SyncLogEntry
import com.example.data.SyncLogManager
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncLogScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    val logs by SyncLogManager.logs.collectAsStateWithLifecycle()
    var filterMode by remember { mutableStateOf("ALL") }

    val stats = remember(logs) {
        val total = logs.size
        val succeeded = logs.count { it.success }
        val successRate = if (total > 0) (succeeded * 100) / total else 0
        val totalBytes = logs.sumOf { it.payloadSizeBytes }
        val avgDurationMs = if (total > 0) logs.sumOf { it.durationMs } / total else 0L
        SyncDiagnosticSummary(total, succeeded, successRate, totalBytes, avgDurationMs)
    }

    val filteredLogs = remember(logs, filterMode) {
        when (filterMode) {
            "SUCCESS" -> logs.filter { it.success }
            "FAILURE" -> logs.filter { !it.success }
            else -> logs
        }
    }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .testTag("sync_log_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("btn_sync_logs_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zpět",
                        tint = OmnisCyan
                    )
                }
                Column {
                    Text(
                        text = "SYNC DIAGNOSTIC LOGS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Historie synchronizací ElectricSQL & PostgreSQL 17",
                        color = OmnisTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            IconButton(
                onClick = { SyncLogManager.clearLogs() },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("btn_clear_sync_logs")
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Vymazat logy",
                    tint = OmnisRed
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SyncDiagnosticSummaryStrip(stats = stats)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == "ALL",
                onClick = { filterMode = "ALL" },
                label = { Text("Všechny (${logs.size})") },
                modifier = Modifier.testTag("chip_sync_all")
            )
            FilterChip(
                selected = filterMode == "SUCCESS",
                onClick = { filterMode = "SUCCESS" },
                label = { Text("Úspěšné (${stats.succeededCount})") },
                modifier = Modifier.testTag("chip_sync_success")
            )
            FilterChip(
                selected = filterMode == "FAILURE",
                onClick = { filterMode = "FAILURE" },
                label = { Text("Chyby (${stats.totalRuns - stats.succeededCount})") },
                modifier = Modifier.testTag("chip_sync_failure")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Žádné záznamy synchronizace k zobrazení.",
                    color = OmnisTextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    SyncLogItemCard(entry, dateFormat)
                }
            }
        }
    }
}

private data class SyncDiagnosticSummary(
    val totalRuns: Int,
    val succeededCount: Int,
    val successRatePercent: Int,
    val totalPayloadBytes: Long,
    val avgDurationMs: Long
)

@Composable
private fun SyncDiagnosticSummaryStrip(stats: SyncDiagnosticSummary) {
    val totalPayloadText = remember(stats.totalPayloadBytes) {
        formatSyncPayloadBytes(stats.totalPayloadBytes)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_diagnostic_summary"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
        border = BorderStroke(1.dp, OmnisBorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetricCell("OPERACÍ", "${stats.totalRuns}", Color.White)
            SummaryMetricCell(
                "ÚSPĚŠNOST",
                "${stats.successRatePercent}%",
                if (stats.successRatePercent >= 80 || stats.totalRuns == 0) OmnisEmerald else OmnisAmber
            )
            SummaryMetricCell("PŘENESENO", totalPayloadText, OmnisCyan)
            SummaryMetricCell("PRŮM. ČAS", "${stats.avgDurationMs} ms", OmnisViolet)
        }
    }
}

@Composable
private fun SummaryMetricCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = OmnisTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatSyncPayloadBytes(bytes: Long): String {
    return if (bytes < 1024L) "$bytes B"
    else String.format(Locale.US, "%.2f KB", bytes / 1024.0)
}

@Composable
fun SyncLogItemCard(entry: SyncLogEntry, dateFormat: SimpleDateFormat) {
    val statusColor = if (entry.success) OmnisEmerald else OmnisRed
    val statusIcon = if (entry.success) Icons.Default.CheckCircle else Icons.Default.Error
    val formattedTime = remember(entry.timestamp) { dateFormat.format(Date(entry.timestamp)) }
    val payloadText = remember(entry.payloadSizeBytes) { formatSyncPayloadBytes(entry.payloadSizeBytes) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_log_item_${entry.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = entry.taskType,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = formattedTime,
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = entry.message,
                color = if (entry.success) Color.White.copy(alpha = 0.9f) else OmnisRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Položky: ${entry.syncedCount}",
                    color = OmnisTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Čas: ${entry.durationMs} ms",
                    color = OmnisViolet,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Payload: $payloadText",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
