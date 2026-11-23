package nl.spoonpaco.ninegrid.domain

/**
 * Testset voor losse kaarten (één kaart per foto): de map `losse-kaarten/` met NNN.jpg en één uitslag.txt.
 * Regels: `001: Thriving Bluff (J25 775) | set=ONE | kader=120 300 960 1480`. Alleen het eerste deel is verplicht;
 * set= is wat er in het setveld stond bij het scannen (leeg = geen), kader= het richtkader op de foto in pixels.
 */
object SingleTestset {
    data class Item(val id: String, val expect: TestsetScore.Expect, val set: String, val crop: Area?)

    private val LINE = Regex("""^\s*(\d{1,4})\s*:\s*(.+)$""")

    fun parse(lines: List<String>): List<Item> = lines.mapNotNull { raw ->
        val l = raw.substringBefore('#').trim()
        val m = LINE.find(l) ?: return@mapNotNull null
        val parts = m.groupValues[2].split('|').map { it.trim() }
        val expect = TestsetScore.parse(listOf("vak 1: " + parts.first()))[1] ?: return@mapNotNull null
        var set = ""
        var crop: Area? = null
        for (p in parts.drop(1)) {
            when {
                p.startsWith("set=") -> set = p.removePrefix("set=").trim().uppercase()
                p.startsWith("kader=") -> crop = p.removePrefix("kader=").trim().split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                    .takeIf { it.size == 4 }?.let { Area(it[0], it[1], it[2], it[3]) }
            }
        }
        Item(m.groupValues[1].padStart(3, '0'), expect, set, crop)
    }

    /** Eén regel voor de uitslag, zoals de verzameling hem als voorstel schrijft. */
    fun line(id: String, card: String, set: String, crop: String): String =
        "$id: $card" + (if (set.isNotBlank()) " | set=$set" else "") + (if (crop.isNotBlank()) " | kader=$crop" else "")
}
