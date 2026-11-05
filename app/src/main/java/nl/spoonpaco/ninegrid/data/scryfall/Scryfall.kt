package nl.spoonpaco.ninegrid.data.scryfall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.data.db.CardPrint
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class SfImageUris(val small: String? = null, val normal: String? = null)

@Serializable
data class SfFace(
    @SerialName("image_uris") val imageUris: SfImageUris? = null,
    @SerialName("mana_cost") val manaCost: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
)

@Serializable
data class SfPrices(
    val eur: String? = null,
    @SerialName("eur_foil") val eurFoil: String? = null,
    val usd: String? = null,
    @SerialName("usd_foil") val usdFoil: String? = null,
    @SerialName("usd_etched") val usdEtched: String? = null,
)

@Serializable
data class SfPurchase(val cardmarket: String? = null, val tcgplayer: String? = null)

@Serializable
data class SfCard(
    val id: String,
    @SerialName("oracle_id") val oracleId: String? = null,
    val name: String,
    val set: String,
    @SerialName("set_name") val setName: String = "",
    @SerialName("collector_number") val collectorNumber: String,
    val rarity: String = "",
    @SerialName("type_line") val typeLine: String = "",
    @SerialName("mana_cost") val manaCost: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val cmc: Double = 0.0,
    val colors: List<String>? = null,
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    @SerialName("image_uris") val imageUris: SfImageUris? = null,
    @SerialName("card_faces") val cardFaces: List<SfFace>? = null,
    @SerialName("scryfall_uri") val scryfallUri: String? = null,
    @SerialName("purchase_uris") val purchaseUris: SfPurchase? = null,
    val prices: SfPrices = SfPrices(),
    val legalities: Map<String, String> = emptyMap(),
    @SerialName("game_changer") val gameChanger: Boolean = false,
    @SerialName("released_at") val releasedAt: String = "",
    @SerialName("produced_mana") val producedMana: List<String>? = null,
) {
    fun toPrint(): CardPrint {
        val img = imageUris ?: cardFaces?.firstOrNull()?.imageUris
        val order = "WUBRG"
        fun sortColors(c: List<String>) = c.sortedBy { order.indexOf(it) }.joinToString("")
        return CardPrint(
            id = id,
            oracleId = oracleId ?: "",
            name = name,
            setCode = set.uppercase(),
            setName = setName,
            collectorNumber = collectorNumber,
            rarity = rarity,
            typeLine = typeLine,
            manaCost = manaCost ?: cardFaces?.firstOrNull()?.manaCost ?: "",
            oracleText = oracleText ?: cardFaces?.mapNotNull { it.oracleText }?.joinToString("\n//\n") ?: "",
            cmc = cmc,
            colors = sortColors(colors ?: emptyList()),
            colorIdentity = sortColors(colorIdentity),
            producedMana = sortColors(producedMana.orEmpty().filter { it in listOf("W", "U", "B", "R", "G", "C") }),
            imageSmall = img?.small,
            imageNormal = img?.normal,
            scryfallUri = scryfallUri,
            cardmarketUri = purchaseUris?.cardmarket,
            tcgplayerUri = purchaseUris?.tcgplayer,
            eur = prices.eur?.toDoubleOrNull(),
            eurFoil = prices.eurFoil?.toDoubleOrNull(),
            usd = prices.usd?.toDoubleOrNull(),
            usdFoil = prices.usdFoil?.toDoubleOrNull(),
            usdEtched = prices.usdEtched?.toDoubleOrNull(),
            legalities = legalities.entries.joinToString(",") { "${it.key}=${it.value}" },
            gameChanger = gameChanger,
            releasedAt = releasedAt,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

@Serializable
data class SfList(val data: List<SfCard> = emptyList(), @SerialName("has_more") val hasMore: Boolean = false, @SerialName("next_page") val nextPage: String? = null)

@Serializable
data class SfCollection(val data: List<SfCard> = emptyList(), @SerialName("not_found") val notFound: List<JsonObject> = emptyList())

@Serializable
data class SfCatalog(val data: List<String> = emptyList())

@Serializable
data class SfSetList(val data: List<SfSet> = emptyList())

@Serializable
data class SfSet(
    val code: String,
    val name: String = "",
    @SerialName("card_count") val cardCount: Int = 0,
    @SerialName("printed_size") val printedSize: Int? = null,
    @SerialName("released_at") val releasedAt: String? = null,
)

/** Eén vraag aan Scryfall: op set en nummer, of alleen op naam. */
@Serializable
data class CardQuery(
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val name: String? = null,
    val id: String? = null,
    /** Andere gelezen regels die de naam kunnen zijn; alleen geaccepteerd als de kaart in [setCode] bestaat. */
    val altNames: List<String> = emptyList(),
)

class ScryfallException(message: String, val code: Int = 0) : Exception(message)

/**
 * Toegang tot Scryfall (gratis, geen sleutel nodig). Scryfall stelt grenzen
 * (https://scryfall.com/docs/api/rate-limits): /cards/search, /cards/named, /cards/random en /cards/collection
 * hoogstens 2 keer per seconde, al het andere 10 keer per seconde. Bij een weigering (429) is de app
 * 30 seconden geblokkeerd; dan wachten we die tijd en proberen het één keer opnieuw.
 */
class ScryfallClient {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gate = Mutex()
    private var lastCall = 0L
    private var lastSlowCall = 0L

    /** Scans hebben voorrang: zolang er een camerascherm open is of een scan wordt opgezocht, wacht de achtergrond. */
    private val foreground = java.util.concurrent.atomic.AtomicInteger(0)
    fun holdForeground() { foreground.incrementAndGet() }
    fun releaseForeground() { foreground.updateAndGet { (it - 1).coerceAtLeast(0) } }

    /** Wacht (hoogstens [maxMs]) tot er geen scan meer bezig is. Voor de prijsupdate op de achtergrond. */
    suspend fun yieldToForeground(maxMs: Long = 30 * 60_000L) {
        val until = System.currentTimeMillis() + maxMs
        while (foreground.get() > 0 && System.currentTimeMillis() < until) delay(1000)
    }

    /** Wordt aangeroepen als Scryfall om pauze vraagt, zodat de app een melding kan tonen. */
    var onThrottled: (() -> Unit)? = null

    /** Geduldige stand (importeren): bij een weigering langer wachten en vaker opnieuw proberen, nooit opgeven. */
    private val patient = java.util.concurrent.atomic.AtomicInteger(0)
    fun holdPatient() { patient.incrementAndGet() }
    fun releasePatient() { patient.updateAndGet { (it - 1).coerceAtLeast(0) } }

    /** Tot wanneer we wachten na een weigering (millis), voor een aftelling op het scherm. 0 = niet aan het wachten. */
    @Volatile var throttledUntil = 0L
        private set

    private fun isSlow(url: String) = listOf("/cards/search", "/cards/named", "/cards/random", "/cards/collection").any { url.contains(it) }

    private suspend fun call(request: Request): String? = withContext(Dispatchers.IO) {
        val waits = if (patient.get() > 0) listOf(30_000L, 60_000L, 120_000L, 120_000L, 240_000L) else listOf(30_000L)
        for (attempt in 0..waits.size) {
            val slow = isSlow(request.url.encodedPath)
            gate.withLock {
                val now = System.currentTimeMillis()
                val wait = maxOf(110 - (now - lastCall), if (slow) 520 - (now - lastSlowCall) else 0)
                if (wait > 0) delay(wait)
                lastCall = System.currentTimeMillis()
                if (slow) lastSlowCall = lastCall
            }
            val req = request.newBuilder()
                .header("User-Agent", "NineGrid/${BuildConfig.VERSION_NAME} (+https://github.com/spoonpaco-lgtm/ninegrid)")
                .header("Accept", "application/json")
                .build()
            val result: Pair<Int, String?> = http.newCall(req).execute().use { resp ->
                resp.code to (if (resp.isSuccessful) resp.body?.string() else null)
            }
            when {
                result.first in 200..299 -> return@withContext result.second
                result.first == 404 -> return@withContext null
                result.first == 429 && attempt < waits.size -> {
                    val pause = waits[attempt]
                    throttledUntil = System.currentTimeMillis() + pause
                    onThrottled?.invoke()
                    // Iedereen wacht: ook andere verzoeken mogen in deze tijd niet.
                    gate.withLock { delay(pause); lastCall = System.currentTimeMillis(); lastSlowCall = lastCall }
                    throttledUntil = 0L
                }
                else -> throw ScryfallException("Scryfall ${result.first}", result.first)
            }
        }
        throw ScryfallException("Scryfall 429", 429)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun cardById(id: String): SfCard? {
        val body = call(Request.Builder().url("https://api.scryfall.com/cards/${enc(id)}").build())
        return body?.let { json.decodeFromString(SfCard.serializer(), it) }
    }

    suspend fun cardBySetNumber(set: String, number: String): SfCard? {
        val body = call(Request.Builder().url("https://api.scryfall.com/cards/${enc(set.lowercase())}/${enc(number)}").build())
        return body?.let { json.decodeFromString(SfCard.serializer(), it) }
    }

    suspend fun cardByName(name: String, set: String? = null): SfCard? {
        val setPart = if (set != null) "&set=${enc(set.lowercase())}" else ""
        val body = call(Request.Builder().url("https://api.scryfall.com/cards/named?exact=${enc(name)}$setPart").build())
            ?: call(Request.Builder().url("https://api.scryfall.com/cards/named?fuzzy=${enc(name)}").build())
        return body?.let { json.decodeFromString(SfCard.serializer(), it) }
    }

    suspend fun autocomplete(q: String): List<String> {
        if (q.length < 2) return emptyList()
        val body = call(Request.Builder().url("https://api.scryfall.com/cards/autocomplete?q=${enc(q)}").build()) ?: return emptyList()
        return json.decodeFromString(SfCatalog.serializer(), body).data
    }

    suspend fun set(code: String): SfSet? {
        val body = call(Request.Builder().url("https://api.scryfall.com/sets/${enc(code.lowercase())}").build()) ?: return null
        return json.decodeFromString(SfSet.serializer(), body)
    }

    /** Alle sets (voor setnamen omzetten naar codes, bijv. "Modern Horizons" naar MH1). */
    suspend fun allSets(): List<SfSet> {
        val body = call(Request.Builder().url("https://api.scryfall.com/sets").build()) ?: return emptyList()
        return json.decodeFromString(SfSetList.serializer(), body).data
    }

    /** Alle kaarten van een set (alle prints, ook varianten), in setvolgorde. */
    suspend fun cardsOfSet(code: String): List<SfCard> {
        val q = enc("set:${code.lowercase()}")
        val out = mutableListOf<SfCard>()
        var url: String? = "https://api.scryfall.com/cards/search?q=$q&unique=prints&order=set&include_extras=true&include_variations=true"
        var pages = 0
        while (url != null && pages < 15) {
            val body = call(Request.Builder().url(url).build()) ?: break
            val list = json.decodeFromString(SfList.serializer(), body)
            out += list.data
            url = if (list.hasMore) list.nextPage else null
            pages++
        }
        return out
    }

    /** Alle kaartnamen (ongeveer 35.000), voor het verbeteren van gelezen namen zonder internet. */
    suspend fun cardNames(): List<String> {
        val body = call(Request.Builder().url("https://api.scryfall.com/catalog/card-names").build()) ?: return emptyList()
        return json.decodeFromString(SfCatalog.serializer(), body).data
    }

    /** Alle prints van een kaart, nieuwste eerst. */
    suspend fun printsOf(name: String): List<SfCard> {
        val q = enc("!\"$name\"")
        val out = mutableListOf<SfCard>()
        var url: String? = "https://api.scryfall.com/cards/search?q=$q&unique=prints&order=released&dir=desc"
        var pages = 0
        while (url != null && pages < 4) {
            val body = call(Request.Builder().url(url).build()) ?: break
            val list = json.decodeFromString(SfList.serializer(), body)
            out += list.data
            url = if (list.hasMore) list.nextPage else null
            pages++
        }
        return out
    }

    /**
     * Haalt veel kaarten tegelijk op, 75 per verzoek. Geeft per vraag het gevonden kaartobject terug,
     * of null als Scryfall hem niet kent.
     */
    /** Kan Scryfall met deze vraag iets in een collection-verzoek? (Een lege vraag laat het hele verzoek mislukken.) */
    private fun usable(q: CardQuery) = q.id != null || (q.setCode != null && q.collectorNumber != null) || q.name != null

    suspend fun collection(queries: List<CardQuery>, background: Boolean = false): List<SfCard?> {
        val results = arrayOfNulls<SfCard>(queries.size)
        val idx = queries.indices.filter { usable(queries[it]) }
        idx.chunked(75).forEach { chunkIdx ->
            if (background) yieldToForeground()
            val chunk = chunkIdx.map { queries[it] }
            val payload = buildJsonObject {
                put("identifiers", buildJsonArray {
                    chunk.forEach { q ->
                        add(buildJsonObject {
                            if (q.id != null) {
                                put("id", q.id)
                            } else if (q.setCode != null && q.collectorNumber != null) {
                                put("set", q.setCode.lowercase())
                                put("collector_number", q.collectorNumber)
                            } else if (q.name != null) {
                                put("name", q.name)
                                if (q.setCode != null) put("set", q.setCode.lowercase())
                            }
                        })
                    }
                })
            }
            val body = try {
                call(
                    Request.Builder().url("https://api.scryfall.com/cards/collection")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                )
            } catch (e: ScryfallException) {
                if (e.code == 429) throw e
                null // Scryfall weigert het verzoek: de kaarten worden daarna één voor één gezocht.
            } ?: return@forEach
            val coll = json.decodeFromString(SfCollection.serializer(), body)
            // Scryfall geeft gevonden kaarten in volgorde terug, zonder de niet-gevonden vragen.
            val found = coll.data.toMutableList()
            chunk.forEachIndexed { i, q ->
                val match = found.firstOrNull { c -> matches(q, c) }
                if (match != null) {
                    results[chunkIdx[i]] = match
                    found.remove(match)
                }
            }
        }
        return results.toList()
    }

    private fun matches(q: CardQuery, c: SfCard): Boolean =
        if (q.id != null) c.id == q.id
        else if (q.setCode != null && q.collectorNumber != null)
            c.set.equals(q.setCode, true) && c.collectorNumber.equals(q.collectorNumber, true)
        else if (q.name != null)
            c.name.equals(q.name, true) || c.name.substringBefore(" // ").equals(q.name.substringBefore(" // "), true)
        else false
}
