package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.Section

/** Statistieken van een deck: puur rekenwerk, werkt offline. */
data class DeckStats(
    /** Aantal niet-landkaarten per manawaarde 0, 1, ... 6 en 7+. */
    val curve: List<Int>,
    val averageCmc: Double,
    /** Kleursymbolen in de kosten, per kleur (W U B R G). */
    val costSymbols: Map<Char, Int>,
    /** Lands die een kleur kunnen maken, per kleur. */
    val landSources: Map<Char, Int>,
    val lands: Int,
    val deckSize: Int,
    /** Kans op precies 2, 3 en 4 lands in een openingshand van 7, en samen. */
    val handOdds: Map<Int, Double>,
) {
    val oddsTwoToFour get() = (handOdds[2] ?: 0.0) + (handOdds[3] ?: 0.0) + (handOdds[4] ?: 0.0)
}

object DeckStatsCalc {
    private const val COLORS = "WUBRG"
    private val basicTypes = mapOf("Plains" to 'W', "Island" to 'U', "Swamp" to 'B', "Mountain" to 'R', "Forest" to 'G')

    fun isLand(typeLine: String) = typeLine.substringBefore("//").contains("Land", ignoreCase = true)

    fun compute(cards: List<DeckCard>): DeckStats {
        // De bibliotheek: alles behalve sideboard en commander (die zit niet in je trekstapel).
        val library = cards.filter { it.section == Section.MAIN }
        val nonLand = cards.filter { it.section != Section.SIDEBOARD && !isLand(it.typeLine) }
        val curve = MutableList(8) { 0 }
        nonLand.forEach { c -> curve[c.cmc.toInt().coerceIn(0, 7)] += c.quantity }
        val nonLandCount = nonLand.sumOf { it.quantity }
        val avg = if (nonLandCount == 0) 0.0 else nonLand.sumOf { it.cmc * it.quantity } / nonLandCount

        val symbols = COLORS.associateWith { 0 }.toMutableMap()
        nonLand.forEach { c ->
            Regex("""\{([^}]+)\}""").findAll(c.manaCost).forEach { m ->
                // Hybride ({W/U}) en Phyrexisch ({W/P}) tellen voor elke kleur erin.
                m.groupValues[1].filter { it in COLORS }.toSet().forEach { ch -> symbols[ch] = symbols.getValue(ch) + c.quantity }
            }
        }

        val landCards = library.filter { isLand(it.typeLine) }
        val sources = COLORS.associateWith { 0 }.toMutableMap()
        landCards.forEach { c ->
            val fromTypes = basicTypes.filterKeys { c.typeLine.contains(it) }.values.toSet()
            val fromText = Regex("""\{([WUBRG])\}""").findAll(c.oracleText.substringAfter("Add", "")).map { it.groupValues[1][0] }.toSet()
            val anyColor = c.oracleText.contains("mana of any color", true) || c.oracleText.contains("any one color", true)
            val made = when {
                anyColor -> COLORS.toSet()
                fromTypes.isNotEmpty() || fromText.isNotEmpty() -> fromTypes + fromText
                else -> c.colorIdentity.toSet()
            }
            made.forEach { ch -> if (ch in COLORS) sources[ch] = sources.getValue(ch) + c.quantity }
        }

        val n = library.sumOf { it.quantity }
        val k = landCards.sumOf { it.quantity }
        val odds = (0..7).associateWith { hyper(n, k, 7, it) }
        return DeckStats(curve, avg, symbols, sources, k, n, odds)
    }

    /** Hypergeometrische kans: precies [x] lands in [draw] kaarten uit [n] kaarten waarvan [k] lands. */
    fun hyper(n: Int, k: Int, draw: Int, x: Int): Double {
        if (n <= 0 || draw > n || x > k || draw - x > n - k || x < 0) return 0.0
        return choose(k, x) * choose(n - k, draw - x) / choose(n, draw)
    }

    private fun choose(n: Int, r: Int): Double {
        if (r < 0 || r > n) return 0.0
        var res = 1.0
        for (i in 1..r) res = res * (n - r + i) / i
        return res
    }
}
