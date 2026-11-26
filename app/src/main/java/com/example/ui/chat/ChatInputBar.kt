package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gateway.SemanticQuerySynthesizer
import com.example.ui.theme.OmnisStyleSheet

/**
 * Vstupní lišta chatu O.M.N.I.S. s inteligentním Pre-flight filtrem
 * a automatickým sémantickým zpřesňovačem vágních dotazů.
 */
@Composable
fun ChatInputBar(
    inputQuery: String,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    isLoading: Boolean,
    isOcrLoading: Boolean,
    isListening: Boolean = false,
    hasStagedAttachment: Boolean = false,
    onOpenModules: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val canSend = (inputQuery.isNotBlank() || hasStagedAttachment) && !isLoading && !isOcrLoading
    val haptic = LocalHapticFeedback.current

    // Pre-flight analýza vágnosti vstupu v reálném čase
    val preflightAnalysis = remember(inputQuery) {
        SemanticQuerySynthesizer.analyzeInputPreflight(inputQuery)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        // Pre-flight Guidance Strip (Zobrazí se při detekci vágnosti či neúplného dotazu)
        AnimatedVisibility(
            visible = preflightAnalysis.isVague && preflightAnalysis.suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = OmnisStyleSheet.Colors.PanelBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, OmnisStyleSheet.Colors.CyanAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("preflight_guidance_strip")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = OmnisStyleSheet.Colors.CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "NÁPOVĚDA PRO ZPŘESNĚNÍ VSTUPU:",
                            color = OmnisStyleSheet.Colors.CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (!preflightAnalysis.guidanceHint.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = preflightAnalysis.guidanceHint,
                            color = OmnisStyleSheet.Colors.TextMuted,
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // 2-3 Interaktivní tlačítka / čipy pro okamžitou sémantickou fúzi
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        preflightAnalysis.suggestions.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = OmnisStyleSheet.Colors.InputBackground,
                                border = BorderStroke(0.6.dp, OmnisStyleSheet.Colors.CyanAccent.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        // Inteligentně přepíše dotaz do ucelené a věcné podoby
                                        val refined = SemanticQuerySynthesizer.synthesizeRefinedQuery(inputQuery, suggestion)
                                        onQueryChange(refined)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "+",
                                        color = OmnisStyleSheet.Colors.CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = suggestion.label,
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hlavní vstupní lišta
        Surface(
            color = OmnisStyleSheet.Colors.PanelBackground,
            shape = OmnisStyleSheet.Shapes.input,
            border = OmnisStyleSheet.Borders.subtle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachFile,
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Přiložit soubor",
                        tint = OmnisStyleSheet.Colors.CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Vyfotit/Obrázek",
                        tint = OmnisStyleSheet.Colors.CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onOpenModules != null) {
                    IconButton(
                        onClick = onOpenModules,
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("open_modular_bottom_sheet_btn")
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Kognitivní moduly a audit",
                            tint = OmnisStyleSheet.Colors.CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .background(OmnisStyleSheet.Colors.InputBackground, OmnisStyleSheet.Shapes.input)
                        .border(1.dp, OmnisStyleSheet.Colors.BorderMuted, OmnisStyleSheet.Shapes.input)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = inputQuery,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            color = OmnisStyleSheet.Colors.TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(OmnisStyleSheet.Colors.CyanAccent),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend) {
                                    onSend()
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_input_field"),
                        decorationBox = { innerTextField ->
                            if (inputQuery.isEmpty()) {
                                Text(
                                    text = if (isListening) "Poslouchám váš hlas..." else "Zadejte cíl nebo dotaz...",
                                    color = if (isListening) OmnisStyleSheet.Colors.CyanAccent else OmnisStyleSheet.Colors.TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isListening) Color(0x33FF0055) else Color.Transparent)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = if (isListening) "Zastavit nahrávání" else "Hlasový vstup",
                        tint = if (isListening) Color(0xFFFF2A6D) else OmnisStyleSheet.Colors.CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (canSend) {
                            onSend()
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend)
                                OmnisStyleSheet.Colors.CyanAccent
                            else
                                OmnisStyleSheet.Colors.BorderMuted
                        )
                        .testTag("send_query_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Odeslat dotaz",
                        tint = if (canSend)
                            Color.Black
                        else
                            OmnisStyleSheet.Colors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
