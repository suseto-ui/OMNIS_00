package com.example.ui.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.share.PendingSharePayload
import com.example.share.SharedContentType
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareActionBottomSheet(
    payload: PendingSharePayload,
    onInsertIntoChat: () -> Unit,
    onSummarizeNow: () -> Unit,
    onExtractOcrOrPdf: () -> Unit,
    onAnalyzeRisks: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasExtractableMedia = payload.attachments.any { it.isImage || it.isPdf }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OmnisPanelDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OmnisBorderDark) },
        modifier = Modifier.testTag("share_action_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Sdílený obsah",
                        tint = OmnisCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SDÍLENÝ VSTUP PRO O.M.N.I.S.",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = OmnisCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = when (payload.contentType) {
                                SharedContentType.URL -> "URL"
                                SharedContentType.IMAGE -> "OBRÁZEK"
                                SharedContentType.PDF -> "PDF"
                                SharedContentType.MIXED -> "SMÍŠENÝ"
                                SharedContentType.TEXT -> "TEXT"
                            },
                            color = OmnisCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_sheet_dismiss_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zavřít dialog sdílení",
                            tint = OmnisTextMuted
                        )
                    }
                }
            }

            // Preview card
            Surface(
                color = OmnisBgDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OmnisBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    payload.subject?.let { subject ->
                        Text(
                            text = subject,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (payload.detectedUrls.isNotEmpty()) {
                        payload.detectedUrls.take(3).forEach { url ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Sdílený odkaz",
                                    tint = OmnisCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = url,
                                    color = OmnisCyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    payload.sharedText?.let { text ->
                        Text(
                            text = text.take(260) + if (text.length > 260) "..." else "",
                            color = OmnisTextLight,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (payload.attachments.isNotEmpty()) {
                        HorizontalDivider(color = OmnisBorderDark.copy(alpha = 0.6f))
                        payload.attachments.forEach { att ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (att.isImage) {
                                    AsyncImage(
                                        model = File(att.localCachePath),
                                        contentDescription = "Náhled sdíleného obrázku ${att.displayName}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, OmnisBorderDark, RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(OmnisPanelDark)
                                            .border(1.dp, OmnisViolet.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (att.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                            contentDescription = att.displayName,
                                            tint = if (att.isPdf) OmnisAmber else OmnisCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.displayName,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${att.mimeType} • ${formatBytes(att.byteSize)}",
                                        color = OmnisTextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "VYBERTE RYCHLOU AKCI:",
                color = OmnisTextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Quick Actions
            ShareQuickActionButton(
                title = "Vložit do vstupu chatu",
                subtitle = "Předvyplní zadání v chatu k ruční úpravě před odesláním",
                icon = Icons.AutoMirrored.Filled.Input,
                accentColor = OmnisCyan,
                testTag = "share_action_insert_chat",
                onClick = onInsertIntoChat
            )

            ShareQuickActionButton(
                title = "Okamžitě shrnout",
                subtitle = "Vytvoří strukturovaný výtah klíčových bodů v chatu",
                icon = Icons.Default.AutoAwesome,
                accentColor = OmnisEmerald,
                testTag = "share_action_summarize",
                onClick = onSummarizeNow
            )

            if (hasExtractableMedia) {
                ShareQuickActionButton(
                    title = "Extrahovat text (OCR / PDF) a ověřit",
                    subtitle = "Přečte obsah dokumentu nebo obrázku a otevře náhled k editaci",
                    icon = Icons.Default.DocumentScanner,
                    accentColor = OmnisViolet,
                    testTag = "share_action_extract_ocr_pdf",
                    onClick = onExtractOcrOrPdf
                )
            }

            ShareQuickActionButton(
                title = "Hloubková analýza rizik",
                subtitle = "Prověří právní, bezpečnostní a faktická rizika v 8D matici",
                icon = Icons.Default.Shield,
                accentColor = OmnisAmber,
                testTag = "share_action_analyze_risks",
                onClick = onAnalyzeRisks
            )
        }
    }
}

@Composable
private fun ShareQuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = OmnisBgDark,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = OmnisTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
