package nl.spoonpaco.ninegrid

import nl.spoonpaco.ninegrid.domain.CrashText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashTextTest {
    @Test
    fun reportHoldsVersionDeviceAndTrace() {
        val t = RuntimeException("kapot", IllegalStateException("oorzaak"))
        val text = CrashText.build("0.9.11 – Tezzeret", "samsung SM-A165F", "15", "main", "2026-11-17 12:00:00", CrashText.traceOf(t))
        assertTrue(text.startsWith("NineGrid crashrapport"))
        assertTrue(text.contains("versie: 0.9.11 – Tezzeret · toestel: samsung SM-A165F · Android 15"))
        assertTrue(text.contains("java.lang.RuntimeException: kapot"))
        assertTrue(text.contains("Caused by: java.lang.IllegalStateException: oorzaak"))
    }

    @Test
    fun longTraceIsCut() {
        val text = CrashText.build("v", "d", "1", "t", "w", "x".repeat(CrashText.MAX_TRACE + 500))
        assertTrue(text.contains("(ingekort)"))
        assertEquals(CrashText.MAX_TRACE, text.count { it == 'x' })
    }
}
