package nl.spoonpaco.ninegrid.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.data.db.AppDatabase
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.ChangeRecord
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.UndoData
import nl.spoonpaco.ninegrid.data.scryfall.CardQuery
import nl.spoonpaco.ninegrid.data.scryfall.ScryfallClient
import nl.spoonpaco.ninegrid.data.scryfall.SfCard
import nl.spoonpaco.ninegrid.domain.ScanParser

/** Een exemplaar dat nog moet worden opgeslagen, met zijn print. */
data class NewEntry(val print: CardPrint, val entry: Entry)

@Serializable
data class BackupFile(
    val app: String = "NineGrid",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val prints: List<CardPrint>,
    val entries: List<Entry>,
    val decks: List<Deck>,
)

/**
 * Alle wijzigingen aan de collectie lopen via deze klasse. Elke wijziging komt in het wijzigingslog,
 * met genoeg gegevens om hem ongedaan te maken.
 */
class Repository(
    private val db: AppDatabase,
    val scryfall: ScryfallClient,
    private val onPrintsRemoved: suspend (List<CardPrint>) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val allEntries: Flow<List<EntryWithPrint>> = db.entries().observeAll()
    val decks: Flow<List<Deck>> = db.decks().observeAll()
    val changes: Flow<List<ChangeRecord>> = db.changes().observeAll()
    val binders: Flow<List<String>> = db.entries().binders()

    fun entriesForPrint(printId: String) = db.entries().observeForPrint(printId)
    fun entriesForDeck(deckId: Long) = db.entries().observeForDeck(deckId)
    fun deck(id: Long) = db.decks().observe(id)
    fun print(id: String) = db.prints().observe(id)

    suspend fun nextPage(binder: String): Int = (db.entries().maxPage(binder) ?: 0) + 1
    suspend fun entriesAtSlot(binder: String, page: Int, slot: Int) = db.entries().atSlot(binder, page, slot)

    private suspend fun log(summary: String, undo: UndoData) {
        db.changes().insert(ChangeRecord(summary = summary, undoJson = json.encodeToString(UndoData.serializer(), undo)))
    }

    private fun sameStack(a: Entry, b: Entry) = a.printId == b.printId && a.finish == b.finish &&
        a.language == b.language && a.condition == b.condition && a.proxy == b.proxy &&
        a.deckId == b.deckId && a.section == b.section && a.binder == b.binder && a.page == b.page && a.slot == b.slot

    /** Voegt exemplaren toe. Gelijke exemplaren op dezelfde plek worden samengevoegd tot één stapel. */
    suspend fun addEntries(items: List<NewEntry>, summary: String): Int {
        if (items.isEmpty()) return 0
        val changed = mutableListOf<Entry>()
        val created = mutableListOf<Long>()
        db.withTransaction {
            db.prints().upsert(items.map { it.print }.distinctBy { it.id })
            val existing = db.entries().all().toMutableList()
            for (item in items) {
                val e = item.entry.copy(printId = item.print.id)
                val match = existing.firstOrNull { sameStack(it, e) }
                if (match != null) {
                    if (changed.none { it.id == match.id } && match.id !in created) changed += match
                    val updated = match.copy(quantity = match.quantity + e.quantity)
                    db.entries().upsert(listOf(updated))
                    existing[existing.indexOf(match)] = updated
                } else {
                    val id = db.entries().insert(e.copy(id = 0))
                    created += id
                    existing += e.copy(id = id)
                }
            }
            log(summary, UndoData(restoreEntries = changed, deleteEntryIds = created))
        }
        return items.sumOf { it.entry.quantity }
    }

    suspend fun createDeck(deck: Deck, items: List<NewEntry>, summary: String): Long {
        var deckId = 0L
        db.withTransaction {
            deckId = db.decks().insert(deck.copy(id = 0))
            db.prints().upsert(items.map { it.print }.distinctBy { it.id })
            val ids = items.map { db.entries().insert(it.entry.copy(id = 0, printId = it.print.id, deckId = deckId, binder = null, page = null, slot = null)) }
            log(summary, UndoData(deleteEntryIds = ids, deleteDeckIds = listOf(deckId)))
        }
        return deckId
    }

    /** Een kaart voor een deck: nieuw toevoegen, of eerst uit de collectie halen (en alleen het tekort nieuw). */
    data class DeckItem(val item: NewEntry, val fromCollection: Boolean)

    data class DeckResult(val deckId: Long, val taken: Int, val added: Int)

    /**
     * Zet kaarten in een (nieuw of bestaand) deck. Kaarten met fromCollection gaan uit de collectie het deck in,
     * zodat niets dubbel telt: eerst dezelfde print en afwerking, dan dezelfde print, dan andere prints van dezelfde kaart.
     * Wat de collectie niet heeft, komt er als nieuw exemplaar bij. Alles in één wijziging, dus in één keer ongedaan te maken.
     */
    suspend fun addToDeck(newDeck: Deck?, existingDeckId: Long?, items: List<DeckItem>, summary: (Int, Int) -> String): DeckResult {
        var result = DeckResult(0, 0, 0)
        db.withTransaction {
            val deckId = newDeck?.let { db.decks().insert(it.copy(id = 0)) } ?: existingDeckId ?: return@withTransaction
            db.prints().upsert(items.map { it.item.print }.distinctBy { it.id })
            val oracleOf = db.prints().all().associate { it.id to it.oracleId }
            val cur = db.entries().all().toMutableList()
            val restore = mutableListOf<Entry>()
            val created = mutableListOf<Long>()
            fun snapshot(e: Entry) { if (e.id !in created && restore.none { it.id == e.id }) restore += e }
            suspend fun put(e: Entry) {
                val target = e.copy(id = 0, deckId = deckId, binder = null, page = null, slot = null)
                val match = cur.firstOrNull { sameStack(it, target) }
                if (match != null) {
                    snapshot(match)
                    val upd = match.copy(quantity = match.quantity + target.quantity)
                    db.entries().upsert(listOf(upd)); cur[cur.indexOf(match)] = upd
                } else {
                    val id = db.entries().insert(target); created += id; cur += target.copy(id = id)
                }
            }
            var taken = 0; var added = 0
            for (di in items) {
                val want = di.item.entry
                var rem = want.quantity
                if (di.fromCollection) {
                    val oracle = di.item.print.oracleId
                    val candidates = cur.filter { it.deckId == null && !it.proxy && (it.printId == want.printId || (oracle.isNotEmpty() && oracleOf[it.printId] == oracle)) }
                        .sortedWith(compareBy({ if (it.printId == want.printId && it.finish == want.finish) 0 else if (it.printId == want.printId) 1 else if (it.finish == want.finish) 2 else 3 }, { -it.quantity }))
                    for (c in candidates) {
                        if (rem <= 0) break
                        val n = minOf(rem, c.quantity)
                        snapshot(c)
                        if (n >= c.quantity) { db.entries().deleteIds(listOf(c.id)); cur.removeAll { it.id == c.id } }
                        else { val upd = c.copy(quantity = c.quantity - n); db.entries().upsert(listOf(upd)); cur[cur.indexOfFirst { it.id == c.id }] = upd }
                        put(c.copy(quantity = n, section = want.section ?: nl.spoonpaco.ninegrid.data.db.Section.MAIN))
                        rem -= n; taken += n
                    }
                }
                if (rem > 0) {
                    put(want.copy(quantity = rem, section = want.section ?: nl.spoonpaco.ninegrid.data.db.Section.MAIN))
                    added += rem
                }
            }
            log(summary(taken, added), UndoData(restoreEntries = restore, deleteEntryIds = created, deleteDeckIds = if (newDeck != null) listOf(deckId) else emptyList()))
            result = DeckResult(deckId, taken, added)
        }
        return result
    }

    suspend fun updateDeck(deck: Deck, summary: String) {
        db.withTransaction {
            val before = db.decks().get(deck.id) ?: return@withTransaction
            db.decks().upsert(listOf(deck))
            log(summary, UndoData(restoreDecks = listOf(before)))
        }
    }

    /** Verwijdert een deck. De kaarten gaan terug naar de collectie, of worden verwijderd. */
    suspend fun deleteDeck(deckId: Long, keepCards: Boolean, summary: String) {
        var removedPrints: List<CardPrint> = emptyList()
        db.withTransaction {
            val deck = db.decks().get(deckId) ?: return@withTransaction
            val entries = db.entries().forDeck(deckId)
            if (keepCards) {
                db.entries().upsert(entries.map { it.copy(deckId = null, section = null) })
            } else {
                db.entries().deleteIds(entries.map { it.id })
            }
            db.decks().deleteIds(listOf(deckId))
            log(summary, UndoData(restoreEntries = entries, restoreDecks = listOf(deck)))
            if (!keepCards) removedPrints = cleanupPrints()
        }
        if (removedPrints.isNotEmpty()) onPrintsRemoved(removedPrints)
    }

    suspend fun updateEntry(entry: Entry, summary: String) {
        db.withTransaction {
            val before = db.entries().get(entry.id) ?: return@withTransaction
            if (entry.quantity <= 0) {
                db.entries().deleteIds(listOf(entry.id))
            } else {
                db.entries().upsert(listOf(entry))
            }
            log(summary, UndoData(restoreEntries = listOf(before)))
        }
        if (entry.quantity <= 0) cleanupPrints().let { if (it.isNotEmpty()) onPrintsRemoved(it) }
    }

    suspend fun deleteEntry(entry: Entry, summary: String) = updateEntry(entry.copy(quantity = 0), summary)

    /**
     * Verplaatst (een deel van) een stapel naar een deck of naar de collectie.
     * Het exemplaar verandert alleen van plek; het aantal in de collectie als geheel blijft gelijk.
     */
    suspend fun moveEntry(entry: Entry, quantity: Int, toDeckId: Long?, section: String?, binder: String?, page: Int?, slot: Int?, summary: String) {
        db.withTransaction {
            val before = db.entries().get(entry.id) ?: return@withTransaction
            val qty = quantity.coerceIn(1, before.quantity)
            val target = before.copy(
                id = 0, quantity = qty, deckId = toDeckId,
                section = if (toDeckId != null) (section ?: nl.spoonpaco.ninegrid.data.db.Section.MAIN) else null,
                binder = if (toDeckId == null) binder else null,
                page = if (toDeckId == null) page else null,
                slot = if (toDeckId == null) slot else null,
            )
            val existing = db.entries().all().firstOrNull { sameStack(it, target) }
            val created = mutableListOf<Long>()
            val restore = mutableListOf(before)
            if (existing != null) {
                restore += existing
                db.entries().upsert(listOf(existing.copy(quantity = existing.quantity + qty)))
            } else {
                created += db.entries().insert(target)
            }
            if (qty >= before.quantity) db.entries().deleteIds(listOf(before.id))
            else db.entries().upsert(listOf(before.copy(quantity = before.quantity - qty)))
            log(summary, UndoData(restoreEntries = restore, deleteEntryIds = created))
        }
    }

    /** Draait de laatste wijziging terug. Alleen de nieuwste kan, zodat er nooit iets door elkaar raakt. */
    suspend fun undoLatest(): Boolean {
        var done = false
        db.withTransaction {
            val change = db.changes().latestActive() ?: return@withTransaction
            val undo = json.decodeFromString(UndoData.serializer(), change.undoJson)
            if (undo.deleteEntryIds.isNotEmpty()) db.entries().deleteIds(undo.deleteEntryIds)
            if (undo.restoreDecks.isNotEmpty()) db.decks().upsert(undo.restoreDecks)
            if (undo.restoreEntries.isNotEmpty()) db.entries().upsert(undo.restoreEntries)
            if (undo.deleteDeckIds.isNotEmpty()) db.decks().deleteIds(undo.deleteDeckIds)
            db.changes().markReverted(change.id)
            done = true
        }
        cleanupPrints().let { if (it.isNotEmpty()) onPrintsRemoved(it) }
        return done
    }

    /**
     * Prints zonder exemplaren. De gegevens blijven bewaard zodat ongedaan maken werkt;
     * alleen hun afbeeldingen worden van de telefoon verwijderd.
     */
    private suspend fun cleanupPrints(): List<CardPrint> {
        val owned = db.prints().owned().map { it.id }.toSet()
        return db.prints().all().filter { it.id !in owned }
    }

    /** Ververst de prijzen en gegevens van alle kaarten die je hebt. */
    suspend fun refreshPrices(): Int {
        val prints = db.prints().owned()
        if (prints.isEmpty()) return 0
        val cards = scryfall.collection(prints.map { CardQuery(id = it.id) })
        val updated = cards.filterNotNull().map { it.toPrint() }
        db.prints().upsert(updated)
        return updated.size
    }

    /**
     * Zoekt kaarten op bij Scryfall. Bij een set en nummer die niet bestaan, probeert hij
     * de gangbare OCR-verwarringen (O/0, I/1) en daarna de naam.
     */
    suspend fun resolve(queries: List<CardQuery>): List<SfCard?> {
        val first = scryfall.collection(queries).toMutableList()
        // Nummer verkeerd gelezen? Als de gelezen naam duidelijk niet past bij de gevonden kaart, opnieuw op naam zoeken.
        queries.forEachIndexed { i, q ->
            val c = first[i]
            if (c != null && q.id == null && q.name != null && q.collectorNumber != null && !namesLookAlike(q.name, c.name)) first[i] = null
        }
        queries.forEachIndexed { i, q ->
            if (first[i] != null) return@forEachIndexed
            if (q.setCode != null && q.collectorNumber != null) {
                for ((s, n) in ScanParser.alternatives(q.setCode, q.collectorNumber)) {
                    val c = runCatching { scryfall.cardBySetNumber(s, n) }.getOrNull()
                    if (c != null) { first[i] = c; break }
                }
            }
            if (first[i] == null && q.name != null) {
                first[i] = runCatching { scryfall.cardByName(q.name, q.setCode) }.getOrNull()
                // Gevonden op naam maar in een andere set? Probeer dan de print uit de opgegeven set.
                val c = first[i]
                if (c != null && q.setCode != null && !c.set.equals(q.setCode, true)) {
                    runCatching { scryfall.cardByName(c.name, q.setCode) }.getOrNull()?.takeIf { it.set.equals(q.setCode, true) }?.let { first[i] = it }
                }
            }
        }
        return first
    }

    suspend fun exportBackup(): BackupFile = BackupFile(
        prints = db.prints().owned(),
        entries = db.entries().all(),
        decks = db.decks().all(),
    )

    fun backupToJson(b: BackupFile): String = json.encodeToString(BackupFile.serializer(), b)

    fun parseBackup(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    /** Vervangt alles door de inhoud van een back-up. */
    suspend fun restoreBackup(b: BackupFile) {
        db.withTransaction {
            db.entries().deleteAll()
            db.decks().deleteAll()
            db.prints().deleteAll()
            db.changes().deleteAll()
            db.prints().upsert(b.prints)
            db.decks().upsert(b.decks)
            db.entries().upsert(b.entries)
        }
    }

    suspend fun allPrintsOwned() = db.prints().owned()

    private fun namesLookAlike(read: String, real: String): Boolean {
        fun norm(x: String) = x.lowercase().filter { it.isLetter() }
        val a = norm(read); val b = norm(real.substringBefore(" // "))
        if (a.isEmpty() || b.isEmpty()) return true
        if (b.contains(a) || a.contains(b)) return true
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (x in 0..a.length) d[x][0] = x
        for (y in 0..b.length) d[0][y] = y
        for (x in 1..a.length) for (y in 1..b.length) {
            d[x][y] = minOf(d[x - 1][y] + 1, d[x][y - 1] + 1, d[x - 1][y - 1] + if (a[x - 1] == b[y - 1]) 0 else 1)
        }
        return d[a.length][b.length] <= maxOf(a.length, b.length) / 2
    }
}
