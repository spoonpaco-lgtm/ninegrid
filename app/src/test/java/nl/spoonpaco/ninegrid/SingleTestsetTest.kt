package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.domain.SingleTestset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 0.9.16: uitslag.txt van losse-kaarten/ lezen en schrijven. */
class SingleTestsetTest {
    @Test
    fun parsesNameSetAndCrop() {
        val items = SingleTestset.parse(listOf(
            "# Losse kaarten",
            "001: Thriving Bluff (J25 775) | set=ONE | kader=120 300 960 1480",
            "2: Thriving Grove",
            "rommel zonder nummer",
        ))
        assertEquals(2, items.size)
        val a = items[0]
        assertEquals("001", a.id); assertEquals("Thriving Bluff", a.expect.name); assertEquals("J25", a.expect.set); assertEquals("775", a.expect.number)
        assertEquals("ONE", a.set); assertEquals(120f, a.crop!!.left); assertEquals(1480f, a.crop!!.bottom)
        val b = items[1]
        assertEquals("002", b.id); assertEquals("Thriving Grove", b.expect.name); assertNull(b.expect.set); assertEquals("", b.set); assertNull(b.crop)
    }

    @Test
    fun lineRoundTrips() {
        val l = SingleTestset.line("007", "Shock (STA 44)", "STA", "1 2 3 4")
        assertEquals("007: Shock (STA 44) | set=STA | kader=1 2 3 4", l)
        val back = SingleTestset.parse(listOf(l)).single()
        assertEquals("Shock", back.expect.name); assertEquals("STA", back.set)
        assertEquals("008: ?", SingleTestset.line("008", "?", "", ""))
    }
}
