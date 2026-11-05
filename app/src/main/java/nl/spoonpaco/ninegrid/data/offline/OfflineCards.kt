package nl.spoonpaco.ninegrid.data.offline

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import nl.spoonpaco.ninegrid.data.scryfall.CardQuery
import nl.spoonpaco.ninegrid.data.scryfall.SfCard
import nl.spoonpaco.ninegrid.data.scryfall.SfImageUris
import nl.spoonpaco.ninegrid.data.scryfall.SfPrices
import nl.spoonpaco.ninegrid.data.scryfall.SfPurchase
import java.io.File
import java.io.InputStream

/**
 * Eén kaart uit de offline kaartdatabase: alleen wat nodig is om een kaart te herkennen en op te slaan.
 * Regeltekst en legaliteit ontbreken bewust (scheelt veel ruimte); die vult de dagelijkse prijsupdate aan.
 */
@Entity(tableName = "cards", indices = [Index(value = ["setCode", "number"]), Index("front"), Index("name")])
data class OfflineCard(
    @PrimaryKey val id: String,
    val oracleId: String,
    val name: String,
    /** Naam in kleine letters, en de voorkant bij dubbelzijdige kaarten, om op te zoeken. */
    val front: String,
    val setCode: String,
    val setName: String,
    val number: String,
    val rarity: String,
    val typeLine: String,
    val manaCost: String,
    val cmc: Double,
    val colors: String,
    val colorIdentity: String,
    val producedMana: String,
    val imageSmall: String?,
    val imageNormal: String?,
    val scryfallUri: String?,
    val cardmarketUri: String?,
    val eur: String?, val eurFoil: String?, val usd: String?, val usdFoil: String?, val usdEtched: String?,
    val releasedAt: String,
    /** Welke download deze rij schreef; oude rijen gaan na een geslaagde download weg. */
    val gen: Long,
) {
    fun toSfCard() = SfCard(
        id = id, oracleId = oracleId, name = name, set = setCode, setName = setName, collectorNumber = number,
        rarity = rarity, typeLine = typeLine, manaCost = manaCost, cmc = cmc,
        colors = colors.map { it.toString() }, colorIdentity = colorIdentity.map { it.toString() },
        imageUris = SfImageUris(imageSmall, imageNormal), scryfallUri = scryfallUri, purchaseUris = SfPurchase(cardmarketUri, null),
        prices = SfPrices(eur, eurFoil, usd, usdFoil, usdEtched), releasedAt = releasedAt,
        producedMana = producedMana.map { it.toString() },
    )

    companion object {
        fun from(c: SfCard, gen: Long): OfflineCard {
            val p = c.toPrint()
            return OfflineCard(
                p.id, p.oracleId, p.name, p.name.substringBefore(" // ").lowercase(), c.set.lowercase(), p.setName, p.collectorNumber.lowercase(),
                p.rarity, p.typeLine, p.manaCost, p.cmc, p.colors, p.colorIdentity, p.producedMana, p.imageSmall, p.imageNormal,
                p.scryfallUri, p.cardmarketUri, c.prices.eur, c.prices.eurFoil, c.prices.usd, c.prices.usdFoil, c.prices.usdEtched, p.releasedAt, gen,
            )
        }
    }
}

@Dao
interface OfflineDao {
    @Upsert
    suspend fun upsert(items: List<OfflineCard>)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: String): OfflineCard?

    @Query("SELECT * FROM cards WHERE setCode = :set AND number = :number LIMIT 1")
    suspend fun bySetNumber(set: String, number: String): OfflineCard?

    /** Op naam (of voorkant), het liefst in de gegeven set, anders de nieuwste print. */
    @Query("SELECT * FROM cards WHERE (front = :name OR LOWER(name) = :name) AND (:set IS NULL OR setCode = :set) ORDER BY releasedAt DESC LIMIT 1")
    suspend fun byName(name: String, set: String?): OfflineCard?

    @Query("DELETE FROM cards WHERE gen != :gen")
    suspend fun deleteOtherThan(gen: Long)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int
}

@Database(entities = [OfflineCard::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun cards(): OfflineDao
}

/**
 * De offline kaartdatabase: een eigen databasebestand, los van je collectie, zodat uitzetten alles
 * in één keer weghaalt. Zoeken kan alleen als hij aan staat en gevuld is; anders altijd null.
 */
class OfflineCards(private val context: Context) {
    private val file get() = context.getDatabasePath(FILE)
    @Volatile private var db: OfflineDatabase? = null
    /** Uit de instellingen: staat de optie aan. */
    @Volatile var enabled = false

    private fun open(): OfflineDatabase = db ?: synchronized(this) {
        db ?: Room.databaseBuilder(context, OfflineDatabase::class.java, FILE).fallbackToDestructiveMigration().build().also { db = it }
    }

    private fun ready() = enabled && file.exists()

    suspend fun find(q: CardQuery): SfCard? {
        if (!ready()) return null
        val d = open().cards()
        return runCatching {
            when {
                q.id != null -> d.byId(q.id)
                q.setCode != null && q.collectorNumber != null -> d.bySetNumber(q.setCode.lowercase(), q.collectorNumber.lowercase())
                q.name != null -> d.byName(q.name.substringBefore(" // ").lowercase(), q.setCode?.lowercase())
                else -> null
            }?.toSfCard()
        }.getOrNull()
    }

    suspend fun count(): Int = if (!file.exists()) 0 else runCatching { open().cards().count() }.getOrDefault(0)

    /** Ruimte op de telefoon in bytes. */
    fun sizeBytes(): Long = listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }

    /**
     * Leest de bulk-download van Scryfall stuk voor stuk in (nooit het hele bestand in het geheugen) en schrijft per
     * 1.000 kaarten. Alleen als alles gelukt is, gaan de kaarten van de vorige download weg.
     */
    suspend fun importFrom(input: InputStream, onCount: suspend (Int) -> Unit): Int {
        val d = open()
        val gen = System.currentTimeMillis()
        var n = 0
        val batch = ArrayList<OfflineCard>(1000)
        for (c in cards(input)) {
            if (c.digital) continue
            batch += OfflineCard.from(c, gen)
            if (batch.size >= 1000) {
                val part = batch.toList(); batch.clear()
                d.withTransaction { d.cards().upsert(part) }
                n += part.size
                onCount(n)
            }
        }
        if (batch.isNotEmpty()) { d.withTransaction { d.cards().upsert(batch.toList()) }; n += batch.size }
        d.cards().deleteOtherThan(gen)
        return n
    }

    /** Uitzetten: het hele bestand weg. */
    fun delete() {
        synchronized(this) { db?.close(); db = null }
        context.deleteDatabase(FILE)
    }

    companion object {
        const val FILE = "offline-cards.db"

        /**
         * Kaarten uit een bulk-bestand, één voor één. Scryfall levert nu JSON Lines (één kaart per regel), ingepakt
         * met gzip; vroeger één grote JSON-lijst. Beide worden herkend, zodat een wijziging bij Scryfall niet meteen breekt.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun cards(raw: InputStream): Sequence<SfCard> {
            val buffered = java.io.BufferedInputStream(raw, 1 shl 16)
            buffered.mark(2)
            val gz = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            val input = java.io.BufferedInputStream(if (gz) java.util.zip.GZIPInputStream(buffered, 1 shl 16) else buffered, 1 shl 16)
            input.mark(64)
            var first = input.read()
            while (first == ' '.code || first == '\n'.code || first == '\r'.code || first == '\t'.code || first == 0xEF || first == 0xBB || first == 0xBF) first = input.read()
            input.reset()
            return if (first == '['.code) json.decodeToSequence(input, SfCard.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
            else input.bufferedReader().lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                runCatching { json.decodeFromString(SfCard.serializer(), line) }.getOrNull()
            }
        }
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
