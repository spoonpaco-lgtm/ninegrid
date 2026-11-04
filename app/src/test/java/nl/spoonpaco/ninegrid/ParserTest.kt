package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.domain.DeckListParser
import nl.spoonpaco.ninegrid.domain.Formats
import nl.spoonpaco.ninegrid.domain.OcrLine
import nl.spoonpaco.ninegrid.domain.ScanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    @Test
    fun readsMicksFormat() {
        val r = DeckListParser.parse(
            """
            3 Arms Dealer (M13) 120
            1 Goblin Bombardment (WOT) 43 *F*
            2 Hordeling Outburst (PLST) A25-134
            1 Legion's Landing / Adanto, the First Fort (XLN) 22
            1 Duress (STA) 29 *E*

            SIDEBOARD:
            2 Armored Skaab (ISD) 43
            """.trimIndent()
        )
        assertEquals(6, r.lines.size)
        assertEquals("Arms Dealer", r.lines[0].name)
        assertEquals("M13", r.lines[0].setCode)
        assertEquals("120", r.lines[0].collectorNumber)
        assertTrue(r.lines[1].foil)
        assertEquals("A25-134", r.lines[2].collectorNumber)
        assertEquals("Legion's Landing // Adanto, the First Fort", r.lines[3].name)
        assertTrue(r.lines[4].etched)
        assertEquals(DeckListParser.SIDEBOARD, r.lines[5].section)
        assertTrue(r.hadSideboard)
        assertTrue(r.unreadable.isEmpty())
    }

    @Test
    fun readsArenaStyle() {
        val r = DeckListParser.parse("Commander\n1 Edgar Markov (INR) 328\n\nDeck\n1x Sol Ring\n4 Forest")
        assertEquals(DeckListParser.COMMANDER, r.lines[0].section)
        assertEquals(DeckListParser.MAIN, r.lines[1].section)
        assertNull(r.lines[1].setCode)
        assertEquals("Sol Ring", r.lines[1].name)
        assertTrue(r.hadCommanderHeader)
    }

    @Test
    fun blankLineWithoutHeadersStartsSideboard() {
        val r = DeckListParser.parse("4 Murder (M20) 109\n\n2 Negate (M12) 69")
        assertEquals(DeckListParser.SIDEBOARD, r.lines[1].section)
    }

    @Test
    fun suggestsCommanderFor100Singleton() {
        val lines = (1..99).map { "1 Card $it" } + "1 Edgar Markov"
        val r = DeckListParser.parse(lines.joinToString("\n"))
        assertEquals(DeckFormat.COMMANDER, Formats.suggest(r.lines))
    }

    @Test
    fun suggestsCasualFor64() {
        val r = DeckListParser.parse((1..16).joinToString("\n") { "4 Card $it" })
        assertEquals(DeckFormat.CASUAL, Formats.suggest(r.lines))
    }

    @Test
    fun scanFindsCollectorLines() {
        val w = 900; val h = 900
        val lines = listOf(
            OcrLine("229/254 M", 100f, 280f), OcrLine("MH1 • EN", 100f, 295f),
            OcrLine("M 0074", 400f, 280f), OcrLine("WOT • EN", 400f, 295f),
            OcrLine("U 0859", 700f, 880f), OcrLine("CMM • DE", 700f, 890f),
            OcrLine("Some rules text", 400f, 500f),
        )
        val slots = ScanParser.parse(lines, w, h)
        assertEquals("MH1", slots[0].setCode); assertEquals("229", slots[0].collectorNumber); assertEquals("", slots[0].language)
        assertEquals("WOT", slots[1].setCode); assertEquals("74", slots[1].collectorNumber)
        assertEquals("CMM", slots[8].setCode); assertEquals("859", slots[8].collectorNumber); assertEquals("DE", slots[8].language)
        assertFalse(slots[4].complete)
    }

    @Test
    fun scanFindsNamesAndLooseSeparator() {
        val w = 900; val h = 1200
        val lines = mutableListOf<OcrLine>()
        for (r in 0..2) for (c in 0..2) {
            val x = 150f + c * 300f; val top = r * 400f
            lines += OcrLine("Card Name ${'A' + (r * 3 + c)}", x, top + 30f)
            lines += OcrLine("Creature — Vampire", x, top + 220f)
            lines += OcrLine("${r * 3 + c + 1}/254 R", x, top + 370f)
            lines += OcrLine("MH1 EN", x, top + 385f)
        }
        val slots = ScanParser.parse(lines, w, h)
        assertEquals("Card Name A", slots[0].name)
        assertEquals("Card Name I", slots[8].name)
        assertEquals("MH1", slots[4].setCode)
        assertEquals("5", slots[4].collectorNumber)
    }

    @Test
    fun dominantSetAndCrop() {
        val reads = listOf(
            nl.spoonpaco.ninegrid.domain.SlotRead(1, "ONE", "258", "", listOf("x")),
            nl.spoonpaco.ninegrid.domain.SlotRead(2, "ONE", "249", "", listOf("x")),
            nl.spoonpaco.ninegrid.domain.SlotRead(3, "WOE", "255", "", listOf("x")),
            nl.spoonpaco.ninegrid.domain.SlotRead(4, null, null, null, listOf("x")),
        )
        assertEquals("ONE", ScanParser.dominantSet(reads))
        val lines = listOf(OcrLine("Seachrome Coast", 100f, 50f), OcrLine("Other Page Card", 950f, 50f))
        val r = ScanParser.analyze(lines, 1000, 1000, nl.spoonpaco.ninegrid.domain.Area(0f, 0f, 800f, 1000f))
        assertEquals(1, r.linesInside)
    }
}
