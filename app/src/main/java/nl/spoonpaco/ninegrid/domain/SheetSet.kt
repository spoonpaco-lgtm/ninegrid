package nl.spoonpaco.ninegrid.domain

/**
 * Voorstel "Alles uit set X?" op het controlescherm. Is er bij een blad geen set opgegeven en niet gelezen (oude kaarten
 * hebben geen setcode, of een te kleine), dan kiest de app geen print meer op de gok. Wel kijkt hij in welke set
 * alle gevonden kaarten samen bestaan. Dat is een voorstel: pas als je erop tikt telt die set, net alsof je hem
 * in de scanner had ingevuld (een set telt alleen als de gebruiker hem opgeeft).
 */
object SheetSet {
    /** Zo veel verschillende kaarten moeten er minstens gevonden zijn, anders zegt een gezamenlijke set weinig. */
    const val MIN_CARDS = 3

    /**
     * [setsPerCard]: per gevonden kaart de sets (code → releasedatum) waarin hij gedrukt is. Geeft de set waarin
     * alle kaarten voorkomen; zijn dat er meer, dan de oudste (de oorspronkelijke set; een herdruk met precies
     * dezelfde kaarten is zeldzaam). Null als er geen gezamenlijke set is (een gemengd blad).
     */
    fun common(setsPerCard: List<Map<String, String>>): String? {
        if (setsPerCard.size < MIN_CARDS) return null
        val shared = setsPerCard.map { it.keys.map(String::lowercase).toSet() }.reduce { a, b -> a intersect b }
        if (shared.isEmpty()) return null
        val dates = setsPerCard.first().mapKeys { it.key.lowercase() }
        return shared.minByOrNull { dates[it] ?: "9999" }
    }
}
