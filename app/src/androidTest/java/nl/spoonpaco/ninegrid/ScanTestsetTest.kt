package nl.spoonpaco.ninegrid

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.spoonpaco.ninegrid.domain.OcrLine
import nl.spoonpaco.ninegrid.domain.ScanParser
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Draait de scanner (tekstherkenning + ScanParser) op de foto's in testset/ en vergelijkt met uitslag.txt.
 * Slaagt altijd als de test kon draaien: de uitslag staat in het rapport (testset-uitslag.txt) en in de log (tag TESTSET).
 */
@RunWith(AndroidJUnit4::class)
class ScanTestsetTest {
    private data class Expect(val empty: Boolean, val name: String?, val set: String?, val number: String?)

    private fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private fun parseExpect(line: String): Expect {
        val body = line.substringAfter(":").trim()
        if (body.startsWith("leeg")) return Expect(true, null, null, null)
        val m = Regex("""^(.*?)\s*\(([A-Za-z0-9]+)\s+([^\s,)?]+)""").find(body)
        return if (m != null) Expect(false, m.groupValues[1], m.groupValues[2].uppercase(), m.groupValues[3])
        else Expect(false, body.substringBefore("(").trim(), null, null)
    }

    @Test
    fun runTestset() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val out = StringBuilder("NineGrid testset-uitslag\n")
        var totalOk = 0; var total = 0
        val dirs = (ctx.assets.list("") ?: emptyArray()).filter { d -> ctx.assets.list(d)?.contains("uitslag.txt") == true }.sorted()
        assertTrue("Geen testmappen gevonden", dirs.isNotEmpty())
        for (dir in dirs) {
            val bmp = ctx.assets.open("$dir/foto.jpg").use { BitmapFactory.decodeStream(it) }
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.exactCenterX(), it.exactCenterY()) } }
            val reads = ScanParser.analyze(lines, bmp.width, bmp.height).slots.associateBy { it.slot }
            val expected = ctx.assets.open("$dir/uitslag.txt").bufferedReader().readLines()
                .filter { it.startsWith("vak ") }.associate { it.substringAfter("vak ").substringBefore(":").trim().toInt() to parseExpect(it) }
            out.appendLine("\n== $dir")
            var ok = 0
            for ((slot, e) in expected.toSortedMap()) {
                val r = reads[slot]
                val good = if (e.empty) r == null || (r.name == null && r.collectorNumber == null)
                else r != null && (e.name == null || norm(r.name).let { n -> n.isNotEmpty() && (norm(e.name).contains(n) || n.contains(norm(e.name))) }) &&
                    (e.set == null || r.setCode.equals(e.set, true)) && (e.number == null || r.collectorNumber?.trimStart('0') == e.number.trimStart('0'))
                if (good) ok++
                out.appendLine("vak $slot: ${if (good) "goed" else "FOUT"} · verwacht ${if (e.empty) "leeg" else "${e.name} (${e.set} ${e.number})"} · gelezen ${r?.let { "${it.name} (${it.setCode} ${it.collectorNumber})" } ?: "niets"}")
            }
            out.appendLine("$ok van ${expected.size} goed")
            totalOk += ok; total += expected.size
        }
        out.appendLine("\nTotaal: $totalOk van $total vakken goed")
        Log.i("TESTSET", out.toString())
        File(ctx.getExternalFilesDir(null) ?: ctx.cacheDir, "testset-uitslag.txt").writeText(out.toString())
    }
}
