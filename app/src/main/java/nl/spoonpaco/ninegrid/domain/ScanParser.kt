package nl.spoonpaco.ninegrid.domain

/** Een stuk herkende tekst met zijn positie op de foto (middelpunt, in pixels). */
data class OcrLine(val text: String, val centerX: Float, val centerY: Float)

/** Wat de scanner in één vak van het 3x3-blad heeft gevonden. */
data class SlotRead(
    val slot: Int,
    val setCode: String?,
    val collectorNumber: String?,
    val language: String?,
    val lines: List<String>,
) {
    val complete get() = setCode != null && collectorNumber != null
}

/**
 * Zet de tekst van een foto om naar vakken 1 t/m 9 (1 = linksboven, 9 = rechtsonder) en zoekt per vak
 * de regel onderaan de kaart: nummer ("229/254 M" of "M 0074") en set met taal ("MH1 • EN").
 */
object ScanParser {
    // "229/254 M", "0074/281 R", "250/271 R"
    private val numberSlash = Regex("""(?<![A-Z0-9])(\d{1,4})\s*/\s*(\d{1,4})(?:\s*[CURMSLTP])?(?![0-9])""")
    // Nieuwe kaarten (2023+): "M 0074", "U 0859", "R 0049"
    private val numberLetterFirst = Regex("""(?<![A-Z0-9])([CURMSLTP])\s+(\d{3,4})(?![0-9])""")
    // "MH1 • EN", "WOT · EN", "CMM - EN", "LTC * EN"
    private val setLang = Regex("""(?<![A-Z0-9])([A-Z0-9]{3,5})\s*[•·*.\-]\s*(EN|DE|FR|IT|ES|PT|JA|JP|KO|RU|ZH|CS|CT|PH)(?![A-Z])""")

    private val langMap = mapOf("EN" to "", "JA" to "JP", "ZH" to "CN", "CS" to "CN", "CT" to "TW")

    fun slotOf(x: Float, y: Float, width: Int, height: Int): Int {
        val col = (x / (width / 3f)).toInt().coerceIn(0, 2)
        val row = (y / (height / 3f)).toInt().coerceIn(0, 2)
        return row * 3 + col + 1
    }

    fun parse(lines: List<OcrLine>, width: Int, height: Int): List<SlotRead> {
        val bySlot = lines.groupBy { slotOf(it.centerX, it.centerY, width, height) }
        return (1..9).map { slot ->
            val ls = bySlot[slot].orEmpty()
            var number: String? = null
            var set: String? = null
            var lang: String? = null
            // Onderste regels eerst: de verzamelregel staat onderaan de kaart.
            for (l in ls.sortedByDescending { it.centerY }) {
                val t = cleanup(l.text)
                if (number == null) {
                    numberSlash.find(t)?.let { number = it.groupValues[1].trimStart('0').ifEmpty { "0" } }
                        ?: numberLetterFirst.find(t)?.let { number = it.groupValues[2].trimStart('0').ifEmpty { "0" } }
                }
                if (set == null) {
                    setLang.find(t)?.let {
                        set = fixSetCode(it.groupValues[1])
                        lang = langMap[it.groupValues[2]] ?: it.groupValues[2]
                    }
                }
            }
            SlotRead(slot, set, number, lang, ls.map { it.text })
        }
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
