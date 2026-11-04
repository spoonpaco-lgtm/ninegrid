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
data class SfFace(@SerialName("image_uris") val imageUris: SfImageUris? = null, @SerialName("mana_cost") val manaCost: String? = null)

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
            cmc = cmc,
            colors = sortColors(colors ?: emptyList()),
            colorIdentity = sortColors(colorIdentity),
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

/** Eén vraag aan Scryfall: op set en nummer, of alleen op naam. */
data class CardQuery(
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val name: String? = null,
    val id: String? = null,
    /** Andere gelezen regels die de naam kunnen zijn; alleen geaccepteerd als de kaart in [setCode] bestaat. */
    val altNames: List<String> = emptyList(),
)

class ScryfallException(message: String) : Exception(message)

/**
 * Toegang tot Scryfall (gratis, geen sleutel nodig). Scryfall vraagt om rustig aan te doen:
 * hoogstens 10 verzoeken per seconde, met een eigen User-Agent.
 */
class ScryfallClient {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gate = Mutex()
    private var lastCall = 0L

    private suspend fun call(request: Request): String? = withContext(Dispatchers.IO) {
        gate.withLock {
            val wait = 110 - (System.currentTimeMillis() - lastCall)
            if (wait > 0) delay(wait)
            lastCall = System.currentTimeMillis()
        }
        val req = request.newBuilder()
            .header("User-Agent", "NineGrid/${BuildConfig.VERSION_NAME} (+https://github.com/spoonpaco-lgtm/ninegrid)")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            when {
                resp.isSuccessful -> resp.body?.string()
                resp.code == 404 -> null
                else -> throw ScryfallException("Scryfall ${resp.code}")
            }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

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
    suspend fun collection(queries: List<CardQuery>): List<SfCard?> {
        val results = arrayOfNulls<SfCard>(queries.size)
        queries.chunked(75).forEachIndexed { chunkIndex, chunk ->
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
            val body = call(
                Request.Builder().url("https://api.scryfall.com/cards/collection")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ) ?: return@forEachIndexed
            val coll = json.decodeFromString(SfCollection.serializer(), body)
            // Scryfall geeft gevonden kaarten in volgorde terug, zonder de niet-gevonden vragen.
            val found = coll.data.toMutableList()
            chunk.forEachIndexed { i, q ->
                val match = found.firstOrNull { c -> matches(q, c) }
                if (match != null) {
                    results[chunkIndex * 75 + i] = match
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
