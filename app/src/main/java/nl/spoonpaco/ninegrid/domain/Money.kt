package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Finish
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    /** Prijs per stuk voor dit exemplaar. Foil krijgt de foilprijs; een proxy is niets waard. */
    fun unitPrice(entry: Entry, print: CardPrint?, currency: String): Double {
        if (print == null || entry.proxy) return 0.0
        return if (currency == "USD") when (entry.finish) {
            Finish.FOIL -> print.usdFoil ?: print.usd
            Finish.ETCHED -> print.usdEtched ?: print.usdFoil ?: print.usd
            else -> print.usd ?: print.usdFoil
        } ?: 0.0
        else when (entry.finish) {
            Finish.FOIL, Finish.ETCHED -> print.eurFoil ?: print.eur
            else -> print.eur ?: print.eurFoil
        } ?: 0.0
    }

    fun total(entry: Entry, print: CardPrint?, currency: String) = unitPrice(entry, print, currency) * entry.quantity

    fun format(value: Double, currency: String): String {
        val f = NumberFormat.getCurrencyInstance(if (currency == "USD") Locale.US else Locale("nl", "NL"))
        f.currency = Currency.getInstance(currency)
        return f.format(value)
    }
}
