package nl.spoonpaco.ninegrid.domain

/** Een stuk herkende tekst met zijn positie op de foto (middelpunt, in pixels). */
data class OcrLine(val text: String, val centerX: Float, val centerY: Float)

/** Een rechthoek op de foto, in pixels. */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    val width get() = right - left
    val height get() = bottom - top
}

/** Uitkomst van een scan: de vakken, en hoeveel van het kader de tekst beslaat (0..1). */
data class ScanResult(val slots: List<SlotRead>, val coverage: Float, val linesInside: Int, val frame: Area? = null)

/** Wat de scanner in één vak van het 3x3-blad heeft gevonden. */
data class SlotRead(
    val slot: Int,
    val setCode: String?,
    val collectorNumber: String?,
    val language: String?,
    val lines: List<String>,
    val name: String? = null,
    /** Andere regels uit de bovenkant van het vak die een naam kunnen zijn, voor als de eerste niets oplevert. */
    val altNames: List<String> = emptyList(),
) {
    val complete get() = setCode != null && collectorNumber != null
}

/**
 * Zet de tekst van een foto om naar vakken 1 t/m 9 (1 = linksboven, 9 = rechtsonder) en zoekt per vak
 * de regel onderaan de kaart: nummer ("229/254 M" of "M 0074") en set met taal ("MH1 • EN").
 */
object ScanParser {
    // "229/254 M", "0074/281 R", "250/271 R"
    // Het totaal heeft minstens 2 cijfers, zodat kracht/weerstand ("2/2") niet als nummer telt.
    private val numberSlash = Regex("""(?<![A-Z0-9])(\d{1,4})\s*/\s*(\d{2,4})(?:\s*[CURMSLTP])?(?![0-9])""")
    // Nieuwe kaarten (2023+): "M 0074", "U 0859", "R 0049"
    private val numberLetterFirst = Regex("""(?<![A-Z0-9])([CURMSLTP])\s+(\d{3,4})(?![0-9])""")
    // "MH1 • EN", "WOT · EN", "CMM - EN", "LTC * EN"
    // Het scheidingsteken leest de OCR vaak niet, dus dat is optioneel.
    private val setLang = Regex("""(?<![A-Z0-9])([A-Z0-9]{3,5})\s*[•·*.\-+°,]?\s*(EN|DE|FR|IT|ES|PT|JA|JP|KO|RU|ZH|CS|CT|PH)(?![A-Z])""")

    // Extended art, borderless en showcase: alleen een nummer, zonder "/totaal" ("374 R", "374").
    private val numberAlone = Regex("""^\s*(\d{1,4})\s*([CURMSLTP])?\s*$""")
    private val numberThenRarity = Regex("""^\s*(\d{1,4})\s+[CURMSLTP](?![A-Z])""")

    /** Van de code achter de setcode (FR, JP, CN ...) naar de taalcode van Scryfall; null = Engels. */
    fun scryfallLang(code: String?): String? = when (code?.uppercase()) {
        null, "", "EN" -> null
        "FR" -> "fr"; "DE" -> "de"; "IT" -> "it"; "ES" -> "es"; "PT" -> "pt"; "RU" -> "ru"
        "JP", "JA" -> "ja"; "KO" -> "ko"; "CN", "ZH", "CS" -> "zhs"; "TW", "CT" -> "zht"; "PH" -> "ph"
        else -> null
    }

    private val langMap = mapOf("EN" to "", "JA" to "JP", "ZH" to "CN", "CS" to "CN", "CT" to "TW")

    fun slotOf(x: Float, y: Float, width: Int, height: Int): Int {
        val col = (x / (width / 3f)).toInt().coerceIn(0, 2)
        val row = (y / (height / 3f)).toInt().coerceIn(0, 2)
        return row * 3 + col + 1
    }

    /** Het kader van het blad: de uiterste tekst op de foto, iets ruimer genomen. Valt terug op de hele foto. */
    private data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun frameOf(lines: List<OcrLine>, width: Int, height: Int, bounds: Area? = null): Frame {
        val full = bounds?.let { Frame(it.left, it.top, it.right, it.bottom) } ?: Frame(0f, 0f, width.toFloat(), height.toFloat())
        if (lines.size < 6) return full
        val minX = lines.minOf { it.centerX }; val maxX = lines.maxOf { it.centerX }
        val minY = lines.minOf { it.centerY }; val maxY = lines.maxOf { it.centerY }
        val padX = (maxX - minX) * 0.06f; val padY = (maxY - minY) * 0.04f
        val f = Frame((minX - padX).coerceAtLeast(full.left), (minY - padY).coerceAtLeast(full.top), (maxX + padX).coerceAtMost(full.right), (maxY + padY).coerceAtMost(full.bottom))
        // Te klein (bijvoorbeeld maar één rij kaarten tekst gevonden)? Dan het hele kader.
        return if (f.right - f.left < (full.right - full.left) * 0.4f || f.bottom - f.top < (full.bottom - full.top) * 0.4f) full else f
    }

    private fun slotIn(f: Frame, x: Float, y: Float): Int {
        val col = ((x - f.left) / ((f.right - f.left) / 3f)).toInt().coerceIn(0, 2)
        val row = ((y - f.top) / ((f.bottom - f.top) / 3f)).toInt().coerceIn(0, 2)
        return row * 3 + col + 1
    }

    /** Ziet eruit als een kaartnaam: vooral letters, geen verzamelregel. */
    private fun nameCandidate(raw: String): String? {
        val t = raw.replace(Regex("""[^\p{L} ,'\-]"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (t.length < 3 || t.length > 45) return null
        val letters = t.count { it.isLetter() }
        if (letters < 3 || letters < raw.length * 0.6) return null
        val up = raw.uppercase()
        if (numberSlash.containsMatchIn(up) || numberLetterFirst.containsMatchIn(up) || setLang.containsMatchIn(up)) return null
        if (Regex("""(?i)^(legendary|creature|instant|sorcery|artifact|enchantment|land|planeswalker|basic|tribal|kindred|battle)\b""").containsMatchIn(t)) return null
        if (Regex("""(?i)illus|wizards|™|©""").containsMatchIn(raw)) return null
        return t
    }

    /**
     * Leest een foto. Met [crop] telt alleen tekst binnen het richtkader mee, zodat een half zichtbaar
     * volgend blad de vakken niet verschuift.
     */
    fun analyze(all: List<OcrLine>, width: Int, height: Int, crop: Area? = null): ScanResult {
        val lines = if (crop != null) all.filter { crop.contains(it.centerX, it.centerY) } else all
        val area = crop ?: Area(0f, 0f, width.toFloat(), height.toFloat())
        val coverage = if (lines.size < 2) 0f else {
            val w = (lines.maxOf { it.centerX } - lines.minOf { it.centerX }) / area.width
            val h = (lines.maxOf { it.centerY } - lines.minOf { it.centerY }) / area.height
            (w.coerceIn(0f, 1f) * h.coerceIn(0f, 1f))
        }
        val (slots, frame) = parseWithFrame(lines, width, height, crop)
        return ScanResult(slots, coverage, lines.size, frame)
    }

    private fun looseName(raw: String): String? {
        val t = raw.replace(Regex("""[^\p{L} ,'\-]"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (t.length < 4 || t.length > 45) return null
        return if (t.count { it.isLetter() } >= raw.length * 0.5) t else null
    }

    /** De set die op een blad het vaakst voorkomt, als die duidelijk de meerderheid is. */
    fun dominantSet(reads: List<SlotRead>): String? {
        val sets = reads.mapNotNull { it.setCode }
        if (sets.size < 2) return null
        val (best, n) = sets.groupingBy { it }.eachCount().maxBy { it.value }
        // Alleen bij een duidelijke meerderheid: meer dan de helft van alle gelezen sets.
        return if (n >= 2 && n * 2 > sets.size) best else null
    }

    fun parse(lines: List<OcrLine>, width: Int, height: Int, crop: Area? = null): List<SlotRead> = parseWithFrame(lines, width, height, crop).first

    private fun parseWithFrame(lines: List<OcrLine>, width: Int, height: Int, crop: Area?): Pair<List<SlotRead>, Area> {
        val frame = frameOf(lines, width, height, crop)
        val cellH = (frame.bottom - frame.top) / 3f
        val bySlot = lines.groupBy { slotIn(frame, it.centerX, it.centerY) }
        val slots = (1..9).map { slot ->
            val row = (slot - 1) / 3
            readRegion(slot, bySlot[slot].orEmpty(), frame.top + row * cellH, cellH)
        }
        return slots to Area(frame.left, frame.top, frame.right, frame.bottom)
    }

    /** Leest één kaart: naam bovenaan, nummer, set en taal onderaan. */
    private fun readRegion(slot: Int, ls: List<OcrLine>, regionTop: Float, regionH: Float): SlotRead {
        var number: String? = null
        var set: String? = null
        var lang: String? = null
        // Onderste regels eerst: de verzamelregel staat onderaan de kaart.
        for (l in ls.sortedByDescending { it.centerY }) {
            val t = cleanup(l.text)
            val inBottom = l.centerY > regionTop + regionH * 0.78f
            if (number == null) {
                numberSlash.find(t)?.let { number = it.groupValues[1].trimStart('0').ifEmpty { "0" } }
                    ?: numberLetterFirst.find(t)?.let { number = it.groupValues[2].trimStart('0').ifEmpty { "0" } }
                    // Een los nummer alleen onderaan de kaart, zodat getallen uit de regeltekst niet meetellen.
                    ?: (if (inBottom) (numberAlone.find(t) ?: numberThenRarity.find(t)) else null)?.let { number = it.groupValues[1].trimStart('0').ifEmpty { "0" } }
            }
            if (set == null) {
                setLang.find(t)?.let {
                    set = fixSetCode(it.groupValues[1])
                    lang = langMap[it.groupValues[2]] ?: it.groupValues[2]
                }
            }
        }
        // De naam staat bovenaan de kaart: de bovenste regel in het bovenste deel die op een naam lijkt.
        val top = ls.filter { it.centerY < regionTop + regionH * 0.35f }.sortedBy { it.centerY }
        val names = top.mapNotNull { nameCandidate(it.text) }.distinct()
        // Ruimer: ook regels uit de bovenste helft die vooral uit letters bestaan.
        val loose = ls.filter { it.centerY < regionTop + regionH * 0.5f }.sortedBy { it.centerY }
            .mapNotNull { looseName(it.text) }.filter { it !in names }.distinct()
        val name = names.firstOrNull()
        val alt = (names.drop(1) + loose).take(4)
        return SlotRead(slot, set, number, lang, ls.sortedBy { it.centerY }.map { it.text }, name, alt)
    }

    /** Mist dit vak naam, set of nummer? Dan loont opnieuw lezen van uitvergrote stroken. */
    fun needsReread(r: SlotRead) = r.lines.isNotEmpty() && (r.name == null || r.setCode == null || r.collectorNumber == null)

    /**
     * Vult een vak aan met wat er in de uitvergrote stroken gelezen is: de bovenste strook (naam) en de
     * onderste strook (nummer, set, taal). Alleen wat nog ontbrak wordt ingevuld.
     */
    fun enrich(r: SlotRead, top: List<String>, bottom: List<String>): SlotRead {
        var number = r.collectorNumber; var set = r.setCode; var lang = r.language
        for (raw in bottom) {
            val t = cleanup(raw)
            if (number == null) {
                number = (numberSlash.find(t)?.groupValues?.get(1) ?: numberLetterFirst.find(t)?.groupValues?.get(2)
                    ?: (numberAlone.find(t) ?: numberThenRarity.find(t))?.groupValues?.get(1))?.trimStart('0')?.ifEmpty { "0" }
            }
            if (set == null) setLang.find(t)?.let { set = fixSetCode(it.groupValues[1]); lang = langMap[it.groupValues[2]] ?: it.groupValues[2] }
        }
        val name = r.name ?: top.firstNotNullOfOrNull { nameCandidate(it) }
        return r.copy(collectorNumber = number, setCode = set, language = lang, name = name, lines = r.lines + top + bottom)
    }

    /** Eén losse kaart in het kader: alle tekst hoort bij die kaart. */
    fun analyzeSingle(all: List<OcrLine>, width: Int, height: Int, crop: Area?): ScanResult {
        val lines = if (crop != null) all.filter { crop.contains(it.centerX, it.centerY) } else all
        val area = crop ?: Area(0f, 0f, width.toFloat(), height.toFloat())
        if (lines.isEmpty()) return ScanResult(listOf(SlotRead(1, null, null, null, emptyList())), 0f, 0, area)
        // De kaart zelf: van de bovenste tot de onderste regel, iets ruimer.
        val minY = lines.minOf { it.centerY }; val maxY = lines.maxOf { it.centerY }
        val pad = (maxY - minY) * 0.04f
        val top = (minY - pad).coerceAtLeast(area.top); val h = (maxY + pad).coerceAtMost(area.bottom) - top
        val coverage = ((maxY - minY) / area.height).coerceIn(0f, 1f)
        return ScanResult(listOf(readRegion(1, lines, top, h.coerceAtLeast(1f))), coverage, lines.size, area)
    }

    /** OCR haalt soms tekens door elkaar. */
    private fun cleanup(s: String): String = s.uppercase()
        .replace('•', '•')
        .replace(Regex("""\s+"""), " ")

    /** Setcodes beginnen meestal met letters; een 0 aan het begin is bijna altijd een O. */
    fun fixSetCode(code: String): String {
        val c = code.uppercase()
        return if (c.firstOrNull() == '0') "O" + c.drop(1) else c
    }

    /** Alternatieven voor als de eerste poging bij Scryfall niets vindt (O/0 en I/1 verwisseld). */
    fun alternatives(set: String, number: String): List<Pair<String, String>> {
        val sets = linkedSetOf(set, set.replace('0', 'O'), set.replace('O', '0'), set.replace('1', 'I'), set.replace('I', '1'))
        val nums = linkedSetOf(number, number.replace('O', '0'), number.replace('I', '1'))
        return sets.flatMap { s -> nums.map { n -> s to n } }.filter { it != (set to number) }
    }
}
