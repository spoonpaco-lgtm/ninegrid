package nl.spoonpaco.ninegrid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.spoonpaco.ninegrid.domain.Area
import nl.spoonpaco.ninegrid.domain.OcrLine
import nl.spoonpaco.ninegrid.domain.ScanParser
import nl.spoonpaco.ninegrid.domain.ScanResult
import nl.spoonpaco.ninegrid.domain.SlotRead
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Vergelijkt varianten van de leesstraat (eerste ronde, opnieuw lezen, per vak lezen, andere vergrotingen)
 * op de testset en zet per variant één regel in de log (tag TESTSET). Alleen metingen, geen app-code.
 */
@RunWith(AndroidJUnit4::class)
class ScanVariantsTest {
    private data class Expect(val empty: Boolean, val name: String?, val set: String?, val number: String?)

    private class Cfg(
        val label: String,
        val upscale: Float = 1f,
        val cellFirst: Boolean = false,
        val rereadAll: Boolean = false,
        val rereadSkip: Boolean = false,
        val topH: Float = 0.2f, val botH: Float = 0.17f,
        val topScale: Int = 2, val botScale: Int = 3,
        val multi: Boolean = false,
    )

    private val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private fun ocr(b: Bitmap) = Tasks.await(rec.process(InputImage.fromBitmap(b, 0))).textBlocks.flatMap { it.lines }

    private fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return 1.0 - d[a.length][b.length].toDouble() / maxOf(a.length, b.length)
    }

    private fun parseExpect(line: String): Expect {
        val body = line.substringAfter(":").trim()
        if (body.startsWith("leeg")) return Expect(true, null, null, null)
        val m = Regex("""^(.*?)\s*\(([A-Za-z0-9]+)\s+([^\s,)?]+)""").find(body)
        return if (m != null) Expect(false, m.groupValues[1], m.groupValues[2].uppercase(), m.groupValues[3])
        else Expect(false, body.substringBefore("(").trim(), null, null)
    }

    private fun contrast(bmp: Bitmap): Bitmap {
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val histogram = IntArray(256)
        pixels.forEach { p -> histogram[((p shr 16 and 255) * 30 + (p shr 8 and 255) * 59 + (p and 255) * 11) / 100]++ }
        val clip = (pixels.size / 100).coerceAtLeast(1)
        var seen = 0; var low = 0
        while (low < 255 && seen + histogram[low] < clip) { seen += histogram[low++] }
        seen = 0
        var high = 255
        while (high > low && seen + histogram[high] < clip) { seen += histogram[high--] }
        val span = (high - low).coerceAtLeast(1)
        pixels.indices.forEach { i ->
            val p = pixels[i]
            val gray = ((p shr 16 and 255) * 30 + (p shr 8 and 255) * 59 + (p and 255) * 11) / 100
            val v = ((gray - low) * 255 / span).coerceIn(0, 255)
            pixels[i] = -0x1000000 or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun strip(bmp: Bitmap, a: Area, high: Boolean, scale: Int): List<String> {
        val l = a.left.toInt().coerceIn(0, bmp.width - 1); val t = a.top.toInt().coerceIn(0, bmp.height - 1)
        val w = a.width.toInt().coerceIn(1, bmp.width - l); val h = a.height.toInt().coerceIn(1, bmp.height - t)
        val part = Bitmap.createBitmap(bmp, l, t, w, h)
        val factor = minOf(scale.toFloat(), 3600f / w, 1400f / h).coerceAtLeast(1f)
        val big = Bitmap.createScaledBitmap(part, (w * factor).toInt().coerceAtLeast(1), (h * factor).toInt().coerceAtLeast(1), true)
        return ocr(if (high) contrast(big) else big).map { it.text }
    }

    private fun first(bmp: Bitmap): ScanResult {
        val lines = ocr(bmp).mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.exactCenterX(), it.exactCenterY()) } }
        return ScanParser.analyze(lines, bmp.width, bmp.height, null)
    }

    private fun cellOf(f: Area, slot: Int): Area {
        val cw = f.width / 3f; val ch = f.height / 3f
        val row = (slot - 1) / 3; val col = (slot - 1) % 3
        return Area(f.left + col * cw, f.top + row * ch, f.left + (col + 1) * cw, f.top + (row + 1) * ch)
    }

    private fun score(r: SlotRead) = (if (r.name != null) 1 else 0) + (if (r.setCode != null) 1 else 0) + (if (r.collectorNumber != null) 1 else 0)

    /** Elk vak los lezen, 2 keer vergroot, in plaats van het hele blad in één keer. */
    private fun cellPass(bmp: Bitmap, res: ScanResult, known: Set<String>): ScanResult {
        val f = res.frame ?: return res
        val slots = res.slots.map { r ->
            val c = cellOf(f, r.slot)
            val l = c.left.toInt().coerceIn(0, bmp.width - 1); val t = c.top.toInt().coerceIn(0, bmp.height - 1)
            val w = c.width.toInt().coerceIn(1, bmp.width - l); val h = c.height.toInt().coerceIn(1, bmp.height - t)
            val part = Bitmap.createBitmap(bmp, l, t, w, h)
            val big = Bitmap.createScaledBitmap(part, w * 2, h * 2, true)
            val lines = ocr(big).mapNotNull { x -> x.boundingBox?.let { OcrLine(x.text, it.exactCenterX(), it.exactCenterY()) } }
            val cr = ScanParser.analyzeSingle(lines, big.width, big.height, null).slots.first().copy(slot = r.slot)
            // Per vak lezen telt alleen als het minstens zoveel oplevert als de eerste ronde.
            if (score(cr) >= score(r) && cr.lines.isNotEmpty()) cr else r
        }
        return res.copy(slots = slots)
    }

    private fun reread(bmp: Bitmap, res: ScanResult, c: Cfg, known: Set<String>): ScanResult {
        val f = res.frame ?: return res
        val slots = res.slots.map { r ->
            val need = c.rereadAll || ScanParser.needsReread(r)
            if (!need) return@map r
            val cell = cellOf(f, r.slot)
            val topArea = Area(cell.left, cell.top, cell.right, cell.top + cell.height * c.topH)
            val botArea = Area(cell.left, cell.bottom - cell.height * c.botH, cell.right, cell.bottom)
            var e = ScanParser.enrich(r, runCatching { strip(bmp, topArea, false, c.topScale) }.getOrDefault(emptyList()),
                runCatching { strip(bmp, botArea, false, c.botScale) }.getOrDefault(emptyList()), known)
            if (e.name == null) e = ScanParser.enrich(e, runCatching { strip(bmp, topArea, true, c.topScale) }.getOrDefault(emptyList()), emptyList(), known)
            if (e.setCode == null || e.collectorNumber == null)
                e = ScanParser.enrich(e, emptyList(), runCatching { strip(bmp, botArea, true, c.botScale) }.getOrDefault(emptyList()), known)
            if (c.multi) {
                for ((sc, hc) in listOf(4 to false, 4 to true, 2 to true, 5 to false)) {
                    if (e.setCode != null && e.collectorNumber != null) break
                    e = ScanParser.enrich(e, emptyList(), runCatching { strip(bmp, botArea, hc, sc) }.getOrDefault(emptyList()), known)
                }
                // Een iets hogere strook voor kaarten waar de voetregel hoger staat (randloos, extended art).
                if (e.setCode == null || e.collectorNumber == null) {
                    val tall = Area(cell.left, cell.bottom - cell.height * 0.26f, cell.right, cell.bottom)
                    e = ScanParser.enrich(e, emptyList(), runCatching { strip(bmp, tall, true, 3) }.getOrDefault(emptyList()), known)
                }
            }
            e
        }
        return res.copy(slots = slots)
    }

    private fun run(orig: Bitmap, c: Cfg, known: Set<String>): Map<Int, SlotRead> {
        val bmp = if (c.upscale == 1f) orig else Bitmap.createScaledBitmap(orig, (orig.width * c.upscale).toInt(), (orig.height * c.upscale).toInt(), true)
        var res = first(bmp)
        if (c.cellFirst) res = cellPass(bmp, res, known)
        if (!c.rereadSkip) res = reread(bmp, res, c, known)
        return res.slots.associateBy { it.slot }
    }

    private class Tally { var name = 0; var nameN = 0; var set = 0; var setN = 0; var num = 0; var numN = 0; var all = 0; var allN = 0 }

    @Test
    fun vergelijkVarianten() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val known = emptySet<String>()
        val configs = listOf(
            Cfg("A eerste ronde alleen", rereadSkip = true),
            Cfg("B zoals de app (eerste ronde + opnieuw lezen)"),
            Cfg("C opnieuw lezen van alle vakken", rereadAll = true),
            Cfg("D elk vak los lezen + opnieuw lezen", cellFirst = true),
            Cfg("E app + meer pogingen onderaan (4x, 5x, hoge strook)", multi = true),
            Cfg("F hele foto 1,5x vergroot + app", upscale = 1.5f),
            Cfg("G elk vak los + meer pogingen", cellFirst = true, multi = true),
            Cfg("H app met hogere voetstrook (22%) en 4x", botH = 0.22f, botScale = 4),
        )
        val dirs = (ctx.assets.list("") ?: emptyArray()).filter { d -> ctx.assets.list(d)?.contains("uitslag.txt") == true }.sorted()
        val jobs = dirs.flatMap { d -> listOf("foto.jpg", "foto-2.jpg").filter { f -> ctx.assets.list(d)?.contains(f) == true }.map { f -> d to f } }
        val out = StringBuilder("NineGrid varianten\n")
        val fails = StringBuilder()
        for (cfg in configs) {
            val t = Tally(); val t0 = System.currentTimeMillis()
            for ((dir, file) in jobs) {
                val bmp = ctx.assets.open("$dir/$file").use { BitmapFactory.decodeStream(it) }
                val reads = runCatching { run(bmp, cfg, known) }.getOrElse { out.appendLine("fout ${cfg.label} $dir/$file: $it"); emptyMap() }
                val expected = ctx.assets.open("$dir/uitslag.txt").bufferedReader().readLines()
                    .filter { it.startsWith("vak ") }.associate { it.substringAfter("vak ").substringBefore(":").trim().toInt() to parseExpect(it) }
                for ((slot, e) in expected) {
                    val r = reads[slot]
                    t.allN++
                    if (e.empty) {
                        if (r == null || (r.name == null && r.collectorNumber == null)) t.all++
                        continue
                    }
                    t.nameN++
                    val nameOk = r?.name != null && e.name != null && sim(norm(r.name), norm(e.name)) >= 0.8
                    if (nameOk) t.name++
                    var setOk = true; var numOk = true
                    if (e.set != null) { t.setN++; setOk = r?.setCode.equals(e.set, true); if (setOk) t.set++ }
                    if (e.number != null) { t.numN++; numOk = r?.collectorNumber?.trimStart('0') == e.number.trimStart('0'); if (numOk) t.num++ }
                    if (nameOk && setOk && numOk) t.all++
                    else if (cfg.label.startsWith("B") || cfg.label.startsWith("E"))
                        fails.appendLine("${cfg.label.take(1)} $dir/$file vak $slot: verwacht ${e.name} (${e.set} ${e.number}) · gelezen ${r?.name} (${r?.setCode} ${r?.collectorNumber})")
                }
            }
            val secs = (System.currentTimeMillis() - t0) / 1000
            out.appendLine("${cfg.label}: naam ${t.name}/${t.nameN} · set ${t.set}/${t.setN} · nummer ${t.num}/${t.numN} · alles goed ${t.all}/${t.allN} · ${secs} s")
            Log.i("TESTSET", "SAMENVATTING ${cfg.label}: naam ${t.name}/${t.nameN} · set ${t.set}/${t.setN} · nummer ${t.num}/${t.numN} · alles goed ${t.all}/${t.allN} · ${secs} s")
        }
        out.appendLine("\nFouten (B en E):").appendLine(fails.toString())
        fails.lines().forEach { Log.i("TESTSET", "FOUT $it") }
        runCatching {
            val target = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = (target.getExternalFilesDir(null) ?: target.filesDir).also { it.mkdirs() }
            File(dir, "testset-varianten.txt").writeText(out.toString())
        }
    }
}
