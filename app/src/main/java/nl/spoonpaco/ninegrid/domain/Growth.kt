package nl.spoonpaco.ninegrid.domain

/**
 * Waar de groei in een periode vandaan komt: door prijzen (de koers per dag, die toevoegen en weghalen niet meetelt)
 * of door kaarten erbij of eraf (de rest van de verandering).
 */
object Growth {
    data class Split(val total: Double, val prices: Double, val cards: Double)

    /**
     * [days]: per dag (op volgorde) de waarde en de koers van die dag tegenover de dag ervoor. De koers van de eerste dag
     * telt niet: die ligt vóór de periode. Null als er minder dan twee dagen zijn of de koers van een dag onbekend is.
     */
    fun split(days: List<Pair<Double, Double?>>): Split? {
        if (days.size < 2) return null
        if (days.drop(1).any { it.second == null }) return null
        val total = days.last().first - days.first().first
        val prices = days.drop(1).sumOf { it.second ?: 0.0 }
        return Split(total, prices, total - prices)
    }
}
