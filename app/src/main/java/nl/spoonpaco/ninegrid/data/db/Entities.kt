package nl.spoonpaco.ninegrid.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Eén print van een kaart, zoals Scryfall hem kent. Gedeeld door alle exemplaren van die print. */
@Serializable
@Entity(tableName = "prints", indices = [Index(value = ["setCode", "collectorNumber"])])
data class CardPrint(
    @PrimaryKey val id: String,
    val oracleId: String = "",
    val name: String,
    val setCode: String,
    val setName: String = "",
    val collectorNumber: String,
    val rarity: String = "",
    val typeLine: String = "",
    val manaCost: String = "",
    val cmc: Double = 0.0,
    val colors: String = "",
    /** Regeltekst, voor de bracket-signalen (landvernietiging, extra beurten, tutors). */
    val oracleText: String = "",
    val colorIdentity: String = "",
    val imageSmall: String? = null,
    val imageNormal: String? = null,
    val scryfallUri: String? = null,
    val cardmarketUri: String? = null,
    val tcgplayerUri: String? = null,
    val eur: Double? = null,
    val eurFoil: Double? = null,
    val usd: Double? = null,
    val usdFoil: Double? = null,
    val usdEtched: Double? = null,
    val legalities: String = "",
    val gameChanger: Boolean = false,
    val releasedAt: String = "",
    val updatedAt: Long = 0,
)

object Finish {
    const val NONFOIL = "nonfoil"
    const val FOIL = "foil"
    const val ETCHED = "etched"
}

object Section {
    const val COMMANDER = "COMMANDER"
    const val MAIN = "MAIN"
    const val SIDEBOARD = "SIDEBOARD"
}

/**
 * Een stapel gelijke exemplaren op één plek. Een exemplaar staat altijd op precies één plek:
 * in een deck (deckId gevuld) of in de collectie (deckId leeg, eventueel met map, blad en vak).
 * Zo kan een kaart nooit dubbel geteld worden.
 */
@Serializable
@Entity(tableName = "entries", indices = [Index("printId"), Index("deckId")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val printId: String,
    val finish: String = Finish.NONFOIL,
    val language: String = "",
    val condition: String = "NM",
    val proxy: Boolean = false,
    val quantity: Int = 1,
    val deckId: Long? = null,
    val section: String? = null,
    val binder: String? = null,
    val page: Int? = null,
    val slot: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

object DeckFormat {
    const val COMMANDER = "commander"
    const val MODERN = "modern"
    const val PIONEER = "pioneer"
    const val STANDARD = "standard"
    const val LEGACY = "legacy"
    const val PAUPER = "pauper"
    const val CASUAL = "casual"
    val all = listOf(COMMANDER, MODERN, PIONEER, STANDARD, LEGACY, PAUPER, CASUAL)
}

@Serializable
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val format: String = DeckFormat.CASUAL,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Eén wijziging, met alles wat nodig is om hem ongedaan te maken. */
@Entity(tableName = "changes")
data class ChangeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val undoJson: String,
    val reverted: Boolean = false,
)

@Serializable
data class UndoData(
    val restoreEntries: List<Entry> = emptyList(),
    val deleteEntryIds: List<Long> = emptyList(),
    val restoreDecks: List<Deck> = emptyList(),
    val deleteDeckIds: List<Long> = emptyList(),
)

/** Een exemplaar met zijn print erbij, voor lijsten. */
data class EntryWithPrint(
    @androidx.room.Embedded val entry: Entry,
    @androidx.room.Relation(parentColumn = "printId", entityColumn = "id")
    val print: CardPrint?,
)

/**
 * Momentopname van één dag voor één lijn in de grafiek: totaal, sets, decks, een deck (deck:ID) of een set (set:CODE).
 * Per dag blijft de laatste stand bewaard. Waarde in beide valuta, zodat wisselen van valuta de historie niet breekt.
 */
@Serializable
@Entity(tableName = "snapshots", primaryKeys = ["day", "key"])
data class Snapshot(
    val day: String,
    val key: String,
    val label: String,
    val eur: Double,
    val usd: Double,
    val count: Int,
)

/** Momenten binnen een dag (bij elke wijziging en prijsupdate), voor 1D, 1W en 1M. Na een maand opgeruimd. */
@Entity(tableName = "moments", primaryKeys = ["ts", "key"])
data class Moment(
    val ts: Long,
    val key: String,
    val eur: Double,
    val usd: Double,
    val count: Int,
)

/** Prijs van één print op één dag, voor het koersverschil. Een maand per dag, daarna één per maand. */
@Serializable
@Entity(tableName = "price_history", primaryKeys = ["printId", "day"])
data class PricePoint(
    val printId: String,
    val day: String,
    val eur: Double?,
    val eurFoil: Double?,
    val usd: Double?,
    val usdFoil: Double?,
    val usdEtched: Double?,
)
