package com.example.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.UserRole
import com.example.ui.theme.*

@Composable
fun MethodologyView(userRole: UserRole) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Metodika, 1: Manuál Modulů, 2: FAQ & Průvodce
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OmnisBgDark)
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("methodology_view_root")
    ) {
        HeaderSection(userRole)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(OmnisPanelDark)
                .border(1.dp, OmnisBorderDark, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GuideTabButton(
                title = if (userRole == UserRole.ADMIN_OPERATOR) "Metodika SIGMA (Admin)" else "Metodika SIGMA",
                icon = Icons.Default.VerifiedUser,
                isSelected = selectedSection == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedSection = 0 }
            )
            GuideTabButton(
                title = "Manuál Modulů",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                isSelected = selectedSection == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedSection = 1 }
            )
            GuideTabButton(
                title = "FAQ & Nápověda",
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                isSelected = selectedSection == 2,
                modifier = Modifier.weight(1f),
                onClick = { selectedSection = 2 }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (selectedSection) {
            0 -> SigmaMethodologySection(userRole)
            1 -> FullModulesManualSection(userRole)
            2 -> FaqAndTooltipsSection(userRole)
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun GuideTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) OmnisCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) OmnisCyan else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) OmnisCyan else OmnisTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = if (isSelected) Color.White else OmnisTextMuted,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HeaderSection(userRole: UserRole) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OmnisCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = OmnisCyan, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    if (userRole == UserRole.ADMIN_OPERATOR) "DOKUMENTACE OPERÁTORA O.M.N.I.S." else "UŽIVATELSKÁ DOKUMENTACE O.M.N.I.S.",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    if (userRole == UserRole.ADMIN_OPERATOR) 
                        "Systémová příručka, pokročilá metodika SIGMA a diagnostické postupy."
                    else 
                        "Kompletní uživatelská příručka, základy metodiky a popis modulů.",
                    color = OmnisTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SigmaMethodologySection(userRole: UserRole) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MethodologyCard(
            title = if (userRole == UserRole.ADMIN_OPERATOR) "ADMINISTRÁTORSKÝ RÁMEC" else "O.M.N.I.S. OPERAČNÍ RÁMEC",
            icon = Icons.Default.Architecture,
            content = if (userRole == UserRole.ADMIN_OPERATOR)
                "O.M.N.I.S. z pohledu operátora slouží k hloubkové systémové diagnostice, správě kognitivních tenzorů a dohledu nad stabilitou 8D matice v reálném čase."
            else
                "O.M.N.I.S. představuje kognitivní nástroj navržený pro hloubkovou analýzu a simulaci komplexních úloh v 8 klíčových dimenzích pro běžné uživatelské potřeby."
        )

        SectionHeader("Klíčové Principy (SIGMA)")

        SigmaCard(
            title = "DETERMINISMUS",
            description = "Automatizovaná verifikace vstupů i výstupů (Dual-Gate) proti systémovým invariantům.",
            icon = Icons.Default.VerifiedUser,
            color = OmnisCyan
        )

        SigmaCard(
            title = "GATEKEEPER G1–G6 (PŘED MATICÍ)",
            description = "Sekvenční vrstva G1–G6 před 8D maticí dopadů: každá brána zapisuje do auditního logu a při NaN, Inf nebo nulovém vektoru řetězec okamžitě přeruší (GatekeeperChainException).",
            icon = Icons.Default.Security,
            color = OmnisEmerald
        )

        if (userRole == UserRole.ADMIN_OPERATOR) {
            SigmaCard(
                title = "SYSTÉMOVÝ JISTIČ (BREAKER)",
                description = "Ochrana před přetížením a kaskádovým selháním včetně automatického HTTP 429 Retry-After cooldownu (5–300 s) a stavu HALF-OPEN.",
                icon = Icons.Default.ElectricBolt,
                color = Color.Red
            )
        }

        SigmaCard(
            title = "8D MATICE DOPADU",
            description = "Multidimenzionální hodnocení v osách SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC (Systém, Ekonomie, Kognice, Ekologie, Právo, Bezpečnost, Fyzika, Společnost).",
            icon = Icons.Default.Layers,
            color = OmnisViolet
        )

        SectionHeader(if (userRole == UserRole.ADMIN_OPERATOR) "Protokoly Operátora" else "Metodické Postupy Uživatele")

        BestPracticeItem(
            number = "01",
            title = if (userRole == UserRole.ADMIN_OPERATOR) "Systémový Dohled" else "Sémantická Přesnost",
            description = if (userRole == UserRole.ADMIN_OPERATOR)
                "Pravidelně kontrolujte stabilitu tenzorů v Admin Hubu a reagujte na výstrahy v Telemetrii."
            else
                "Definujte dotazy s vysokou specificitou pro dosažení maximální přesnosti odpovědí."
        )

        if (userRole == UserRole.ADMIN_OPERATOR) {
            BestPracticeItem(
                number = "02",
                title = "Krizové Scénáře",
                description = "Při simulaci událostí s vysokým rizikem (např. 85%+ nestabilita) vždy aktivujte manuální revizi Prompt Gateway."
            )
        } else {
            BestPracticeItem(
                number = "02",
                title = "Iterativní Refaktoring",
                description = "Rozdělujte velké úkoly na menší podúkoly v modulu Autonomní Cíle."
            )
        }
    }
}

@Composable
private fun FullModulesManualSection(userRole: UserRole) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(if (userRole == UserRole.ADMIN_OPERATOR) "SYSTÉMOVÁ PŘÍRUČKA MODULŮ (ADMIN / OPERÁTOR)" else "UŽIVATELSKÁ PŘÍRUČKA MODULŮ")

        ExpandableManualModule(
            title = "1. Operační Kokpit, Sémantická Brána & Gatekeeper G1–G6",
            icon = Icons.AutoMirrored.Filled.Chat,
            color = OmnisCyan,
            summary = "Primární rozhraní kognitivní interakce s Dual-Gate ochranou vstupu i výstupu a sekvenční vrstvou Gatekeeper G1–G6.",
            details = if (userRole == UserRole.ADMIN_OPERATOR) listOf(
                "Sémantická Brána & Dual-Gate G1–G6: Kontroluje vstupní dotaz i vygenerovanou odpověď, povyšuje nejasné prompty na standard [Doména] + [Akce] + [Kritérium] a při nedostupnosti sítě přepíná na lokální fallback.",
                "Gatekeeper G1–G6 před Maticí Dopadů: Postupně validuje a sanitizuje data přes všech 6 bran (G1–G6), každou operaci zaznamenává do auditního systému a při NaN, Inf nebo nulovém vektoru řetězec přeruší výjimkou GatekeeperChainException.",
                "Multimodální Photo Picker, OCR & STT: Bezpečný výběr médií přes Android Photo Picker (bez širokých oprávnění úložiště), OCR dokumentů a hlasové diktování.",
                "Stavový Banner & Autorizace: ChatStatusBanner zobrazuje přesný cooldown při HTTP 429 (5–300 s); admin může v kontextu autorizovat pozastavené záznamy."
            ) else listOf(
                "Sémantický Chat & Dual-Gate: Zadávejte strukturované dotazy podle vzoru [Doména] + [Akce] + [Kritérium]; vstup i výstup hlídá štít Gatekeeper G1–G6.",
                "Photo Picker, OCR & Hlas: Obrázky vybíráte bezpečně přes systémový Photo Picker bez udělování oprávnění ke všem souborům, k dispozici je i kamera a diktování.",
                "Stav Sítě & Degraded Režim: Při přetížení (HTTP 429) zobrazí horní banner dobu čekání a automaticky aktivuje bezpečnou offline záložní větev."
            )
        )

        if (userRole == UserRole.ADMIN_OPERATOR) {
            ExpandableManualModule(
                title = "2. Dev Prompt Lab (Vývojářská Laboratoř)",
                icon = Icons.Default.Science,
                color = OmnisViolet,
                summary = "Izolované testovací prostředí pro ověřování odolnosti LLM a ladění sémantických prahů.",
                details = listOf(
                    "Testování Injekčních Útoků: Simulace a verifikace obrany proti Jailbreaku, nepřímo vloženým promptům (Indirect Injection) a exfiltraci systémových dat.",
                    "Benchmark Parametrů: Kalibrace hyperparametrů modelu (Temperature, Top-K, Top-P, Safety Thresholds).",
                    "Validace Sémantických Prahů: Měření úspěšnosti sémantického povýšení před nasazením do produkční fronty."
                )
            )

            ExpandableManualModule(
                title = "3. Reaktivní Akční Panel & Produkční Audit",
                icon = Icons.Default.PlayCircle,
                color = OmnisEmerald,
                summary = "Exekuce servisních operací nad jádrem O.M.N.I.S. a kompletní dohled v tabech Produkční Audit a Tensor Oversight.",
                details = listOf(
                    "Spouštění ActionPayload: Vyvolání diagnostických skriptů, rekalibrace tenzorů a údržba databáze (včetně pročištění starších záznamů).",
                    "Tensor Oversight & Produkční Audit: Přehled integrity 8D tenzorů, průchodů branami G1–G6 a kontrola kvality výstupů.",
                    "Bezpečnostní Zámek: Striktní Zero-Trust ověření identity a zápis každého zásahu do append-only auditního řetězce."
                )
            )

            ExpandableManualModule(
                title = "4. Admin Hub & 8D Tenzorová Matice",
                icon = Icons.Default.SettingsSuggest,
                color = OmnisAmber,
                summary = "Centrální řízení stability kognitivního systému a orchestrace 8 dimenzí dopadu chráněná branami G1–G6.",
                details = listOf(
                    "8D Matice Dopadu: Řízení vah dimenzí SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC po průchodu vrstvou Gatekeeper G1–G6.",
                    "Krizové Řízení: Výpočet koherence, kauzální simulace do(X) a predikce nestability ekosystému v reálném čase.",
                    "Orchestrace Tenzorů: Úprava váhových vektorů a kontrola integrity lokální DB vs. PostgreSQL 17 / ElectricSQL."
                )
            )

            ExpandableManualModule(
                title = "5. Circuit Breaker (Systémový Jistič & HTTP 429)",
                icon = Icons.Default.ElectricBolt,
                color = Color(0xFFEF4444),
                summary = "Fail-safe mechanismus chránící klientskou i serverovou LLM kaskádu před kaskádovým selháním a rate-limity.",
                details = listOf(
                    "Stavový Automat: CLOSED (normální provoz) -> OPEN (odpojení při opakovaném selhání nebo HTTP 429) -> HALF-OPEN (ověřovací sonda).",
                    "HTTP 429 Retry-After Cooldown: Jistič respektuje hlavičku Retry-After v bezpečném rozmezí 5–300 s a informuje uživatele přes ChatStatusBanner.",
                    "Manuální vypnutí a reset: Operátor může jistič okamžitě vypnout nebo autorizovaně resetovat po stabilizaci backendu."
                )
            )

            ExpandableManualModule(
                title = "6. Správa Kognitivní Paměti & Vektorové Fragmenty",
                icon = Icons.Default.Memory,
                color = OmnisCyan,
                summary = "Architektura pro dlouhodobé ukládání a 64D sémantické vyhledávání kognitivního kontextu.",
                details = listOf(
                    "64D Vektorové Úložiště: L2-normalizované fragmenty paměti v lokální tabulce omnis_local_vectors_64d a PostgreSQL pgvector.",
                    "Konsolidace Paměti: Spouštění syntézy a komprese starších fragmentů pro redukci kontextového šumu.",
                    "Sémantické Kotvy: Správa a rekalibrace vazeb mezi záznamy a kognitivními uzly."
                )
            )

            ExpandableManualModule(
                title = "7. Telemetrie & Auditní Stopa (ZK Ledger)",
                icon = Icons.Default.Assessment,
                color = OmnisAmber,
                summary = "Komplexní monitoring výkonu, průchodů branami G1–G6, bezpečnostních událostí a spotřeby zdrojů.",
                details = listOf(
                    "Auditní Logy & ZK Ledger: Neodstranitelný SHA-256 řetězec všech operací Gatekeeper G1–G6, výjimek GatekeeperChainException a zásahů operátora.",
                    "Metriky Latence: Trasování délky odezvy sítě, inferenčního času modelu a databázových operací.",
                    "Spotřeba Tokenů: Monitorování přidělených kvót a přepínání kaskády lokální/free providerů."
                )
            )
        }

        if (userRole == UserRole.STANDARD_USER) {
            ExpandableManualModule(
                title = "2. Autonomní Cíle & Kauzální Simulátor",
                icon = Icons.Default.Flag,
                color = OmnisEmerald,
                summary = "Sledování progrese projektů a interaktivní simulace intervencí do(X) v 8D matici.",
                details = listOf(
                    "Dekompozice & Priority: Tvorba podúkolů s automatickým výpočtem plnění.",
                    "Kauzální Simulátor & Dialektika: Testování dopadu změn napříč 8 dimenzemi před reálným nasazením.",
                    "Tensor Oversight: Přehled stability a skóre ověření každé odpovědi."
                )
            )
        }

        ExpandableManualModule(
            title = if (userRole == UserRole.ADMIN_OPERATOR) "8. Nexus & Multi-Agentní Debata" else "3. Nexus & Multi-Agentní Debata",
            icon = Icons.Default.Groups,
            color = OmnisViolet,
            summary = "Kolaborace a oponentní dialektika specializovaných agentů nad jedním zadáním.",
            details = listOf(
                "Multi-Agentní Debata: Paralelní pohled Architekta, Security experta, Ekonoma a Právního auditora.",
                "Topologie Sítě: Vizualizace hierarchie, uzlů a komunikačních kanálů mezi agenty.",
                "Synergie & Konsenzus: Výpočet kognitivní koherence a odhalování názorových rozporů před finální syntézou."
            )
        )

        ExpandableManualModule(
            title = if (userRole == UserRole.ADMIN_OPERATOR) "9. Exportní Engine & Room v19 SQLCipher" else "4. Export & Šifrované Úložiště",
            icon = Icons.Default.PictureAsPdf,
            color = OmnisCyan,
            summary = "Šifrovaná lokální perzistence (Room v19 + SQLCipher AES-256) a generování auditních reportů.",
            details = if (userRole == UserRole.ADMIN_OPERATOR) listOf(
                "PdfExportEngine: Generování auditních PDF protokolů a artefaktů s časovým razítkem relace.",
                "Room v19 + SQLCipher AES-256-CBC: Šifrovaná DB (omnis_local_vectors_64d, omnis_sync_outbox) chráněná Android Keystore.",
                "Sjednocená Synchronizace: Transakční outbox replikovaný do PostgreSQL 17 (pgvector + Apache AGE) a ElectricSQL 1.8.1."
            ) else listOf(
                "PDF & Artefakty: Export výsledků analýz a scénářů do přehledných dokumentů.",
                "Room v19 + SQLCipher: Vaše záznamy jsou na zařízení šifrovány pomocí AES-256 a bezpečně synchronizovány."
            )
        )

        if (userRole == UserRole.ADMIN_OPERATOR) {
            ExpandableManualModule(
                title = "10. SOP: Postup při hláškách SYSTEMS_INTELLIGENCE a doménových tazích",
                icon = Icons.Default.Info,
                color = OmnisEmerald,
                summary = "Standardní operační postup pro předcházení chybám při směrování kognitivních domén.",
                details = listOf(
                    "Význam tagu: [SYSTEMS_INTELLIGENCE] indikuje automatické směrování do domény architektury a stability jádra (není to chyba).",
                    "Krok 1 (Struktura promptu): Vždy zadejte [Akční sloveso] + [Modul/Komponenta] + [Kritérium] (např. '[SYSTEMS_INTELLIGENCE] Optimalizuj latenci dotazů a zkontroluj integritu tenzorů').",
                    "Krok 2 (Prompt Gateway Review): Pokud vyskočí okno s navrženým promptem, zkontrolujte text a klikněte na 'Převzít a odeslat' pro garantovanou validní exekuci.",
                    "Krok 3 (Filtry domén): Pokud vidíte pouze systémové záznamy, použijte 'Zrušit filtr domén' v horní části chatu pro obnovení kompletního toku.",
                    "Krok 4 (Krizová reakce): Při hlášce o rozepnutí jističe (Circuit Breaker OPEN) zkontrolujte telemetrii a proveďte autorizovaný reset jističe."
                )
            )
        }
    }
}

@Composable
private fun FaqAndTooltipsSection(userRole: UserRole) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("ČASTO KLADENÉ DOTAZY & NÁPOVĚDA")

        if (userRole == UserRole.ADMIN_OPERATOR) {
            FaqItem(
                question = "Co dělat, když systém vypíše SYSTEMS_INTELLIGENCE nebo jiný doménový tag?",
                answer = "Hláška typu [SYSTEMS_INTELLIGENCE] není chyba, ale sémantický doménový tag (Cognitive Routing). Systém zařadil váš dotaz do domény systémové architektury a orchestrace. Pro bezchybnou exekuci postupujte podle SOP: 1) Doplňte formát [Akční sloveso] + [Komponenta] + [Kritérium] (např. 'SYSTEMS_INTELLIGENCE: Analyzuj stav databáze a latenci tokenů'). 2) Pokud vyskočí okno sémantické brány, klikněte na 'Převzít a odeslat'. 3) Pokud jsou zprávy filtrovány, klikněte na 'Zrušit filtr domén'. 4) Při chybovém hlášení zkontrolujte jistič (Circuit Breaker) v horní liště."
            )
            FaqItem(
                question = "Jak se vyhnout chybám a zamítnutí dotazů v sémantické bráně?",
                answer = "Zadávejte konkrétní technické výrazy místo vágních dotazů. Využívejte nápovědu PromptGuideTooltip nad textovým polem a v nastavení si ponechte sémantickou kontrolu zapnutou."
            )
            FaqItem(
                question = "Jak bezpečně resetovat jistič systému (Circuit Breaker) a co znamená HTTP 429 cooldown?",
                answer = "Při přetížení LLM providera (HTTP 429) jistič automaticky respektuje hlavičku Retry-After v rozsahu 5–300 s, zobrazí v chatu ChatStatusBanner a přepne do bezpečného režimu. Po uplynutí cooldownu přejde do stavu HALF-OPEN, nebo jej operátor může po kontrole Telemetrie ručně resetovat v horní liště či Admin Hubu."
            )
            FaqItem(
                question = "Kdy použít Dev Prompt Lab?",
                answer = "Před zavedením nových systémových promptů nebo při podezření na bezpečnostní zranitelnost (Prompt Injection / Jailbreak) otestujte vstup v izolovaném prostředí Labu."
            )
            FaqItem(
                question = "Jak funguje autorizace zablokovaných záznamů?",
                answer = "Pokud sémantická brána nebo bezpečnostní perimetr pozastaví dotaz, administrátor může v kontextovém menu záznamu kliknout na 'Autorizovat' a dotaz propustit k exekuci."
            )
        }

        FaqItem(
            question = "Jak funguje vrstva Gatekeeper G1–G6 před 8D Maticí dopadu?",
            answer = "Než jakákoli data vstoupí do 8D Matice dopadu (SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC), projdou sekvenčně šesti branami G1–G6 (včetně Dual-Gate kontroly vstupu i výstupu). Každá brána zapíše výsledek do auditní stopy a při chybě (např. NaN, Inf nebo nulový vektor) řetězec okamžitě přeruší výjimkou GatekeeperChainException."
        )

        FaqItem(
            question = "K čemu slouží 8D Matice dopadu?",
            answer = "Matice v 8 rozměrech (SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC) posuzuje dopad každé změny na stabilitu celého ekosystému, aby se předešlo neočekávaným vedlejším účinkům."
        )

        FaqItem(
            question = "Jak jsou ukládána data v aplikaci?",
            answer = "Data jsou lokálně šifrována v databázi Room v19 pomocí SQLCipher (AES-256-CBC) s klíčem v Android Keystore, včetně 64D vektorů (omnis_local_vectors_64d) a transakčního outboxu (omnis_sync_outbox) synchronizovaného s PostgreSQL 17 / ElectricSQL 1.8.1."
        )
    }
}

@Composable
private fun ExpandableManualModule(
    title: String,
    icon: ImageVector,
    color: Color,
    summary: String,
    details: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        color = OmnisCardDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) color else OmnisBorderDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = OmnisTextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = summary, color = OmnisTextMuted, fontSize = 12.sp, lineHeight = 17.sp)

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = OmnisBorderDark, modifier = Modifier.padding(bottom = 4.dp))
                    details.forEach { detail ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(top = 2.dp)
                            )
                            Text(
                                text = detail,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Surface(
        color = OmnisPanelDark,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = OmnisCyan, modifier = Modifier.size(18.dp))
                Text(question, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(answer, color = OmnisTextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun MethodologyCard(title: String, icon: ImageVector, content: String) {
    Surface(
        color = OmnisCardDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OmnisBorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = OmnisCyan, modifier = Modifier.size(20.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                color = OmnisTextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = OmnisCyan,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 6.dp, top = 6.dp)
    )
}

@Composable
private fun SigmaCard(title: String, description: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
                .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(description, color = OmnisTextMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun BestPracticeItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = number,
            color = OmnisCyan.copy(alpha = 0.3f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(40.dp)
        )
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = OmnisTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
