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
import kotlinx.serialization.json.decodeToSequence

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
    fun scanReadsFooterWithoutLanguageAndRepairsOcrDigits() {
        val lines = listOf(
            OcrLine("Fire // Ice", 50f, 20f), OcrLine("Instant", 50f, 170f),
            // l and O are common OCR readings for 1 and 0; the suffix belongs to the collector number.
            OcrLine("lO3a/280 R", 50f, 290f), OcrLine("MH2", 50f, 296f),
        )
        val r = ScanParser.analyzeSingle(lines, 100, 300, null).slots.single()
        assertEquals("103A", r.collectorNumber)
        assertEquals("MH2", r.setCode)
        assertEquals("Fire // Ice", r.name)
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

    /**
     * Android gebruikt ICU voor regexes, en die is strenger dan de JVM: een losse '{' of '}'
     * (geen quantifier, geen \p{..}) laat de app crashen. Deze test leest alle Regex-literals
     * in de broncode en keurt ze zoals ICU dat doet.
     */
    @Test
    fun regexesAreAndroidSafe() {
        val root = java.io.File("src/main/java")
        assertTrue("bronmap niet gevonden", root.isDirectory)
        val lit = Regex("Regex\\(\"\"\"(.*?)\"\"\"")
        val bad = mutableListOf<String>()
        root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { n, line ->
                lit.findAll(line).forEach { m -> strictBraceProblem(m.groupValues[1])?.let { bad += "${f.name}:${n + 1} $it: ${m.groupValues[1]}" } }
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    private fun strictBraceProblem(re: String): String? {
        var i = 0; var inClass = false
        while (i < re.length) {
            val c = re[i]
            when {
                c == '\\' -> {
                    // \p{L} / \P{..} slaan we als geheel over; andere escapes één teken.
                    if (i + 2 < re.length && (re[i + 1] == 'p' || re[i + 1] == 'P') && re[i + 2] == '{') {
                        val end = re.indexOf('}', i); if (end < 0) return "open \\p{"; i = end
                    } else i++
                }
                inClass -> if (c == ']') inClass = false
                c == '[' -> { inClass = true; if (re.getOrNull(i + 1) == '^') i++; if (re.getOrNull(i + 1) == ']') i++ }
                c == '{' -> {
                    val end = re.indexOf('}', i)
                    if (end < 0 || !Regex("""\d+(,\d*)?""").matches(re.substring(i + 1, end))) return "losse '{' op $i"
                    i = end
                }
                c == '}' -> return "losse '}' op $i"
            }
            i++
        }
        return null
    }

    @Test
    fun rampRegexFindsManaSymbols() {
        assertTrue(nl.spoonpaco.ninegrid.domain.Role.RAMP in nl.spoonpaco.ninegrid.domain.Suggestions.rolesOf("{T}: Add {G}.", "Creature — Elf Druid"))
    }

    @Test
    fun suggestionsColorsSplashAndFetchlands() {
        val S = nl.spoonpaco.ninegrid.domain.Suggestions
        fun cand(id: Long, name: String, type: String, ci: String, text: String = "", produced: String = "") =
            nl.spoonpaco.ninegrid.domain.Candidate(id, name, type, text, "", 2.0, ci, "modern=legal", false, null, produced)
        // Rood-zwart modern deck met één losse blauwe kaart: blauw telt niet als deckkleur.
        val deck = (1..12).map { card("Rakdos $it", 4, type = "Instant", id = "BR", text = "Destroy target creature.", legal = "modern=legal") } +
            card("Stray Opt", 1, type = "Instant", id = "U", legal = "modern=legal") +
            card("Swamp", 10, type = "Basic Land — Swamp", legal = "modern=legal") +
            card("Island", 2, type = "Basic Land — Island", legal = "modern=legal")
        assertEquals(setOf('B', 'R'), S.deckColors(deck))
        val pool = listOf(
            cand(1, "Divination", "Sorcery", "U", "Draw two cards."),
            cand(2, "Tormenting Voice", "Sorcery", "R", "As an additional cost, discard a card. Draw two cards."),
            cand(3, "Polluted Delta", "Land", "", "{T}, Pay 1 life, Sacrifice Polluted Delta: Search your library for an Island or Swamp card, put it onto the battlefield."),
            cand(4, "Bloodstained Mire", "Land", "", "{T}, Pay 1 life, Sacrifice Bloodstained Mire: Search your library for a Swamp or Mountain card, put it onto the battlefield."),
        )
        val plain = S.suggest(DeckFormat.MODERN, deck, emptyMap(), pool, nl.spoonpaco.ninegrid.domain.DeckRules())
        assertFalse(plain.adds.any { it.card.name == "Divination" })
        assertTrue(plain.adds.any { it.card.name == "Tormenting Voice" })
        assertTrue(plain.lands.any { it.card.name == "Bloodstained Mire" })
        assertFalse(plain.lands.any { it.card.name == "Polluted Delta" })
        assertEquals("BR", plain.colors)
        // Met splash: de Islands maken blauw, dus Counterspell mag.
        val splash = S.suggest(DeckFormat.MODERN, deck, emptyMap(), pool, nl.spoonpaco.ninegrid.domain.DeckRules(splash = true))
        assertTrue(splash.adds.any { it.card.name == "Divination" })
        assertEquals("U", splash.splashColors)
        assertEquals(setOf('U', 'B'), S.landColors("Land", pool[2].oracleText, "", ""))
    }

    @Test
    fun winnerAndBreakout() {
        val M = nl.spoonpaco.ninegrid.domain.PriceMovers
        fun pr(id: String, name: String, type: String, eur: Double) = nl.spoonpaco.ninegrid.data.db.CardPrint(id = id, name = name, setCode = "X", collectorNumber = "1", typeLine = type, eur = eur)
        fun old(id: String, eur: Double) = nl.spoonpaco.ninegrid.data.db.PricePoint(id, "2026-11-01", eur, null, null, null, null)
        val bulk = pr("a", "Bulk Rare", "Creature — Elf", 0.30)
        val shock = pr("b", "Godless Shrine", "Land — Plains Swamp", 14.0)
        val ghast = pr("c", "Bloodghast", "Creature — Vampire Spirit", 21.0)
        val e = listOf(
            nl.spoonpaco.ninegrid.data.db.Entry(printId = "a", quantity = 1) to bulk,
            nl.spoonpaco.ninegrid.data.db.Entry(printId = "b", quantity = 4) to shock,
            nl.spoonpaco.ninegrid.data.db.Entry(printId = "c", quantity = 1) to ghast,
        )
        val mv = M.movers(e, mapOf("a" to old("a", 0.10), "b" to old("b", 12.0), "c" to old("c", 15.0)), "EUR")
        // Winner in euro over je eigen exemplaren: 4 × €2 = €8 wint van 1 × €6 en van +200% op een bulkkaart.
        assertEquals("Godless Shrine", M.winner(mv)?.print?.name)
        // Breakout per stuk: +€6 en +40% haalt de drempel, +€2 (16,7%) niet.
        assertEquals("Bloodghast", M.breakout(mv)?.print?.name)
        assertEquals("Creature", M.mainType("Artifact Creature — Golem"))
        assertEquals("Land", M.mainType("Land — Plains Swamp"))
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun offlineBulkParsesStreamed() {
        val bulk = """[
          {"id":"a1","oracle_id":"o1","name":"Bloodghast","set":"mh3","set_name":"Modern Horizons 3","collector_number":"12","rarity":"rare",
           "type_line":"Creature — Vampire Spirit","mana_cost":"{1}{B}","cmc":2,"colors":["B"],"color_identity":["B"],
           "image_uris":{"small":"s.jpg","normal":"n.jpg"},"prices":{"eur":"20.5","eur_foil":null},"released_at":"2024-06-14","legalities":{"modern":"legal"}},
          {"id":"d1","name":"Arena Only","set":"ymid","collector_number":"1","digital":true},
          {"id":"f1","name":"Delver of Secrets // Insectile Aberration","set":"isd","collector_number":"51","card_faces":[{"image_uris":{"small":"x.jpg"},"mana_cost":"{U}"}]}
        ]"""
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val cards = json.decodeToSequence(bulk.byteInputStream(), nl.spoonpaco.ninegrid.data.scryfall.SfCard.serializer(), kotlinx.serialization.json.DecodeSequenceMode.ARRAY_WRAPPED)
            .filter { !it.digital }.map { nl.spoonpaco.ninegrid.data.offline.OfflineCard.from(it, 1L) }.toList()
        assertEquals(2, cards.size)
        assertEquals("delver of secrets", cards[1].front)
        val back = cards[0].toSfCard().toPrint()
        assertEquals("MH3", back.setCode)
        assertEquals(20.5, back.eur!!, 0.001)
        assertEquals("n.jpg", back.imageNormal)
        assertEquals("{U}", cards[1].manaCost)
    }

    @Test
    fun dateStylesOnlyChangeDisplay() {
        val D = nl.spoonpaco.ninegrid.domain.Dates
        val d = java.time.LocalDate.of(2026, 11, 5)
        assertEquals("05-11-2026", D.date(d, nl.spoonpaco.ninegrid.domain.DateStyle(date = "dmy")))
        assertEquals("11/05/2026", D.date(d, nl.spoonpaco.ninegrid.domain.DateStyle(date = "mdy")))
        assertEquals("2026-11-05", D.date(d, nl.spoonpaco.ninegrid.domain.DateStyle(date = "ymd")))
        assertEquals(java.time.DayOfWeek.SUNDAY, D.weekStart(nl.spoonpaco.ninegrid.domain.DateStyle(weekStart = "sun")))
    }

    /** Alleen in de diagnose-bouw: het echte bulk-bestand van Scryfall inlezen zoals de app dat doet. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun realBulkParses() {
        val path = System.getenv("BULK_FILE") ?: return
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val t0 = System.currentTimeMillis()
        var n = 0; var digital = 0
        java.io.File(path).inputStream().use { input ->
            for (c in nl.spoonpaco.ninegrid.data.offline.OfflineCards.cards(input)) {
                if (c.digital) { digital++; continue }
                nl.spoonpaco.ninegrid.data.offline.OfflineCard.from(c, 1L); n++
            }
        }
        println("BULK parsed=$n digital=$digital ms=${System.currentTimeMillis() - t0} mem=${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576}MB")
        assertTrue(n > 50000)
    }

    @Test
    fun testWorkbookImports() {
        // De tabbladen van het testbestand als CSV (de xlsx-lezer gebruikt Android en draait hier niet).
        val dir = java.io.File(javaClass.classLoader!!.getResource("testsheets")?.toURI() ?: return)
        val sheets = dir.listFiles()!!.sortedBy { it.name }.map { f ->
            f.name.substringAfter('-').removeSuffix(".csv") to nl.spoonpaco.ninegrid.domain.SheetImport.parseCsv(f.readText())
        }
        val d = nl.spoonpaco.ninegrid.domain.SheetImport.interpret(sheets)
        println("TESTFILE format=${d.format} collection=${d.collection.size} decks=${d.decks.map { it.name + ":" + it.cards.size }} history=${d.history.size} " +
            "fr=${d.collection.count { it.language == "FR" }} proxy=${d.collection.count { it.proxy }} foil=${d.collection.count { it.foil }}")
        assertEquals(18, d.collection.size)
        assertEquals(2, d.decks.size)
        assertTrue(d.history.size > 100)
    }

    @Test
    fun importNameCheck() {
        val W = nl.spoonpaco.ninegrid.data.ImportWorker
        fun card(name: String, lang: String = "") = nl.spoonpaco.ninegrid.domain.ImportCard(setCode = "MH2", number = "183", name = name, foil = false, etched = false, language = lang, quantity = 1, proxy = false)
        assertTrue(W.nameFits(card("Ragavan, Nimble Pilferer"), "Ragavan, Nimble Pilferer"))
        assertFalse(W.nameFits(card("Ragavan, Nimble Pilferer"), "Some Common"))
        assertTrue(W.nameFits(card("Lightning Bolt (FR)"), "Lightning Bolt"))
        assertTrue(W.nameFits(card("Delver of Secrets"), "Delver of Secrets // Insectile Aberration"))
        assertTrue(W.nameFits(card("Foudre", "FR"), "Lightning Bolt"))
        assertTrue(W.nameFits(card(""), "Anything"))
    }

    @Test
    fun aiJsonIsFoundInChattyAnswers() {
        val j = nl.spoonpaco.ninegrid.data.ai.AiClient.extractJson("Sure! ```json\n{\"name\": \"Lightning Bolt\", \"set\": \"M11\", \"number\": \"146\", \"lang\": null}\n```")
        assertTrue(j != null && j.toString().contains("Lightning Bolt"))
        assertEquals(null, nl.spoonpaco.ninegrid.data.ai.AiClient.extractJson("no json here"))
    }

    @Test
    fun spellbookTwoCardCombosFromNewShape() {
        val est = kotlinx.serialization.json.Json.parseToJsonElement("""
            {"bracketTag":"S","cards":[],"combos":[
             {"combo":{"id":"690-3966","uses":[{"card":{"name":"Sanguine Bond"}},{"card":{"name":"Exquisite Blood"}}]},"relevant":true,"definitelyTwoCard":false,"arguablyTwoCard":true,"speed":5},
             {"combo":{"id":"1274-1495","uses":[{"card":{"name":"Heliod, Sun-Crowned"}},{"card":{"name":"Triskelion"}}]},"relevant":true,"definitelyTwoCard":true,"arguablyTwoCard":true,"speed":5},
             {"combo":{"id":"628-3693","uses":[{"card":{"name":"Walking Ballista"}},{"card":{"name":"Mikaeus, the Unhallowed"}}]},"relevant":false,"definitelyTwoCard":true,"speed":5}
            ]}""").let { (it as kotlinx.serialization.json.JsonObject) }
        val combos = nl.spoonpaco.ninegrid.data.ComboService.twoCardCombos(est)
        assertEquals(listOf(listOf("Heliod, Sun-Crowned", "Triskelion")), combos)
        val check = Formats.check(DeckFormat.COMMANDER, emptyList(), Formats.ComboSignals(late = combos))
        assertEquals("3", check.bracketHint)
    }

    private fun ewp(id: Long, name: String, set: String, nr: String, qty: Int = 1, deckId: Long? = null, binder: String? = null, proxy: Boolean = false,
                    type: String = "Creature — Elf", ci: String = "G", eur: Double = 1.0, section: String? = null, text: String = "") =
        nl.spoonpaco.ninegrid.data.db.EntryWithPrint(
            nl.spoonpaco.ninegrid.data.db.Entry(id = id, printId = "p-$name-$set", quantity = qty, deckId = deckId, binder = binder, proxy = proxy, section = section),
            nl.spoonpaco.ninegrid.data.db.CardPrint(id = "p-$name-$set", name = name, setCode = set, collectorNumber = nr, typeLine = type, oracleText = text, colorIdentity = ci, eur = eur, legalities = "commander=legal"),
        )

    @Test
    fun shareFileRoundTripAndChoices() {
        val S = nl.spoonpaco.ninegrid.domain.Share
        val decks = listOf(nl.spoonpaco.ninegrid.data.db.Deck(id = 7, name = "Elves", format = DeckFormat.COMMANDER))
        val entries = listOf(
            ewp(1, "Llanowar Elves", "M19", "314", qty = 3, binder = "Groen"),
            ewp(2, "Llanowar Elves", "M19", "314", qty = 1, binder = "Rood"),
            ewp(3, "Sol Ring", "C21", "263", binder = "Rood"),
            ewp(4, "Fake Ring", "C21", "999", proxy = true),
            ewp(5, "Elvish Mystic", "M14", "169", deckId = 7, section = "MAIN"),
        )
        val all = S.packages(entries, decks, nl.spoonpaco.ninegrid.domain.ShareChoice(all = true))
        assertEquals(listOf("loose", "deck:Elves"), all.map { it.key })
        // Gelijke kaarten samen, proxy niet mee.
        assertEquals(4, all[0].cards.first { it.name == "Llanowar Elves" }.qty)
        assertTrue(all[0].cards.none { it.name == "Fake Ring" })
        assertEquals("MAIN", all[1].cards.single().section)
        val rood = S.packages(entries, decks, nl.spoonpaco.ninegrid.domain.ShareChoice(binders = setOf("Rood"), sets = setOf("c21")))
        assertEquals(listOf("binder:Rood", "set:C21"), rood.map { it.key })
        assertEquals(2, S.count(rood.take(1)))
        val f = nl.spoonpaco.ninegrid.domain.ShareFile(senderId = "abc", senderName = "Mick", createdAt = 1, packages = all)
        val back = S.decode(S.encode(f))
        assertEquals(f, back)
        assertNull(S.decode("{\"format\":\"something\",\"senderId\":\"x\",\"senderName\":\"\",\"createdAt\":1,\"packages\":[]}"))
        assertNull(S.decode("not json"))
        assertEquals("NineGrid - Mick.ninegrid", S.fileName("Mick"))
        assertEquals("NineGrid - ab.ninegrid", S.fileName("a/b"))
    }

    @Test
    fun tradeMatchFindsCardsForDecks() {
        val T = nl.spoonpaco.ninegrid.domain.TradeMatch
        val myDeck = (1..30).map { ewp(100L + it, "Elf $it", "AAA", "$it", deckId = 1, section = "MAIN") } +
            ewp(99, "Elf Lord", "AAA", "99", deckId = 1, section = "COMMANDER", type = "Legendary Creature — Elf")
        val mine = myDeck + ewp(200, "Mountain Goat", "BBB", "1", type = "Creature — Goat", ci = "R")
        val theirs = listOf(
            ewp(-1, "Priest of Titania", "CCC", "1", eur = 3.0, text = "{T}: Add {G} for each Elf on the battlefield."),
            ewp(-2, "Lightning Bolt", "CCC", "2", type = "Instant", ci = "R", eur = 1.5, text = "Lightning Bolt deals 3 damage to any target."),
            ewp(-3, "Elf 1", "AAA", "1"),
        )
        val r = T.compute(mine, listOf(nl.spoonpaco.ninegrid.domain.TradeMatch.DeckInput("Elves", DeckFormat.COMMANDER, myDeck)), theirs, emptyList(), "EUR")
        val names = r.get.map { it.print.name }
        assertTrue(names.contains("Priest of Titania"))
        // Buiten de kleuren van de commander, en al in het deck: niet.
        assertFalse(names.contains("Lightning Bolt"))
        assertFalse(names.contains("Elf 1"))
        assertEquals(listOf("Elves"), r.get.first { it.print.name == "Priest of Titania" }.decks)
        assertTrue(r.give.isEmpty())
    }

    @Test
    fun guideSearchFindsChapters() {
        val G = nl.spoonpaco.ninegrid.ui.guide.Guide
        val hits = G.search(G.chapters(true), "back-up")
        assertTrue(hits.isNotEmpty() && hits.first().chapter.id == "backup")
        assertTrue(G.search(G.chapters(true), "x").isEmpty())
        assertTrue(G.search(G.chapters(false), "qqqzzz").isEmpty())
    }

    @Test
    fun aiErrorsAreClassified() {
        val A = nl.spoonpaco.ninegrid.data.ai.AiClient
        assertEquals(nl.spoonpaco.ninegrid.data.ai.AiError.OVERLOADED, A.classify(503))
        assertEquals(nl.spoonpaco.ninegrid.data.ai.AiError.RATE_LIMIT, A.classify(429))
        assertEquals(nl.spoonpaco.ninegrid.data.ai.AiError.BAD_KEY, A.classify(401))
        assertEquals(nl.spoonpaco.ninegrid.data.ai.AiError.MODEL, A.classify(404))
        assertTrue(A.retryable(A.classify(503)) && A.retryable(A.classify(429)))
        assertFalse(A.retryable(A.classify(401)))
    }

    /** De migraties naar versie 9 en 10 moeten precies de tabellen maken die Room verwacht (anders start de app niet na de update). */
    @Test
    fun friendMigrationMatchesRoomSchema() {
        // Het nieuwste schema dat Room schreef (bij elke nieuwe databaseversie een nieuw bestand).
        val dir = listOf("schemas", "app/schemas").map { java.io.File(it, "nl.spoonpaco.ninegrid.data.db.AppDatabase") }.firstOrNull { it.isDirectory }
            ?: error("geen schemamap in " + java.io.File(".").absolutePath)
        val f = dir.listFiles().orEmpty().filter { it.name.endsWith(".json") }.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }!!
        assertEquals("10", f.nameWithoutExtension)
        val root = kotlinx.serialization.json.Json.parseToJsonElement(f.readText()) as kotlinx.serialization.json.JsonObject
        val entities = ((root["database"] as kotlinx.serialization.json.JsonObject)["entities"] as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
        fun str(o: kotlinx.serialization.json.JsonObject, k: String) = (o[k] as kotlinx.serialization.json.JsonPrimitive).content
        val expected = mutableListOf<String>()
        for (e in entities) {
            val t = str(e, "tableName")
            if (!t.startsWith("friend") && t !in setOf("wishlist", "boosters", "booster_cards", "booster_boxes")) continue
            expected += str(e, "createSql").replace("`\${TABLE_NAME}`", "`$t`")
            (e["indices"] as? kotlinx.serialization.json.JsonArray)?.forEach { i ->
                expected += str(i as kotlinx.serialization.json.JsonObject, "createSql").replace("`\${TABLE_NAME}`", "`$t`")
            }
        }
        assertEquals(expected.sorted(), (nl.spoonpaco.ninegrid.data.db.AppDatabase.FRIEND_SQL + nl.spoonpaco.ninegrid.data.db.AppDatabase.V10_SQL).sorted())
    }

    /** Elk scherm dat een filter opslaat, moet als filterscherm geregistreerd zijn; anders springt het filter terug. */
    @Test
    fun everyFilterScreenIsRegistered() {
        val root = java.io.File("src/main/java")
        val used = root.walk().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> Regex("""setFilter\("([a-z]+)"""").findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        assertTrue("geen setFilter-aanroepen gevonden", used.isNotEmpty())
        val missing = used - nl.spoonpaco.ninegrid.data.FILTER_SCREENS.toSet()
        assertTrue("niet geregistreerd: $missing", missing.isEmpty())
    }

    // ---- Lege vakken op een blad: het kader is het raster ----
    private fun pageLines(slots: List<Int>, left: Float = 0f, top: Float = 0f, cw: Float = 300f, ch: Float = 420f) = slots.flatMap { slot ->
        val r = (slot - 1) / 3; val c = (slot - 1) % 3
        val x = left + c * cw; val y = top + r * ch
        listOf(
            nl.spoonpaco.ninegrid.domain.OcrLine("Kaart$slot", x + cw * 0.4f, y + ch * 0.07f),
            nl.spoonpaco.ninegrid.domain.OcrLine("Creature — Elf", x + cw * 0.4f, y + ch * 0.55f),
            nl.spoonpaco.ninegrid.domain.OcrLine("MH2 • EN", x + cw * 0.25f, y + ch * 0.93f),
        )
    }
    private val guide = nl.spoonpaco.ninegrid.domain.Area(0f, 0f, 900f, 1260f)
    private fun bySlot(lines: List<nl.spoonpaco.ninegrid.domain.OcrLine>, crop: nl.spoonpaco.ninegrid.domain.Area? = guide) =
        nl.spoonpaco.ninegrid.domain.ScanParser.parse(lines, 900, 1260, crop).associate { it.slot to it.lines }

    private fun assertPage(filled: List<Int>, lines: List<nl.spoonpaco.ninegrid.domain.OcrLine> = pageLines(filled)) {
        val got = bySlot(lines)
        for (slot in 1..9) {
            if (slot in filled) assertTrue("vak $slot mist Kaart$slot: ${got[slot]}", got[slot].orEmpty().any { it == "Kaart$slot" })
            else assertTrue("vak $slot hoort leeg te zijn: ${got[slot]}", got[slot].orEmpty().isEmpty())
        }
    }

    @Test fun emptyTopRowKeepsSlots() = assertPage(listOf(4, 5, 6, 7, 8, 9))
    @Test fun emptyBottomRowKeepsSlots() = assertPage(listOf(1, 2, 3, 4, 5, 6))
    @Test fun emptyLeftColumnKeepsSlots() = assertPage(listOf(2, 3, 5, 6, 8, 9))
    @Test fun emptyRightColumnKeepsSlots() = assertPage(listOf(1, 2, 4, 5, 7, 8))
    @Test fun singleCardKeepsSlot() = assertPage(listOf(5))
    @Test fun diagonalKeepsSlots() = assertPage(listOf(1, 5, 9))
    @Test fun fullPageStillWorks() = assertPage((1..9).toList())

    @Test
    fun emptySlotsByImageStructure() {
        val e = mapOf(1 to 1.5, 2 to 22.0, 3 to 25.0, 4 to 2.0, 5 to 20.0, 6 to 9.0, 7 to 18.0, 8 to 1.0, 9 to 24.0)
        val hasText = setOf(2, 3, 5, 7, 9)
        // 6 heeft geen tekst maar wel structuur (een kaart die niet gelezen kon worden): niet leeg.
        assertEquals(setOf(1, 4, 8), nl.spoonpaco.ninegrid.domain.EmptySlots.decide(e, hasText))
        val flat = IntArray(48 * 64) { 128 }
        assertEquals(0.0, nl.spoonpaco.ninegrid.domain.EmptySlots.energy(flat, 48, 64), 0.0001)
        val stripes = IntArray(48 * 64) { i -> if ((i % 48) % 2 == 0) 0 else 200 }
        assertTrue(nl.spoonpaco.ninegrid.domain.EmptySlots.energy(stripes, 48, 64) > 50)
    }

    /** Deck met drie prints van Island, terwijl er 5th Edition Islands in de collectie liggen: alleen exact dezelfde print telt. */
    @Test
    fun basicLandsOnlyTakeTheExactPrint() {
        val T = nl.spoonpaco.ninegrid.domain.DeckTake
        fun e(id: Long, print: String, qty: Int, deck: Long? = null, finish: String = nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL, proxy: Boolean = false) =
            nl.spoonpaco.ninegrid.data.db.Entry(id = id, printId = print, quantity = qty, deckId = deck, finish = finish, proxy = proxy)
        val oracle = mapOf("isl-5ed" to "o-island", "isl-mh3" to "o-island", "isl-blb" to "o-island", "isl-neo" to "o-island", "bolt-m10" to "o-bolt", "bolt-2xm" to "o-bolt")
        val collection = listOf(e(1, "isl-5ed", 20), e(2, "isl-mh3", 2), e(3, "isl-mh3", 1, finish = nl.spoonpaco.ninegrid.data.db.Finish.FOIL), e(4, "bolt-m10", 3), e(5, "isl-blb", 4, deck = 9))
        // MH3 Island: alleen de 2 gewone MH3-exemplaren, niet de foil en niet de 5ED-lands.
        val mh3 = T.candidates(e(0, "isl-mh3", 4), "o-island", true, collection, oracle)
        assertEquals(listOf(2L), mh3.map { it.id })
        assertEquals(listOf(2L to 2), T.plan(mh3, 4).map { it.first.id to it.second })
        // BLB en NEO Island: niets los in de collectie (BLB zit al in een deck), dus alles komt er nieuw bij.
        assertTrue(T.candidates(e(0, "isl-blb", 3), "o-island", true, collection, oracle).isEmpty())
        assertTrue(T.candidates(e(0, "isl-neo", 3), "o-island", true, collection, oracle).isEmpty())
        // Een gewone kaart mag wel een andere print uit de collectie pakken.
        val bolt = T.candidates(e(0, "bolt-2xm", 2), "o-bolt", false, collection, oracle)
        assertEquals(listOf(4L), bolt.map { it.id })
        assertEquals(2, T.plan(bolt, 2).sumOf { it.second })
    }

    // ---- Scanner: verzamelregel ----
    private fun single(vararg bottom: String, name: String = "Seachrome Coast") = ScanParser.analyzeSingle(
        listOf(OcrLine(name, 50f, 20f), OcrLine("Land", 50f, 170f)) + bottom.mapIndexed { i, t -> OcrLine(t, 50f, 288f + i * 4f) }, 100, 300, null,
    ).slots.single()

    /** Een losse ruisletter onderaan is geen nummer: dan blijft het vak "nummer mist" en wordt het opnieuw gelezen. */
    @Test
    fun scanNoiseLettersAreNotANumber() {
        for (noise in listOf("I", "O", "LL", "IO", "L")) {
            val r = single(noise, "MH2 • EN")
            assertNull("ruis '$noise' gelezen als nummer ${r.collectorNumber}", r.collectorNumber)
            assertTrue(ScanParser.needsReread(r))
        }
        // Met een echt cijfer erbij is het wél een nummer, en worden O, I en l hersteld.
        assertEquals("100", single("IO0", "MH2 • EN").collectorNumber)
        assertEquals("103", single("lO3/280 R", "MH2 • EN").collectorNumber)
        assertEquals("17", single("I7", "MH2 • EN").collectorNumber)
    }

    /** De M van mythic hoort niet bij het nummer, ook niet als de OCR de spatie mist. */
    @Test
    fun scanRarityLetterIsNotPartOfTheNumber() {
        assertEquals("374", single("374M", "ONE • EN").collectorNumber)
        assertEquals("374", single("374 M", "ONE • EN").collectorNumber)
        assertEquals("229", single("229/254 M", "MH1 • EN").collectorNumber)
        assertEquals("74", single("M 0074", "WOT • EN").collectorNumber)
        // Een echte letter achter het nummer blijft staan.
        assertEquals("103A", single("103a/280 R", "MH2 • EN").collectorNumber)
    }

    /** Kracht/weerstand is geen kaartnummer. */
    @Test
    fun scanPowerToughnessIsNotANumber() {
        val lines = listOf(OcrLine("Grizzly Bears", 50f, 20f), OcrLine("Creature — Bear", 50f, 170f), OcrLine("2/2", 85f, 270f), OcrLine("M10 • EN", 30f, 296f))
        val r = ScanParser.analyzeSingle(lines, 100, 300, null).slots.single()
        assertNull(r.collectorNumber)
        assertEquals("M10", r.setCode)
    }

    /** Foils hebben een ster tussen set en taal: de set wordt gelezen en het vak krijgt de foil-hint. */
    @Test
    fun scanReadsFoilStar() {
        val foil = single("229/254 M", "MH1 ★ EN")
        assertEquals("MH1", foil.setCode); assertEquals("", foil.language); assertEquals("229", foil.collectorNumber)
        assertTrue(foil.foilStar)
        val german = single("229/254 M", "MH1★DE")
        assertEquals("MH1", german.setCode); assertEquals("DE", german.language); assertTrue(german.foilStar)
        val plain = single("229/254 M", "MH1 • EN")
        assertEquals("MH1", plain.setCode); assertFalse(plain.foilStar)
        // Bij opnieuw lezen van de onderste strook komt de ster er alsnog bij.
        val missing = single("229/254 M")
        assertNull(missing.setCode)
        val again = ScanParser.enrich(missing, emptyList(), listOf("MH1 ★ EN"))
        assertEquals("MH1", again.setCode); assertTrue(again.foilStar)
    }
}

