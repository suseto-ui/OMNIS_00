package com.example.ui.beginner

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.AppLocaleManager
import com.example.ui.theme.*

data class BeginnerGuideStep(
    val stepNumber: Int,
    val titleCs: String,
    val titleEn: String,
    val descriptionCs: String,
    val descriptionEn: String,
    val tipCs: String,
    val tipEn: String,
    val icon: ImageVector,
    val color: Color
)

private val guideSteps = listOf(
    BeginnerGuideStep(
        stepNumber = 1,
        titleCs = "1. Jak se jednoduše zeptat rádce",
        titleEn = "1. How to easily ask the assistant",
        descriptionCs = "Zeptejte se úplně běžnou lidskou řečí, diktujte hlasem nebo přiložte dokument či fotku. Ochranný štít Gatekeeper G1–G6 automaticky ohlídá bezpečnost vašeho zadání.",
        descriptionEn = "Ask in plain language, use voice input, or attach a document or photo. The Gatekeeper G1–G6 shield automatically keeps your request safe.",
        tipCs = "Tip: Vyzkoušejte: 'Chci změnit zaměstnání, na co si dát pozor a jaká jsou rizika?'",
        tipEn = "Tip: Try asking: 'I want to change jobs, what risks and benefits should I consider?'",
        icon = Icons.AutoMirrored.Filled.Chat,
        color = OmnisCyan
    ),
    BeginnerGuideStep(
        stepNumber = 2,
        titleCs = "2. Srozumitelné a ověřené odpovědi",
        titleEn = "2. Clear and verified answers",
        descriptionCs = "V režimu pro začátečníky rádce nepoužívá složité vzorce ani žargon. Každá odpověď před zobrazením projde kontrolou kvality a při výpadku sítě se přepne do bezpečného offline režimu.",
        descriptionEn = "In Beginner mode, the assistant avoids heavy formulas and jargon. Every answer passes quality verification before display and seamlessly falls back offline if needed.",
        tipCs = "Tip: Barevné štítky vám ukáží dotčené oblasti a v záložce Debata můžete porovnat pohledy více specialistů.",
        tipEn = "Tip: Colored badges show affected areas, and the Debate tab lets you compare multiple specialist perspectives.",
        icon = Icons.Default.Psychology,
        color = OmnisEmerald
    ),
    BeginnerGuideStep(
        stepNumber = 3,
        titleCs = "3. Záznam a sledování vašich cílů",
        titleEn = "3. Recording and tracking your goals",
        descriptionCs = "V části 'Moje cíle' si snadno zapíšete, čeho chcete dosáhnout. Vaše konverzace i cíle jsou chráněny šifrovaným úložištěm na zařízení a připraveny na zálohování.",
        descriptionEn = "In 'My Goals', easily track what you want to achieve. Your conversations and goals are protected by encrypted on-device storage.",
        tipCs = "Tip: V 'Jednoduchém přehledu' máte všechny rozdělané úkoly, uložené artefakty i poslední shrnutí pohromadě.",
        tipEn = "Tip: In 'Quick Overview', all your active tasks, saved artifacts, and latest insights stay in one place.",
        icon = Icons.Default.Flag,
        color = Color(0xFF8B5CF6)
    )
)

/**
 * Uživatelsky přívětivý průvodce krok za krokem pro začínající uživatele.
 */
@Composable
fun BeginnerGuideDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLang by AppLocaleManager.currentLanguage.collectAsState()
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = guideSteps[currentStepIndex]

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = OmnisPanelDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, step.color.copy(alpha = 0.6f)),
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("beginner_guide_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ikona kroku
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(step.color.copy(alpha = 0.18f))
                        .border(1.5.dp, step.color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = step.color,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Titulek
                Text(
                    text = if (currentLang == AppLanguage.EN) step.titleEn else step.titleCs,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Popis
                Text(
                    text = if (currentLang == AppLanguage.EN) step.descriptionEn else step.descriptionCs,
                    color = OmnisTextLight,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tip Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OmnisBgDark,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, step.color.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (currentLang == AppLanguage.EN) step.tipEn else step.tipCs,
                            color = Color(0xFFF3F4F6),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Indikátor teček (kroků)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    guideSteps.forEachIndexed { idx, s ->
                        val isCurrent = idx == currentStepIndex
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) s.color else OmnisBorderDark)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Tlačítka navigace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OmnisTextLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (currentLang == AppLanguage.EN) "Back" else "Zpět", fontSize = 11.sp)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(if (currentLang == AppLanguage.EN) "Skip" else "Přeskočit", color = OmnisTextMuted, fontSize = 11.sp)
                        }
                    }

                    if (currentStepIndex < guideSteps.size - 1) {
                        Button(
                            onClick = { currentStepIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = step.color, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (currentLang == AppLanguage.EN) "Next" else "Další", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = OmnisEmerald, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (currentLang == AppLanguage.EN) "Got it! Start" else "Rozumím, začít!", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
