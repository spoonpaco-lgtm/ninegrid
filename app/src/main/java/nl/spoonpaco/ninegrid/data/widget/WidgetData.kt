package nl.spoonpaco.ninegrid.data.widget

import nl.spoonpaco.ninegrid.AppContainer
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.domain.Dates
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.domain.Mover
import nl.spoonpaco.ninegrid.domain.PriceMovers

/** Wat de widgets laten zien: ze lezen wat de app al bewaart en rekenen zelf niets bijzonders. */
object WidgetData {
    data class Value(val total: Double, val change: Double?, val series: List<Double>, val currency: String)
    data class Wish(val name: String, val now: Double?, val target: Double, val currency: String)
    data class Daily(val print: CardPrint, val unit: Double, val currency: String)

    /** Waarde van de collectie nu (zonder proxies), het verschil met de laatste dag ervoor en de laatste 30 dagen. */
    suspend fun value(c: AppContainer, currency: String): Value {
        val prints = c.db.prints().all().associateBy { it.id }
        val entries = c.db.entries().all().filter { !it.proxy }
        val total = entries.sumOf { Money.total(it, prints[it.printId], currency) }
        val today = java.time.LocalDate.now().toString()
        val days = c.db.snapshots().all().filter { it.key == "total" && it.day < today }.sortedBy { it.day }
        val past = days.takeLast(29).map { if (currency == "USD") it.usd else it.eur }
        val change = past.lastOrNull()?.let { total - it }
        return Value(total, change, past + total, currency)
    }

    /** Winner of the Week, zoals het Overzicht hem berekent (week vanaf de weekstart, prijzen van de maandag). */
    suspend fun winner(c: AppContainer, currency: String): Mover? {
        val style = c.settings.current().dateStyle
        val monday = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(Dates.weekStart(style)))
        val first = c.repository.firstPriceDay()
        if (first == null || first > monday.minusDays(7).toString()) return null
        val then = c.repository.pricesAsOf(monday.minusDays(7).toString())
        val now = c.repository.pricesAsOf(monday.toString())
        val prints = c.db.prints().all().associateBy { it.id }
        val list = c.db.entries().all().mapNotNull { e ->
            val p = prints[e.printId] ?: return@mapNotNull null
            val n = now[p.id] ?: return@mapNotNull null
            e to p.copy(eur = n.eur, eurFoil = n.eurFoil, usd = n.usd, usdFoil = n.usdFoil, usdEtched = n.usdEtched)
        }
        return PriceMovers.winner(PriceMovers.movers(list, then, currency))
    }

    /** De drie kaarten van de verlanglijst die het dichtst bij hun alarmprijs zitten. */
    suspend fun wish(c: AppContainer): List<Wish> {
        val prints = c.db.prints().all().associateBy { it.id }
        return c.db.wishlist().all().filter { it.alarmPrice != null && it.got < it.wanted }.map { w ->
            val p = prints[w.printId]
            val usd = w.alarmCurrency == "USD"
            val now = if (p == null) null else if (usd) (if (w.foilOnly) p.usdFoil else p.usd ?: p.usdFoil) else (if (w.foilOnly) p.eurFoil else p.eur ?: p.eurFoil)
            Wish(w.name, now, w.alarmPrice!!, w.alarmCurrency)
        }.sortedBy { if (it.now == null || it.target <= 0) Double.MAX_VALUE else (it.now - it.target) / it.target }.take(3)
    }

    /** Elke dag een andere kaart uit je collectie (vast per dag, zodat het widget niet telkens wisselt). */
    suspend fun daily(c: AppContainer, currency: String): Daily? {
        val prints = c.db.prints().all().associateBy { it.id }
        val owned = c.db.entries().all().filter { !it.proxy && prints[it.printId] != null }.sortedBy { it.id }
        if (owned.isEmpty()) return null
        val e = owned[(java.time.LocalDate.now().toEpochDay() % owned.size).toInt().let { if (it < 0) it + owned.size else it }]
        val p = prints[e.printId]!!
        return Daily(p, Money.unitPrice(e, p, currency), currency)
    }
}
