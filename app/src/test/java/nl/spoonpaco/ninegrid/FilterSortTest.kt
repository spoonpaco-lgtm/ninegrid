package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.domain.Growth
import nl.spoonpaco.ninegrid.ui.components.CardFilter
import nl.spoonpaco.ninegrid.ui.components.SavedView
import nl.spoonpaco.ninegrid.ui.components.SortKey
import nl.spoonpaco.ninegrid.ui.components.priceLabel
import nl.spoonpaco.ninegrid.ui.components.reversedIf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.9.15: prijsfilter, sorteerrichting, bewaarde combinaties en waar de groei vandaan komt. */
class FilterSortTest {
    @Test
    fun priceOkRespectsBothLimits() {
        val f = CardFilter(priceMin = 5.0, priceMax = 20.0)
        assertTrue(f.priceOk(5.0)); assertTrue(f.priceOk(20.0)); assertTrue(f.priceOk(12.5))
        assertFalse(f.priceOk(4.99)); assertFalse(f.priceOk(20.01)); assertFalse(f.priceOk(null))
        assertTrue(CardFilter().priceOk(null))
        assertTrue(CardFilter(priceMax = 1.0).priceOk(0.5))
        assertEquals(1, CardFilter(priceMin = 1.0).count)
    }

    @Test
    fun matchesUsesUnitPriceAndFoil() {
        val p = CardPrint(id = "x", name = "Bolt", setCode = "m10", setName = "M10", collectorNumber = "1", rarity = "common", eur = 2.0, eurFoil = 30.0)
        val normal = EntryWithPrint(Entry(printId = "x", quantity = 4), p)
        val foil = EntryWithPrint(Entry(printId = "x", finish = "foil", quantity = 1), p)
        val f = CardFilter(priceMin = 5.0)
        // Per stuk: 4 × € 2 is € 8 in totaal, maar per stuk € 2.
        assertFalse(f.matches(normal, ""))
        assertTrue(f.matches(foil, ""))
    }

    @Test
    fun sortKeyRoundTrip() {
        assertEquals("price", SortKey.key("price~r")); assertTrue(SortKey.rev("price~r"))
        assertEquals("price", SortKey.key("price")); assertFalse(SortKey.rev("price"))
        assertEquals("new~r", SortKey.of("new", true)); assertEquals("new", SortKey.of("new", false))
        assertEquals(listOf(3, 2, 1), listOf(1, 2, 3).reversedIf(true))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).reversedIf(false))
    }

    @Test
    fun savedViewSurvivesJson() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val v = SavedView("Nieuw en duur", CardFilter(priceMin = 5.0, onlyNew = true), "new")
        val back = json.decodeFromString(SavedView.serializer(), json.encodeToString(SavedView.serializer(), v))
        assertEquals(v, back)
        // Een oud bewaard filter zonder prijsvelden leest nog.
        assertNull(CardFilter.fromJson("""{"rarities":["rare"]}""").priceMin)
    }

    @Test
    fun priceLabels() {
        assertTrue(priceLabel(5.0, 20.0, "EUR").contains("5–20"))
        assertTrue(priceLabel(null, 1.0, "EUR").startsWith("<"))
        assertTrue(priceLabel(20.0, null, "USD").startsWith("> $"))
    }

    @Test
    fun growthSplitsPricesAndCards() {
        // Dag 1 € 100; dag 2 € 130 (+5 koers, dus +25 kaarten erbij); dag 3 € 128 (−2 koers).
        val s = Growth.split(listOf(100.0 to 0.0, 130.0 to 5.0, 128.0 to -2.0))!!
        assertEquals(28.0, s.total, 1e-9)
        assertEquals(3.0, s.prices, 1e-9)
        assertEquals(25.0, s.cards, 1e-9)
        assertNull(Growth.split(listOf(100.0 to 0.0)))
        assertNull(Growth.split(listOf(100.0 to 0.0, 110.0 to null)))
    }
}
