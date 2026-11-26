package com.example.ui.chat

import android.app.Activity
import android.content.Intent
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.OmnisViewModel
import com.example.ui.bottomsheet.OmnisModularBottomSheet
import com.example.ui.components.RobustnessGuardModal
import com.example.ui.theme.*

@Composable
fun ChatView(
    viewModel: OmnisViewModel,
    onSpeak: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatViewModel = viewModel.chatViewModel
    val records by viewModel.records.collectAsStateWithLifecycle()
    val inputText by viewModel.inputQuery.collectAsStateWithLifecycle()
    val viewModelLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val chatLoading by chatViewModel.isLoading.collectAsStateWithLifecycle()
    val viewModelOcrLoading by viewModel.isOcrLoading.collectAsStateWithLifecycle()
    val chatOcrLoading by chatViewModel.isOcrLoading.collectAsStateWithLifecycle()
    val stagedAttachment by viewModel.stagedChatAttachment.collectAsStateWithLifecycle()
    val stagedAttachments by viewModel.stagedChatAttachments.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val userExperienceMode by viewModel.userExperienceMode.collectAsStateWithLifecycle()
    val scrollToId by viewModel.scrollToId.collectAsStateWithLifecycle()
    val cognitiveError by viewModel.cognitiveError.collectAsState()

    val context = LocalContext.current
    var showModularSheet by remember { mutableStateOf(false) }
    val showLegalBar by ChatDisplayPreferences.showLegalStatusBarAboveInput.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val isLoading = viewModelLoading || chatLoading
    val isOcrLoading = viewModelOcrLoading || chatOcrLoading

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.extractTextFromImage(uri)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
            }.getOrNull() ?: "dokument_${System.currentTimeMillis()}"
            if (mimeType.startsWith("image/", ignoreCase = true)) {
                viewModel.extractTextFromImage(uri)
            } else {
                viewModel.extractTextFromDocument(uri, displayName, mimeType)
            }
        }
    }

    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val merged = if (inputText.isBlank()) spoken else "$inputText $spoken"
                viewModel.onQueryChange(merged.trim())
            }
        }
    }

    LaunchedEffect(records.size) {
        if (records.isNotEmpty() && scrollToId == null) {
            listState.animateScrollToItem(records.lastIndex)
        }
    }

    LaunchedEffect(scrollToId, records) {
        val targetId = scrollToId ?: return@LaunchedEffect
        val index = records.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            viewModel.clearScrollJump()
        }
    }

    cognitiveError?.let { err ->
        RobustnessGuardModal(
            errorMessage = err,
            onDismiss = { viewModel.dismissError() },
            onSelfRepair = { viewModel.triggerSelfRepair() }
        )
    }

    if (showModularSheet) {
        OmnisModularBottomSheet(
            onDismiss = { showModularSheet = false },
            onInjectToChat = { injected ->
                viewModel.onQueryChange(injected)
                showModularSheet = false
            },
            latestRecords = records
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OmnisBgDark)
    ) {
        // Živý stav kaskády modelů / backend proxy / jističe přímo v chatu
        ChatStatusBanner(
            userRole = currentRole,
            userExperienceMode = userExperienceMode,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (records.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = OmnisPanelDark.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, OmnisBorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(OmnisCyan.copy(alpha = 0.3f), OmnisMagenta.copy(alpha = 0.3f))
                                                )
                                            )
                                            .border(1.dp, OmnisCyan, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Ω", color = OmnisCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "O.M.N.I.S. AKGE-8D",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Aktivní jádro • Připraveno k analýze",
                                            color = OmnisEmerald,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Surface(
                                    color = OmnisEmerald.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, OmnisEmerald)
                                ) {
                                    Text(
                                        text = "READY",
                                        color = OmnisEmerald,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val quickPrompts = listOf(
                                "💡 Celková analýza" to "Zahájit celkovou analýzu stavu O.M.N.I.S. infrastruktury",
                                "🎯 Aktivní cíle & 8D" to "Jaké jsou mé aktivní cíle a stav 8D matice?",
                                "⚖️ Analýza rizik" to "Srovnávací vyhodnocení ekonomických a bezpečnostních rizik",
                                "🛡️ Bezpečnostní test" to "Test autonomních jističů a bezpečnostních bariér"
                            )

                            quickPrompts.forEach { (label, prompt) ->
                                Surface(
                                    onClick = { viewModel.sendQuery(prompt) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = OmnisPanelDark,
                                    border = BorderStroke(0.8.dp, OmnisBorderDark)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    ChatMessageItem(
                        record = record,
                        onQuickQuery = { query -> viewModel.sendQuery(query) },
                        onSpeak = onSpeak
                    )
                }
            }
        }

        stagedAttachment?.let { attachment ->
            Surface(
                color = OmnisPanelDark,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, OmnisCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("staged_share_attachment_chip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (attachment.isPdf) Icons.Default.PictureAsPdf else Icons.Default.AttachFile,
                            contentDescription = "Přiložený sdílený soubor",
                            tint = if (attachment.isPdf) OmnisAmber else OmnisCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        val labelText = if (stagedAttachments.size > 1) {
                            "${attachment.displayName} (+${stagedAttachments.size - 1} další)"
                        } else {
                            attachment.displayName
                        }
                        Text(
                            text = labelText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearStagedChatAttachment() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("remove_staged_attachment_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Odebrat přílohu",
                            tint = OmnisTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (showLegalBar) {
            ChatLegalStatusBar(
                onOpenAudit = { showModularSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 1.dp)
            )
        }

        ChatInputBar(
            inputQuery = inputText,
            onQueryChange = viewModel::onQueryChange,
            onSend = {
                if (inputText.isNotBlank() || stagedAttachment != null) {
                    viewModel.sendQuery(
                        query = inputText,
                        attachedImagePath = stagedAttachment?.localCachePath
                    )
                }
            },
            onAttachFile = {
                runCatching {
                    documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                }.onFailure {
                    showModularSheet = true
                }
            },
            onCameraClick = {
                runCatching {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }.onFailure {
                    showModularSheet = true
                }
            },
            onMicClick = {
                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Nadiktujte dotaz pro O.M.N.I.S.")
                }
                runCatching {
                    speechInputLauncher.launch(speechIntent)
                }.onFailure {
                    onSpeak("Hlasové diktování není na tomto zařízení dostupné.")
                }
            },
            isLoading = isLoading,
            isOcrLoading = isOcrLoading,
            hasStagedAttachment = stagedAttachment != null,
            onOpenModules = { showModularSheet = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
