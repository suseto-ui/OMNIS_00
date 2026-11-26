package com.example.ui.installer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import com.example.system.PermissionItemStatus
import com.example.system.SystemPermissionManager
import com.example.ui.theme.*

@Composable
fun PermissionsOnboardingView(
    onInstallationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionsList by remember {
        mutableStateOf(SystemPermissionManager.getPermissionsOverview(context))
    }

    fun refreshPermissions() {
        permissionsList = SystemPermissionManager.getPermissionsOverview(context)
    }

    // Camera Permission Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshPermissions()
    }

    // Notifications Permission Launcher
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshPermissions()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .padding(16.dp)
            .testTag("permissions_installer_container")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = OmnisCyan,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = "PRŮVODCE SYSTÉMOVÝMI OPRÁVNĚNÍMI",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SYSTÉMOVÁ POVOLENÍ & BEZPEČNOSTNÍ PROTOKOL",
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Info Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = OmnisEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Aplikace O.M.N.I.S. plně respektuje soukromí a zásady Google Play. Runtime oprávnění slouží výhradně pro OCR kameru, hlasový vstup a notifikace; obrázky z galerie vybírá systémový Photo Picker bez plošných oprávnění.",
                    color = OmnisTextLight,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Permissions Checklist
        Text(
            text = "KONTROLNÍ SEZNAM SYSTÉMOVÝCH POVOLENÍ:",
            color = OmnisCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(permissionsList, key = { it.permissionKey }) { item ->
                PermissionCardItem(
                    item = item,
                    onRequestPermission = {
                        when (item.permissionKey) {
                            Manifest.permission.CAMERA -> cameraLauncher.launch(Manifest.permission.CAMERA)
                            Manifest.permission.POST_NOTIFICATIONS, "android.permission.POST_NOTIFICATIONS" -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Battery Optimization Banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = OmnisPanelDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SystemPermissionManager.getBatteryOptimizationStatus(context),
                    color = OmnisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = OmnisEmerald.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "OPTIMÁLNÍ",
                        color = OmnisEmerald,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Complete Installation Button
        Button(
            onClick = onInstallationComplete,
            colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("finish_installation_button")
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "DOKONČIT INSTALACI A SPUSTIT O.M.N.I.S.",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PermissionCardItem(
    item: PermissionItemStatus,
    onRequestPermission: () -> Unit
) {
    val badgeColor = if (item.isGranted) OmnisEmerald else OmnisAmber

    Card(
        colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("perm_item_${item.permissionKey}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (item.permissionKey) {
                            Manifest.permission.CAMERA -> Icons.Default.CameraAlt
                            Manifest.permission.POST_NOTIFICATIONS, "android.permission.POST_NOTIFICATIONS" -> Icons.Default.Notifications
                            else -> Icons.Default.Shield
                        },
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "KATEGORIE: ${item.category}",
                            color = OmnisTextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = if (item.isGranted) "SCHVÁLENO" else "VYŽADOVÁNO",
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                color = OmnisTextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            if (!item.isGranted && (item.permissionKey == Manifest.permission.CAMERA || item.permissionKey.contains("POST_NOTIFICATIONS"))) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = OmnisCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("request_perm_button_${item.permissionKey}")
                ) {
                    Text(
                        text = "POVOLIT OPRÁVNĚNÍ",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
