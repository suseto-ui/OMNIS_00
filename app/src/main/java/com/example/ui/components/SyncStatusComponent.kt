package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.OmnisBatchSyncScheduler
import com.example.data.OmnisBatchSyncWorker
import com.example.data.SyncLogManager
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.AppLocaleManager
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncStatusComponent(
    modifier: Modifier = Modifier,
    onTriggerSync: () -> Unit = {},
    onOpenLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentLang by AppLocaleManager.currentLanguage.collectAsStateWithLifecycle()
    val isEnglish = currentLang == AppLanguage.EN
    val workManager = remember { WorkManager.getInstance(context) }

    val periodicInfos by workManager.getWorkInfosForUniqueWorkFlow(OmnisBatchSyncScheduler.PERIODIC_WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val immediateInfos by workManager.getWorkInfosForUniqueWorkFlow(OmnisBatchSyncScheduler.IMMEDIATE_WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recoveryInfos by workManager.getWorkInfosForUniqueWorkFlow(OmnisBatchSyncScheduler.NETWORK_RECOVERY_WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val logLastSuccessTime by SyncLogManager.lastSuccessfulSyncTime.collectAsStateWithLifecycle()

    val allInfos = remember(periodicInfos, immediateInfos, recoveryInfos) {
        periodicInfos + immediateInfos + recoveryInfos
    }

    val activeWork = allInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val failedWork = allInfos.firstOrNull { it.state == WorkInfo.State.FAILED }
    val blockedWork = allInfos.firstOrNull { it.state == WorkInfo.State.BLOCKED }
    val lastSuccessWork = allInfos.filter { it.state == WorkInfo.State.SUCCEEDED }
        .maxByOrNull { it.outputData.getLong(OmnisBatchSyncWorker.KEY_LAST_SYNC_TIME, 0L) }

    val isRunning = activeWork?.state == WorkInfo.State.RUNNING
    val isEnqueued = activeWork?.state == WorkInfo.State.ENQUEUED
    val isBlocked = activeWork == null && blockedWork != null
    val hasFailed = activeWork == null && !isBlocked && failedWork != null

    val syncedCount = activeWork?.outputData?.getInt(OmnisBatchSyncWorker.KEY_SYNCED_COUNT, 0)
        ?: lastSuccessWork?.outputData?.getInt(OmnisBatchSyncWorker.KEY_SYNCED_COUNT, 0) ?: 0
    val payloadBytes = lastSuccessWork?.outputData?.getLong(OmnisBatchSyncWorker.KEY_PAYLOAD_BYTES, 0L) ?: 0L

    val workSuccessTime = lastSuccessWork?.outputData?.getLong(OmnisBatchSyncWorker.KEY_LAST_SYNC_TIME, 0L) ?: 0L
    val effectiveLastSuccessTime = maxOf(workSuccessTime, logLastSuccessTime ?: 0L)

    val statusText = when {
        isRunning -> if (isEnglish) "Synchronizing..." else "Synchronizace probíhá..."
        isEnqueued -> if (isEnglish) "Waiting for network connection..." else "Čeká na síťové připojení..."
        isBlocked -> if (isEnglish) "Sync paused (waiting for constraints)..." else "Synchronizace pozastavena (čeká na podmínky)..."
        hasFailed -> if (isEnglish) "Last sync failed (check logs)" else "Poslední synchronizace selhala (zkontrolujte záznamy)"
        lastSuccessWork != null || logLastSuccessTime != null -> {
            val payloadInfo = if (payloadBytes > 0L) {
                if (payloadBytes < 1024L) " · $payloadBytes B"
                else String.format(Locale.US, " · %.1f KB", payloadBytes / 1024.0)
            } else ""
            if (isEnglish) {
                "Synchronized (Batch: $syncedCount records$payloadInfo)"
            } else {
                "Synchronizováno (Dávka: $syncedCount záznamů$payloadInfo)"
            }
        }
        else -> if (isEnglish) "Ready to synchronize" else "Připraveno k synchronizaci"
    }

    val statusColor = when {
        isRunning || isEnqueued -> OmnisCyan
        hasFailed -> OmnisRed
        isBlocked -> OmnisAmber
        lastSuccessWork != null || logLastSuccessTime != null -> OmnisEmerald
        else -> OmnisTextMuted
    }

    val statusIcon = when {
        isRunning || isEnqueued -> Icons.Default.CloudSync
        hasFailed -> Icons.Default.Error
        isBlocked -> Icons.Default.CloudOff
        lastSuccessWork != null || logLastSuccessTime != null -> Icons.Default.CloudDone
        else -> Icons.Default.Sync
    }

    val badgeLabel = when {
        isRunning -> "SYNC"
        isEnqueued -> if (isEnglish) "QUEUE" else "FRONTA"
        isBlocked -> if (isEnglish) "WAIT" else "ČEKÁ"
        hasFailed -> if (isEnglish) "FAIL" else "CHYBA"
        lastSuccessWork != null || logLastSuccessTime != null -> "OK"
        else -> if (isEnglish) "READY" else "PŘIPRAVENO"
    }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()) }
    val lastSuccessTimeStr = remember(effectiveLastSuccessTime, isEnglish) {
        if (effectiveLastSuccessTime > 0L) dateFormat.format(Date(effectiveLastSuccessTime))
        else if (isEnglish) "None" else "Žádná"
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
                    modifier = Modifier.weight(1f),
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
                            text = if (isEnglish) "ElectricSQL / PostgreSQL Sync" else "Synchronizace ElectricSQL / PostgreSQL",
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenLogs,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("btn_open_sync_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = if (isEnglish) "Sync history" else "Historie synchronizace",
                            tint = OmnisTextMuted
                        )
                    }
                    IconButton(
                        onClick = onTriggerSync,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("btn_manual_sync")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = if (isEnglish) "Run sync" else "Spustit synchronizaci",
                            tint = OmnisCyan
                        )
                    }
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
                    text = if (isEnglish) "Last successful sync: $lastSuccessTimeStr" else "Poslední úspěšná synchronizace: $lastSuccessTimeStr",
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badgeLabel,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
