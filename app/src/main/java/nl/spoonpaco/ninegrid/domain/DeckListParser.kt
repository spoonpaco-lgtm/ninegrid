package nl.spoonpaco.ninegrid.domain

/** Eén regel uit een geplakte lijst. */
data class ParsedLine(
    val quantity: Int,
    val name: String,
    val setCode: String?,
    val collectorNumber: String?,
    val foil: Boolean,
    val etched: Boolean,
    val section: String,
    val lineNumber: Int,
    val raw: String,
)

data class ParseResult(
    val lines: List<ParsedLine>,
    val unreadable: List<Pair<Int, String>>,
    val hadCommanderHeader: Boolean,
    val hadSideboard: Boolean,
)

/**
 * Leest lijsten in het formaat "aantal Naam (SET) nummer", met *F* voor foil en *E* voor etched.
 * Werkt ook met exports van Moxfield, Archidekt en MTG Arena ("1x Naam", "Deck", "Sideboard", "Commander").
 */
object DeckListParser {
    const val MAIN = "MAIN"
    const val SIDEBOARD = "SIDEBOARD"
    const val COMMANDER = "COMMANDER"

    private val cardLine = Regex(
        """^\s*(\d+)\s*[xX]?\s+(.+?)(?:\s+\(([A-Za-z0-9]{2,6})\)(?:\s+([A-Za-z0-9\-★†]+))?)?((?:\s+\*[A-Za-z]\*)*)\s*$"""
    )
    private val headerWords = mapOf(
        "sideboard" to SIDEBOARD,
        "side" to SIDEBOARD,
        "sb" to SIDEBOARD,
        "commander" to COMMANDER,
        "commanders" to COMMANDER,
        "deck" to MAIN,
        "main" to MAIN,
        "maindeck" to MAIN,
        "mainboard" to MAIN,
        "companion" to SIDEBOARD,
    )

    fun parse(text: String): ParseResult {
        val lines = mutableListOf<ParsedLine>()
        val unreadable = mutableListOf<Pair<Int, String>>()
        var section = MAIN
        var hadCommander = false
        var hadSideboard = false
        var sawHeader = false
        var blankAfterCards = false

        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (lines.isNotEmpty()) blankAfterCards = true
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("#")) return@forEachIndexed

            val headerKey = line.trimEnd(':').trim().lowercase()
                .replace(Regex("""\s*\(\d+\)$"""), "")
            val header = headerWords[headerKey]
            if (header != null) {
                section = header
                sawHeader = true
                if (header == COMMANDER) hadCommander = true
                if (header == SIDEBOARD) hadSideboard = true
                return@forEachIndexed
            }

            // "SB: 2 Naam" (oud MTGO-formaat)
            var work = line
            var lineSection = section
            if (work.startsWith("SB:", ignoreCase = true)) {
                work = work.substring(3).trim()
                lineSection = SIDEBOARD
                hadSideboard = true
            }

            val m = cardLine.matchEntire(work)
            if (m == null) {
                unreadable += (index + 1) to rawLine
                return@forEachIndexed
            }
            // Arena/MTGO: een lege regel zonder kopjes betekent dat het sideboard begint.
            if (!sawHeader && blankAfterCards && lineSection == MAIN) {
                section = SIDEBOARD
                lineSection = SIDEBOARD
                hadSideboard = true
            }
            blankAfterCards = false

            val markers = m.groupValues[5].uppercase()
            lines += ParsedLine(
                quantity = m.groupValues[1].toInt(),
                name = normalizeName(m.groupValues[2]),
                setCode = m.groupValues[3].ifBlank { null }?.uppercase(),
                collectorNumber = m.groupValues[4].ifBlank { null },
                foil = "*F*" in markers,
                etched = "*E*" in markers,
                section = lineSection,
                lineNumber = index + 1,
                raw = rawLine,
            )
        }
        return ParseResult(lines, unreadable, hadCommander, hadSideboard)
    }

    /** "Legion's Landing / Adanto" wordt "Legion's Landing // Adanto", zoals Scryfall het schrijft. */
    fun normalizeName(name: String): String =
        name.trim().replace(Regex("""\s+/{1,2}\s+"""), " // ")
}
