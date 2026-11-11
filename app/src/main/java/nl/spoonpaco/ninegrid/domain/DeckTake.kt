package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.Entry

/**
 * Welke losse exemplaren uit de collectie het deck in gaan als je een kaart "uit de collectie haalt".
 * Zo telt niets dubbel: eerst dezelfde print en afwerking, dan dezelfde print, dan andere prints van dezelfde kaart.
 * Basic lands zijn de uitzondering: elke Island is voor Magic dezelfde kaart, maar wie een lijst met bepaalde
 * prints importeert, wil precies die prints. Daar telt dus alleen exact dezelfde print en afwerking.
 */
object DeckTake {
    /** De kandidaten in de volgorde waarin ze gepakt worden. [loose] = alles wat nu los in de collectie ligt of in een deck zit. */
    fun candidates(want: Entry, oracleId: String, basicLand: Boolean, all: List<Entry>, oracleOf: Map<String, String>): List<Entry> {
        val loose = all.filter { it.deckId == null && !it.proxy }
        if (basicLand) return loose.filter { it.printId == want.printId && it.finish == want.finish }.sortedByDescending { it.quantity }
        return loose.filter { it.printId == want.printId || (oracleId.isNotEmpty() && oracleOf[it.printId] == oracleId) }
            .sortedWith(compareBy({ if (it.printId == want.printId && it.finish == want.finish) 0 else if (it.printId == want.printId) 1 else if (it.finish == want.finish) 2 else 3 }, { -it.quantity }))
    }

    /** Hoeveel er van elk exemplaar gepakt wordt voor [quantity] kaarten; wat overblijft komt er nieuw bij. */
    fun plan(candidates: List<Entry>, quantity: Int): List<Pair<Entry, Int>> {
        var rem = quantity
        val out = mutableListOf<Pair<Entry, Int>>()
        for (c in candidates) {
            if (rem <= 0) break
            val n = minOf(rem, c.quantity)
            out += c to n; rem -= n
        }
        return out
    }
}
