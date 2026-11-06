package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint

/**
 * Ruilmatch met de collectie van iemand anders, met dezelfde logica als de decksuggesties:
 * wat zijn losse kaarten jouw decks beter maken, en wat jouw losse kaarten zijn decks beter maken.
 */
object TradeMatch {
    /** Een deck om voor te zoeken: naam, format, kaarten (en de regels die ervoor gelden). */
    data class DeckInput(val name: String, val format: String, val cards: List<EntryWithPrint>, val rules: DeckRules = DeckRules())

    /** Eén kaart die geruild kan worden. [decks] = voor welke decks hij een verbetering is. */
    data class Item(val entry: Entry, val print: CardPrint, val decks: List<String>, val reasons: List<String>, val unit: Double, val score: Double)

    /** Zekere ruilen: wat op een verlanglijst staat en de ander los heeft. */
    data class Result(val get: List<Item>, val give: List<Item>, val wishGet: List<Item> = emptyList(), val wishGive: List<Item> = emptyList())

    /** Een kaart die iemand zoekt. */
    data class Want(val name: String, val printId: String, val anyPrint: Boolean, val foilOnly: Boolean, val qty: Int)

    /** Wat van [wants] los in [have] ligt (bij "willekeurige print" elke print met die naam), de goedkoopste geschikte. */
    fun matchWants(wants: List<Want>, have: List<EntryWithPrint>, currency: String): List<Item> = wants.mapNotNull { w ->
        val fits = have.filter { e ->
            val p = e.print ?: return@filter false
            !e.entry.proxy && e.entry.deckId == null && p.name.equals(w.name, true) && (w.anyPrint || p.id == w.printId) &&
                (!w.foilOnly || e.entry.finish != nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL)
        }
        val best = fits.minByOrNull { Money.unitPrice(it.entry, it.print, currency) } ?: return@mapNotNull null
        Item(best.entry, best.print!!, emptyList(), listOf("wish"), Money.unitPrice(best.entry, best.print, currency), 100.0)
    }

    private fun candidate(e: EntryWithPrint): Candidate {
        val p = e.print!!
        return Candidate(e.entry.id, p.name, p.typeLine, p.oracleText, p.manaCost, p.cmc, p.colorIdentity, p.legalities, p.gameChanger, null, p.producedMana)
    }

    /** Voor elk deck de beste kaarten uit [pool]; per kaart samengevoegd over de decks heen. */
    private fun forDecks(decks: List<DeckInput>, pool: List<EntryWithPrint>, skipNames: Set<String>, currency: String, perDeck: Int): List<Item> {
        val usable = pool.filter { it.print != null && !it.entry.proxy && it.entry.quantity > 0 && it.print.name.lowercase() !in skipNames }
        if (usable.isEmpty()) return emptyList()
        // Eén kandidaat per kaartnaam: de goedkoopste versie (de ruil gaat meestal om de gewone print).
        val byName = usable.groupBy { it.print!!.name }.mapValues { (_, v) -> v.minBy { Money.unitPrice(it.entry, it.print, currency) } }
        val candidates = byName.values.map(::candidate)
        val byKey = byName.values.associateBy { it.entry.id }
        val found = LinkedHashMap<Long, Item>()
        for (d in decks) {
            val deckCards = Formats.toDeckCards(d.cards.map { it.entry to it.print })
            if (deckCards.isEmpty()) continue
            val keys = d.cards.filter { it.print != null }.associate { it.print!!.name to it.entry.id }
            val report = runCatching { Suggestions.suggest(d.format, deckCards, keys, candidates, d.rules, max = perDeck) }.getOrNull() ?: continue
            (report.adds + report.lands.take(3)).forEach { s ->
                val src = byKey[s.card.key] ?: return@forEach
                val old = found[s.card.key]
                found[s.card.key] = if (old == null) Item(src.entry, src.print!!, listOf(d.name), s.reasons, Money.unitPrice(src.entry, src.print, currency), s.score)
                else old.copy(decks = (old.decks + d.name).distinct(), reasons = (old.reasons + s.reasons).distinct(), score = maxOf(old.score, s.score))
            }
        }
        return found.values.sortedWith(compareByDescending<Item> { it.decks.size }.thenByDescending { it.score })
    }

    /**
     * [mine] = al jouw exemplaren, [myDecks] = jouw decks; [theirLoose] = de losse kaarten van de ander,
     * [theirDecks] = de decks die hij meestuurde (alleen daarmee kan "wat hij zoekt" worden berekend).
     */
    fun compute(
        mine: List<EntryWithPrint>,
        myDecks: List<DeckInput>,
        theirLoose: List<EntryWithPrint>,
        theirDecks: List<DeckInput>,
        currency: String,
        perDeck: Int = 8,
        myWants: List<Want> = emptyList(),
        theirWants: List<Want> = emptyList(),
    ): Result {
        // Wat je zelf al los hebt, hoef je niet te ruilen; wat hij al heeft ook niet.
        val myLooseNames = mine.filter { it.entry.deckId == null && !it.entry.proxy }.mapNotNull { it.print?.name?.lowercase() }.toSet()
        val theirNames = (theirLoose + theirDecks.flatMap { it.cards }).mapNotNull { it.print?.name?.lowercase() }.toSet()
        val get = forDecks(myDecks, theirLoose, myLooseNames, currency, perDeck)
        val give = forDecks(theirDecks, mine.filter { it.entry.deckId == null }, theirNames, currency, perDeck)
        val wishGet = matchWants(myWants, theirLoose, currency)
        val wishGive = matchWants(theirWants, mine, currency)
        // Wat al bij de zekere ruilen staat, niet nog eens bij de suggesties.
        val wg = wishGet.map { it.print.name.lowercase() }.toSet(); val wv = wishGive.map { it.print.name.lowercase() }.toSet()
        return Result(get.filter { it.print.name.lowercase() !in wg }, give.filter { it.print.name.lowercase() !in wv }, wishGet, wishGive)
    }

    fun formatOf(f: String?) = f?.takeIf { it in DeckFormat.all } ?: DeckFormat.CASUAL
}
