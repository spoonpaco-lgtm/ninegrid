package nl.spoonpaco.ninegrid.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.data.scryfall.ScryfallClient
import nl.spoonpaco.ninegrid.data.scryfall.SfCard
import java.io.File

/** Eén kaart uit een setlijst, zo klein mogelijk bewaard. */
@Serializable
data class SetCard(
    val id: String,
    val name: String,
    val number: String,
    val rarity: String,
    val eur: Double? = null,
    val usd: Double? = null,
    val image: String? = null,
    /** Hoort bij de gewone setlijst (geen borderless, showcase, extended art of promo). */
    val regular: Boolean = true,
)

@Serializable
data class SetList(val code: String, val name: String, val printedSize: Int, val fetchedAt: Long, val cards: List<SetCard>)

/**
 * Setlijsten en de namenlijst van Scryfall, op de telefoon bewaard. Een setlijst wordt een week bewaard,
 * de namenlijst (ongeveer 0,7 MB) wordt één keer per week vernieuwd.
 */
class CardCache(private val context: Context, private val scryfall: ScryfallClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.filesDir, "scryfall").apply { mkdirs() }
    private val week = 7L * 24 * 3600 * 1000
    private val lock = Mutex()
    private val sets = mutableMapOf<String, SetList>()
    @Volatile private var names: List<String>? = null
    @Volatile private var normalized: Map<String, String>? = null
    /** Namen per lengte (genormaliseerd), zodat verbeteren alleen namen van ongeveer dezelfde lengte bekijkt. */
    @Volatile private var byLength: Map<Int, List<Pair<String, String>>>? = null

    private fun isRegular(c: SfCard, printed: Int): Boolean {
        val n = c.collectorNumber.toIntOrNull() ?: return false
        return n <= printed
    }

    /** De lijst van een set, uit de opslag of (eens per week) van Scryfall. */
    suspend fun setList(code: String, refresh: Boolean = false): SetList? = lock.withLock {
        val key = code.lowercase()
        sets[key]?.takeIf { !refresh && System.currentTimeMillis() - it.fetchedAt < week }?.let { return it }
        val file = File(dir, "set-$key.json")
        if (!refresh) withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(SetList.serializer(), file.readText()) }.getOrNull()
        }?.takeIf { System.currentTimeMillis() - it.fetchedAt < week }?.let { sets[key] = it; return it }
        val info = scryfall.set(key) ?: return null
        val cards = scryfall.cardsOfSet(key)
        val printed = info.printedSize ?: info.cardCount
        val list = SetList(
            code = info.code.uppercase(), name = info.name, printedSize = printed, fetchedAt = System.currentTimeMillis(),
            cards = cards.map { c ->
                val p = c.toPrint()
                SetCard(c.id, c.name, c.collectorNumber, c.rarity, p.eur, p.usd, p.imageSmall, isRegular(c, printed))
            },
        )
        withContext(Dispatchers.IO) { runCatching { file.writeText(json.encodeToString(SetList.serializer(), list)) } }
        sets[key] = list
        list
    }

    /** Alleen wat al op de telefoon staat (geen internet). */
    fun cachedSetList(code: String): SetList? = sets[code.lowercase()] ?: runCatching {
        json.decodeFromString(SetList.serializer(), File(dir, "set-${code.lowercase()}.json").readText())
    }.getOrNull()?.also { sets[code.lowercase()] = it }

    private val namesFile get() = File(dir, "card-names.json")

    /**
     * De namenlijst. Zit in de app zelf (door de bouwstraat meegegeven), dus werkt meteen en offline.
     * Eens per week kijkt de app of er sinds die lijst nieuwe sets zijn; alleen dan haalt hij de lijst opnieuw op.
     */
    suspend fun ensureNames() {
        if (names == null) loadNames()
        val f = namesFile
        val checkedFile = File(dir, "names-checked")
        if (System.currentTimeMillis() - checkedFile.lastModified() < week) return
        runCatching { checkedFile.writeText("") }
        val newest = runCatching { scryfall.allSets() }.getOrNull()?.mapNotNull { it.releasedAt }?.filter { it <= java.time.LocalDate.now().toString() }?.maxOrNull()
        val listDate = if (f.exists()) java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else bundledDate()
        if (newest != null && newest <= listDate) return
        val list = runCatching { scryfall.cardNames() }.getOrNull()
        if (!list.isNullOrEmpty()) {
            withContext(Dispatchers.IO) { runCatching { f.writeText(json.encodeToString(ListSerializer(String.serializer()), list)) } }
            setNames(list)
        }
    }

    /** Datum van de app zelf (en dus van de ingebouwde lijst). */
    private fun bundledDate(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        java.time.Instant.ofEpochMilli(info.lastUpdateTime).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }.getOrDefault("2000-01-01")

    private suspend fun loadNames() = withContext(Dispatchers.IO) {
        // Eerst de opgehaalde lijst, anders de ingebouwde.
        val fromFile = runCatching { json.decodeFromString(ListSerializer(String.serializer()), namesFile.readText()) }.getOrNull()
        val list = fromFile ?: runCatching {
            context.assets.open("card-names.json").use { s -> json.decodeFromString(nl.spoonpaco.ninegrid.data.scryfall.SfCatalog.serializer(), s.readBytes().decodeToString()).data }
        }.getOrNull()
        if (!list.isNullOrEmpty()) setNames(list)
    }

    /** Alle sets (naam → code), uit de app zelf of van Scryfall. Voor het omzetten van setnamen bij importeren. */
    suspend fun setCodesByName(): Map<String, String> = withContext(Dispatchers.IO) {
        val bundled = runCatching {
            context.assets.open("sets.json").use { s -> json.decodeFromString(nl.spoonpaco.ninegrid.data.scryfall.SfSetList.serializer(), s.readBytes().decodeToString()).data }
        }.getOrDefault(emptyList())
        val live = if (bundled.isEmpty()) runCatching { scryfall.allSets() }.getOrDefault(emptyList()) else emptyList()
        (bundled + live).associate { it.name.lowercase() to it.code.uppercase() }
    }

    private fun setNames(list: List<String>) {
        names = list
        normalized = list.associateBy { norm(it) }
        byLength = list.map { norm(it) to it }.groupBy { it.first.length }
    }

    /** Is dit letterlijk een kaartnaam (zonder op hoofdletters en leestekens te letten)? */
    fun isExactName(read: String): Boolean = normalized?.containsKey(norm(read)) == true

    val namesLoaded get() = names != null

    private fun norm(s: String) = s.substringBefore(" // ").lowercase().filter { it.isLetterOrDigit() }

    /**
     * Zoekt de echte kaartnaam bij een gelezen naam met tikfouten. Eerst exact (zonder leestekens), dan de
     * dichtstbijzijnde naam met hoogstens een kwart verschil. Geeft null als niets duidelijk past.
     */
    fun correctName(read: String): String? {
        val map = normalized ?: return null
        val buckets = byLength ?: return null
        val n = norm(read)
        if (n.length < 3) return null
        // Klopt de naam al, dan meteen klaar (de meeste gevallen).
        map[n]?.let { return it }
        val maxDist = (n.length / 4).coerceAtLeast(1)
        var best: String? = null; var bestD = Int.MAX_VALUE
        // Alleen namen van ongeveer dezelfde lengte, en met dezelfde eerste of laatste letter.
        for (len in (n.length - maxDist)..(n.length + maxDist)) {
            for ((m, name) in buckets[len].orEmpty()) {
                if (m[0] != n[0] && m[m.length - 1] != n[n.length - 1]) continue
                val d = distance(n, m, maxDist)
                if (d < bestD) { bestD = d; best = name; if (d == 0) return name }
            }
        }
        return if (bestD <= maxDist) best else null
    }

    /** Levenshtein-afstand, stopt vroeg zodra hij boven [limit] komt. */
    private fun distance(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }
}
