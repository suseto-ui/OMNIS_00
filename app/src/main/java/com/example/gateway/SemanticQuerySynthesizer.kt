package com.example.gateway

import java.util.Locale

data class RefinementOption(
    val label: String,
    val intentKey: String,
    val domain: String,
    val contextModifier: String,
    val directPrompt: String? = null
)

data class PreflightAnalysisResult(
    val isVague: Boolean,
    val identifiedDomain: String?,
    val guidanceHint: String?,
    val suggestions: List<RefinementOption>
)

/**
 * Centrální kognitivní syntetizátor a rewriter dotazů pro O.M.N.I.S.
 * Transformuje vágní či neúplné uživatelské vstupy na exaktní, doménově
 * ukotvené systémové prompty bez sémantického balastu.
 */
object SemanticQuerySynthesizer {

    private val VAGUE_TRIGGERS = listOf(
        "jak to", "jak na to", "co s tim", "co s tím", "pomoc", "pomoz",
        "optimalizuj", "zabezpec", "zabezpeč", "oprav", "vysvetli", "vysvětli",
        "udelej", "udělej", "nastav", "proc", "proč", "chci vedet", "chci vědět",
        "jak to funguje", "nejde to", "co mam delat", "co mám dělat"
    )

    /**
     * V reálném čase analyzuje text psaný uživatelem a detekuje vágnost,
     * nedostatek parametrů nebo chybějící doménové ukotvení.
     */
    fun analyzeInputPreflight(inputQuery: String): PreflightAnalysisResult {
        val trimmed = inputQuery.trim()
        if (trimmed.isEmpty()) {
            return PreflightAnalysisResult(
                isVague = false,
                identifiedDomain = null,
                guidanceHint = null,
                suggestions = emptyList()
            )
        }

        val lower = trimmed.lowercase(Locale.ROOT)
        val isShort = trimmed.length in 1..24
        val hasVagueStem = VAGUE_TRIGGERS.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }

        // Domain detection
        val identifiedDomain = when {
            lower.contains("bezpec") || lower.contains("hesl") || lower.contains("utok") || lower.contains("nis2") -> "SEC"
            lower.contains("cena") || lower.contains("naklad") || lower.contains("token") || lower.contains("faktur") -> "ECON"
            lower.contains("vykon") || lower.contains("rychlost") || lower.contains("latenc") || lower.contains("hw") -> "PHYS"
            lower.contains("pravo") || lower.contains("zakon") || lower.contains("gdpr") || lower.contains("ai act") -> "LAW"
            lower.contains("ergonom") || lower.contains("stres") || lower.contains("pocity") || lower.contains("psych") -> "PSYCH"
            lower.contains("eko") || lower.contains("energie") || lower.contains("zelen") || lower.contains("uhlik") -> "ECO"
            lower.contains("spolecn") || lower.contains("etika") || lower.contains("lide") || lower.contains("tym") -> "SOC"
            lower.contains("architekt") || lower.contains("databaz") || lower.contains("kod") || lower.contains("system") -> "SYS"
            else -> null
        }

        val isVague = isShort || hasVagueStem || (trimmed.split("\\s+".toRegex()).size <= 3 && identifiedDomain == null)

        val suggestions = if (isVague) {
            generateSmartSuggestions(trimmed, identifiedDomain)
        } else {
            emptyList()
        }

        val guidanceHint = if (isVague) {
            when {
                identifiedDomain == "SEC" -> "Dotaz postrádá specifikaci bezpečnostní normy a úrovně auditu."
                identifiedDomain == "ECON" -> "Doplňte rozpočtový rámec nebo limity spotřeby tokenů."
                identifiedDomain == "PHYS" -> "Upřesněte hardwarovou architekturu a požadavek na latenci."
                identifiedDomain == "SYS" -> "Specifikujte komponentu nebo datovou vrstvu k optimalizaci."
                else -> "Dotaz je příliš obecný. Zvolte jedno z věcných upřesnění níže:"
            }
        } else null

        return PreflightAnalysisResult(
            isVague = isVague,
            identifiedDomain = identifiedDomain,
            guidanceHint = guidanceHint,
            suggestions = suggestions
        )
    }

    /**
     * Zpřesní a sémanticky přepíše původní uživatelský dotaz s využitím vybrané volby.
     * Zabraňuje útržkovitému posílání a vytváří ucelený dotaz se srozumitelnou větnou stavbou.
     */
    fun synthesizeRefinedQuery(originalQuery: String, refinement: RefinementOption): String {
        // Pokud možnost obsahuje přímý plný dotaz, použijeme jej
        if (!refinement.directPrompt.isNullOrBlank() && refinement.directPrompt.length > 25) {
            return refinement.directPrompt.trim()
        }

        val rawClean = originalQuery.trim()
            .removeSuffix("?")
            .removeSuffix(".")
            .trim()

        val lower = rawClean.lowercase(Locale.ROOT)

        // 1. Detekce a transformace obecných sloves
        return when {
            lower.contains("zabezpec") || lower.contains("zabezpečit") -> {
                "Jak zabezpečit systém O.M.N.I.S. ${refinement.contextModifier} s garancí auditní stopy (HMAC-SHA256 Hash-Chain)?"
            }
            lower.contains("optimalizov") || lower.contains("optimalizuj") -> {
                "Jak provést optimalizaci v systému O.M.N.I.S. ${refinement.contextModifier} pro zamezení kaskádovým chybám?"
            }
            lower.contains("jak to funguje") || lower.contains("vysvetli") || lower.contains("vysvětli") -> {
                "Vysvětlete přesné technické principy fungování O.M.N.I.S. ${refinement.contextModifier} bez zbytečného balastu."
            }
            lower.contains("oprav") || lower.contains("vyres") || lower.contains("vyřeš") -> {
                "Jaké konkrétní kroky a opravy provést v systému O.M.N.I.S. ${refinement.contextModifier}?"
            }
            lower.length <= 15 -> {
                // Velmi krátký dotaz - syntetizujeme kompletní otázku
                "Jak v systému O.M.N.I.S. aplikovat ${refinement.label} ${refinement.contextModifier}?"
            }
            else -> {
                // Přirozené logické sloučení původního dotazu a kontextového modifikátoru
                if (lower.contains(refinement.label.lowercase(Locale.ROOT))) {
                    "$rawClean – se zaměřením na ${refinement.contextModifier}."
                } else {
                    "$rawClean (${refinement.contextModifier})?"
                }
            }
        }
    }

    /**
     * Převede řetězec doporučení od Gemini (např. oddělený '|') na strukturované RefinementOptions.
     */
    fun parseRefinementsFromText(followUpText: String, defaultDomain: String = "SYS"): List<RefinementOption> {
        if (followUpText.isBlank()) return emptyList()

        val parts = followUpText.split("|", "\n")
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() && it.length > 3 }

        return parts.take(3).mapIndexed { idx, part ->
            val label = cleanLabel(part)
            val dom = detectDomainFromText(part, defaultDomain)
            RefinementOption(
                label = label,
                intentKey = "CLARIFY_STEP_${idx + 1}",
                domain = dom,
                contextModifier = "se zaměřením na $label",
                directPrompt = if (part.endsWith("?")) part else null
            )
        }
    }

    private fun cleanLabel(text: String): String {
        return text
            .removePrefix("Chcete ")
            .removePrefix("Máte zájem o ")
            .removePrefix("Můžeme ")
            .removeSuffix("?")
            .trim()
            .take(45)
    }

    private fun detectDomainFromText(text: String, fallback: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("bezpec") || lower.contains("nis2") || lower.contains("audit") -> "SEC"
            lower.contains("naklad") || lower.contains("token") || lower.contains("rozpoc") -> "ECON"
            lower.contains("pravo") || lower.contains("zakon") || lower.contains("ai act") -> "LAW"
            lower.contains("vykon") || lower.contains("rychlost") || lower.contains("latenc") -> "PHYS"
            lower.contains("stres") || lower.contains("psych") -> "PSYCH"
            lower.contains("eko") || lower.contains("uhlik") -> "ECO"
            lower.contains("soc") || lower.contains("etika") -> "SOC"
            else -> fallback
        }
    }

    private fun generateSmartSuggestions(query: String, domain: String?): List<RefinementOption> {
        return when (domain) {
            "SEC" -> listOf(
                RefinementOption(
                    label = "Norma NIS2 (čl. 21)",
                    intentKey = "SEC_NIS2",
                    domain = "SEC",
                    contextModifier = "v souladu s bezpečnostními požadavky směrnice NIS2 (čl. 21)"
                ),
                RefinementOption(
                    label = "HMAC-SHA256 ověření",
                    intentKey = "SEC_ZK",
                    domain = "SEC",
                    contextModifier = "s kryptografickým potvrzením integrity přes HMAC-SHA256 Hash-Chain"
                ),
                RefinementOption(
                    label = "Ochrana před útoky",
                    intentKey = "SEC_DEFENSE",
                    domain = "SEC",
                    contextModifier = "z hlediska obrany proti prompt-injekcím a neautorizovaným zásahům"
                )
            )
            "ECON" -> listOf(
                RefinementOption(
                    label = "Optimalizace tokenů",
                    intentKey = "ECON_TOKENS",
                    domain = "ECON",
                    contextModifier = "s ořezáním kontextového okna pro minimální spotřebu tokenů"
                ),
                RefinementOption(
                    label = "Firestore limity",
                    intentKey = "ECON_FIRESTORE",
                    domain = "ECON",
                    contextModifier = "s využitím paginovaných dotazů a limitů čtení v Cloud Firestore"
                ),
                RefinementOption(
                    label = "0-Cost režim",
                    intentKey = "ECON_FREE_TIER",
                    domain = "ECON",
                    contextModifier = "v bezplatném Free-Tier pásmu bez překročení kvót"
                )
            )
            "PHYS", "SYS" -> listOf(
                RefinementOption(
                    label = "Architektura CSR matice",
                    intentKey = "SYS_CSR",
                    domain = "SYS",
                    contextModifier = "s využitím paměťově efektivní řídké matice CSR v AKGE-8D"
                ),
                RefinementOption(
                    label = "Minimalizace latence",
                    intentKey = "PHYS_LATENCY",
                    domain = "PHYS",
                    contextModifier = "s cílem dosažení latence odezvy pod 100 ms na mobilním zařízení"
                ),
                RefinementOption(
                    label = "Stabilizace kaskád",
                    intentKey = "SYS_PERCOLATION",
                    domain = "SYS",
                    contextModifier = "s prevencí perkolace a kaskádového selhání domén"
                )
            )
            else -> listOf(
                RefinementOption(
                    label = "Bezpečnostní prověrka",
                    intentKey = "GEN_SEC",
                    domain = "SEC",
                    contextModifier = "se zaměřením na kybernetickou odolnost a ověřitelnost"
                ),
                RefinementOption(
                    label = "Technická implementace",
                    intentKey = "GEN_TECH",
                    domain = "SYS",
                    contextModifier = "s konkrétními kroky implementace v Kotlin a Jetpack Compose"
                ),
                RefinementOption(
                    label = "Nákladová efektivita",
                    intentKey = "GEN_ECON",
                    domain = "ECON",
                    contextModifier = "s ohledem na minimální zátěž zdrojů a tokenů"
                )
            )
        }
    }
}
