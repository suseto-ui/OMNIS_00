package com.example.gateway

import org.junit.Assert.*
import org.junit.Test

class SemanticQuerySynthesizerTest {

    @Test
    fun `analyzeInputPreflight detects vague and short queries`() {
        val vague1 = SemanticQuerySynthesizer.analyzeInputPreflight("jak to zabezpecit")
        assertTrue("Dotaz 'jak to zabezpecit' musí být označen jako vágní", vague1.isVague)
        assertEquals("SEC", vague1.identifiedDomain)
        assertTrue("Musí obsahovat návrhy na upřesnění", vague1.suggestions.isNotEmpty())

        val vague2 = SemanticQuerySynthesizer.analyzeInputPreflight("optimalizuj")
        assertTrue("Jednoslovný dotaz musí být označen jako vágní", vague2.isVague)
        assertTrue(vague2.suggestions.size in 2..3)

        val vague3 = SemanticQuerySynthesizer.analyzeInputPreflight("pomoc s chybou")
        assertTrue("Obecný dotaz s 'pomoc' musí být označen jako vágní", vague3.isVague)
    }

    @Test
    fun `analyzeInputPreflight marks detailed specific queries as non-vague`() {
        val detailed = SemanticQuerySynthesizer.analyzeInputPreflight(
            "Jak implementovat vícerozměrnou CSR matici pro 8 domén s garancí nízké paměťové náročnosti v Kotlin 2.0?"
        )
        assertFalse("Specifický podrobný dotaz nesmí být označen jako vágní", detailed.isVague)
        assertTrue(detailed.suggestions.isEmpty())
    }

    @Test
    fun `synthesizeRefinedQuery rewrites vague query into articulate technical question`() {
        val original = "jak to zabezpečit"
        val option = RefinementOption(
            label = "Norma NIS2 (čl. 21)",
            intentKey = "SEC_NIS2",
            domain = "SEC",
            contextModifier = "v souladu s bezpečnostními požadavky směrnice NIS2 (čl. 21)"
        )

        val synthesized = SemanticQuerySynthesizer.synthesizeRefinedQuery(original, option)

        assertTrue(synthesized.isNotBlank())
        assertTrue("Syntetizovaný dotaz musí obsahovat kontext normy NIS2", synthesized.contains("NIS2 (čl. 21)"))
        assertTrue("Syntetizovaný dotaz musí zmiňovat systém O.M.N.I.S.", synthesized.contains("O.M.N.I.S."))
        assertTrue("Dotaz musí končit otazníkem", synthesized.endsWith("?"))
    }

    @Test
    fun `synthesizeRefinedQuery handles optimization query properly`() {
        val original = "optimalizuj to"
        val option = RefinementOption(
            label = "Spotřeba tokenů",
            intentKey = "ECON_TOKENS",
            domain = "ECON",
            contextModifier = "s ořezáním kontextového okna pro minimální spotřebu tokenů"
        )

        val synthesized = SemanticQuerySynthesizer.synthesizeRefinedQuery(original, option)

        assertTrue(synthesized.isNotBlank())
        assertTrue(synthesized.contains("optimalizaci"))
        assertTrue(synthesized.contains("spotřebu tokenů"))
    }

    @Test
    fun `parseRefinementsFromText parses pipe separated and bullet lines cleanly`() {
        val rawFollowUps = "Norma NIS2 a bezpečnostní audit | Optimalizace tokenů v bezplatném režimu | Implementace CSR matice v Kotlin"
        val parsed = SemanticQuerySynthesizer.parseRefinementsFromText(rawFollowUps, defaultDomain = "SYS")

        assertEquals(3, parsed.size)
        assertEquals("Norma NIS2 a bezpečnostní audit", parsed[0].label)
        assertEquals("SEC", parsed[0].domain)
        assertEquals("ECON", parsed[1].domain)
        assertEquals("SYS", parsed[2].domain)
    }
}
