package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.data.offline.ComboDb
import nl.spoonpaco.ninegrid.data.offline.OwnedCombo
import nl.spoonpaco.ninegrid.domain.ComboFilter
import nl.spoonpaco.ninegrid.domain.ComboGroup
import nl.spoonpaco.ninegrid.domain.ComboView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.9.18: het bulkbestand van Commander Spellbook lezen en de combo's indelen en filteren. */
class ComboDbTest {
    private val doc = """
        {"timestamp": "2026-11-23T10:00:00Z", "version": "x[y]{z}",
         "variants": [
           {"id": "1-2", "status": "OK", "identity": "UB",
            "uses": [{"card": {"id": 1, "name": "Thassa's Oracle", "oracleId": "a"}, "quantity": 1},
                     {"card": {"id": 2, "name": "Demonic Consultation"}, "quantity": 1}],
            "requires": [], "produces": [{"feature": {"id": 5, "name": "Win the game"}}],
            "description": "Cast \"Consultation\" [naming] {a card}.", "popularity": 12345, "bracketTag": "R",
            "legalities": {"commander": true, "legacy": true, "modern": false}, "prices": {"tcgplayer": "1.00"}},
           {"id": "3", "status": "NR", "uses": [{"card": {"name": "A"}}, {"card": {"name": "B"}}]},
           {"id": "4", "status": "OK", "uses": [{"card": {"name": "Solo"}}], "requires": [], "popularity": null}
         ],
         "aliases": [{"id": "9", "variant": "1-2"}]}
    """.trimIndent()

    @Test
    fun streamsOnlyTheVariantsArray() {
        val vs = ComboDb.variants(doc.byteInputStream()).toList()
        assertEquals(3, vs.size)
        assertEquals(listOf("Thassa's Oracle", "Demonic Consultation"), vs[0].uses.map { it.card.name })
        val e = ComboDb.parse(vs[0])!!
        assertEquals(listOf("commander", "legacy"), e.legal)
        assertEquals(listOf("Win the game"), e.produces)
        assertEquals(12345, e.popularity)
        // Niet gepubliceerd, of één kaart zonder voorwaarden: niet bewaren.
        assertNull(ComboDb.parse(vs[1]))
        assertNull(ComboDb.parse(vs[2]))
    }

    /** 0.9.19: stappen ingepakt bewaren en terug; dezelfde tekst geeft dezelfde vingerafdruk. */
    @Test
    fun packAndHash() {
        val t = "Cast Demonic Consultation naming a card not in your deck. " .repeat(8) + "Ünïcode ✓"
        val b = ComboDb.pack(t)
        assertTrue(b.size < t.length / 2)
        assertEquals(t, ComboDb.unpack(b))
        assertEquals(ComboDb.textHash(t), ComboDb.textHash(String(t.toCharArray())))
        assertFalse(ComboDb.textHash(t) == ComboDb.textHash(t + "."))
    }

    @Test
    fun gzipWorksToo() {
        val bytes = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(doc.toByteArray()) } }.toByteArray()
        assertEquals(3, ComboDb.variants(bytes.inputStream()).count())
    }

    private fun combo(id: String, cards: List<String>, missing: List<String> = emptyList(), produces: List<String> = listOf("Infinite colorless mana"), identity: String = "C", pop: Int = 0, legal: Set<String> = setOf("commander")) =
        OwnedCombo(id, cards, missing, produces, emptyList(), identity, "", "", pop, null, legal)

    @Test
    fun groupsAndFilters() {
        assertEquals(ComboGroup.WIN, ComboView.group(listOf("Infinite colorless mana", "Win the game")))
        assertEquals(ComboGroup.MANA, ComboView.group(listOf("Infinite colorless mana")))
        assertEquals(ComboGroup.TOKENS, ComboView.group(listOf("Infinite creature tokens")))
        assertEquals(ComboGroup.OTHER, ComboView.group(listOf("Infinite ETB")))
        assertTrue(ComboView.fitsColors("UB", setOf('U', 'B', 'G')))
        assertFalse(ComboView.fitsColors("UB", setOf('U')))
        assertTrue(ComboView.fitsColors("C", setOf('W')))
        val c = combo("1", listOf("Basalt Monolith", "Rings of Brighthearth"))
        // Alle kaarten vrij: Rings zit in een deck.
        assertFalse(ComboView.matches(c, ComboFilter(free = true), setOf("basalt monolith"), { null }))
        assertTrue(ComboView.matches(c, ComboFilter(free = true), setOf("basalt monolith", "rings of brighthearth"), { null }))
        assertFalse(ComboView.matches(c, ComboFilter(format = "modern"), emptySet(), { null }))
        assertTrue(ComboView.matches(c, ComboFilter(card = "Basalt Monolith"), emptySet(), { null }))
        val almost = combo("2", listOf("Dramatic Reversal", "Isochron Scepter"), missing = listOf("Isochron Scepter"))
        assertFalse(ComboView.matches(almost, ComboFilter(maxPrice = 1.0), emptySet(), { 3.0 }))
        assertTrue(ComboView.matches(almost, ComboFilter(maxPrice = 5.0), emptySet(), { 3.0 }))
    }

    @Test
    fun bestBuyCountsCombosPerMissingCard() {
        val list = listOf(
            combo("a", listOf("X", "Scepter"), listOf("Scepter")), combo("b", listOf("Y", "Scepter"), listOf("Scepter")),
            combo("c", listOf("Z", "Oracle"), listOf("Oracle")),
        )
        val buys = ComboView.bestBuys(list) { if (it == "Oracle") 10.0 else 3.0 }
        assertEquals("Scepter", buys.first().card); assertEquals(2, buys.first().combos.size)
    }
}
