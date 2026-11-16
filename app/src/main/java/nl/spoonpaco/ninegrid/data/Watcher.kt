package nl.spoonpaco.ninegrid.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.domain.Watch
import java.io.File

/**
 * Melding bij ban of reprint (punt 40). Draait na de dagelijkse prijsupdate (die ook de legaliteit ververst).
 *
 * Bans: de stand per kaart van de vorige keer staat in een bestand; is hij veranderd in een gevolgd format, dan een melding.
 * Reprints: sets die nog niet uit zijn of de afgelopen 30 dagen uitkwamen; alleen als het aantal kaarten van zo'n set
 * veranderde, worden zijn kaarten opgehaald (tijdens spoilers elke dag een paar, daarna niets meer).
 * De eerste keer wordt alleen de stand vastgelegd, zonder meldingen, zodat een update niet met tientallen meldingen begint.
 */
class Watcher(private val context: Context, private val repo: Repository) {
    @Serializable
    data class State(
        val legalReady: Boolean = false,
        /** Per oracle-id de korte stand (zie Watch.compact). */
        val legal: Map<String, String> = emptyMap(),
        val reprintsReady: Boolean = false,
        /** Per gevolgde set het aantal kaarten bij de vorige controle. */
        val setCounts: Map<String, Int> = emptyMap(),
        /** Al gemeld (of bij de eerste keer vastgelegd): "SET:oracle-id". */
        val reprintsSeen: Set<String> = emptySet(),
    )

    data class LegalAlert(
        val name: String, val printId: String, val format: String, val kind: Watch.Kind,
        val copies: Int, val value: Double, val decks: List<String>,
    )

    data class ReprintAlert(
        val name: String, val printId: String, val setCode: String, val setName: String, val releasedAt: String,
        val yourSets: List<String>, val copies: Int, val unitValue: Double,
    )

    data class Result(val legal: List<LegalAlert>, val reprints: List<ReprintAlert>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file get() = File(context.filesDir, "watch.json")

    private fun load(): State = runCatching { json.decodeFromString(State.serializer(), file.readText()) }.getOrDefault(State())
    private fun save(s: State) { runCatching { file.writeText(json.encodeToString(State.serializer(), s)) } }

    /** Alles vergeten (fabrieksinstellingen): de volgende controle legt alleen weer de stand vast. */
    fun reset() { runCatching { file.delete() } }

    /** Reprints weer aangezet: eerst de stand opnieuw vastleggen, zodat er geen melding komt over alles van de tussentijd. */
    fun resetReprints() = save(load().copy(reprintsReady = false, setCounts = emptyMap(), reprintsSeen = emptySet()))

    suspend fun check(s: AppSettings): Result {
        var st = load()
        val owned = repo.ownedForWatch()
        val byOracle = owned.filter { it.second.oracleId.isNotBlank() }.groupBy { it.second.oracleId }
        val decks = repo.allDecks()
        val deckNames = decks.associate { it.id to it.name }

        // ---- Bans ----
        val nowLegal = byOracle.mapNotNull { (oracle, list) ->
            list.firstOrNull { it.second.legalities.isNotBlank() }?.let { oracle to Watch.compact(it.second.legalities) }
        }.toMap()
        val formats = if (s.watchAllFormats) Watch.FORMATS.toSet() else decks.map { it.format }.filter { it in Watch.FORMATS }.toSet()
        val legal = if (st.legalReady && s.banAlerts) Watch.changes(st.legal, nowLegal, formats).mapNotNull { ch ->
            val list = byOracle[ch.oracleId] ?: return@mapNotNull null
            val best = list.maxByOrNull { Money.unitPrice(it.first, it.second, s.currency) } ?: return@mapNotNull null
            LegalAlert(
                name = best.second.name, printId = best.second.id, format = ch.format, kind = ch.kind,
                copies = list.sumOf { it.first.quantity }, value = list.sumOf { Money.total(it.first, it.second, s.currency) },
                decks = list.mapNotNull { it.first.deckId?.let { id -> deckNames[id] } }.distinct(),
            )
        } else emptyList()
        st = st.copy(legalReady = true, legal = nowLegal)

        // ---- Reprints ----
        val reprints = mutableListOf<ReprintAlert>()
        if (s.reprintAlerts || !st.reprintsReady) {
            val today = java.time.LocalDate.now().toString()
            val sets = runCatching { repo.scryfall.allSets() }.getOrDefault(emptyList())
                .filter { Watch.follow(it.setType, it.digital, it.releasedAt, today) }
            if (sets.isNotEmpty()) {
                val ownedSets = byOracle.mapValues { (_, l) -> l.map { it.second.setCode.uppercase() }.toSet() }
                val counts = st.setCounts.filterKeys { k -> sets.any { it.code.equals(k, true) } }.toMutableMap()
                val seen = st.reprintsSeen.filter { key -> sets.any { key.startsWith(it.code.uppercase() + ":") } }.toMutableSet()
                for (set in sets) {
                    val code = set.code.uppercase()
                    if (counts[code] == set.cardCount) continue
                    val cards = runCatching { repo.scryfall.cardsOfSet(set.code) }.getOrNull() ?: continue
                    counts[code] = set.cardCount
                    val found = Watch.reprints(
                        cards.filter { !it.oracleId.isNullOrBlank() }.map { Watch.SetCard(it.oracleId!!, it.name, it.set.uppercase(), it.setName.ifBlank { set.name }, it.releasedAt.ifBlank { set.releasedAt ?: "" }) },
                        ownedSets, seen,
                    )
                    found.forEach { seen += Watch.reprintKey(it.setCode, it.oracleId) }
                    if (!st.reprintsReady || !s.reprintAlerts) continue
                    found.forEach { c ->
                        val list = byOracle[c.oracleId] ?: return@forEach
                        val best = list.maxByOrNull { Money.unitPrice(it.first, it.second, s.currency) } ?: return@forEach
                        val unit = Money.unitPrice(best.first, best.second, s.currency)
                        if (unit < s.reprintMin) return@forEach
                        reprints += ReprintAlert(
                            name = best.second.name, printId = best.second.id, setCode = c.setCode, setName = c.setName, releasedAt = c.releasedAt,
                            yourSets = list.map { it.second.setCode.uppercase() }.distinct(), copies = list.sumOf { it.first.quantity }, unitValue = unit,
                        )
                    }
                }
                st = st.copy(reprintsReady = true, setCounts = counts, reprintsSeen = seen)
            }
        }
        save(st)
        return Result(legal, reprints.sortedByDescending { it.unitValue })
    }
}
