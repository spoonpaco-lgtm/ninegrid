package nl.spoonpaco.ninegrid.domain

/**
 * 0.9.19: een half gelezen naam herkennen aan de letters die er wel staan, in de goede volgorde.
 * "ierüd, Skyknight" → Tura Kennerüd, Skyknight; "avoc etva" → Havoc Festival (testset 23 nov).
 * Alleen binnen de set, alleen als één naam duidelijk het best past. Zo'n kaart wordt nooit groen.
 */
object NameLetters {
    private fun letters(s: String) = s.lowercase().filter { it.isLetter() }

    /** Langste gemeenschappelijke deelrij (letters in dezelfde volgorde, niet per se naast elkaar). */
    fun lcs(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var prev = IntArray(b.length + 1)
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            for (j in 1..b.length) cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else maxOf(prev[j], cur[j - 1])
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * De naam uit [names] waarin bijna alle gelezen letters (85%) in volgorde terugkomen en die minstens de helft
     * van zijn eigen letters terugziet; de tweede beste moet duidelijk slechter passen. Minstens 6 gelezen letters.
     */
    fun best(read: String, names: List<String>): String? {
        val r = letters(read)
        if (r.length < 6) return null
        data class S(val name: String, val ofRead: Double, val ofName: Double)
        val scored = names.distinct().map { n ->
            val l = letters(n)
            val k = lcs(r, l).toDouble()
            S(n, k / r.length, if (l.isEmpty()) 0.0 else k / l.length)
        }.sortedByDescending { it.ofRead + it.ofName / 4 }
        val best = scored.firstOrNull() ?: return null
        if (best.ofRead < 0.85 || best.ofName < 0.5) return null
        val second = scored.getOrNull(1)
        if (second != null && second.ofRead > best.ofRead - 0.15 && second.ofName >= 0.5) return null
        return best.name
    }
}
