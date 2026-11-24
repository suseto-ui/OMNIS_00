package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    modifier: Modifier = Modifier
) {
    val logs by SyncLogManager.logs.collectAsStateWithLifecycle()
    var filterMode by remember { mutableStateOf("ALL") }

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
            .padding(16.dp)
            .testTag("sync_log_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SYNC DIAGNOSTIC LOGS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Historie pozadových synchronizací ElectricSQL & PostgreSQL",
                    color = OmnisTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(
                onClick = { SyncLogManager.clearLogs() },
                modifier = Modifier.testTag("btn_clear_sync_logs")
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Vymazat logy",
                    tint = OmnisRed
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == "ALL",
                onClick = { filterMode = "ALL" },
                label = { Text("Všechny (${logs.size})") }
            )
            FilterChip(
                selected = filterMode == "SUCCESS",
                onClick = { filterMode = "SUCCESS" },
                label = { Text("Úspěšné (${logs.count { it.success }})") }
            )
            FilterChip(
                selected = filterMode == "FAILURE",
                onClick = { filterMode = "FAILURE" },
                label = { Text("Chyby (${logs.count { !it.success }})") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

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

@Composable
fun SyncLogItemCard(entry: SyncLogEntry, dateFormat: SimpleDateFormat) {
    val statusColor = if (entry.success) OmnisEmerald else OmnisRed
    val statusIcon = if (entry.success) Icons.Default.CheckCircle else Icons.Default.Error
    val formattedTime = remember(entry.timestamp) { dateFormat.format(Date(entry.timestamp)) }
    val payloadKb = String.format(Locale.US, "%.2f KB", entry.payloadSizeBytes / 1024.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "Synchronizováno položek: ${entry.syncedCount}",
                    color = OmnisTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Velikost payloadu: $payloadKb",
                    color = OmnisCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
