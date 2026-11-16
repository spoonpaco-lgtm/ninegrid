package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Finish

/** Eén print in de printlijst (punt 8), met de afwerkingen die er van bestaan. */
data class PrintOption(val print: CardPrint, val finishes: List<String>)

object PrintChoice {
    private val ORDER = listOf(Finish.NONFOIL, Finish.FOIL, Finish.ETCHED)

    /**
     * Welke afwerkingen een print heeft. Liefst zoals Scryfall ze opgeeft (finishes); anders afgeleid uit de prijzen.
     * Is er niets bekend, dan normaal en foil, zodat je nooit vastzit.
     */
    fun finishes(scryfall: List<String>, p: CardPrint): List<String> {
        val known = ORDER.filter { it in scryfall }
        if (known.isNotEmpty()) return known
        val out = mutableListOf<String>()
        if (p.eur != null || p.usd != null) out += Finish.NONFOIL
        if (p.eurFoil != null || p.usdFoil != null) out += Finish.FOIL
        if (p.usdEtched != null) out += Finish.ETCHED
        return out.ifEmpty { listOf(Finish.NONFOIL, Finish.FOIL) }
    }

    /** Prijs van één afwerking. Cardmarket heeft geen aparte etched-prijs: dan de foilprijs, maar alleen als er geen gewone foil is. */
    fun price(p: CardPrint, finish: String, currency: String, finishes: List<String> = ORDER): Double? =
        if (currency == "USD") when (finish) { Finish.FOIL -> p.usdFoil; Finish.ETCHED -> p.usdEtched; else -> p.usd }
        else when (finish) {
            Finish.FOIL -> p.eurFoil
            Finish.ETCHED -> if (Finish.FOIL in finishes) null else p.eurFoil
            else -> p.eur
        }

    /** De laagste prijs van een print, over zijn afwerkingen. */
    fun cheapest(o: PrintOption, currency: String): Double? = o.finishes.mapNotNull { price(o.print, it, currency, o.finishes) }.minOrNull()

    /** De goedkoopste print van de lijst (voor het label "goedkoopst"). */
    fun cheapestId(options: List<PrintOption>, currency: String): String? =
        options.mapNotNull { o -> cheapest(o, currency)?.let { o.print.id to it } }.minByOrNull { it.second }?.first

    /** Nieuwste eerst (op datum, dan nummer) of goedkoopste eerst (prints zonder prijs achteraan). */
    fun sort(options: List<PrintOption>, cheapestFirst: Boolean, currency: String): List<PrintOption> =
        if (cheapestFirst) options.sortedWith(compareBy<PrintOption> { cheapest(it, currency) == null }.thenBy { cheapest(it, currency) ?: 0.0 }.thenByDescending { it.print.releasedAt })
        else options.sortedWith(compareByDescending<PrintOption> { it.print.releasedAt }.thenBy { it.print.setCode }.thenBy { numberKey(it.print.collectorNumber) })

    private fun numberKey(n: String) = n.filter { it.isDigit() }.take(6).padStart(6, '0') + n

    /** Afwerking na het wisselen van print: blijft als de nieuwe print hem heeft, anders Normaal, anders wat er is. */
    fun fitFinish(current: String, available: List<String>): String = when {
        current in available -> current
        Finish.NONFOIL in available -> Finish.NONFOIL
        else -> available.firstOrNull() ?: Finish.NONFOIL
    }

    /** Het jaar van een print, voor de lijst ("2024"). */
    fun year(p: CardPrint) = p.releasedAt.take(4)
}
