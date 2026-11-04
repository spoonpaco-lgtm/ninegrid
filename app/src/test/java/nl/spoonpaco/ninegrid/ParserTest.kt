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

    @Test
    fun readsNumberWithoutSlash() {
        val lines = listOf(
            OcrLine("Seachrome Coast", 50f, 20f), OcrLine("Land", 50f, 200f),
            OcrLine("374 R", 30f, 290f), OcrLine("ONE • EN", 40f, 296f),
        )
        val r = ScanParser.analyzeSingle(lines, 100, 300, null)
        assertEquals("374", r.slots[0].collectorNumber)
        assertEquals("ONE", r.slots[0].setCode)
        assertEquals("Seachrome Coast", r.slots[0].name)
    }

    @Test
    fun importsSheetLayout() {
        val header = listOf("Set", "Nr.", "Kaart", "Rarity", "Foil", "Taal", "Aantal", "Prijs/stuk (€)", "Totaal (€)", "Proxy")
        val mh1 = listOf(header, listOf("MH1", "1", "Morophon", "rare", "", "", "", "", "", ""), listOf("MH1", "2", "Sliver", "rare", "foil", "", "2", "", "", ""))
        val decks = listOf(
            listOf("Edgar Markov"), header,
            listOf("", "", "Commander"), listOf("INR", "328", "Edgar Markov", "mythic", "", "", "1", "", "", ""),
            listOf("", "", "Deck"), listOf("C21", "263", "Sol Ring", "uncommon", "", "", "1", "", "", "ja"),
            emptyList(), listOf("", "", "mythic", "", "", "", "1"), emptyList(), emptyList(),
            listOf("Krenko"), header, listOf("M20", "109", "Murder (FR)", "common", "", "", "4", "", "", ""),
        )
        val d = nl.spoonpaco.ninegrid.domain.SheetImport.interpret(listOf("MH1" to mh1, "Decks" to decks, "Overzicht" to listOf(listOf("x"))))
        assertEquals(1, d.collection.size)
        assertTrue(d.collection[0].foil)
        assertEquals(2, d.decks.size)
        assertEquals("COMMANDER", d.decks[0].cards[0].section)
        assertTrue(d.decks[0].cards[1].proxy)
        assertEquals(2, d.decks[0].cards.size)
        assertEquals("FR", d.decks[1].cards[0].language)
        assertEquals("Murder", d.decks[1].cards[0].name)
    }

    @Test
    fun parsesCsv() {
        val rows = nl.spoonpaco.ninegrid.domain.SheetImport.parseCsv("Set;Nr.;Kaart\nMH1;1;\"A; B\"\n")
        assertEquals("A; B", rows[1][2])
    }

    // ---------- Deckcontrole ----------
    private fun card(name: String, qty: Int = 1, section: String = nl.spoonpaco.ninegrid.data.db.Section.MAIN, type: String = "Creature", id: String = "B",
                     gc: Boolean = false, text: String = "", proxy: Boolean = false, legal: String = "commander=legal,modern=legal") =
        nl.spoonpaco.ninegrid.domain.DeckCard(name, qty, section, type, id, legal, gc, proxy, text)

    @Test
    fun commanderChecks() {
        val cards = listOf(card("Edgar Markov", section = nl.spoonpaco.ninegrid.data.db.Section.COMMANDER, id = "RWB")) +
            (1..98).map { card("Card $it") } + card("Swamp", 1, type = "Basic Land — Swamp")
        val ok = Formats.check(DeckFormat.COMMANDER, cards)
        assertTrue(ok.issues.none { it.code == "commander_size" || it.code == "singleton" })
        val dup = Formats.check(DeckFormat.COMMANDER, cards.dropLast(1) + card("Card 1"))
        assertTrue(dup.issues.any { it.code == "singleton" && it.detail == "Card 1" })
        val green = Formats.check(DeckFormat.COMMANDER, cards.dropLast(1) + card("Llanowar Elves", id = "G"))
        assertTrue(green.issues.any { it.code == "color_identity" })
    }

    @Test
    fun bracketSignals() {
        val base = listOf(card("Cmd", section = nl.spoonpaco.ninegrid.data.db.Section.COMMANDER)) + (1..99).map { card("C$it") }
        assertEquals("1", Formats.check(DeckFormat.COMMANDER, base).bracketHint)
        val mld = base.dropLast(1) + card("Armageddon", text = "Destroy all lands.")
        assertEquals("4", Formats.check(DeckFormat.COMMANDER, mld).bracketHint)
        val turns = base.dropLast(2) + card("Time Warp", text = "Target player takes an extra turn after this one.") + card("Temporal Manipulation", text = "Take an extra turn after this one.")
        assertEquals("3", Formats.check(DeckFormat.COMMANDER, turns).bracketHint)
        val tutor = base.dropLast(1) + card("Demonic Tutor", text = "Search your library for a card, put that card into your hand, then shuffle.")
        assertEquals("2", Formats.check(DeckFormat.COMMANDER, tutor).bracketHint)
        val ramp = base.dropLast(1) + card("Cultivate", text = "Search your library for up to two basic land cards, reveal those cards.")
        assertEquals("1", Formats.check(DeckFormat.COMMANDER, ramp).bracketHint)
        val gc = base.dropLast(1) + card("Rhystic Study", gc = true)
        assertEquals("3", Formats.check(DeckFormat.COMMANDER, gc).bracketHint)
    }

    @Test
    fun modernChecks() {
        val cards = (1..14).map { card("Card $it", 4) } + card("Card 15", 5)
        val c = Formats.check(DeckFormat.MODERN, cards)
        assertTrue(c.issues.any { it.code == "max_four" && it.detail == "Card 15" })
        val small = Formats.check(DeckFormat.MODERN, (1..10).map { card("Card $it", 4) })
        assertTrue(small.issues.any { it.code == "main_size" })
        val banned = Formats.check(DeckFormat.MODERN, (1..15).map { card("Card $it", 4) } + card("Bad", legal = "modern=banned"))
        assertTrue(banned.issues.any { it.code == "banned" && it.detail == "Bad" })
    }

    // ---------- Prijzen ----------
    private fun print(eur: Double? = 1.0, eurFoil: Double? = 3.0, usd: Double? = 2.0, usdFoil: Double? = null, usdEtched: Double? = 5.0) =
        nl.spoonpaco.ninegrid.data.db.CardPrint(id = "x", name = "X", setCode = "MH1", collectorNumber = "1", eur = eur, eurFoil = eurFoil, usd = usd, usdFoil = usdFoil, usdEtched = usdEtched)

    private fun entry(finish: String = nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL, qty: Int = 2, proxy: Boolean = false) =
        nl.spoonpaco.ninegrid.data.db.Entry(printId = "x", finish = finish, quantity = qty, proxy = proxy)

    @Test
    fun money() {
        val M = nl.spoonpaco.ninegrid.domain.Money
        assertEquals(2.0, M.total(entry(), print(), "EUR"), 1e-9)
        assertEquals(6.0, M.total(entry(nl.spoonpaco.ninegrid.data.db.Finish.FOIL), print(), "EUR"), 1e-9)
        assertEquals(0.0, M.total(entry(proxy = true), print(), "EUR"), 1e-9)
        assertEquals(10.0, M.total(entry(nl.spoonpaco.ninegrid.data.db.Finish.ETCHED), print(), "USD"), 1e-9)
        assertEquals(0.0, M.total(entry(), null, "EUR"), 1e-9)
    }

    @Test
    fun openingHandOdds() {
        val S = nl.spoonpaco.ninegrid.domain.DeckStatsCalc
        // 99 kaarten met 37 lands: kans op 2-4 lands in 7 is ongeveer 75%.
        val p = (2..4).sumOf { S.hyper(99, 37, 7, it) }
        assertTrue(p in 0.72..0.80)
        assertEquals(1.0, (0..7).sumOf { S.hyper(60, 24, 7, it) }, 1e-9)
    }

    @Test
    fun importsOtherApps() {
        val I = nl.spoonpaco.ninegrid.domain.SheetImport
        val mox = I.interpret(listOf("mox" to I.parseCsv(
            "\"Count\",\"Tradelist Count\",\"Name\",\"Edition\",\"Condition\",\"Language\",\"Foil\",\"Tags\",\"Last Modified\",\"Collector Number\"\n" +
            "\"2\",\"0\",\"Lightning Bolt\",\"m10\",\"Near Mint\",\"Japanese\",\"foil\",\"\",\"2024\",\"146\"\n")))
        assertEquals("Moxfield", mox.format)
        val c = mox.collection.single()
        assertEquals("M10", c.setCode); assertEquals("146", c.number); assertEquals(2, c.quantity)
        assertTrue(c.foil); assertEquals("JP", c.language); assertEquals("NM", c.condition)

        val box = I.interpret(listOf("deckbox" to I.parseCsv(
            "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil\n1,0,Sol Ring,Commander 2021,263,Lightly Played,English,\n")))
        assertEquals("Deckbox", box.format)
        assertEquals("Commander 2021", box.collection.single().setName)
        assertEquals("LP", box.collection.single().condition)

        val mana = I.interpret(listOf("manabox" to I.parseCsv(
            "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Language\nMurder,M20,Core Set 2020,109,normal,common,4,1,abc-123,en\n")))
        assertEquals("ManaBox", mana.format)
        assertEquals("abc-123", mana.collection.single().scryfallId)
        assertFalse(mana.collection.single().foil)

        val dragon = I.interpret(listOf("ds" to I.parseCsv(
            "sep=,\nFolder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language\nBinder,3,0,Duress,STA,Strixhaven Mystical Archive,29,NearMint,Foil,English\n")))
        assertEquals("Dragon Shield", dragon.format)
        assertEquals(3, dragon.collection.single().quantity)
        assertTrue(dragon.collection.single().foil)

        val unknown = I.interpret(listOf("x" to I.parseCsv("a,b,c\n1,2,3\n")))
        assertTrue(unknown.needsMapping)
        val mapped = I.interpret(listOf("x" to I.parseCsv("a,b,c\nOpt,XLN,65\n")),
            mapOf(0 to nl.spoonpaco.ninegrid.domain.SheetImport.Role.NAME, 1 to nl.spoonpaco.ninegrid.domain.SheetImport.Role.SET, 2 to nl.spoonpaco.ninegrid.domain.SheetImport.Role.NUMBER))
        assertEquals("Opt", mapped.collection.single().name)
    }

    @Test
    fun suggestionsRespectTribeAndColors() {
        val S = nl.spoonpaco.ninegrid.domain.Suggestions
        val cmd = card("Edgar Markov", section = nl.spoonpaco.ninegrid.data.db.Section.COMMANDER, type = "Legendary Creature — Vampire Knight", id = "RWB")
        val deck = listOf(cmd) + (1..30).map { card("Vamp $it", type = "Creature — Vampire", id = "B", text = "Whenever another Vampire enters, create a 1/1 token.") } +
            card("Bear", type = "Creature — Bear", id = "G") + card("Swamp", 30, type = "Basic Land — Swamp")
        val tribe = S.guessTribe(deck)
        assertEquals("Vampire", tribe)
        fun cand(id: Long, name: String, type: String, ci: String, text: String = "") =
            nl.spoonpaco.ninegrid.domain.Candidate(id, name, type, text, "", 2.0, ci, "commander=legal", false)
        val pool = listOf(
            cand(1, "Bloodghast", "Creature — Vampire Spirit", "B", "Whenever a Vampire enters, create a token."),
            cand(2, "Grizzly Bears", "Creature — Bear", "G"),
            cand(3, "Llanowar Elves", "Creature — Elf Druid", "G", "{T}: Add {G}."),
            cand(4, "Night's Whisper", "Sorcery", "B", "You draw two cards and you lose 2 life."),
            cand(5, "Elf Lord", "Creature — Elf", "B", "Draw a card."),
            cand(6, "Godless Shrine", "Land — Plains Swamp", "WB"),
        )
        val r = S.suggest(DeckFormat.COMMANDER, deck, emptyMap(), pool, nl.spoonpaco.ninegrid.domain.DeckRules(tribe = tribe))
        val names = r.adds.map { it.card.name }
        assertTrue("Bloodghast" in names)
        assertTrue("Night's Whisper" in names)
        assertFalse("Grizzly Bears" in names) // buiten de kleuren
        assertFalse("Elf Lord" in names) // geen Vampire
        assertTrue(r.lands.any { it.card.name == "Godless Shrine" })
        assertTrue(r.cuts.any { it.name == "Bear" && it.reasons.any { w -> w.startsWith("not_tribe") } })
    }
}
