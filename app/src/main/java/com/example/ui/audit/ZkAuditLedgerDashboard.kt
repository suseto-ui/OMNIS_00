package com.example.ui.audit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.OverrideHistoryEvent
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ZkAuditLedgerDashboard(
    events: List<OverrideHistoryEvent>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        items(events) { event ->
            OverrideHistoryItem(event)
        }
    }
}
}

@Composable
fun OverrideHistoryItem(event: OverrideHistoryEvent) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Override Event", fontWeight = FontWeight.Bold)
                    Text(text = dateFormat.format(Date(event.timestamp)), style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Reason: ${event.reason}", style = MaterialTheme.typography.bodyMedium)
                    Text("Admin: ${event.adminUser}", style = MaterialTheme.typography.bodyMedium)
                    Text("Audit Hash Seal: ${event.zkSnarkSeal.take(16)}...", style = MaterialTheme.typography.labelSmall)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Pre-state: ${event.prevState}", style = MaterialTheme.typography.bodySmall)
                    Text("Post-state: ${event.postState}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
