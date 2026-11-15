package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class TourStep(
    val stepIndex: Int,
    val title: String,
    val tag: String,
    val description: String,
    val technicalNote: String,
    val iconEmoji: String
)

private val KYBER_TOUR_STEPS = listOf(
    TourStep(
        stepIndex = 1,
        title = "Kognitivní Injekce & Pre-flight",
        tag = "CHAT / THESIS ENGINE",
        description = "Zadávání systémových dotazů a tezí. Pre-flight filtr vyhodnocuje sémantickou čistotu (D1), entropii (D2) a zabraňuje halucinacím před odesláním do modelu.",
        technicalNote = "G1-G2 Bezpečnostní brána • Prahová shoda 0.75 • Proof of Necessity",
        iconEmoji = "⚡"
    ),
    TourStep(
        stepIndex = 2,
        title = "4-Agentní Oponentní Dialektika",
        tag = "DIALECTIC ENGINE 30s",
        description = "Architekt, Skeptik, Regulátor a Inženýr vedou nelítostnou oponentní debatu. Výsledkem je věcně oponované, deterministické finální rozhodnutí.",
        technicalNote = "Sekundární a terciární kauzální smyčky • Stresové testování Černých labutí",
        iconEmoji = "⚖️"
    ),
    TourStep(
        stepIndex = 3,
        title = "8D Tenzorová Matice & Octagon",
        tag = "AKGE-8D INFERENCE",
        description = "Multidimenzionální vizualizace 8 provozních os: D1 Záměr, D2 Entropie, D3 Čas, D4 Zdroje, D5 Riziko, D6 Konsenzus, D7 Oprávnění, D8 Predikční horizont.",
        technicalNote = "Tenzorový vektor D1-D8 • Interaktivní radarový visualizer",
        iconEmoji = "🔮"
    ),
    TourStep(
        stepIndex = 4,
        title = "Kauzální Simulátor do(X)",
        tag = "PEARL CAUSAL ENGINE",
        description = "Modelování systémových dopadů v reálném čase. Pomocí operátoru do(X) simulujete kaskádové efekty na stav celého ekosystému a eliminujete skryté zátěže.",
        technicalNote = "Kauzální graf CSR • Vyhodnocení P(Y | do(X=x)) • NIS2 Readiness",
        iconEmoji = "🌐"
    ),
    TourStep(
        stepIndex = 5,
        title = "Kognitivní Reputace & Odznaky",
        tag = "AUDIT HASH & REPUTATION",
        description = "Za každou dialektickou syntézu, kaskádovou analýzu a prověření Černé labutě získáváte kryptograficky ověřitelné invariantní odznaky a zvyšujete svůj KQP kredit.",
        technicalNote = "CommitmentHash = HMAC-SHA256 Hash-Chain • Odznaky v reálném čase",
        iconEmoji = "🛡️"
    )
)

/**
 * Interaktivní celoobrazovkový průvodce (Cybernetic Tour Overlay).
 * Automaticky nabízí novým uživatelům seznámení se systémem O.M.N.I.S.,
 * s možností kdykoliv průvodce přeskočit nebo přepínat mezi kroky.
 */
@Composable
fun CyberTourOverlay(
    isVisible: Boolean,
    onDismissTour: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    var currentStep by remember { mutableIntStateOf(0) }
    val step = KYBER_TOUR_STEPS[currentStep]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(enabled = false) { /* zamezení prokliku na spodní vrstvu */ }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = OmnisPanelDark,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OmnisCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Horní lišta s tlačítkem přeskočit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = OmnisCyan.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OmnisCyan)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(step.iconEmoji, fontSize = 16.sp)
                            }
                        }

                        Column {
                            Text(
                                text = "PROTOKOL ONBOARDING [${currentStep + 1}/${KYBER_TOUR_STEPS.size}]",
                                color = OmnisCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = step.tag,
                                color = OmnisMagenta,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissTour,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Přeskočit průvodce",
                            tint = OmnisTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nadpis kroku
                Text(
                    text = step.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Popis
                Text(
                    text = step.description,
                    color = OmnisTextLight,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Default
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Technická poznámka (KYBER-AI nota)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, OmnisBorderDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = OmnisEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = step.technicalNote,
                            color = OmnisEmerald,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Krokovače a akční tlačítka
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indikátory teček
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KYBER_TOUR_STEPS.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentStep) 18.dp else 7.dp, 7.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (index == currentStep) OmnisCyan else OmnisBorderDark
                                    )
                            )
                        }
                    }

                    // Tlačítka Další / Spustit
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismissTour,
                            colors = ButtonDefaults.textButtonColors(contentColor = OmnisTextMuted)
                        ) {
                            Text("PŘESKOČIT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                if (currentStep < KYBER_TOUR_STEPS.size - 1) {
                                    currentStep++
                                } else {
                                    onDismissTour()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OmnisCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (currentStep < KYBER_TOUR_STEPS.size - 1) "DALŠÍ >>" else "AKTIVOVAT JÁDRO",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
