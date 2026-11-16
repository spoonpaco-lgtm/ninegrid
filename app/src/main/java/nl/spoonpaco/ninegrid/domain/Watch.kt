package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.db.DeckFormat

/**
 * Melding bij ban of reprint (punt 40). Alleen de rekenregels; ophalen en melden gebeurt in data/Watcher.
 *
 * Legaliteit: per kaart (oracle-id) een korte stand per format, bijv. "l,l,b,n,l,l" (legal, banned, not_legal,
 * restricted) in de volgorde van [FORMATS]. De dagelijkse controle vergelijkt die met de vorige stand.
 */
object Watch {
    /** Formats die gevolgd kunnen worden: dezelfde als bij een deck (zonder Casual). */
    val FORMATS = listOf(DeckFormat.COMMANDER, DeckFormat.MODERN, DeckFormat.PIONEER, DeckFormat.STANDARD, DeckFormat.LEGACY, DeckFormat.PAUPER)

    private fun code(l: String?) = when (l) { "legal" -> 'l'; "banned" -> 'b'; "restricted" -> 'r'; "not_legal" -> 'n'; else -> '-' }
    private fun word(c: Char) = when (c) { 'l' -> "legal"; 'b' -> "banned"; 'r' -> "restricted"; 'n' -> "not_legal"; else -> "" }

    /** De korte stand van een kaart, uit de legaliteit zoals die bij de print bewaard is ("commander=legal,modern=banned,..."). */
    fun compact(legalities: String): String = FORMATS.map { code(Formats.legalityOf(legalities, it)) }.joinToString("")

    enum class Kind { BANNED, RESTRICTED, UNBANNED, NOT_LEGAL, NOW_LEGAL }

    data class Change(val oracleId: String, val format: String, val from: String, val to: String) {
        val kind: Kind get() = when {
            to == "banned" -> Kind.BANNED
            to == "restricted" -> Kind.RESTRICTED
            to == "legal" && from == "not_legal" -> Kind.NOW_LEGAL
            to == "legal" -> Kind.UNBANNED
            else -> Kind.NOT_LEGAL
        }
    }

    /** Is deze verandering een melding waard? Van banned naar not_legal (of terug) zegt niets voor een speler. */
    fun interesting(from: String, to: String): Boolean =
        from.isNotEmpty() && to.isNotEmpty() && from != to && setOf(from, to) != setOf("banned", "not_legal")

    /**
     * Wat er sinds de vorige stand veranderd is, alleen voor kaarten die er toen ook al waren (een nieuwe kaart
     * in de collectie geeft geen melding) en alleen in de gevolgde [formats].
     */
    fun changes(old: Map<String, String>, now: Map<String, String>, formats: Set<String>): List<Change> {
        val out = mutableListOf<Change>()
        for ((oracle, cur) in now) {
            val prev = old[oracle] ?: continue
            FORMATS.forEachIndexed { i, f ->
                if (f !in formats) return@forEachIndexed
                val a = word(prev.getOrElse(i) { '-' })
                val b = word(cur.getOrElse(i) { '-' })
                if (interesting(a, b)) out += Change(oracle, f, a, b)
            }
        }
        return out
    }

    /** Eén kaart in een (nieuwe) set. */
    data class SetCard(val oracleId: String, val name: String, val setCode: String, val setName: String, val releasedAt: String)

    fun reprintKey(setCode: String, oracleId: String) = setCode.uppercase() + ":" + oracleId

    /**
     * Kaarten uit een nieuwe set die je al hebt in een andere print (uit een andere set), en waarover nog niet gemeld is.
     * [ownedSets]: per oracle-id de sets van jouw exemplaren.
     */
    fun reprints(cards: List<SetCard>, ownedSets: Map<String, Set<String>>, seen: Set<String>): List<SetCard> =
        cards.filter { c ->
            val mine = ownedSets[c.oracleId] ?: return@filter false
            mine.any { !it.equals(c.setCode, true) } && reprintKey(c.setCode, c.oracleId) !in seen
        }.distinctBy { reprintKey(it.setCode, it.oracleId) }

    /** Settypes zonder echte speelkaarten (tokens, memorabilia, digitaal) volgen we niet. */
    private val SKIP_TYPES = setOf("token", "memorabilia", "minigame", "alchemy", "treasure_chest", "vanguard")

    /** Een set volgen: echte kaarten, niet alleen digitaal, nog niet uit of hoogstens [days] dagen geleden uitgebracht. */
    fun follow(setType: String, digital: Boolean, releasedAt: String?, today: String, days: Long = 30): Boolean {
        if (digital || setType in SKIP_TYPES || releasedAt.isNullOrBlank()) return false
        val from = runCatching { java.time.LocalDate.parse(today).minusDays(days).toString() }.getOrDefault(today)
        return releasedAt >= from
    }
}
