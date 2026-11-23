package nl.spoonpaco.ninegrid.domain

import nl.spoonpaco.ninegrid.data.offline.OwnedCombo

/** Indeling van combo's naar wat ze doen (0.9.18). Eén blok per combo: het eerste dat past in deze volgorde. */
enum class ComboGroup { WIN, MANA, TOKENS, LOCK, OTHER }

/** Filters voor Combo's in je collectie. */
data class ComboFilter(
    /** Kleuren waarbinnen de combo moet passen (W U B R G); leeg = alles. */
    val colors: Set<Char> = emptySet(),
    /** Format-sleutel van Spellbook (commander, modern ...); null = alles. */
    val format: String? = null,
    /** Alleen combo's waarvan elke kaart los in je collectie ligt (niet in een deck). */
    val free: Boolean = false,
    /** Alleen combo's van twee kaarten zonder andere voorwaarden. */
    val twoCard: Boolean = false,
    /** Hoogste prijs van de ontbrekende kaart (bijna-combo's); null = alles. */
    val maxPrice: Double? = null,
    /** Alleen combo's met deze kaart (vanaf het kaartscherm). */
    val card: String? = null,
)

/** Een kaart die je mist en hoeveel combo's hij afmaakt. */
data class BestBuy(val card: String, val combos: List<OwnedCombo>, val price: Double?)

object ComboView {
    fun group(produces: List<String>): ComboGroup {
        val p = produces.map { it.lowercase() }
        fun any(vararg w: String) = p.any { f -> w.any { f.contains(it) } }
        return when {
            any("win the game", "loses the game", "lose the game", "infinite damage", "infinite lifeloss", "infinite life loss", "infinite mill", "near-infinite damage") -> ComboGroup.WIN
            any("infinite mana", "infinite colored mana", "infinite colorless mana") -> ComboGroup.MANA
            any("token") -> ComboGroup.TOKENS
            any("lock", "infinite turns", "extra turn", "can't untap", "skip") -> ComboGroup.LOCK
            else -> ComboGroup.OTHER
        }
    }

    /** Past de kleurenidentiteit van de combo binnen de gekozen kleuren? ("C" = kleurloos past altijd.) */
    fun fitsColors(identity: String, colors: Set<Char>): Boolean =
        colors.isEmpty() || identity.uppercase().filter { it in "WUBRG" }.all { it in colors }

    fun matches(c: OwnedCombo, f: ComboFilter, freeNames: Set<String>, price: (String) -> Double?): Boolean {
        if (!fitsColors(c.identity, f.colors)) return false
        if (f.format != null && f.format !in c.legal) return false
        if (f.twoCard && (c.cards.size != 2 || c.requires.isNotEmpty())) return false
        if (f.card != null && c.cards.none { it.equals(f.card, true) || it.substringBefore(" // ").equals(f.card.substringBefore(" // "), true) }) return false
        if (f.free && c.cards.any { n -> n !in c.missing && n.lowercase() !in freeNames && n.substringBefore(" // ").lowercase() !in freeNames }) return false
        if (f.maxPrice != null && c.missing.isNotEmpty()) {
            val p = c.missing.firstOrNull()?.let(price) ?: return false
            if (p > f.maxPrice) return false
        }
        return true
    }

    /** Per blok, populairste eerst. Blokken in vaste volgorde, lege blokken weg. */
    fun grouped(list: List<OwnedCombo>): List<Pair<ComboGroup, List<OwnedCombo>>> =
        list.groupBy { group(it.produces) }.toList()
            .sortedBy { it.first.ordinal }
            .map { (g, l) -> g to l.sortedWith(compareByDescending<OwnedCombo> { it.popularity }.thenBy { it.cards.size }) }

    /** Welke ontbrekende kaarten de meeste combo's afmaken; bij gelijk aantal de goedkoopste eerst. */
    fun bestBuys(almost: List<OwnedCombo>, price: (String) -> Double?): List<BestBuy> =
        almost.filter { it.missing.size == 1 }.groupBy { it.missing.first() }
            .map { (card, l) -> BestBuy(card, l.sortedByDescending { it.popularity }, price(card)) }
            .sortedWith(compareByDescending<BestBuy> { it.combos.size }.thenBy { it.price ?: Double.MAX_VALUE }.thenBy { it.card })
}
