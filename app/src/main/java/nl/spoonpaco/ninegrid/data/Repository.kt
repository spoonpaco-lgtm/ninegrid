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
    val snapshots: List<nl.spoonpaco.ninegrid.data.db.Snapshot> = emptyList(),
    val moments: List<nl.spoonpaco.ninegrid.data.db.Moment> = emptyList(),
    val prices: List<nl.spoonpaco.ninegrid.data.db.PricePoint> = emptyList(),
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
    private companion object { const val PART = 250_000; const val PARTS = "@parts" }

    /** Setlijsten en namenlijst op de telefoon (voor de herkenning). */
    var cache: CardCache? = null

    val allEntries: Flow<List<EntryWithPrint>> = db.entries().observeAll()
    val decks: Flow<List<Deck>> = db.decks().observeAll()
    val changes: Flow<List<ChangeRecord>> = db.changes().observeAll()
    val binders: Flow<List<String>> = db.entries().binders()
    val snapshots = db.snapshots().observeAll()
    val moments = db.moments().observeAll()

    /**
     * Bewaart de stand van vandaag: totaal, sets, decks en elk deck en elke set apart.
     * Wordt per dag overschreven, zodat alleen de laatste stand van de dag blijft.
     */
    suspend fun takeSnapshot() {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val entries = db.entries().all().filter { !it.proxy }
        val prints = db.prints().all().associateBy { it.id }
        val decks = db.decks().all()
        fun snap(key: String, label: String, list: List<Entry>) = nl.spoonpaco.ninegrid.data.db.Snapshot(
            day, key, label,
            list.sumOf { nl.spoonpaco.ninegrid.domain.Money.total(it, prints[it.printId], "EUR") },
            list.sumOf { nl.spoonpaco.ninegrid.domain.Money.total(it, prints[it.printId], "USD") },
            list.sumOf { it.quantity },
        )
        val loose = entries.filter { it.deckId == null }
        val inDecks = entries.filter { it.deckId != null }
        val rows = mutableListOf(snap("total", "", entries), snap("sets", "", loose), snap("decks", "", inDecks))
        decks.forEach { d -> rows += snap("deck:${d.id}", d.name, inDecks.filter { it.deckId == d.id }) }
        loose.groupBy { prints[it.printId]?.setCode ?: "?" }.forEach { (code, l) -> rows += snap("set:$code", code, l) }
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.snapshots().deleteDay(day)
            db.snapshots().upsert(rows)
            // Momenten: hoogstens één per minuut, en na een maand weg.
            db.moments().deleteAfter(now - 60_000)
            db.moments().deleteBefore(now - 31L * 24 * 3600 * 1000)
            db.moments().upsert(rows.map { nl.spoonpaco.ninegrid.data.db.Moment(now, it.key, it.eur, it.usd, it.count) })
        }
    }

    fun entriesForPrint(printId: String) = db.entries().observeForPrint(printId)
    fun entriesForDeck(deckId: Long) = db.entries().observeForDeck(deckId)
    fun deck(id: Long) = db.decks().observe(id)
    fun print(id: String) = db.prints().observe(id)

    suspend fun nextPage(binder: String): Int = (db.entries().maxPage(binder) ?: 0) + 1
    suspend fun entriesAtSlot(binder: String, page: Int, slot: Int) = db.entries().atSlot(binder, page, slot)

    private suspend fun log(summary: String, undo: UndoData) {
        val text = json.encodeToString(UndoData.serializer(), undo)
        if (text.length <= PART) {
            db.changes().insert(ChangeRecord(summary = summary, undoJson = text))
        } else {
            // Groot (bijv. een import): in stukken, zodat het wijzigingslog nooit vastloopt.
            val id = db.changes().insert(ChangeRecord(summary = summary, undoJson = PARTS))
            db.changes().insertParts(text.chunked(PART).mapIndexed { i, part -> nl.spoonpaco.ninegrid.data.db.UndoPart(id, i, part) })
        }
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

    /**
     * Importeert een hele collectie in één keer, als één wijziging (dus in één keer ongedaan te maken).
     * Met replace wordt eerst alles verwijderd; ongedaan maken zet het oude dan terug.
     */
    suspend fun importAll(
        collection: List<NewEntry>,
        decks: List<Pair<Deck, List<NewEntry>>>,
        history: List<nl.spoonpaco.ninegrid.data.db.Snapshot>,
        replace: Boolean,
        summary: String,
    ) {
        db.withTransaction {
            val oldEntries = if (replace) db.entries().all() else emptyList()
            val oldDecks = if (replace) db.decks().all() else emptyList()
            if (replace) { db.entries().deleteAll(); db.decks().deleteAll() }
            val prints = (collection + decks.flatMap { it.second }).map { it.print }.distinctBy { it.id }
            db.prints().upsert(prints)
            val created = mutableListOf<Long>()
            val deckIds = mutableListOf<Long>()
            // Collectie: gelijke exemplaren samenvoegen tot één stapel.
            collection.groupBy { Triple(it.print.id, it.entry.finish, Triple(it.entry.language, it.entry.proxy, it.entry.condition)) }.forEach { (_, list) ->
                val e = list.first().entry.copy(id = 0, printId = list.first().print.id, quantity = list.sumOf { it.entry.quantity }, deckId = null, section = null)
                created += db.entries().insert(e)
            }
            for ((deck, cards) in decks) {
                val id = db.decks().insert(deck.copy(id = 0)); deckIds += id
                cards.groupBy { Triple(it.print.id, it.entry.finish, Triple(it.entry.section, it.entry.proxy, it.entry.language)) }.forEach { (_, list) ->
                    val e = list.first().entry.copy(id = 0, printId = list.first().print.id, quantity = list.sumOf { it.entry.quantity }, deckId = id, binder = null, page = null, slot = null)
                    created += db.entries().insert(e)
                }
            }
            if (history.isNotEmpty()) db.snapshots().upsert(history)
            log(summary, UndoData(restoreEntries = oldEntries, deleteEntryIds = created, restoreDecks = oldDecks, deleteDeckIds = deckIds))
        }
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
            val text = if (change.undoJson == PARTS) db.changes().parts(change.id).joinToString("") { it.data } else change.undoJson
            val undo = json.decodeFromString(UndoData.serializer(), text)
            // In stukken: SQLite accepteert op oudere telefoons hoogstens 999 waarden per opdracht.
            undo.deleteEntryIds.chunked(500).forEach { db.entries().deleteIds(it) }
            if (undo.restoreDecks.isNotEmpty()) db.decks().upsert(undo.restoreDecks)
            undo.restoreEntries.chunked(500).forEach { db.entries().upsert(it) }
            undo.deleteDeckIds.chunked(500).forEach { db.decks().deleteIds(it) }
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
    suspend fun refreshPrices(background: Boolean = false): Int {
        val prints = db.prints().owned()
        if (prints.isEmpty()) return 0
        val cards = scryfall.collection(prints.map { CardQuery(id = it.id) }, background)
        val updated = cards.filterNotNull().map { it.toPrint() }
        db.prints().upsert(updated)
        recordPrices(updated)
        return updated.size
    }

    /**
     * Zoekt kaarten op bij Scryfall. Bij een set en nummer die niet bestaan, probeert hij
     * de gangbare OCR-verwarringen (O/0, I/1) en daarna de naam.
     */
    /** Een fout van Scryfall voor één kaart telt als niet gevonden, behalve een weigering (429): die gaat door naar boven. */
    private suspend fun <T> soft(block: suspend () -> T?): T? = try {
        block()
    } catch (e: nl.spoonpaco.ninegrid.data.scryfall.ScryfallException) {
        if (e.code == 429) throw e else null
    }

    private fun nameOk(q: CardQuery, c: SfCard) = q.name == null || namesLookAlike(q.name, c.name)

    /**
     * Verbetert gelezen vragen vóór het opzoeken: tikfouten in namen via de namenlijst, en als de set bekend is,
     * naam en nummer controleren tegen de lijst van die set. Klopt een combinatie, dan wordt het een vraag op id.
     */
    /** Tijd van de laatste preResolve: namen verbeteren en setlijsten. */
    var lastNamesMs = 0L
        private set

    suspend fun preResolve(queries: List<CardQuery>): List<CardQuery> {
        val c = cache ?: return queries
        val t0 = System.currentTimeMillis()
        runCatching { c.ensureNames() }
        val out = queries.map { q0 ->
            if (q0.id != null) return@map q0
            // Alleen de naam bovenaan de kaart wordt verbeterd; andere regels tellen alleen als ze letterlijk een kaartnaam zijn.
            val fixedName = q0.name?.let { n -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.correctName(n) } ?: n }
            val alts = if (c.namesLoaded) q0.altNames.filter { c.isExactName(it) } else emptyList()
            var q = q0.copy(name = fixedName ?: q0.name, altNames = alts)
            val set = q.setCode ?: return@map q
            val list = runCatching { c.setList(set) }.getOrNull() ?: c.cachedSetList(set) ?: return@map q
            fun byName(): SetCard? {
                q.name?.let { n -> list.cards.filter { namesLookAlike(n, it.name) }.let { m -> (m.firstOrNull { it.regular } ?: m.firstOrNull())?.let { return it } } }
                // Andere regels: alleen exact.
                return q.altNames.firstNotNullOfOrNull { a -> list.cards.firstOrNull { it.name.equals(a, true) } }
            }
            val names = listOfNotNull(q.name) + q.altNames
            val byNumber = q.collectorNumber?.let { nr -> list.cards.firstOrNull { it.number.equals(nr, true) } }
            val pick = when {
                // Nummer en naam kloppen samen: zeker.
                byNumber != null && (names.isEmpty() || names.any { namesLookAlike(it, byNumber.name) }) -> byNumber
                // Naam staat in de set, nummer niet of verkeerd gelezen: kies de print met dezelfde naam, het liefst met het gelezen nummer.
                else -> byName()?.let { found ->
                    list.cards.firstOrNull { it.name == found.name && it.number.equals(q.collectorNumber, true) } ?: found
                }
            }
            if (pick != null) q = q.copy(id = pick.id)
            q
        }
        lastNamesMs = System.currentTimeMillis() - t0
        return out
    }

    suspend fun resolve(queries: List<CardQuery>, onProgress: ((Int, Int) -> Unit)? = null): List<SfCard?> {
        val first = scryfall.collection(queries).toMutableList()
        // Past de gelezen naam niet bij wat set en nummer opleveren, dan was set of nummer verkeerd gelezen.
        queries.forEachIndexed { i, q ->
            val c = first[i]
            if (c != null && q.id == null && !nameOk(q, c)) first[i] = null
        }
        queries.forEachIndexed { i, q ->
            onProgress?.invoke(i, queries.size)
            first[i]?.let { found -> first[i] = betterPrint(q, found); return@forEachIndexed }
            if (q.id != null) {
                first[i] = soft { scryfall.cardById(q.id) }
                if (first[i] != null) return@forEachIndexed
            }
            if (q.setCode != null && q.collectorNumber != null) {
                for ((s, n) in listOf(q.setCode to q.collectorNumber) + ScanParser.alternatives(q.setCode, q.collectorNumber)) {
                    val c = soft { scryfall.cardBySetNumber(s, n) }
                    // Ook in de herkansing: de naam moet kloppen met wat er gelezen is.
                    if (c != null && nameOk(q, c)) { first[i] = c; break }
                }
            }
            if (first[i] == null && q.name != null) {
                first[i] = byNameInSet(q.name, q.setCode, strict = false)?.let { betterPrint(q, it) }
            }
            // Laatste poging: andere gelezen regels, maar alleen als die kaart in de set van dit blad bestaat.
            if (first[i] == null && q.setCode != null) {
                for (alt in q.altNames) {
                    val c = byNameInSet(alt, q.setCode, strict = true)
                    if (c != null) { first[i] = betterPrint(q, c); break }
                }
            }
        }
        return first
    }

    /**
     * Op naam gevonden, maar het gelezen nummer hoort bij een andere print (bijvoorbeeld extended art 374
     * in plaats van 258)? Dan die print, bij voorkeur uit de gelezen set.
     */
    /** Mogelijke prints van een kaart voor het vergelijken van plaatjes: in de gelezen set, anders de nieuwste 8. */
    /** Prints met dezelfde naam in de setlijst op de telefoon (bijv. gewoon en extended art): id en klein plaatje. */
    fun printCandidates(name: String, set: String?): List<SetCard> {
        val list = set?.let { cache?.cachedSetList(it) } ?: return emptyList()
        return list.cards.filter { it.name == name }
    }

    private suspend fun betterPrint(q: CardQuery, c: SfCard): SfCard {
        val number = q.collectorNumber ?: return c
        if (c.collectorNumber.equals(number, true) && (q.setCode == null || c.set.equals(q.setCode, true))) return c
        // Eerst de setlijst op de telefoon: geen zoekopdracht bij Scryfall nodig.
        cache?.cachedSetList(q.setCode ?: c.set)?.cards?.firstOrNull { it.name == c.name && it.number.equals(number, true) }?.let { hit ->
            if (hit.id == c.id) return c
            soft { scryfall.cardById(hit.id) }?.let { return it }
        }
        if (q.setCode != null) {
            soft { scryfall.cardBySetNumber(q.setCode, number) }?.takeIf { it.name == c.name }?.let { return it }
        }
        val prints = soft { scryfall.printsOf(c.name) } ?: return c
        val matches = prints.filter { it.collectorNumber.equals(number, true) }
        return matches.firstOrNull { it.set.equals(q.setCode ?: c.set, true) } ?: matches.firstOrNull() ?: c
    }

    /**
     * Zoekt op naam en geeft bij voorkeur de print uit [set]. Met strict alleen een kaart die echt in die set bestaat
     * en waarvan de naam lijkt op wat er gelezen is.
     */
    private suspend fun byNameInSet(name: String, set: String?, strict: Boolean): SfCard? {
        val c = soft { scryfall.cardByName(name, set) } ?: return null
        if (set == null || c.set.equals(set, true)) return if (!strict || namesLookAlike(name, c.name)) c else null
        val inSet = soft { scryfall.cardByName(c.name, set) }?.takeIf { it.set.equals(set, true) }
        return when {
            inSet != null && (!strict || namesLookAlike(name, inSet.name)) -> inSet
            strict -> null
            else -> c
        }
    }

    suspend fun exportBackup(): BackupFile = BackupFile(
        prints = db.prints().owned(),
        entries = db.entries().all(),
        decks = db.decks().all(),
        snapshots = db.snapshots().all(),
        moments = db.moments().all(),
        prices = db.prices().all(),
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
            db.changes().deleteAllParts()
            db.prints().upsert(b.prints)
            db.decks().upsert(b.decks)
            db.entries().upsert(b.entries)
            if (b.snapshots.isNotEmpty()) { db.snapshots().deleteAll(); db.snapshots().upsert(b.snapshots) }
            if (b.moments.isNotEmpty()) { db.moments().deleteAll(); b.moments.chunked(500).forEach { db.moments().upsert(it) } }
            if (b.prices.isNotEmpty()) { db.prices().deleteAll(); b.prices.chunked(500).forEach { db.prices().upsert(it) } }
        }
    }

    suspend fun allPrintsOwned() = db.prints().owned()

    private fun today() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** Prijzen van vandaag bewaren voor het koersverschil; ouder dan een maand alleen één per maand. */
    suspend fun recordPrices(prints: List<CardPrint>? = null) {
        val list = prints ?: db.prints().owned()
        if (list.isEmpty()) return
        val day = today()
        list.chunked(500).forEach { part ->
            db.prices().upsert(part.map { nl.spoonpaco.ninegrid.data.db.PricePoint(it.id, day, it.eur, it.eurFoil, it.usd, it.usdFoil, it.usdEtched) })
        }
        val cutoff = java.time.LocalDate.now().minusDays(31).toString()
        db.prices().thin(cutoff)
    }

    /** Prijzen zoals ze op [day] waren (laatst bekende op of voor die dag), per print. */
    suspend fun pricesAsOf(day: String) = db.prices().asOf(day).associateBy { it.printId }

    suspend fun firstPriceDay() = db.prices().firstDay()

    /** Wat je kunt wissen. */
    data class WipeOptions(val collection: Boolean, val decks: Boolean, val history: Boolean, val changeLog: Boolean, val images: Boolean)

    /**
     * Wist (een deel van) alles. Collectie en decks komen als één wijziging in het log, zodat je het
     * ongedaan kunt maken, tenzij je het log ook wist. Historie en plaatjes zijn niet ongedaan te maken.
     */
    suspend fun wipe(o: WipeOptions, summary: String) {
        var removed: List<CardPrint> = emptyList()
        db.withTransaction {
            val entries = db.entries().all()
            val gone = entries.filter { (o.collection && it.deckId == null) || (o.decks && it.deckId != null) }
            val decks = if (o.decks) db.decks().all() else emptyList()
            gone.map { it.id }.chunked(500).forEach { db.entries().deleteIds(it) }
            if (decks.isNotEmpty()) db.decks().deleteIds(decks.map { it.id })
            if (o.history) { db.snapshots().deleteAll(); db.moments().deleteAll(); db.prices().deleteAll() }
            if (o.changeLog) { db.changes().deleteAll(); db.changes().deleteAllParts() }
            else if (gone.isNotEmpty() || decks.isNotEmpty()) log(summary, UndoData(restoreEntries = gone, restoreDecks = decks))
            removed = cleanupPrints()
        }
        if (!o.images && removed.isNotEmpty()) onPrintsRemoved(removed)
    }

    fun namesLookAlike(read: String, real: String): Boolean {
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
