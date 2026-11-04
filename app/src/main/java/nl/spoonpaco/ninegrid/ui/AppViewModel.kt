package nl.spoonpaco.ninegrid.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.spoonpaco.ninegrid.NineGridApp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.AppSettings
import nl.spoonpaco.ninegrid.data.BackupWorker
import nl.spoonpaco.ninegrid.data.NewEntry
import nl.spoonpaco.ninegrid.data.UpdateChecker
import nl.spoonpaco.ninegrid.data.UpdateInfo
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.data.scryfall.CardQuery
import nl.spoonpaco.ninegrid.domain.DeckListParser
import nl.spoonpaco.ninegrid.domain.Formats
import nl.spoonpaco.ninegrid.domain.SlotRead

enum class ItemStatus { RESOLVING, FOUND, NOT_FOUND }

/** Eén kaart die wacht op bevestiging, na scannen, plakken of zoeken. */
data class PendingItem(
    val key: Int,
    val slot: Int? = null,
    val label: String,
    val query: CardQuery,
    val print: CardPrint? = null,
    val status: ItemStatus = ItemStatus.RESOLVING,
    val quantity: Int = 1,
    val finish: String = Finish.NONFOIL,
    val language: String = "",
    val condition: String = "NM",
    val proxy: Boolean = false,
    val section: String = Section.MAIN,
    val include: Boolean = true,
    val fromCollection: Boolean = true,
)

enum class ImportSource { SCAN, PASTE, SEARCH }

enum class Destination { COLLECTION, NEW_DECK, EXISTING_DECK }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as NineGridApp).container
    val repo = c.repository
    val images = c.images
    val settingsStore = c.settings

    val settings: StateFlow<AppSettings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val entries: StateFlow<List<EntryWithPrint>> = repo.allEntries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val decks: StateFlow<List<Deck>> = repo.decks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val changes = repo.changes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val binders = repo.binders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val snapshots = repo.snapshots.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Eén momentopname per dag: bij het openen van de app (en na elke prijsupdate of wijziging).
        viewModelScope.launch { kotlinx.coroutines.delay(1500); snapshot() }
    }

    fun snapshot() = viewModelScope.launch { runCatching { repo.takeSnapshot() } }

    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var update by mutableStateOf<UpdateInfo?>(null)

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    /** Alleen "geen verbinding" als er echt geen verbinding is; anders de foutcode van Scryfall. */
    private fun errorText(t: Throwable): String = when (t) {
        is nl.spoonpaco.ninegrid.data.scryfall.ScryfallException -> str(R.string.msg_scryfall_error, t.code)
        is java.io.IOException -> str(R.string.msg_network_error)
        else -> str(R.string.msg_scryfall_error, 0)
    }

    // ---------- Invoer ----------
    val pending = mutableStateListOf<PendingItem>()
    var importSource by mutableStateOf(ImportSource.PASTE)
    var destination by mutableStateOf(Destination.COLLECTION)
    var binder by mutableStateOf("")
    var page by mutableStateOf("")
    var newDeckName by mutableStateOf("")
    var newDeckFormat by mutableStateOf(DeckFormat.CASUAL)
    var targetDeckId by mutableStateOf<Long?>(null)
    var unreadableLines by mutableStateOf<List<String>>(emptyList())
    var scanSet by mutableStateOf("")
    private var nextKey = 0

    fun startPaste(text: String, intoDeck: Long?, asNewDeck: Boolean) {
        val r = DeckListParser.parse(text)
        pending.clear()
        importSource = ImportSource.PASTE
        unreadableLines = r.unreadable.map { it.second }
        r.lines.forEach { l ->
            pending += PendingItem(
                key = nextKey++,
                label = l.raw.trim(),
                query = CardQuery(setCode = l.setCode, collectorNumber = l.collectorNumber, name = l.name),
                quantity = l.quantity,
                finish = when { l.etched -> Finish.ETCHED; l.foil -> Finish.FOIL; else -> Finish.NONFOIL },
                section = when (l.section) {
                    DeckListParser.COMMANDER -> Section.COMMANDER
                    DeckListParser.SIDEBOARD -> Section.SIDEBOARD
                    else -> Section.MAIN
                },
            )
        }
        destination = when {
            intoDeck != null -> Destination.EXISTING_DECK
            asNewDeck -> Destination.NEW_DECK
            else -> Destination.COLLECTION
        }
        targetDeckId = intoDeck
        newDeckFormat = Formats.suggest(r.lines)
        resolveAll()
    }

    fun startScan(reads: List<SlotRead>) {
        pending.clear()
        importSource = ImportSource.SCAN
        unreadableLines = emptyList()
        destination = Destination.COLLECTION
        val forcedSet = scanSet.trim().uppercase().ifBlank { null }
        // Komen de meeste kaarten uit één set, dan is dat de set voor de twijfelgevallen op dit blad.
        val pageSet = forcedSet ?: nl.spoonpaco.ninegrid.domain.ScanParser.dominantSet(reads)
        // Altijd alle 9 vakken tonen, ook als er niets gelezen is: dan zie je dat er een kaart gemist is.
        (1..9).map { slot -> reads.firstOrNull { it.slot == slot } ?: SlotRead(slot, null, null, null, emptyList()) }.forEach { r ->
            // De set die je zelf opgeeft wint: de setcode is het kleinste tekstje en wordt het vaakst verkeerd gelezen.
            val set = forcedSet ?: r.setCode ?: pageSet
            val canLook = (set != null && r.collectorNumber != null) || r.name != null || (set != null && r.altNames.isNotEmpty())
            val readParts = listOfNotNull(
                listOfNotNull(r.setCode, r.collectorNumber).joinToString(" ").ifBlank { null },
                (r.name ?: r.altNames.firstOrNull())?.let { "‘" + it.take(22) + "’" },
            )
            pending += PendingItem(
                key = nextKey++,
                slot = r.slot,
                label = if (readParts.isEmpty() && r.lines.isEmpty()) str(R.string.scan_nothing_read)
                    else str(R.string.scan_read_as, (readParts.ifEmpty { r.lines.take(2).map { it.take(22) } }).joinToString(" · ")),
                query = CardQuery(setCode = set, collectorNumber = r.collectorNumber, name = r.name, altNames = r.altNames),
                status = if (canLook) ItemStatus.RESOLVING else ItemStatus.NOT_FOUND,
                language = r.language ?: "",
            )
        }
        viewModelScope.launch {
            if (binder.isNotBlank() && page.isBlank()) page = repo.nextPage(binder).toString()
        }
        resolveAll()
    }

    fun startSingle(print: CardPrint) {
        pending.clear()
        importSource = ImportSource.SEARCH
        unreadableLines = emptyList()
        destination = Destination.COLLECTION
        pending += PendingItem(key = nextKey++, label = print.name, query = CardQuery(id = print.id), print = print, status = ItemStatus.FOUND)
    }

    private fun resolveAll() {
        val todo = pending.filter { it.status == ItemStatus.RESOLVING }
        if (todo.isEmpty()) return
        viewModelScope.launch {
            busy = true
            val found = runCatching { repo.resolve(todo.map { it.query }) }.getOrElse {
                message = errorText(it); List(todo.size) { null }
            }
            todo.forEachIndexed { i, item ->
                val idx = pending.indexOfFirst { it.key == item.key }
                if (idx >= 0) {
                    val card = found[i]
                    pending[idx] = pending[idx].copy(
                        print = card?.toPrint(),
                        status = if (card != null) ItemStatus.FOUND else ItemStatus.NOT_FOUND,
                    )
                }
            }
            if (importSource == ImportSource.PASTE) {
                val typeLines = pending.mapNotNull { p -> p.print?.let { it.name to it.typeLine } }.toMap()
                val parsed = pending.map {
                    nl.spoonpaco.ninegrid.domain.ParsedLine(
                        it.quantity, it.print?.name ?: it.query.name ?: "", null, null, false, false,
                        when (it.section) { Section.COMMANDER -> DeckListParser.COMMANDER; Section.SIDEBOARD -> DeckListParser.SIDEBOARD; else -> DeckListParser.MAIN },
                        0, "",
                    )
                }
                newDeckFormat = Formats.suggest(parsed, typeLines)
            }
            busy = false
        }
    }

    /** Hoeveel exemplaren van deze kaart (alle prints, geen proxies) nu in de collectie liggen. */
    fun inCollection(print: CardPrint?): Int {
        if (print == null) return 0
        return entries.value.filter { it.entry.deckId == null && !it.entry.proxy &&
            (it.entry.printId == print.id || (print.oracleId.isNotEmpty() && it.print?.oracleId == print.oracleId)) }
            .sumOf { it.entry.quantity }
    }

    fun updatePending(item: PendingItem) {
        val idx = pending.indexOfFirst { it.key == item.key }
        if (idx >= 0) pending[idx] = item
    }

    fun setPendingPrint(key: Int, print: CardPrint) {
        val idx = pending.indexOfFirst { it.key == key }
        if (idx >= 0) pending[idx] = pending[idx].copy(print = print, status = ItemStatus.FOUND, include = true)
    }

    /** Geeft de vakken terug waar op dezelfde plek al dezelfde kaart staat (dubbel gescand blad). */
    suspend fun duplicateSlots(): List<Int> {
        if (importSource != ImportSource.SCAN || destination != Destination.COLLECTION || binder.isBlank()) return emptyList()
        val p = page.toIntOrNull() ?: return emptyList()
        return pending.filter { it.include && it.print != null && it.slot != null }.filter { item ->
            repo.entriesAtSlot(binder.trim(), p, item.slot!!).any { it.printId == item.print!!.id }
        }.mapNotNull { it.slot }
    }

    fun savePending(onDone: (Long?) -> Unit) {
        val items = pending.filter { it.include && it.print != null }
        if (items.isEmpty()) return
        viewModelScope.launch {
            busy = true
            val count = items.sumOf { it.quantity }
            when (destination) {
                Destination.COLLECTION -> {
                    val b = binder.trim().ifBlank { null }
                    val pg = if (b != null) page.toIntOrNull() else null
                    val list = items.map { it.toNew(deckId = null, binder = b, page = pg, slot = if (b != null && importSource == ImportSource.SCAN) it.slot else null) }
                    repo.addEntries(list, str(R.string.log_added_collection, count))
                    message = if (importSource == ImportSource.SCAN && b != null && pg != null) str(R.string.msg_page_saved, b, pg, count)
                    else getApplication<Application>().resources.getQuantityString(R.plurals.msg_added, count, count)
                    images.prefetch(list.map { it.print }, settings.value)
                    onDone(null)
                }
                Destination.NEW_DECK, Destination.EXISTING_DECK -> {
                    val isNew = destination == Destination.NEW_DECK
                    val deckId = if (isNew) null else targetDeckId
                    if (!isNew && deckId == null) { busy = false; return@launch }
                    val name = if (isNew) newDeckName.trim().ifBlank { str(R.string.deck_untitled) } else decks.value.firstOrNull { it.id == deckId }?.name ?: ""
                    val format = if (isNew) newDeckFormat else decks.value.firstOrNull { it.id == deckId }?.format ?: DeckFormat.CASUAL
                    val list = items.map {
                        val sec = when {
                            format == DeckFormat.COMMANDER && it.section == Section.SIDEBOARD -> Section.MAIN
                            format != DeckFormat.COMMANDER && it.section == Section.COMMANDER -> Section.MAIN
                            else -> it.section
                        }
                        nl.spoonpaco.ninegrid.data.Repository.DeckItem(
                            it.copy(section = sec).toNew(deckId = 0, binder = null, page = null, slot = null),
                            fromCollection = it.fromCollection && !it.proxy,
                        )
                    }
                    val r = repo.addToDeck(
                        newDeck = if (isNew) Deck(name = name, format = newDeckFormat) else null,
                        existingDeckId = deckId,
                        items = list,
                    ) { taken, added ->
                        (if (isNew) str(R.string.log_deck_created, name, count) else str(R.string.log_added_deck, count, name)) +
                            " · " + str(R.string.msg_taken_added, taken, added)
                    }
                    message = (if (isNew) str(R.string.msg_deck_created, name) + " · " else "") + str(R.string.msg_taken_added, r.taken, r.added)
                    images.prefetch(list.map { it.item.print }, settings.value)
                    onDone(r.deckId)
                }
            }
            if (destination == Destination.COLLECTION && importSource == ImportSource.SCAN && binder.isNotBlank()) {
                page = ((page.toIntOrNull() ?: 0) + 1).toString()
            }
            pending.clear()
            busy = false
            snapshot()
        }
    }

    private fun PendingItem.toNew(deckId: Long?, binder: String?, page: Int?, slot: Int?) = NewEntry(
        print = print!!,
        entry = Entry(
            printId = print.id, finish = finish, language = language, condition = condition, proxy = proxy,
            quantity = quantity, deckId = deckId, section = if (deckId != null) section else null,
            binder = binder, page = page, slot = slot,
        ),
    )

    // ---------- Wijzigen ----------
    fun updateEntry(entry: Entry) = viewModelScope.launch {
        repo.updateEntry(entry, str(R.string.log_edited, nameOf(entry)))
    }

    fun deleteEntry(entry: Entry) = viewModelScope.launch {
        repo.deleteEntry(entry, str(R.string.log_deleted, entry.quantity, nameOf(entry)))
    }

    fun moveEntry(entry: Entry, qty: Int, deckId: Long?, section: String?, binder: String?, page: Int?, slot: Int?) = viewModelScope.launch {
        val where = deckId?.let { id -> decks.value.firstOrNull { it.id == id }?.name } ?: str(R.string.collection)
        repo.moveEntry(entry, qty, deckId, section, binder, page, slot, str(R.string.log_moved, qty, nameOf(entry), where))
        message = str(R.string.msg_moved, where)
    }

    fun updateDeck(deck: Deck) = viewModelScope.launch { repo.updateDeck(deck, str(R.string.log_deck_edited, deck.name)) }

    fun deleteDeck(deck: Deck, keepCards: Boolean) = viewModelScope.launch {
        repo.deleteDeck(deck.id, keepCards, str(R.string.log_deck_deleted, deck.name))
    }

    fun undo() = viewModelScope.launch {
        message = if (repo.undoLatest()) str(R.string.msg_undone) else str(R.string.msg_nothing_to_undo)
    }

    private fun nameOf(e: Entry) = entries.value.firstOrNull { it.entry.id == e.id }?.print?.name ?: "?"

    fun refreshPrices() = viewModelScope.launch {
        busy = true
        runCatching { repo.refreshPrices() }
            .onSuccess {
                settingsStore.setLastPriceUpdate(System.currentTimeMillis())
                snapshot()
                message = str(R.string.msg_prices_updated, it)
            }
            .onFailure { message = errorText(it) }
        busy = false
    }

    // ---------- Back-up ----------
    fun backupTo(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = repo.backupToJson(repo.exportBackup())
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            settingsStore.setLastBackup(System.currentTimeMillis())
        }.onSuccess { message = str(R.string.msg_backup_saved) }.onFailure { message = str(R.string.msg_backup_failed) }
    }

    fun restoreFrom(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
            repo.restoreBackup(repo.parseBackup(text))
        }.onSuccess {
            message = str(R.string.msg_restored)
            images.prefetch(repo.allPrintsOwned(), settings.value)
        }.onFailure { message = str(R.string.msg_restore_failed) }
    }

    fun writeText(uri: Uri, text: String) = viewModelScope.launch {
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }.onSuccess { message = str(R.string.msg_exported) }.onFailure { message = str(R.string.msg_backup_failed) }
    }

    fun setBackupFolder(uri: Uri?) = viewModelScope.launch {
        if (uri != null) {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settingsStore.setBackupFolder(uri?.toString())
        BackupWorker.schedule(getApplication(), uri != null)
        if (uri != null) {
            runCatching { BackupWorker.writeBackup(getApplication(), repo, uri) }
                .onSuccess { settingsStore.setLastBackup(System.currentTimeMillis()); message = str(R.string.msg_backup_saved) }
        }
    }

    // ---------- Instellingen ----------
    fun setTheme(v: String) = viewModelScope.launch { settingsStore.setTheme(v) }
    fun setChartLines(v: List<String>) = viewModelScope.launch { settingsStore.setChartLines(v) }
    fun setChartMetric(v: String) = viewModelScope.launch { settingsStore.setChartMetric(v) }
    fun setChartRange(v: String) = viewModelScope.launch { settingsStore.setChartRange(v) }
    fun setCurrency(v: String) = viewModelScope.launch { settingsStore.setCurrency(v) }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { settingsStore.setWifiOnly(v) }
    fun setOnboardingDone() = viewModelScope.launch { settingsStore.setOnboardingDone(true) }
    fun setImageMode(v: String) = viewModelScope.launch {
        settingsStore.setImageMode(v)
        if (v == nl.spoonpaco.ninegrid.data.ImageMode.OFF) images.clearAll()
        else images.prefetch(repo.allPrintsOwned(), settings.value.copy(imageMode = v))
    }

    fun checkUpdate(manual: Boolean) = viewModelScope.launch {
        val s = settingsStore.current()
        if (!manual && System.currentTimeMillis() - s.lastUpdateCheck < 24 * 3600 * 1000L) return@launch
        settingsStore.setLastUpdateCheck(System.currentTimeMillis())
        val u = UpdateChecker.check()
        update = u
        if (u == null && manual) message = str(R.string.msg_up_to_date)
    }
}
