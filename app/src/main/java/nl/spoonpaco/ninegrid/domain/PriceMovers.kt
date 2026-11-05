package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.PricePoint

/** Eén kaart (print + afwerking) met zijn oude en nieuwe prijs per stuk, en hoeveel je er hebt. */
data class Mover(
    val print: CardPrint,
    val finish: String,
    val quantity: Int,
    val oldUnit: Double,
    val newUnit: Double,
) {
    val unitGain get() = newUnit - oldUnit
    /** Wat het jou opleverde: stijging per stuk maal je aantal. */
    val totalGain get() = unitGain * quantity
    val pct get() = if (oldUnit > 0) unitGain / oldUnit * 100 else 0.0
}

/**
 * Winner of the Week en Breakout: welke kaart steeg het meest. Alleen kaarten die je hebt (geen proxies),
 * vergeleken met een eerdere prijs uit de prijzenhistorie. Kaarten zonder eerdere prijs tellen niet mee.
 */
object PriceMovers {
    const val BREAKOUT_MIN_GAIN = 3.0
    const val BREAKOUT_MIN_PCT = 25.0

    fun movers(entries: List<Pair<Entry, CardPrint?>>, old: Map<String, PricePoint>, currency: String): List<Mover> =
        entries.filter { (e, p) -> p != null && !e.proxy }
            .groupBy { (e, p) -> p!!.id + "|" + e.finish }
            .mapNotNull { (_, list) ->
                val (e, p) = list.first(); p!!
                val o = old[p.id] ?: return@mapNotNull null
                val then = p.copy(eur = o.eur, eurFoil = o.eurFoil, usd = o.usd, usdFoil = o.usdFoil, usdEtched = o.usdEtched)
                val oldUnit = Money.unitPrice(e, then, currency)
                val newUnit = Money.unitPrice(e, p, currency)
                if (oldUnit <= 0 && newUnit <= 0) null else Mover(p, e.finish, list.sumOf { it.first.quantity }, oldUnit, newUnit)
            }

    /** De grootste stijging in geld over je eigen exemplaren; niets gestegen = geen winnaar. */
    fun winner(movers: List<Mover>): Mover? = movers.filter { it.totalGain > 0.005 }.maxByOrNull { it.totalGain }

    /** Een kaart die per stuk minstens €3 én 25% steeg; de grootste uitschieter. */
    fun breakout(movers: List<Mover>): Mover? =
        movers.filter { it.unitGain >= BREAKOUT_MIN_GAIN && it.pct >= BREAKOUT_MIN_PCT }.maxByOrNull { it.unitGain }

    private val typeOrder = listOf("Creature", "Planeswalker", "Battle", "Land", "Instant", "Sorcery", "Artifact", "Enchantment")

    /** Het hoofdtype voor de naam "Breakout ...": bij een Artifact Creature is dat Creature. */
    fun mainType(typeLine: String): String {
        val front = typeLine.substringBefore("//").substringBefore('—')
        return typeOrder.firstOrNull { front.contains(it) } ?: "Card"
    }
}
