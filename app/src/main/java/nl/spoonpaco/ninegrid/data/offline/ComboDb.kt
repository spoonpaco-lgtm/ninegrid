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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import java.io.File
import java.io.InputStream

/** Eén combo uit het bulkbestand van Commander Spellbook, alleen wat NineGrid gebruikt. */
@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey val id: String,
    /** Kaartnamen, één per regel. */
    val cards: String,
    val cardCount: Int,
    /** Wat de combo oplevert (features), één per regel. */
    val produces: String,
    /** Andere voorwaarden (templates, bijv. "een sac outlet"), één per regel. Die telt de app niet na. */
    val requires: String,
    /** Kleuren (WUBRG, C = kleurloos). */
    val identity: String,
    val prerequisites: String,
    val description: String,
    val popularity: Int,
    val bracketTag: String?,
    /** Formats waarin de combo legaal is (sleutels van Spellbook), komma's ertussen. */
    val legal: String,
    val gen: Long,
)

/** Welke kaarten in welke combo zitten: om snel te zoeken op de kaarten die je hebt. */
@Entity(tableName = "combo_cards", primaryKeys = ["comboId", "name"], indices = [Index("name"), Index("front")])
data class ComboCardEntity(val comboId: String, val name: String, val front: String)

@Dao
interface ComboDao {
    @Upsert suspend fun upsert(items: List<ComboEntity>)
    @Upsert suspend fun upsertCards(items: List<ComboCardEntity>)
    @Query("DELETE FROM combos WHERE gen != :gen") suspend fun deleteOtherThan(gen: Long)
    @Query("DELETE FROM combo_cards WHERE comboId NOT IN (SELECT id FROM combos)") suspend fun deleteOrphanCards()
    @Query("SELECT COUNT(*) FROM combos") suspend fun count(): Int
    @Query("SELECT DISTINCT legal FROM combos") suspend fun legalSets(): List<String>
}

@Database(entities = [ComboEntity::class, ComboCardEntity::class], version = 1, exportSchema = false)
abstract class ComboDatabase : RoomDatabase() {
    abstract fun combos(): ComboDao
}

/** Een combo zoals de schermen hem gebruiken: kaarten, wat ontbreekt en de rest. */
data class OwnedCombo(
    val id: String,
    val cards: List<String>,
    /** Kaarten die je niet hebt (0 = compleet, 1 = bijna). */
    val missing: List<String>,
    val produces: List<String>,
    val requires: List<String>,
    val identity: String,
    val prerequisites: String,
    val description: String,
    val popularity: Int,
    val bracketTag: String?,
    val legal: Set<String>,
)

/**
 * De combodatabase van Commander Spellbook op de telefoon (0.9.18). Eén keer het bulkbestand downloaden
 * (https://json.commanderspellbook.com/variants.json.gz, zoals Spellbook vraagt in plaats van de API leeg te halen),
 * daarna zoekt de telefoon zelf: welke combo's je compleet hebt en welke je één kaart missen.
 */
class ComboDb(private val context: Context) {
    private val file get() = context.getDatabasePath(FILE)
    @Volatile private var db: ComboDatabase? = null
    private val prefs get() = context.getSharedPreferences("combo_db", Context.MODE_PRIVATE)

    private fun open(): ComboDatabase = db ?: synchronized(this) {
        db ?: Room.databaseBuilder(context, ComboDatabase::class.java, FILE).fallbackToDestructiveMigration().build().also { db = it }
    }

    /** Is hij gedownload (en dus bruikbaar)? */
    fun ready(): Boolean = file.exists() && prefs.getLong("updated", 0L) > 0L
    val updated: Long get() = prefs.getLong("updated", 0L)
    val comboCount: Int get() = prefs.getInt("count", 0)
    /** Last-Modified van de laatste download (om niet opnieuw te downloaden als er niets nieuws is). */
    var lastModified: String?
        get() = prefs.getString("last_modified", null)
        set(v) { prefs.edit().putString("last_modified", v).apply() }

    /** Gekeken, niets nieuws: de datum bijwerken zodat hij pas over een week weer kijkt. */
    fun touch() = prefs.edit().putLong("updated", System.currentTimeMillis()).apply()

    fun sizeBytes(): Long = listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }

    suspend fun importFrom(input: InputStream, onCount: suspend (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val d = open()
        val gen = System.currentTimeMillis()
        var n = 0
        val batch = ArrayList<ComboEntity>(500)
        val cards = ArrayList<ComboCardEntity>(2000)
        suspend fun flush() {
            if (batch.isEmpty()) return
            val a = batch.toList(); val b = cards.toList(); batch.clear(); cards.clear()
            d.withTransaction { d.combos().upsert(a); d.combos().upsertCards(b) }
            n += a.size
            onCount(n)
        }
        for (v in variants(input)) {
            val e = entity(v, gen) ?: continue
            batch += e
            e.cards.split('\n').distinct().forEach { name -> cards += ComboCardEntity(e.id, name.lowercase(), name.substringBefore(" // ").lowercase()) }
            if (batch.size >= 500) flush()
        }
        flush()
        d.combos().deleteOtherThan(gen)
        d.combos().deleteOrphanCards()
        prefs.edit().putLong("updated", System.currentTimeMillis()).putInt("count", n).apply()
        n
    }

    /** Alles weg (uitzetten). */
    fun delete() {
        synchronized(this) { db?.close(); db = null }
        context.deleteDatabase(FILE)
        prefs.edit().clear().apply()
    }

    /** De formats die in de database voorkomen (voor het filter). */
    suspend fun formats(): List<String> = if (!ready()) emptyList() else withContext(Dispatchers.IO) {
        runCatching { open().combos().legalSets().flatMap { it.split(',') }.filter { it.isNotBlank() }.distinct() }.getOrDefault(emptyList())
    }

    /**
     * Combo's met de kaarten in [owned] (kleine letters; volledige naam en voorkant): compleet en met één kaart te weinig.
     * Rekent in SQLite: eerst de combo's waarin minstens één van jouw kaarten zit, dan per combo tellen.
     */
    suspend fun find(owned: Set<String>): List<OwnedCombo> = if (!ready() || owned.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
        val sql = open().openHelper.writableDatabase
        val out = ArrayList<OwnedCombo>()
        sql.beginTransaction()
        try {
            sql.execSQL("CREATE TEMP TABLE IF NOT EXISTS owned(name TEXT PRIMARY KEY)")
            sql.execSQL("DELETE FROM owned")
            val ins = sql.compileStatement("INSERT OR IGNORE INTO owned(name) VALUES (?)")
            for (n in owned) { ins.bindString(1, n); ins.executeInsert(); ins.clearBindings() }
            val q = """
                SELECT k.id, k.cards, k.produces, k.requires, k.identity, k.prerequisites, k.description, k.popularity, k.bracketTag, k.legal
                FROM combos k JOIN (
                    SELECT c.comboId AS cid, COUNT(*) AS total,
                        SUM(CASE WHEN c.name IN (SELECT name FROM owned) OR c.front IN (SELECT name FROM owned) THEN 1 ELSE 0 END) AS have
                    FROM combo_cards c
                    WHERE c.comboId IN (SELECT comboId FROM combo_cards WHERE name IN (SELECT name FROM owned) OR front IN (SELECT name FROM owned))
                    GROUP BY c.comboId HAVING total - have <= 1
                ) x ON x.cid = k.id
            """.trimIndent()
            sql.query(q).use { cur ->
                while (cur.moveToNext()) {
                    val cards = cur.getString(1).split('\n').filter { it.isNotBlank() }
                    out += OwnedCombo(
                        id = cur.getString(0), cards = cards,
                        missing = cards.filter { c -> c.lowercase() !in owned && c.substringBefore(" // ").lowercase() !in owned }.distinct(),
                        produces = cur.getString(2).split('\n').filter { it.isNotBlank() },
                        requires = cur.getString(3).split('\n').filter { it.isNotBlank() },
                        identity = cur.getString(4), prerequisites = cur.getString(5), description = cur.getString(6),
                        popularity = cur.getInt(7), bracketTag = if (cur.isNull(8)) null else cur.getString(8),
                        legal = cur.getString(9).split(',').filter { it.isNotBlank() }.toSet(),
                    )
                }
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        out
    }

    companion object {
        const val FILE = "combos.db"
        const val URL = "https://json.commanderspellbook.com/variants.json.gz"

        @Serializable data class SbCard(val name: String = "")
        @Serializable data class SbUse(val card: SbCard = SbCard(), val quantity: Int = 1)
        @Serializable data class SbTemplate(val name: String = "")
        @Serializable data class SbReq(val template: SbTemplate = SbTemplate(), val quantity: Int = 1)
        @Serializable data class SbFeature(val name: String = "")
        @Serializable data class SbProd(val feature: SbFeature = SbFeature())
        @Serializable data class SbVariant(
            val id: String = "",
            val status: String = "",
            val uses: List<SbUse> = emptyList(),
            val requires: List<SbReq> = emptyList(),
            val produces: List<SbProd> = emptyList(),
            val identity: String = "",
            val easyPrerequisites: String = "",
            val notablePrerequisites: String = "",
            val description: String = "",
            val popularity: Int = 0,
            val bracketTag: String? = null,
            val legalities: Map<String, Boolean> = emptyMap(),
        )

        /** Alleen gepubliceerde combo's met minstens twee kaarten (of één kaart plus voorwaarden). */
        fun entity(v: SbVariant, gen: Long): ComboEntity? {
            if (v.id.isBlank() || (v.status.isNotBlank() && v.status != "OK" && v.status != "E")) return null
            val cards = v.uses.map { it.card.name }.filter { it.isNotBlank() }.distinct()
            if (cards.isEmpty() || (cards.size == 1 && v.requires.isEmpty())) return null
            val pre = listOf(v.easyPrerequisites, v.notablePrerequisites).filter { it.isNotBlank() }.joinToString("\n")
            return ComboEntity(
                id = v.id, cards = cards.joinToString("\n"), cardCount = cards.size,
                produces = v.produces.map { it.feature.name }.filter { it.isNotBlank() }.distinct().joinToString("\n"),
                requires = v.requires.map { r -> (if (r.quantity > 1) "${r.quantity}× " else "") + r.template.name }.filter { it.isNotBlank() }.joinToString("\n"),
                identity = v.identity, prerequisites = pre, description = v.description,
                popularity = v.popularity, bracketTag = v.bracketTag,
                legal = v.legalities.filterValues { it }.keys.sorted().joinToString(","), gen = gen,
            )
        }

        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

        /** De combo's uit het bulkbestand, één voor één (het bestand is uitgepakt ~730 MB: nooit in zijn geheel). */
        @OptIn(ExperimentalSerializationApi::class)
        fun variants(raw: InputStream): Sequence<SbVariant> {
            val buffered = java.io.BufferedInputStream(raw, 1 shl 16)
            buffered.mark(2)
            val gz = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            val input = java.io.BufferedInputStream(if (gz) java.util.zip.GZIPInputStream(buffered, 1 shl 16) else buffered, 1 shl 16)
            return json.decodeToSequence(ArrayOfKey(input, "variants"), SbVariant.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
        }
    }
}

/**
 * Geeft van een groot JSON-object alleen de lijst onder [key] (op het bovenste niveau) door, als losse stroom:
 * van '[' tot en met de bijbehorende ']'. Daarna einde. Zo kan de JSON-lezer de lijst stuk voor stuk lezen.
 */
class ArrayOfKey(private val inner: InputStream, key: String) : InputStream() {
    private val target = key.toByteArray()
    private var started = false
    private var done = false
    // Zoeken
    private var depth = 0
    private var inString = false
    private var escape = false
    private val str = java.io.ByteArrayOutputStream()
    private var lastKey: ByteArray? = null
    private var afterColon = false
    // Doorgeven
    private var outDepth = 0
    private var outString = false
    private var outEscape = false

    private fun seek(): Boolean {
        while (true) {
            val b = inner.read()
            if (b < 0) return false
            if (inString) {
                if (escape) { escape = false; if (depth == 1) str.write(b); continue }
                if (b == '\\'.code) { escape = true; continue }
                if (b == '"'.code) { inString = false; if (depth == 1) { lastKey = str.toByteArray(); str.reset() }; continue }
                if (depth == 1) str.write(b)
                continue
            }
            when (b) {
                '"'.code -> { inString = true; str.reset(); afterColon = false }
                ':'.code -> afterColon = true
                '{'.code -> depth++
                '}'.code -> depth--
                '['.code -> {
                    if (depth == 1 && afterColon && lastKey?.contentEquals(target) == true) return true
                    depth++
                }
                ']'.code -> depth--
                ','.code -> { afterColon = false; lastKey = null }
            }
        }
    }

    override fun read(): Int {
        if (done) return -1
        if (!started) {
            if (!seek()) { done = true; return -1 }
            started = true; outDepth = 1
            return '['.code
        }
        val b = inner.read()
        if (b < 0) { done = true; return -1 }
        if (outString) {
            if (outEscape) outEscape = false
            else if (b == '\\'.code) outEscape = true
            else if (b == '"'.code) outString = false
        } else when (b) {
            '"'.code -> outString = true
            '['.code, '{'.code -> outDepth++
            ']'.code, '}'.code -> { outDepth--; if (outDepth == 0) done = true }
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var i = 0
        while (i < len) {
            val c = read()
            if (c < 0) break
            b[off + i] = c.toByte()
            i++
            if (done) break
        }
        return if (i == 0) -1 else i
    }

    override fun close() = inner.close()
}
