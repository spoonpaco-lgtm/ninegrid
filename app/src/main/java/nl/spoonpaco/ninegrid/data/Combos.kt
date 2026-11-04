package nl.spoonpaco.ninegrid.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nl.spoonpaco.ninegrid.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Eén combo uit Commander Spellbook. */
@Serializable
data class Combo(
    val id: String,
    val cards: List<String>,
    val produces: List<String>,
    val prerequisites: String,
    val description: String,
    /** Kaarten uit de combo die niet in het deck zitten (bij bijna-combo's). */
    val missing: List<String> = emptyList(),
) {
    val twoCard get() = cards.size == 2
}

@Serializable
data class ComboReport(
    val deckHash: String,
    val fetchedAt: Long,
    val included: List<Combo>,
    val almost: List<Combo>,
    /** Bracketlabel van Commander Spellbook: R, S, P, O, PA of C. */
    val spellbookTag: String? = null,
    val earlyTwoCard: List<String> = emptyList(),
    val lateTwoCard: List<String> = emptyList(),
)

/**
 * Combo's zoeken via Commander Spellbook (https://commanderspellbook.com), de openbare combodatabase.
 * Alleen kaartnamen worden verstuurd. De uitkomst blijft per deck bewaard tot het deck verandert.
 */
class ComboService(private val context: Context) {
    private val base = "https://backend.commanderspellbook.com"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val dir get() = File(context.filesDir, "combos").apply { mkdirs() }

    fun hashOf(commanders: List<Pair<String, Int>>, main: List<Pair<String, Int>>) =
        (commanders.sortedBy { it.first }.joinToString("|") + "#" + main.sortedBy { it.first }.joinToString("|")).hashCode().toString()

    fun cached(deckId: Long, hash: String): ComboReport? = runCatching {
        json.decodeFromString(ComboReport.serializer(), File(dir, "deck-$deckId.json").readText())
    }.getOrNull()?.takeIf { it.deckHash == hash }

    private fun body(commanders: List<Pair<String, Int>>, main: List<Pair<String, Int>>): String = buildJsonObject {
        put("commanders", buildJsonArray { commanders.forEach { (n, q) -> add(buildJsonObject { put("card", n); put("quantity", q) }) } })
        put("main", buildJsonArray { main.forEach { (n, q) -> add(buildJsonObject { put("card", n); put("quantity", q) }) } })
    }.toString()

    private fun post(path: String, body: String): JsonObject {
        val req = Request.Builder().url(base + path)
            .header("User-Agent", "NineGrid/${BuildConfig.VERSION_NAME} (+https://github.com/spoonpaco-lgtm/ninegrid)")
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Commander Spellbook ${resp.code}")
            return json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
        }
    }

    // De API geeft camelCase; voor de zekerheid ook snake_case lezen.
    private fun JsonObject.field(vararg names: String): JsonElement? = names.firstNotNullOfOrNull { this[it] }
    private fun JsonElement?.str() = runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonElement?.arr(): JsonArray = runCatching { this?.jsonArray }.getOrNull() ?: JsonArray(emptyList())

    private fun variant(v: JsonObject, deck: Set<String>): Combo {
        val cards = v.field("uses").arr().mapNotNull { u -> u.jsonObject.field("card")?.jsonObject?.field("name").str() }
        val produces = v.field("produces").arr().mapNotNull { p -> p.jsonObject.field("feature")?.jsonObject?.field("name").str() }
        val pre = listOfNotNull(v.field("easyPrerequisites", "easy_prerequisites").str(), v.field("notablePrerequisites", "notable_prerequisites").str())
            .filter { it.isNotBlank() }.joinToString("\n")
        return Combo(
            id = v.field("id").str().orEmpty(), cards = cards, produces = produces, prerequisites = pre,
            description = v.field("description").str().orEmpty(),
            missing = cards.filter { it.lowercase() !in deck },
        )
    }

    suspend fun find(deckId: Long, commanders: List<Pair<String, Int>>, main: List<Pair<String, Int>>): ComboReport = withContext(Dispatchers.IO) {
        val b = body(commanders, main)
        val deck = (commanders + main).map { it.first.lowercase() }.toSet()
        val found = post("/find-my-combos", b)
        val results = found.field("results")?.jsonObject ?: found
        val included = results.field("included").arr().map { variant(it.jsonObject, deck) }
        val almost = results.field("almostIncluded", "almost_included").arr().map { variant(it.jsonObject, deck) }
            .filter { it.missing.size == 1 }
        // Bracketschatting van Commander Spellbook zelf, met de combo's van twee kaarten (vroeg of laat in het spel).
        val est = runCatching { post("/estimate-bracket", b) }.getOrNull()
        fun ids(vararg keys: String) = keys.flatMap { k -> est?.field(k).arr().mapNotNull { it.jsonObject.field("id").str() } }
        val report = ComboReport(
            deckHash = hashOf(commanders, main), fetchedAt = System.currentTimeMillis(),
            included = included.sortedBy { it.cards.size }, almost = almost.take(30),
            spellbookTag = est?.field("bracketTag", "bracket_tag").str(),
            earlyTwoCard = ids("definitelyEarlyGameTwoCardCombos", "arguablyEarlyGameTwoCardCombos"),
            lateTwoCard = ids("definitelyLateGameTwoCardCombos", "borderlineLateGameTwoCardCombos"),
        )
        runCatching { File(dir, "deck-$deckId.json").writeText(json.encodeToString(ComboReport.serializer(), report)) }
        report
    }
}
