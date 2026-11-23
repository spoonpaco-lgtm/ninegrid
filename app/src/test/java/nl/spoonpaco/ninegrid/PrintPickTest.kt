package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.domain.OcrLine
import nl.spoonpaco.ninegrid.domain.PrintPick
import nl.spoonpaco.ninegrid.domain.ScanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.9.17: voetregels uit de losse kaarten van 23 nov (testset/losse-kaarten), precies zoals ML Kit ze las. */
class PrintPickTest {
    private fun single(vararg bottom: String) = ScanParser.analyzeSingle(
        listOf(OcrLine("Burglar Rat", 50f, 20f), OcrLine("Creature — Rat", 50f, 170f)) + bottom.mapIndexed { i, t -> OcrLine(t, 50f, 280f + i * 6f) }, 100, 300, null,
    ).slots.single()

    @Test
    fun parserReadsGluedAndMisreadNumbers() {
        // Het © van de copyrightregel werd als C gelezen: "C 2024" mag het nummer erboven niet tegenhouden.
        assertEquals("170", single("C O17o", "FDN EN TYLER WALPOLE", "H & C 2024 W'izards of the Coast").collectorNumber)
        assertEquals("98", single("CO098", "FDN EN PAOLO PARENTE", "M&C 2024 W'izards of the Coast").collectorNumber)
        assertEquals("165", single("C O16s", "TM &C 2024 Wzards of the Coast", "FDNEN ANTHONY FRANCISCO").collectorNumber)
        assertEquals("68", single("c o068", "FDN EN RINA NoRDSOL").collectorNumber)
        assertEquals("146", single("U0146", "FDN EN >WINONA NELSON").collectorNumber)
        assertEquals("284", single("L0284", "FDNEN DANIEL LIUNGGREN").collectorNumber)
        assertEquals("FDN", single("L0284", "FDNEN DANIEL LIUNGGREN").setCode)
        // Een jaartal blijft geen nummer.
        assertNull(single("H & C 2024 W'izards of the Coast").collectorNumber)
    }

    @Test
    fun footerTokens() {
        val f = PrintPick.footer(listOf("S0130", "FDNN EVYN ONG", "M&2024W'as f the Cast", "FON EN EVYN FUNG"))
        assertTrue(f.numbers.contains("130")); assertTrue(f.sets.contains("FON")); assertEquals("2024", f.year)
        assertTrue(PrintPick.footer(listOf("FDN•ENDIMITAR MARINSKI")).sets.contains("FDN"))
        assertTrue(PrintPick.footer(listOf("RO122")).numbers.contains("122"))
        // Regeltekst levert geen setcode op ("token" is geen TOK).
        assertTrue(PrintPick.footer(listOf("Destroy target creature. Create a Food token.")).sets.isEmpty())
    }

    private val cathar = listOf(
        PrintPick.Cand("fdn", "fdn", "139", "common", "2024"),
        PrintPick.Cand("mid", "mid", "17", "common", "2021"),
        PrintPick.Cand("sld", "sld", "7132", "rare", "2023"),
    )

    @Test
    fun catharCommandoHalfReadIsOrangeButRight() {
        val p = PrintPick.pick(cathar, listOf("S0130", "FDNN EVYN ONG", "M&2024W'as f the Cast", "FON EN EVYN FUNG"))
        assertNotNull(p); assertEquals("fdn", p!!.cand.id); assertFalse("half gelezen is nooit zeker", p.sure)
    }

    @Test
    fun snakeskinVeilByNumberAndSetIsSure() {
        val cands = listOf(PrintPick.Cand("fdn", "fdn", "233", "uncommon", "2024"), PrintPick.Cand("kld", "kld", "178", "common", "2016"))
        val p = PrintPick.pick(cands, listOf("U 0233", "FDN EN >DAN MURAYAMA SCOTT", "IM&2024 Wizards of thc Coast"))!!
        assertEquals("fdn", p.cand.id); assertTrue(p.sure)
    }

    @Test
    fun withinAKnownSetHalfReadNumberPicksTheRightVariant() {
        val cands = listOf(PrintPick.Cand("176", "lci", "176", "common"), PrintPick.Cand("378", "lci", "378", "common"))
        val p = PrintPick.pick(cands, listOf("C 0170", "LCI EN JOHN DOE"), extraSets = listOf("LCI"))!!
        assertEquals("176", p.cand.id)
        // Niets in de voetregel dat de prints onderscheidt: geen keuze.
        assertNull(PrintPick.pick(cands, listOf("LCI EN JOHN DOE"), extraSets = listOf("LCI")))
    }

    /** Testset 23 nov: een los "200" mocht niet JMP 201 kiezen; en alleen een nummer zonder set is nooit zeker. */
    @Test
    fun looseNumberIsWeakAndNumberAloneIsNotSure() {
        val pie = listOf(PrintPick.Cand("fdn", "fdn", "169", "uncommon", "2024"), PrintPick.Cand("jmp", "jmp", "201", "uncommon", "2020"))
        assertNull(PrintPick.pick(pie, listOf("200", "200", "FONNEN IoLTAN BoROS")))
        val islands = listOf(PrintPick.Cand("tmt", "tmt", "254", "common", "2025", basicLand = true), PrintPick.Cand("fdn", "fdn", "284", "common", "2024", basicLand = true))
        val p = PrintPick.pick(islands, listOf("L 0254", "N&2024 Wards of the Cosst"))
        assertFalse(p?.sure == true)
    }

    /** Testset 23 nov: "Necrosquito" goed gelezen met nummer 1 (een andere kaart) is ruis; het buurvak-geval niet. */
    @Test
    fun numberNoise() {
        val n = nl.spoonpaco.ninegrid.domain.NumberNoise
        assertTrue(n.isNoise("Necrosquito", "Necrosquito", 1, "Adelaide, Hope of the Moon", listOf("Necrosquito", "1")))
        assertTrue(n.isNoise("Caparocti Sunborn", "Caparocti Sunborn", 1, null, listOf("440")))
        // Meer prints met die naam: dan moet het nummer kiezen, dus geen ruis.
        assertFalse(n.isNoise("Kinzu of the Bleak Coven", "Kinzu of the Bleak Coven", 3, "Glistening Deluge", emptyList()))
        // De naam van de kaart bij het nummer staat ook in de gelezen regels: misschien hoort de naamregel bij het buurvak.
        assertFalse(n.isNoise("Shock", "Shock", 1, "Lightning Bolt", listOf("Shock", "Lightning Bolt", "141")))
        // Naam zwak gelezen: nooit.
        assertFalse(n.isNoise("Undecealm Lch", "Underrealm Lich", 1, "Blade Instructor", emptyList()))
    }

    @Test
    fun noFooterNoPick() {
        assertNull(PrintPick.pick(cathar, listOf("Cathar Commando", "Creature — Human Soldier")))
    }

    @Test
    fun artSeriesIsNotACard() {
        assertTrue(PrintPick.notACard("Snakeskin Veil // Snakeskin Veil", "Card // Card"))
        assertTrue(PrintPick.notACard("Rat", "Token Creature — Rat"))
        assertFalse(PrintPick.notACard("Fire // Ice", "Instant // Instant"))
        assertFalse(PrintPick.notACard("Snakeskin Veil", "Instant"))
    }
}
