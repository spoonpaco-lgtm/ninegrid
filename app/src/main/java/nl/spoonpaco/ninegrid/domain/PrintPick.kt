package nl.spoonpaco.ninegrid.domain

/**
 * De printkiezer (0.9.17): de naam is gelezen, nu welke print. Alle prints met die naam (uit de offline database)
 * krijgen punten voor wat er in de voetregel staat: nummer, setcode, zeldzaamheidsletter en jaartal.
 *
 * De voetregel wordt vaak half goed gelezen ("S0130" voor 139, "FON" of "FDNN" voor FDN). Een kleine afwijking
 * telt dus mee, maar alleen tussen prints die echt bestaan met die naam: er komt nooit een verzonnen nummer uit.
 * Groen (zeker) alleen als set en nummer duidelijk kloppen; anders is het een keuze die gecontroleerd moet worden.
 */
object PrintPick {
    data class Cand(val id: String, val set: String, val number: String, val rarity: String = "", val year: String = "", val basicLand: Boolean = false)
    data class Footer(val numbers: Set<String>, val sets: Set<String>, val rarity: Char?, val year: String?)
    data class Pick(val cand: Cand, val sure: Boolean, val score: Double, val why: String)

    private const val LANGS = "EN|DE|FR|IT|ES|PT|JA|JP|KO|RU|ZH|CS|CT|PH"
    // "C 0139", "CO098", "C O16S", "U0146", "R O122": zeldzaamheidsletter met 3 of 4 tekens die cijfers moeten zijn.
    private val letterNumber = Regex("""(?<![A-Z0-9])([CURMSLTP])\s?([0-9OILS]{3,4})(?![0-9A-Z])""")
    // "229/254 M"
    private val slashNumber = Regex("""(?<![A-Z0-9])([0-9OIL]{1,4})\s*/\s*[0-9OIL]{2,4}""")
    // Een regel die alleen een nummer is ("0233", "248").
    private val loneNumber = Regex("""^\s*([0-9OILS]{3,4})\s*[CURMSLTP]?\s*$""")
    // Setcode met taal, ook aan elkaar ("FDNEN", "FDN•ENDIMITAR", "FONNEN").
    private val setLang = Regex("""(?<![A-Z0-9])([A-Z0-9]{2,5}?)\s*[•·*.\-+°,★☆✩✭]?\s*(?:$LANGS)""")
    private val year = Regex("""(?<![0-9])(20[0-3][0-9])(?![0-9])""")

    /** Zet een gelezen nummer om naar cijfers: O → 0, I/L → 1, S → 5; voorloopnullen weg. Null zonder echt cijfer. */
    fun digits(raw: String): String? {
        if (raw.none { it.isDigit() }) return null
        return raw.uppercase().map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; 'S' -> '5'; else -> it } }
            .joinToString("").trimStart('0').ifEmpty { "0" }.takeIf { d -> d.all { it.isDigit() } }
    }

    fun isYear(d: String) = d.length == 4 && d.toIntOrNull()?.let { it in 2000..2039 } == true

    fun footer(lines: List<String>): Footer {
        val numbers = linkedSetOf<String>(); val sets = linkedSetOf<String>()
        var rarity: Char? = null; var yr: String? = null
        for (raw in lines) {
            val r = raw.replace(Regex("""\s+"""), " ")
            val t = r.uppercase()
            // De copyrightregel ("© 2024 Wizards", "C 2024 W'izards"): het jaartal; een jaartal is nooit een nummer (isYear).
            val copyright = t.contains("WIZARD") || t.contains("COAST") || t.contains("©") || t.contains("&")
            if (copyright && yr == null) year.find(t)?.let { yr = it.groupValues[1] }
            for (m in letterNumber.findAll(t)) {
                val d = digits(m.groupValues[2]) ?: continue
                if (isYear(d)) continue
                numbers += d; if (rarity == null) rarity = m.groupValues[1][0]
            }
            slashNumber.findAll(t).forEach { m -> digits(m.groupValues[1])?.let { numbers += it } }
            loneNumber.matchEntire(t)?.let { m -> digits(m.groupValues[1])?.takeUnless(::isYear)?.let { numbers += it } }
            // Op de regel zoals gelezen (niet in hoofdletters): de voetregel is in hoofdletters, regeltekst niet
            // ("Create a Food token" mag geen setcode "TOK" opleveren).
            for (m in setLang.findAll(r)) {
                val code = m.groupValues[1]
                if (code.length >= 3 && code.any { it.isLetter() }) sets += if (code.first() == '0') "O" + code.drop(1) else code
            }
        }
        return Footer(numbers, sets, rarity, yr)
    }

    /** Levenshtein-afstand, met een vroege stop boven 2. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /** 2 = precies, 1 = één teken verschil, 0 = niets. */
    private fun match(read: Set<String>, actual: String, minLen: Int): Int {
        if (actual in read) return 2
        return if (actual.length >= minLen && read.any { it.length >= minLen && distance(it, actual) == 1 }) 1 else 0
    }

    private fun rarityLetter(c: Cand): Char? = when {
        c.basicLand -> 'L'
        c.rarity.startsWith("common") -> 'C'
        c.rarity.startsWith("uncommon") -> 'U'
        c.rarity.startsWith("rare") -> 'R'
        c.rarity.startsWith("mythic") -> 'M'
        c.rarity.startsWith("special") -> 'S'
        else -> null
    }

    /**
     * Kiest de print. [extraSets]/[extraNumbers]: wat de parser al las (telt als precies gelezen).
     * Null als er niets in de voetregel bij een print past (dan beslist de rest van de scanner, zoals vroeger).
     */
    fun pick(cands: List<Cand>, lines: List<String>, extraSets: List<String> = emptyList(), extraNumbers: List<String> = emptyList()): Pick? {
        if (cands.isEmpty()) return null
        val f = footer(lines)
        val nums = f.numbers + extraNumbers.mapNotNull { digits(it) ?: it.uppercase() }
        val sets = f.sets + extraSets.map { it.uppercase() }
        data class S(val c: Cand, val set: Int, val nr: Int, val total: Double)
        val scored = cands.map { c ->
            val nr = match(nums, c.number.uppercase().trimStart('0').ifEmpty { "0" }, 2)
            val st = match(sets, c.set.uppercase(), 3)
            val rar = if (f.rarity != null && rarityLetter(c) == f.rarity) 0.5 else 0.0
            val yr = if (f.year != null && c.year == f.year) 0.5 else 0.0
            S(c, st, nr, st + nr + rar + yr)
        }.sortedByDescending { it.total }
        val best = scored.first()
        if (best.set + best.nr == 0) return null
        val second = scored.getOrNull(1)?.total ?: -10.0
        if (best.total <= second) return null
        // Zeker: set en nummer precies, of één van beide precies en de ander op één teken na met ruime voorsprong.
        val sure = (best.set == 2 && best.nr == 2 && best.total - second >= 1.0) ||
            (best.set + best.nr == 3 && best.total - second >= 2.0)
        val why = "set ${listOf("-", "~", "=")[best.set]} nr ${listOf("-", "~", "=")[best.nr]} · ${"%.1f".format(java.util.Locale.ROOT, best.total)} tegen ${"%.1f".format(java.util.Locale.ROOT, second.coerceAtLeast(0.0))}"
        return Pick(best.c, sure, best.total, why)
    }

    /** Art Series-kaarten, tokens en emblemen zijn geen prints van een speelkaart. */
    fun notACard(name: String, typeLine: String): Boolean {
        val halves = name.split(" // ")
        return typeLine.startsWith("Card") || typeLine.contains("Token") || typeLine.startsWith("Emblem") ||
            (halves.size == 2 && halves[0].equals(halves[1], true))
    }
}
