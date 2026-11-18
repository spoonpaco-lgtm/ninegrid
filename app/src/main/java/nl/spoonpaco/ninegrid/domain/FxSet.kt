package nl.spoonpaco.ninegrid.domain

/**
 * Welke Magic-effecten aan staan. De hoofdschakelaar [master] zet alles in één keer uit; daarnaast kan elk effect
 * los uit ([off], bewaard als komma-lijst). Een effect speelt alleen als de hoofdschakelaar aan staat én het niet los uit staat.
 */
data class FxSet(val master: Boolean = true, val off: Set<String> = emptySet()) {
    fun on(id: String) = master && id !in off

    companion object {
        const val FOIL = "foil"
        const val EXILE = "exile"
        const val STAMPS = "stamps"
        const val SHIELDS = "shields"
        const val MOVERS = "movers"
        const val PRIVACY = "privacy"
        val all = listOf(FOIL, EXILE, STAMPS, SHIELDS, MOVERS, PRIVACY)

        fun parse(master: Boolean, stored: String) = FxSet(master, stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())

        /** De nieuwe bewaarde lijst als [id] aan of uit gaat; vaste volgorde, zodat dezelfde stand altijd hetzelfde wordt bewaard. */
        fun toggle(stored: String, id: String, on: Boolean): String {
            val off = parse(true, stored).off.toMutableSet()
            if (on) off.remove(id) else off.add(id)
            return all.filter { it in off }.joinToString(",")
        }
    }
}
