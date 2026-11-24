package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.OmnisBatchSyncScheduler
import com.example.data.OmnisBatchSyncWorker
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncStatusComponent(
    modifier: Modifier = Modifier,
    onTriggerSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    val periodicInfos by workManager.getWorkInfosForUniqueWorkFlow(OmnisBatchSyncScheduler.PERIODIC_WORK_NAME)
        .collectAsState(initial = emptyList())
    val immediateInfos by workManager.getWorkInfosForUniqueWorkFlow(OmnisBatchSyncScheduler.IMMEDIATE_WORK_NAME)
        .collectAsState(initial = emptyList())
    val recoveryInfos by workManager.getWorkInfosForUniqueWorkFlow(OmnisBatchSyncScheduler.NETWORK_RECOVERY_WORK_NAME)
        .collectAsState(initial = emptyList())

    val allInfos = remember(periodicInfos, immediateInfos, recoveryInfos) {
        periodicInfos + immediateInfos + recoveryInfos
    }

    val activeWork = allInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val lastSuccessWork = allInfos.filter { it.state == WorkInfo.State.SUCCEEDED }
        .maxByOrNull { it.runAttemptCount }

    val isRunning = activeWork?.state == WorkInfo.State.RUNNING
    val isEnqueued = activeWork?.state == WorkInfo.State.ENQUEUED
    val syncedCount = activeWork?.outputData?.getInt(OmnisBatchSyncWorker.KEY_SYNCED_COUNT, 0)
        ?: lastSuccessWork?.outputData?.getInt(OmnisBatchSyncWorker.KEY_SYNCED_COUNT, 0) ?: 0

    val statusText = when {
        isRunning -> "Synchronizace probíhá..."
        isEnqueued -> "Čeká na síťové připojení..."
        lastSuccessWork != null -> "Synchronizováno (Dávka: $syncedCount záznamů)"
        else -> "Připraveno k synchronizaci"
    }

    val statusColor = when {
        isRunning || isEnqueued -> OmnisCyan
        lastSuccessWork != null -> OmnisEmerald
        else -> OmnisTextMuted
    }

    val statusIcon = when {
        isRunning || isEnqueued -> Icons.Default.CloudSync
        lastSuccessWork != null -> Icons.Default.CloudDone
        else -> Icons.Default.Sync
    }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()) }
    var lastSuccessTimeStr by remember { mutableStateOf("Neznámo") }

    LaunchedEffect(lastSuccessWork) {
        if (lastSuccessWork != null) {
            lastSuccessTimeStr = dateFormat.format(Date())
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_status_component"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OmnisCardDark),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ElectricSQL / PostgreSQL Sync",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(
                    onClick = onTriggerSync,
                    modifier = Modifier.testTag("btn_manual_sync")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Spustit synchronizaci",
                        tint = OmnisCyan
                    )
                }
            }

            AnimatedVisibility(visible = isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = OmnisCyan,
                    trackColor = OmnisBorderDark
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Poslední úspěšná synchronizace: $lastSuccessTimeStr",
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isRunning) OmnisCyan.copy(alpha = 0.2f) else OmnisEmerald.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isRunning) "SYNC" else "READY",
                        color = if (isRunning) OmnisCyan else OmnisEmerald,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
