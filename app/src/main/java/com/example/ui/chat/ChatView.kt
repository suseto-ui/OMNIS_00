package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OmnisRecord
import com.example.ui.OmnisTab
import com.example.ui.OmnisViewModel
import com.example.ui.bottomsheet.OmnisModularBottomSheet
import com.example.ui.components.RobustnessGuardModal
import com.example.ui.components.ContextualWhisperer
import com.example.ui.theme.*

@Composable
fun ChatView(
    viewModel: OmnisViewModel,
    onSpeak: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatViewModel = viewModel.chatViewModel
    val records by chatViewModel.records.collectAsStateWithLifecycle()
    val cognitiveError by viewModel.cognitiveError.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showModularSheet by remember { mutableStateOf(false) }
    val showLegalBar by ChatDisplayPreferences.showLegalStatusBarAboveInput.collectAsStateWithLifecycle()
    val cyberGlassEnabled by ChatDisplayPreferences.cyberGlassEffectEnabled.collectAsStateWithLifecycle()
    
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
                inputText = injected
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
        ContextualWhisperer(
            text = "INJEKČNÍ PŘIPRAVENOST: Zadejte systémovou hypotézu či mezní podmínku pro zahájení 4-agentní dialektiky a 8D tenzorové dekompozice.",
            visible = records.isEmpty()
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (records.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compact Top Cyber Header
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = OmnisPanelDark.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(OmnisCyan.copy(alpha = 0.3f), OmnisMagenta.copy(alpha = 0.3f))
                                                )
                                            )
                                            .border(1.dp, OmnisCyan, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Ω", color = OmnisCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text(
                                            text = "O.M.N.I.S. AKGE-8D",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Aktivní jádro • Prahová hodnota 0.75",
                                            color = OmnisEmerald,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Surface(
                                    color = OmnisEmerald.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, OmnisEmerald)
                                ) {
                                    Text(
                                        text = "READY",
                                        color = OmnisEmerald,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Compact Horizontal Quick Prompt Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickPrompts = listOf(
                                "💡 Celková analýza" to "Zahájit celkovou analýzu stavu O.M.N.I.S. infrastruktury",
                                "🎯 Aktivní cíle & 8D" to "Jaké jsou mé aktivní cíle a stav 8D matice?",
                                "⚖️ Analýza rizik" to "Srovnávací vyhodnocení ekonomických a bezpečnostních rizik",
                                "🛡️ Bezpečnostní test" to "Test autonomních jističů a bezpečnostních bariér",
                                "⚡ E-Waste Super-Prompt" to "Anatomie perfektního super-promptu. Definice role a expertizy: Modelu nastavíte identitu špičkového hutního inženýra, chemického prospektora a analytika recyklačního průmyslu e-waste. Kategorizace vstupního materiálu: Zamezíte vynechání jakékoliv kategorie – prompt musí striktně požadovat pokrytí od spotřební elektroniky přes průmyslová zařízení až po telekomunikace. Specifikace sledovaných kovů: Explicitně vyjmenujete drahé i vzácné kovy (Au, Ag, Pd, Pt, Cu, Ta, Nd, In), aby model nezůstal jen u zlata. Požadovaná datová struktura: Nařídíte přesný formát výstupu (metrické jednotky, konverze g/t, g/ks, procentuální podíly a tržní ocenění). Metodika získávání: Vynutíte detailní přehled separačních a rafinačních technik (pyrometalurgie, hydrometalurgie, biohydrometalurgie, elektrochemie) včetně chemických činidel a bezpečnosti. Bariéry a negativní pravidla (Negative Constraints): Zákaz zkratek jako \"a tak dále\", \"další běžné komponenty\", povinnost rozepsat vše do nejmenších detailů."
                            )

                            quickPrompts.forEach { (label, prompt) ->
                                Surface(
                                    onClick = {
                                        chatViewModel.sendQuery(prompt)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = OmnisPanelDark,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp, 
                                        if (label.contains("E-Waste")) OmnisMagenta else OmnisBorderDark
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        color = if (label.contains("E-Waste")) OmnisMagenta else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(records) { record ->
                    ChatMessageItem(
                        record = record,
                        onQuickQuery = { query ->
                            chatViewModel.sendQuery(query)
                        },
                        onSpeak = onSpeak
                    )
                }
            }
        }

        // Minimalistický prolínající se právní stav nad polem vstupu
        if (showLegalBar) {
            ChatLegalStatusBar(
                onOpenAudit = { showModularSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 1.dp)
            )
        }

        // Plynulé plovoucí pole vstupu s inteligentním Pre-flight filtrem
        ChatInputBar(
            inputQuery = inputText,
            onQueryChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    chatViewModel.sendQuery(inputText)
                    inputText = ""
                }
            },
            onAttachFile = { showModularSheet = true },
            onCameraClick = { showModularSheet = true },
            onMicClick = {
                onSpeak("Hlasový modul O.M.N.I.S. připraven k poslechu.")
            },
            isLoading = chatViewModel.isLoading.collectAsStateWithLifecycle().value,
            isOcrLoading = chatViewModel.isOcrLoading.collectAsStateWithLifecycle().value,
            onOpenModules = { showModularSheet = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
