package nl.spoonpaco.ninegrid.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Section

/**
 * Een gedeelde collectie: een klein bestand met alleen kaarten en aantallen, geen prijzen
 * (die haalt de app van de ontvanger zelf op). De code van de deler zorgt dat een nieuwe versie de oude vervangt.
 */
@Serializable
data class ShareFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val senderId: String,
    val senderName: String,
    val createdAt: Long,
    val app: String = "",
    val packages: List<SharePackage>,
) {
    companion object { const val FORMAT = "ninegrid-share" }
}

/** Eén pakket: de losse collectie, een binder, een set of een deck. */
@Serializable
data class SharePackage(
    /** Vaste sleutel binnen deze deler: "loose", "binder:Rood", "set:MH2", "deck:Edgar". */
    val key: String,
    val label: String,
    val kind: String,
    val format: String? = null,
    val cards: List<ShareCard>,
)

@Serializable
data class ShareCard(
    /** Scryfall-id van de print. */
    val id: String,
    val set: String = "",
    val nr: String = "",
    val name: String = "",
    val finish: String = "nonfoil",
    val lang: String = "",
    val qty: Int,
    val section: String? = null,
)

object ShareKind {
    const val LOOSE = "LOOSE"
    const val BINDER = "BINDER"
    const val SET = "SET"
    const val DECK = "DECK"
    /** De verlanglijst van de deler (geen bezit: telt nergens mee, alleen voor de ruilmatch). */
    const val WISH = "WISH"
}

/** Wat iemand kiest om te sturen. Alles = de losse collectie plus alle decks. Combineren mag. */
data class ShareChoice(
    val all: Boolean = false,
    val loose: Boolean = false,
    val binders: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val decks: Set<Long> = emptySet(),
    /** Verlanglijst meesturen (staat in het bestand als eigen pakket). */
    val wishlist: Boolean = false,
) {
    val isEmpty get() = !all && !loose && binders.isEmpty() && sets.isEmpty() && decks.isEmpty()
}

object Share {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Geen proxies: die zijn niet om te ruilen. Gelijke kaarten (zelfde print, foil, taal, deel van het deck) samen. */
    private fun cardsOf(list: List<EntryWithPrint>, withSection: Boolean): List<ShareCard> =
        list.filter { it.print != null && !it.entry.proxy && it.entry.quantity > 0 }
            .groupBy { listOf(it.entry.printId, it.entry.finish, it.entry.language, if (withSection) it.entry.section ?: Section.MAIN else "") }
            .map { (_, v) ->
                val e = v.first().entry; val p = v.first().print!!
                ShareCard(
                    id = e.printId, set = p.setCode, nr = p.collectorNumber, name = p.name, finish = e.finish, lang = e.language,
                    qty = v.sumOf { it.entry.quantity }, section = if (withSection) e.section ?: Section.MAIN else null,
                )
            }
            .sortedWith(compareBy({ it.set }, { it.nr.padStart(5, '0') }))

    fun packages(
        entries: List<EntryWithPrint>, decks: List<Deck>, choice: ShareChoice,
        wishes: List<nl.spoonpaco.ninegrid.data.db.WishWithPrint> = emptyList(),
    ): List<SharePackage> {
        val loose = entries.filter { it.entry.deckId == null }
        val out = mutableListOf<SharePackage>()
        if (choice.all || choice.loose) out += SharePackage("loose", "", ShareKind.LOOSE, cards = cardsOf(loose, false))
        // Binders en sets alleen als de losse collectie er niet al in zit (anders dubbel).
        if (!choice.all && !choice.loose) {
            choice.binders.sorted().forEach { b ->
                out += SharePackage("binder:$b", b, ShareKind.BINDER, cards = cardsOf(loose.filter { it.entry.binder == b }, false))
            }
            choice.sets.sorted().forEach { s ->
                out += SharePackage("set:${s.uppercase()}", s.uppercase(), ShareKind.SET, cards = cardsOf(loose.filter { it.print?.setCode.equals(s, true) }, false))
            }
        }
        val deckIds = if (choice.all) decks.map { it.id }.toSet() else choice.decks
        decks.filter { it.id in deckIds }.sortedBy { it.name.lowercase() }.forEach { d ->
            out += SharePackage("deck:${d.name}", d.name, ShareKind.DECK, d.format, cardsOf(entries.filter { it.entry.deckId == d.id }, true))
        }
        // Verlanglijst: wat nog ontbreekt. "ANY" = elke print is goed.
        if (choice.wishlist) out += SharePackage("wish", "", ShareKind.WISH, cards = wishes.filter { it.item.wanted > it.item.got }.map { w ->
            ShareCard(
                id = w.item.printId, set = w.print?.setCode.orEmpty(), nr = w.print?.collectorNumber.orEmpty(), name = w.item.name,
                finish = if (w.item.foilOnly) nl.spoonpaco.ninegrid.data.db.Finish.FOIL else nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL,
                qty = w.item.wanted - w.item.got, section = if (w.item.anyPrint) "ANY" else null,
            )
        })
        return out.filter { it.cards.isNotEmpty() }
    }

    fun encode(f: ShareFile): String = json.encodeToString(ShareFile.serializer(), f)

    /** Leest een gedeeld bestand; null als het geen NineGrid-bestand is. */
    fun decode(text: String): ShareFile? = runCatching { json.decodeFromString(ShareFile.serializer(), text) }
        .getOrNull()?.takeIf { it.format == ShareFile.FORMAT && it.senderId.isNotBlank() }

    /** Veilige bestandsnaam: "NineGrid - Mick.ninegrid". */
    fun fileName(name: String) = "NineGrid - " + name.replace(Regex("""[\\/:*?"<>|]"""), "").trim().ifBlank { "collectie" } + ".ninegrid"

    fun count(packages: List<SharePackage>) = packages.sumOf { p -> p.cards.sumOf { it.qty } }
}
