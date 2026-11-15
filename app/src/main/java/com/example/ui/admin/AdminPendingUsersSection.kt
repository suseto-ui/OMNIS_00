package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.OmnisAuthService
import com.example.auth.PendingUserDto
import com.example.auth.PendingUsersResult
import com.example.auth.UserAuthService
import com.example.ui.theme.OmnisBgDark
import com.example.ui.theme.OmnisBorderDark
import com.example.ui.theme.OmnisCyan
import com.example.ui.theme.OmnisEmerald
import com.example.ui.theme.OmnisPanelDark
import com.example.ui.theme.OmnisTextMuted
import kotlinx.coroutines.launch

@Composable
fun AdminPendingUsersSection() {
    val coroutineScope = rememberCoroutineScope()
    var pendingUsers by remember { mutableStateOf<List<PendingUserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var processingEmail by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val session by OmnisAuthService.currentSession.collectAsStateWithLifecycle()

    fun loadPending() {
        val token = session?.token
        if (token.isNullOrBlank()) {
            errorMessage = "Chybí platná relace administrátora."
            return
        }
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            when (val res = UserAuthService.instance.fetchPendingUsers(token)) {
                is PendingUsersResult.Success -> {
                    pendingUsers = res.users
                }
                is PendingUsersResult.Error -> {
                    errorMessage = res.message
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(session?.token) {
        loadPending()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OmnisPanelDark,
        border = BorderStroke(1.dp, OmnisBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_pending_users_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = OmnisCyan)
                Text(
                    text = "ŽÁDOSTI O REGISTRACI ÚČTŮ",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { loadPending() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pending_refresh_button")
                ) {
                    Text("Obnovit", color = OmnisCyan, fontSize = 11.sp)
                }
            }

            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FF5252),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_error_banner")
                ) {
                    Text(
                        text = err,
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            statusMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OmnisEmerald.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, OmnisEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_status_banner")
                ) {
                    Text(
                        text = msg,
                        color = OmnisEmerald,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OmnisCyan, modifier = Modifier.size(24.dp))
                }
            } else if (pendingUsers.isEmpty()) {
                Text(
                    text = "Žádné čekající žádosti o registraci.",
                    color = OmnisTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingUsers.forEach { user ->
                        val isBusy = processingEmail == user.email
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OmnisBgDark,
                            border = BorderStroke(1.dp, OmnisBorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.username,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = user.email,
                                        color = OmnisCyan,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Vytvořeno: ${user.createdAt.ifBlank { "—" }}",
                                        color = OmnisTextMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        enabled = !isBusy && processingEmail == null,
                                        onClick = {
                                            val token = session?.token ?: return@IconButton
                                            coroutineScope.launch {
                                                processingEmail = user.email
                                                errorMessage = null
                                                val res = UserAuthService.instance.approveUserDetailed(token, user.email)
                                                if (res.success) {
                                                    statusMessage = res.message
                                                    loadPending()
                                                } else {
                                                    errorMessage = res.message
                                                }
                                                processingEmail = null
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("pending_approve_${user.username}")
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Schválit uživatele ${user.username}",
                                            tint = if (isBusy) OmnisTextMuted else OmnisEmerald
                                        )
                                    }

                                    IconButton(
                                        enabled = !isBusy && processingEmail == null,
                                        onClick = {
                                            val token = session?.token ?: return@IconButton
                                            coroutineScope.launch {
                                                processingEmail = user.email
                                                errorMessage = null
                                                val res = UserAuthService.instance.rejectUserDetailed(token, user.email)
                                                if (res.success) {
                                                    statusMessage = res.message
                                                    loadPending()
                                                } else {
                                                    errorMessage = res.message
                                                }
                                                processingEmail = null
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("pending_reject_${user.username}")
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Zamítnout uživatele ${user.username}",
                                            tint = if (isBusy) OmnisTextMuted else Color(0xFFFF5252)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
