package nl.spoonpaco.ninegrid.domain

/**
 * Vergelijkt een gelezen naam met echte kaartnamen. Strenger dan vroeger (0.9.11 en eerder): toen telde een naam al als
 * gelijk als het gelezen stuk erin zat ("Keyrune" → Azorius Keyrune) of als de helft van de letters klopte, en won de
 * eerste kaart die paste. Dat gaf groene kaarten met de verkeerde naam (testset 18 nov: 8 van 144 vakken).
 */
object NameMatch {
    /** Vanaf deze gelijkenis (0..1) telt een gelezen naam als dezelfde kaart. */
    const val MIN = 0.7
    /** Zoveel moet de beste naam beter passen dan de op één na beste, anders is het gokken. */
    const val MARGIN = 0.12

    private fun norm(s: String) = s.lowercase().filter { it.isLetter() }

    /** Levenshtein-afstand. */
    fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        // Het ene zit helemaal in het andere en is bijna even lang: losse tekens ervoor of erachter ("(Selesnya Keyrune").
        if ((b.contains(a) || a.contains(b)) && minOf(a.length, b.length) >= 0.85 * maxOf(a.length, b.length)) return 1.0
        return 1.0 - distance(a, b).toDouble() / maxOf(a.length, b.length)
    }

    /**
     * Gelijkenis 0..1 tussen gelezen en echte naam. Bij een splitkaart of dubbelzijdige kaart ("A // B") telt
     * de beste van de hele naam en elke helft, want vaak is maar één helft gelezen.
     */
    fun score(read: String, real: String): Double {
        val a = norm(read)
        val parts = listOf(real) + if (real.contains("//")) real.split("//") else emptyList()
        val readParts = listOf(a) + if (read.contains("//")) read.split("//").map(::norm) else emptyList()
        return parts.maxOf { p -> readParts.maxOf { r -> sim(r, norm(p)) } }
    }

    /** Is dit dezelfde kaartnaam (met leesfouten)? */
    fun alike(read: String, real: String): Boolean = score(read, real) >= MIN

    /**
     * De echte naam die het best bij [read] past, of null als niets goed genoeg past of als twee verschillende
     * namen ongeveer even goed passen ("Gloom" bij Gloomwidow en Gloom Surgeon).
     */
    fun best(read: String, names: Collection<String>): String? {
        if (norm(read).length < 3) return null
        val scored = names.distinct().map { it to score(read, it) }.sortedByDescending { it.second }
        val top = scored.firstOrNull() ?: return null
        if (top.second < MIN) return null
        val second = scored.getOrNull(1)?.second ?: 0.0
        return if (top.second - second >= MARGIN || top.second == 1.0 && second < 1.0) top.first else null
    }
}
