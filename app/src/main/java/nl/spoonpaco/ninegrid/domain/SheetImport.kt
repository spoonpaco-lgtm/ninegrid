package nl.spoonpaco.ninegrid.domain

import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** Eén kaartregel uit de Sheet. */
data class ImportCard(
    val setCode: String,
    val number: String,
    val name: String,
    val foil: Boolean,
    val etched: Boolean,
    val language: String,
    val quantity: Int,
    val proxy: Boolean,
    /** Commander, Deck of Sideboard, alleen bij decks. */
    val section: String? = null,
)

data class ImportDeck(val name: String, val cards: List<ImportCard>)

/** Een dag uit het tabblad Historie. */
data class ImportHistory(val day: String, val value: Double, val count: Int)

/** Alles wat er uit het bestand gehaald is. */
data class ImportData(
    val collection: List<ImportCard>,
    val decks: List<ImportDeck>,
    val history: List<ImportHistory>,
    val sheetsRead: List<String>,
    val skippedRows: Int,
) {
    val collectionCount get() = collection.sumOf { it.quantity }
    val deckCount get() = decks.sumOf { d -> d.cards.sumOf { it.quantity } }
}

/**
 * Leest de MTG Collection Sheet, als .xlsx (alle tabbladen) of .csv (één tabblad).
 * Indeling per tabblad: A Set, B Nr., C Kaart, D Rarity, E Foil, F Taal, G Aantal, H Prijs, I Totaal, J Proxy.
 * Settabbladen en Losse kaarten: alleen regels met een aantal. Decks: blokken met de decknaam, de kolomkoppen
 * en kopjes Commander / Deck / Sideboard in kolom C. Historie: Datum, Waarde, Aantal. Overzicht wordt overgeslagen.
 */
object SheetImport {
    private val SKIP = setOf("overzicht", "overview")

    fun read(fileName: String, input: InputStream): ImportData {
        val lower = fileName.lowercase()
        return if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            val rows = parseCsv(input.readBytes().decodeToString())
            interpret(listOf((fileName.substringBeforeLast('.')) to rows))
        } else {
            interpret(readXlsx(input))
        }
    }

    // ---------- Indeling herkennen ----------

    fun interpret(sheets: List<Pair<String, List<List<String>>>>): ImportData {
        val collection = mutableListOf<ImportCard>()
        val decks = mutableListOf<ImportDeck>()
        val history = mutableListOf<ImportHistory>()
        val read = mutableListOf<String>()
        var skipped = 0
        for ((name, rows) in sheets) {
            val n = name.trim().lowercase()
            when {
                n in SKIP -> continue
                n == "historie" || n == "history" -> { history += readHistory(rows); read += name }
                n == "decks" -> { decks += readDecks(rows); read += name }
                else -> {
                    val (cards, sk) = readTable(rows)
                    if (cards.isNotEmpty() || rows.firstOrNull()?.firstOrNull()?.trim()?.equals("Set", true) == true) read += name
                    collection += cards; skipped += sk
                }
            }
        }
        return ImportData(collection, decks, history, read, skipped)
    }

    private data class Cols(val set: Int, val nr: Int, val name: Int, val foil: Int, val lang: Int, val qty: Int, val proxy: Int)

    private val DEFAULT = Cols(0, 1, 2, 4, 5, 6, 9)

    /** Kolommen op naam van de kop, anders de vaste volgorde A..J. */
    private fun colsFrom(header: List<String>?): Cols {
        if (header == null) return DEFAULT
        fun idx(vararg names: String, def: Int): Int {
            val i = header.indexOfFirst { h -> names.any { h.trim().trimEnd('.').equals(it, true) } }
            return if (i >= 0) i else def
        }
        return Cols(
            idx("Set", def = 0), idx("Nr", "Nummer", "Number", "Collector number", def = 1), idx("Kaart", "Card", "Name", "Naam", def = 2),
            idx("Foil", def = 4), idx("Taal", "Language", def = 5), idx("Aantal", "Quantity", "Count", "Qty", def = 6), idx("Proxy", def = 9),
        )
    }

    private fun isHeader(r: List<String>) = r.getOrNull(0)?.trim().equals("Set", true) && r.size > 2

    private fun cardFrom(r: List<String>, c: Cols, section: String?): ImportCard? {
        val set = r.getOrNull(c.set)?.trim().orEmpty()
        val nr = r.getOrNull(c.nr)?.trim()?.removeSuffix(".0").orEmpty()
        val qty = r.getOrNull(c.qty)?.trim()?.replace(',', '.')?.toDoubleOrNull()?.toInt() ?: 0
        if (qty <= 0 || set.isEmpty() || !Regex("""^[A-Za-z0-9]{2,6}$""").matches(set)) return null
        var name = r.getOrNull(c.name)?.trim().orEmpty()
        var lang = r.getOrNull(c.lang)?.trim()?.uppercase().orEmpty()
        // Taal kan ook achter de naam staan: "Edgar Markov (FR)".
        Regex("""\s*\(([A-Z]{2})\)\s*$""").find(name)?.let { m -> if (lang.isEmpty()) lang = m.groupValues[1]; name = name.removeRange(m.range).trim() }
        if (lang == "EN") lang = ""
        val foil = r.getOrNull(c.foil)?.trim()?.lowercase().orEmpty()
        val proxy = r.getOrNull(c.proxy)?.trim()?.lowercase() in setOf("ja", "yes", "x", "true", "1")
        return ImportCard(
            setCode = set.uppercase(), number = nr, name = name,
            foil = foil.startsWith("foil"), etched = foil == "etched",
            language = lang, quantity = qty, proxy = proxy, section = section,
        )
    }

    private fun readTable(rows: List<List<String>>): Pair<List<ImportCard>, Int> {
        val header = rows.firstOrNull { isHeader(it) }
        val c = colsFrom(header)
        val out = mutableListOf<ImportCard>()
        var skipped = 0
        for (r in rows) {
            if (r === header || r.all { it.isBlank() }) continue
            val card = cardFrom(r, c, null)
            if (card != null) out += card
            else if (r.getOrNull(c.set).orEmpty().isNotBlank() && r.getOrNull(c.qty)?.toDoubleOrNull()?.let { it > 0 } == true) skipped++
        }
        return out to skipped
    }

    private fun readDecks(rows: List<List<String>>): List<ImportDeck> {
        val decks = mutableListOf<ImportDeck>()
        var name: String? = null
        var cols = DEFAULT
        var section: String? = null
        var cards = mutableListOf<ImportCard>()
        var reading = false
        fun flush() { name?.let { if (cards.isNotEmpty()) decks += ImportDeck(it, cards.toList()) }; cards = mutableListOf() }
        rows.forEachIndexed { i, r ->
            val next = rows.getOrNull(i + 1)
            val filled = r.filter { it.isNotBlank() }
            when {
                // Decknaam: een regel met alleen tekst in kolom A, direct gevolgd door de kolomkoppen.
                next != null && isHeader(next) && filled.size == 1 && r.firstOrNull()?.isNotBlank() == true -> {
                    flush(); name = r[0].trim(); section = null; reading = false
                }
                isHeader(r) -> { cols = colsFrom(r); reading = true }
                !reading -> Unit
                filled.isEmpty() -> reading = false // lege regel: daaronder staan de tellingen
                filled.size == 1 && r.getOrNull(cols.name)?.isNotBlank() == true -> {
                    section = when (r[cols.name].trim().lowercase()) {
                        "commander" -> "COMMANDER"
                        "sideboard" -> "SIDEBOARD"
                        else -> "MAIN"
                    }
                }
                else -> cardFrom(r, cols, section ?: "MAIN")?.let { cards += it }
            }
        }
        flush()
        return decks
    }

    private val dateFormats = listOf("yyyy-MM-dd", "d-M-yyyy", "dd-MM-yyyy", "d/M/yyyy", "dd/MM/yyyy", "M/d/yyyy").map { DateTimeFormatter.ofPattern(it) }

    private fun parseDate(s: String): String? {
        val t = s.trim().substringBefore(' ').substringBefore('T')
        t.toDoubleOrNull()?.let { serial -> if (serial > 20000) return LocalDate.of(1899, 12, 30).plusDays(serial.toLong()).toString() }
        for (f in dateFormats) runCatching { return LocalDate.parse(t, f).toString() }
        return null
    }

    private fun readHistory(rows: List<List<String>>): List<ImportHistory> = rows.drop(1).mapNotNull { r ->
        val day = parseDate(r.getOrNull(0).orEmpty()) ?: return@mapNotNull null
        val value = r.getOrNull(1)?.replace("€", "")?.replace(" ", "")?.let { v ->
            // 1.234,56 of 1234.56
            if (v.contains(',') ) v.replace(".", "").replace(',', '.') else v
        }?.toDoubleOrNull() ?: return@mapNotNull null
        val count = r.getOrNull(2)?.toDoubleOrNull()?.toInt() ?: 0
        ImportHistory(day, value, count)
    }.groupBy { it.day }.map { it.value.last() }

    // ---------- CSV ----------

    fun parseCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { row += cell.toString(); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    // ---------- XLSX (zip met XML) ----------

    fun readXlsx(input: InputStream): List<Pair<String, List<List<String>>>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && (e.name.startsWith("xl/") && e.name.endsWith(".xml") || e.name.endsWith(".rels"))) files[e.name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { relations(it) } ?: emptyMap()
        val sheets = files["xl/workbook.xml"]?.let { sheetList(it) } ?: emptyList()
        return sheets.mapNotNull { (name, rid) ->
            val target = rels[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.drop(1) else "xl/" + target.removePrefix("./")
            val data = files[path] ?: return@mapNotNull null
            name to sheetRows(data, shared)
        }
    }

    private fun parser(bytes: ByteArray): org.xmlpull.v1.XmlPullParser {
        val p = org.xmlpull.v1.XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        p.setInput(bytes.inputStream(), "UTF-8")
        return p
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inSi = false; var inT = false
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.clear() }
                    "t" -> inT = inSi
                    "rPh" -> inSi = false.also { } // fonetische tekst overslaan
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "rPh" -> inSi = true
                    "si" -> { out += sb.toString(); inSi = false }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun relations(bytes: ByteArray): Map<String, String> {
        val p = parser(bytes)
        val out = mutableMapOf<String, String>()
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.getAttributeValue(null, "Id"); val t = p.getAttributeValue(null, "Target")
                if (id != null && t != null) out[id] = t
            }
            ev = p.next()
        }
        return out
    }

    private fun sheetList(bytes: ByteArray): List<Pair<String, String>> {
        val p = parser(bytes)
        val out = mutableListOf<Pair<String, String>>()
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && p.name == "sheet") {
                val name = p.getAttributeValue(null, "name")
                val rid = p.getAttributeValue(null, "r:id") ?: (0 until p.attributeCount).firstOrNull { p.getAttributeName(it).endsWith(":id") }?.let { p.getAttributeValue(it) }
                if (name != null && rid != null) out += name to rid
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(bytes)
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var rowNo = 0
        var col = 0; var type: String? = null
        val sb = StringBuilder(); var inValue = false
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> rowNo = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNo + 1)
                    "c" -> {
                        col = p.getAttributeValue(null, "r")?.let { colIndex(it) } ?: (col + 1)
                        type = p.getAttributeValue(null, "t"); sb.clear()
                    }
                    "v", "t" -> inValue = true
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (inValue) sb.append(p.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = sb.toString()
                        val v = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw.removeSuffix(".0")
                        }
                        if (v.isNotEmpty()) rows.getOrPut(rowNo) { mutableMapOf() }[col] = v
                    }
                }
            }
            ev = p.next()
        }
        if (rows.isEmpty()) return emptyList()
        // Lege regels behouden: in het tabblad Decks markeren die het einde van de kaarten.
        val last = rows.lastKey()
        return (1..last).map { r ->
            val cells = rows[r] ?: return@map emptyList()
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            List(width) { cells[it] ?: "" }
        }
    }
}
