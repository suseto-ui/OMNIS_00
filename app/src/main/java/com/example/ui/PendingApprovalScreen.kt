package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OmnisAmber
import com.example.ui.theme.OmnisBgDark
import com.example.ui.theme.OmnisBorderDark
import com.example.ui.theme.OmnisCardDark
import com.example.ui.theme.OmnisCyan
import com.example.ui.theme.OmnisEmerald
import com.example.ui.theme.OmnisPanelDark
import com.example.ui.theme.OmnisTextLight
import com.example.ui.theme.OmnisTextMuted
import com.example.ui.theme.OmnisViolet

data class PendingApprovalInfo(
    val username: String,
    val email: String,
    val message: String
)

private data class PreviewTopic(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * Přehledná obrazovka zobrazená ihned po odeslání žádosti o registraci nebo při
 * pokusu o přihlášení k neschválenému účtu. Uživatel si zde může projít účel systému,
 * doporučené postupy práce a lehký náhled toho, co ho čeká po schválení administrátorem.
 */
@Composable
fun PendingApprovalScreen(
    info: PendingApprovalInfo,
    onBackToLogin: () -> Unit
) {
    BackHandler(enabled = true) {
        onBackToLogin()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Účel aplikace", "Správné praktiky", "Co vás čeká")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(OmnisBgDark, Color(0xFF0A1622), OmnisBgDark)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("pending_approval_screen")
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = OmnisPanelDark,
                    border = BorderStroke(1.dp, OmnisAmber.copy(alpha = 0.65f)),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(OmnisAmber.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Čeká na schválení",
                                    tint = OmnisAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ŽÁDOST ČEKÁ NA SCHVÁLENÍ",
                                    color = OmnisAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = "Přístup je před vytvořením aktivní relace ověřován správcem",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OmnisBgDark,
                            border = BorderStroke(1.dp, OmnisBorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = info.message,
                                    color = OmnisTextLight,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                if (info.username.isNotBlank() || info.email.isNotBlank()) {
                                    val accountDesc = listOfNotNull(
                                        info.username.takeIf { it.isNotBlank() },
                                        info.email.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    Text(
                                        text = "Účet v žádosti: $accountDesc",
                                        color = OmnisCyan,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Browsable Preview Tabs
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = OmnisPanelDark,
                    border = BorderStroke(1.dp, OmnisBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            val tag = when (index) {
                                0 -> "pending_tab_purpose"
                                1 -> "pending_tab_practices"
                                else -> "pending_tab_features"
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OmnisCyan.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, OmnisCyan) else null,
                                onClick = { selectedTab = index },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag(tag)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color.White else OmnisTextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Preview Content
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "pending_preview_content"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> PurposePreviewSection()
                        1 -> BestPracticesPreviewSection()
                        else -> UpcomingFeaturesPreviewSection()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Action Bar
            Button(
                onClick = onBackToLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmnisCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(50.dp)
                    .testTag("pending_back_to_login_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ZPĚT NA PŘIHLÁŠENÍ / OVĚŘIT STAV",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PurposePreviewSection() {
    val items = listOf(
        PreviewTopic(
            title = "Proč O.M.N.I.S. existuje",
            subtitle = "Pomáhá dělat promyšlená rozhodnutí u složitých témat, kde se prolínají technologie, ekonomika, právo, bezpečnost i lidský dopad.",
            icon = Icons.Default.Explore,
            accent = OmnisCyan
        ),
        PreviewTopic(
            title = "Souvislosti na jednom místě",
            subtitle = "Místo roztříštěných poznámek vidíte okamžitý 8-směrný pohled na dopady vašeho nápadu nebo problému bez zbytečné informační výplně.",
            icon = Icons.Default.Radar,
            accent = OmnisViolet
        ),
        PreviewTopic(
            title = "Soukromí a kontrolovaný přístup",
            subtitle = "Každý účet prochází ručním schválením, aby pracovní prostředí i konverzační vlákna zůstaly bezpečné a přehledné.",
            icon = Icons.Default.Shield,
            accent = OmnisEmerald
        )
    )
    PreviewTopicCardList(
        header = "ZÁKLADNÍ ORIENTACE",
        topics = items
    )
}

@Composable
private fun BestPracticesPreviewSection() {
    val items = listOf(
        PreviewTopic(
            title = "1. Formulujte kontext a cíl",
            subtitle = "Nejlepší výsledky získáte, když do dotazu stručně uvedete cíl, rozpočet nebo omezení, se kterými pracujete.",
            icon = Icons.Default.Lightbulb,
            accent = OmnisAmber
        ),
        PreviewTopic(
            title = "2. Rozdělujte témata do vláken",
            subtitle = "Pro každý projekt nebo téma si založte vlastní vlákno v levém menu — systém si pak přesně drží kontext dané oblasti.",
            icon = Icons.Default.Psychology,
            accent = OmnisCyan
        ),
        PreviewTopic(
            title = "3. Využívejte návazné otázky",
            subtitle = "Po každé odpovědi dostanete doporučené další kroky a doplňující dotazy, kterými lze analýzu rychle zpřesnit.",
            icon = Icons.Default.AutoAwesome,
            accent = OmnisEmerald
        )
    )
    PreviewTopicCardList(
        header = "JAK Z APLIKACE VYTĚŽIT MAXIMUM",
        topics = items
    )
}

@Composable
private fun UpcomingFeaturesPreviewSection() {
    val items = listOf(
        PreviewTopic(
            title = "Kognitivní Chat & Analýza",
            subtitle = "Hlavní pracovní plocha pro dotazy, nahrávání podkladů a přehledné strukturované syntézy.",
            icon = Icons.Default.AutoAwesome,
            accent = OmnisCyan
        ),
        PreviewTopic(
            title = "Scénáře, Cíle & Paměť",
            subtitle = "Možnost porovnat varianty vývoje, ukládat klíčové poznatky do paměti a vracet se k nim v dalších projektech.",
            icon = Icons.Default.Timeline,
            accent = OmnisViolet
        ),
        PreviewTopic(
            title = "Spolehlivost i bez signálu",
            subtitle = "Vaše lokální historie zůstává šifrovaná v zařízení a po obnovení spojení se bezpečně synchronizuje.",
            icon = Icons.Default.LockClock,
            accent = OmnisEmerald
        )
    )
    PreviewTopicCardList(
        header = "PŘEDBĚŽNÝ NÁHLED PO AKTIVACI",
        topics = items
    )
}

@Composable
private fun PreviewTopicCardList(
    header: String,
    topics: List<PreviewTopic>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = header,
            color = OmnisTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        topics.forEach { topic ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = OmnisCardDark,
                border = BorderStroke(1.dp, topic.accent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(topic.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = topic.icon,
                            contentDescription = null,
                            tint = topic.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = topic.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = topic.subtitle,
                            color = OmnisTextLight.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
