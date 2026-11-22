package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.domain.FrameVotes
import nl.spoonpaco.ninegrid.domain.PictureIndex
import nl.spoonpaco.ninegrid.domain.SlotRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1h-scan: beelden laten stemmen (kaart voor kaart) en zoeken in de vingerafdrukken van een set. */
class PictureScanTest {
    private fun read(name: String?, set: String? = null, nr: String? = null) = FrameVotes.Read(name, set, nr, 0L)
    private fun slot(name: String?, set: String? = null, nr: String? = null) = SlotRead(1, set, nr, null, listOf("x"), name)

    @Test
    fun majorityNeedsTwoVotesAndNoTie() {
        assertNull(FrameVotes.majority(listOf("123")))
        assertEquals("123", FrameVotes.majority(listOf("123", "123", "128")))
        assertNull(FrameVotes.majority(listOf("123", "123", "128", "128")))
        assertNull(FrameVotes.majority(emptyList<String>()))
    }

    @Test
    fun fillsOnlyWhatThePhotoMissed() {
        val frames = listOf(read("Llanowar Elves", "DOM", "168"), read("Llanowar Elvez", "DOM", "168"), read("Llanowar Elves", "D0M", "16"))
        val r = FrameVotes.fill(slot("Llanowar Elves"), frames)
        assertEquals("DOM", r.setCode)
        assertEquals("168", r.collectorNumber)
        // Wat de foto las, wint.
        val own = FrameVotes.fill(slot("Llanowar Elves", nr = "169"), frames)
        assertEquals("169", own.collectorNumber)
        assertEquals("DOM", own.setCode)
    }

    @Test
    fun ignoresFramesOfAnotherCard() {
        val frames = listOf(read("Shivan Dragon", "DOM", "140"), read("Shivan Dragon", "DOM", "140"))
        val r = FrameVotes.fill(slot("Llanowar Elves"), frames)
        assertNull(r.setCode); assertNull(r.collectorNumber)
    }

    @Test
    fun nameFromFramesOnlyWhenTheyAgree() {
        val agree = listOf(read("Llanowar Elves", nr = "168"), read("Llanowar Elves", nr = "168"))
        val r = FrameVotes.fill(slot(null), agree)
        assertEquals("Llanowar Elves", r.name); assertEquals("168", r.collectorNumber)
        // Geen naam op de foto en geen eensgezinde naam in de beelden: niets aanvullen.
        val photo = slot(null)
        assertSame(photo, FrameVotes.fill(photo, listOf(read("Llanowar Elves", nr = "168"), read(null, nr = "168"), read(null, nr = "168"))))
    }

    @Test
    fun completePhotoStaysAsItIs() {
        val photo = slot("Llanowar Elves", "DOM", "168")
        assertSame(photo, FrameVotes.fill(photo, listOf(read("Llanowar Elves", "M19", "314"), read("Llanowar Elves", "M19", "314"))))
    }

    @Test
    fun recentForgetsOldFrames() {
        val v = FrameVotes(window = 3, maxAgeMs = 1000)
        v.add(FrameVotes.Read("A", null, null, 0)); v.add(FrameVotes.Read("B", null, null, 1500)); v.add(FrameVotes.Read(null, null, null, 1600))
        assertEquals(listOf("B"), v.recent(2000).map { it.name })
        repeat(5) { v.add(FrameVotes.Read("C", null, null, 1900)) }
        assertEquals(3, v.recent(2000).size)
    }

    private fun index(vararg rows: Pair<String, FloatArray>): PictureIndex {
        val dim = rows.first().second.size
        val vec = ByteArray(rows.size * dim)
        rows.forEachIndexed { i, (_, v) -> System.arraycopy(PictureIndex.quantize(v), 0, vec, i * dim, dim) }
        return PictureIndex("TST", rows.indices.map { "id$it" }, rows.map { it.first }, rows.indices.map { "${it + 1}" }, dim, vec)
    }

    @Test
    fun quantizeHasLengthOne() {
        val q = PictureIndex.quantize(floatArrayOf(3f, 4f))
        assertEquals(76, q[0].toInt()); assertEquals(102, q[1].toInt())
        assertEquals(0, PictureIndex.quantize(floatArrayOf(0f, 0f))[0].toInt())
    }

    @Test
    fun bestFindsNearestAndMarginSkipsSameName() {
        val ix = index(
            "Eternal Wanderer" to floatArrayOf(1f, 0f, 0f),
            // Showcase met hetzelfde plaatje: telt niet mee voor de marge.
            "Eternal Wanderer" to floatArrayOf(0.98f, 0.2f, 0f),
            "Intruder Alarm" to floatArrayOf(0f, 1f, 0f),
            "Havoc Festival" to floatArrayOf(0f, 0f, 1f),
        )
        val g = ix.best(PictureIndex.quantize(floatArrayOf(0.95f, 0.1f, 0.05f)))
        assertNotNull(g)
        assertEquals("Eternal Wanderer", g!!.name)
        assertEquals("Intruder Alarm", g.runnerUp)
        assertTrue(g.score > 0.9f)
        assertTrue(g.margin > 0.5f)
        assertTrue(g.sure)
        // Twee prints van Eternal Wanderer liggen dicht bij elkaar: het plaatje kiest de print dan niet.
        assertTrue(g.printMargin < PictureIndex.MIN_PRINT_MARGIN)
        assertFalse(g.printSure)
    }

    @Test
    fun printMarginWhenArtDiffers() {
        val ix = index(
            "Eternal Wanderer" to floatArrayOf(1f, 0f, 0f, 0f),
            "Eternal Wanderer" to floatArrayOf(0f, 0f, 0f, 1f),
            "Intruder Alarm" to floatArrayOf(0f, 1f, 0f, 0f),
        )
        val g = ix.best(PictureIndex.quantize(floatArrayOf(0f, 0.1f, 0f, 1f)))!!
        assertEquals("2", g.number)
        assertTrue(g.printSure)
        assertTrue(g.describe().contains(" print "))
    }

    @Test
    fun unclearPictureIsNotSure() {
        val ix = index("A" to floatArrayOf(1f, 0f), "B" to floatArrayOf(0f, 1f))
        val g = ix.best(PictureIndex.quantize(floatArrayOf(1f, 0.97f)))!!
        assertFalse(g.sure)
        assertNull(ix.best(ByteArray(3)))
    }
}
