package nl.spoonpaco.ninegrid.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

enum class ItemStatus { RESOLVING, FOUND, NOT_FOUND, WAITING }

/** Eén kaart die wacht op bevestiging, na scannen, plakken of zoeken. */
@kotlinx.serialization.Serializable
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
    /** Naam, set en nummer kloppen met elkaar: kan zonder controle worden opgeslagen. */
    val confident: Boolean = false,
)

enum class ImportSource { SCAN, PASTE, SEARCH }

/** Wat er bewaard wordt als een scan op internet wacht. */
@kotlinx.serialization.Serializable
private data class SavedPending(val source: ImportSource, val destination: Destination, val binder: String, val page: String, val items: List<PendingItem>)

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
    // De grafiek haalt alleen de gekozen lijnen op (plus het totaal), en tussenstanden van de laatste maand.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val snapshots = settings.map { it.chartLines.ifEmpty { listOf("total") } }.distinctUntilChanged()
        .flatMapLatest { repo.snapshotsFor(it) }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val moments = settings.map { it.chartLines.ifEmpty { listOf("total") } }.distinctUntilChanged()
        .flatMapLatest { repo.momentsFor(it, System.currentTimeMillis() - 32L * 24 * 3600 * 1000) }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    /** Laatste dagstand per lijn: welke lijnen er zijn en hoe ze heten. */
    val latestPerKey = repo.latestSnapshotPerKey.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Gegevens per set (naam, datum, grootte, symbool), voor het setscherm. */
    var setInfo by mutableStateOf<Map<String, nl.spoonpaco.ninegrid.data.scryfall.SfSet>>(emptyMap())
        private set

    init {
        viewModelScope.launch { runCatching { setInfo = c.cache.setInfo() } }
        // Eén momentopname per dag: bij het openen van de app (en na elke prijsupdate of wijziging).
        viewModelScope.launch { kotlinx.coroutines.delay(1500); snapshot(now = true) }
        repo.scryfall.onThrottled = { viewModelScope.launch {
            message = str(R.string.msg_scryfall_pause)
            // Moet de app wachten op Scryfall? Dan één keer de offline kaartdatabase aanbieden.
            val s = settingsStore.current()
            if (s.offlineDb == nl.spoonpaco.ninegrid.data.OfflineDb.OFF && !s.offlineDbOffered) offerOfflineDb = OfflineOffer.THROTTLED
        } }
    }

    private var snapshotJob: kotlinx.coroutines.Job? = null

    /**
     * Dagstand bijwerken. Wacht tot er 3 seconden niets verandert, zodat een reeks scans of wijzigingen
     * maar één keer de hele collectie doorrekent. Nieuwe prints krijgen eerst hun eerste prijs in de historie.
     */
    fun snapshot(now: Boolean = false) {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            if (!now) kotlinx.coroutines.delay(3000)
            runCatching { repo.recordPrices(); repo.takeSnapshot() }
        }
    }

    var message by mutableStateOf<String?>(null)
    /** De huidige melding krijgt een knop Ongedaan maken. */
    var messageUndo by mutableStateOf(false)
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
    private var scanSetState by mutableStateOf("")
    /** De set die je in de scanner opgeeft; wordt onthouden voor de volgende keer. */
    var scanSet: String
        get() = scanSetState
        set(v) { scanSetState = v; viewModelScope.launch { settingsStore.setLastScanSet(v) } }
    private var nextKey = 0

    init { viewModelScope.launch { scanSetState = settingsStore.current().lastScanSet } }

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

    /** Uitsneden van je eigen foto per kaart (sleutel = PendingItem.key). Alleen in het geheugen, nooit bewaard. */
    val crops = androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.ui.graphics.ImageBitmap>()
    private val matcher = nl.spoonpaco.ninegrid.data.ImageMatch(c.images)
    /** Hoe lang het lezen en het opzoeken duurden, voor de melding na een scan. */
    var lastReadMs by mutableStateOf(0L)
    var lastRereadSlots by mutableStateOf(0)
    var lastResolveMs by mutableStateOf(0L)
    var lastNamesMs by mutableStateOf(0L)
    var lastLookupMs by mutableStateOf(0L)
    var lastImagesMs by mutableStateOf(0L)
    /** Dit hele blad is foil. */
    var scanFoil by mutableStateOf(false)

    /** Foil voor het hele blad: ook voor wat er al gescand klaarstaat. */
    fun setPageFoil(v: Boolean) {
        if (pending.none { it.slot != null }) scanFoil = v
        if (importSource == ImportSource.SCAN) pending.indices.forEach { i -> if (pending[i].slot != null) pending[i] = pending[i].copy(finish = if (v) Finish.FOIL else Finish.NONFOIL) }
    }

    /** Eén vak van het blad foil of niet (tikken op het vak in de camera). */
    fun toggleSlotFoil(slot: Int) {
        val i = pending.indexOfFirst { it.slot == slot }
        if (i < 0) return
        val p = pending[i]
        pending[i] = p.copy(finish = if (p.finish == Finish.NONFOIL) Finish.FOIL else Finish.NONFOIL)
    }

    /** Eén keer uitleggen dat je per vak foil kunt zetten. */
    private fun foilTipOnce() = viewModelScope.launch {
        if (!settingsStore.current().foilTipShown) { message = str(R.string.scan_foil_tip); settingsStore.setFoilTipShown() }
    }

    /**
     * Zet een scan klaar. Bij een blad (9 vakken) vervangt hij wat er klaarstond; bij losse kaarten (append)
     * komt elke foto erbij, tot je op Klaar tikt.
     */
    fun startScan(reads: List<SlotRead>, slotCrops: Map<Int, android.graphics.Bitmap> = emptyMap(), single: Boolean = false) {
        val append = single && importSource == ImportSource.SCAN && pending.isNotEmpty() && pending.all { it.slot == null }
        if (!append) { pending.clear(); crops.clear(); aiMarks.clear() }
        importSource = ImportSource.SCAN
        unreadableLines = emptyList()
        destination = Destination.COLLECTION
        val forcedSet = scanSet.trim().uppercase().ifBlank { null }
        // Komen de meeste kaarten uit één set, dan is dat de set voor de twijfelgevallen op dit blad.
        val pageSet = if (single) forcedSet else forcedSet ?: nl.spoonpaco.ninegrid.domain.ScanParser.dominantSet(reads)
        // Bij een blad altijd alle 9 vakken tonen, ook als er niets gelezen is: dan zie je dat er een kaart gemist is.
        val list = if (single) reads.take(1) else (1..9).map { slot -> reads.firstOrNull { it.slot == slot } ?: SlotRead(slot, null, null, null, emptyList()) }
        list.forEach { r ->
            // De set die je zelf opgeeft wint: de setcode is het kleinste tekstje en wordt het vaakst verkeerd gelezen.
            val set = forcedSet ?: r.setCode ?: pageSet
            // Niet-Engelse kaart: de gelezen naam is de gedrukte (bijv. Franse) naam. Die mag niet tegen de Engelse
            // namen worden "verbeterd" of gecontroleerd; set en nummer zijn leidend, de naam wordt apart nagekeken.
            val lang = nl.spoonpaco.ninegrid.domain.ScanParser.scryfallLang(r.language)
            val canLook = (set != null && r.collectorNumber != null) || (lang == null && (r.name != null || (set != null && r.altNames.isNotEmpty())))
            val readParts = listOfNotNull(
                listOfNotNull(r.setCode, r.collectorNumber).joinToString(" ").ifBlank { null },
                (r.name ?: r.altNames.firstOrNull())?.let { "‘" + it.take(22) + "’" },
            )
            val key = nextKey++
            slotCrops[r.slot]?.let { crops[key] = it.asImageBitmapSafe() }
            pending += PendingItem(
                key = key,
                slot = if (single) null else r.slot,
                label = if (readParts.isEmpty() && r.lines.isEmpty()) str(R.string.scan_nothing_read)
                    else str(R.string.scan_read_as, (readParts.ifEmpty { r.lines.take(2).map { it.take(22) } }).joinToString(" · ")),
                query = if (lang == null) CardQuery(setCode = set, collectorNumber = r.collectorNumber, name = r.name, altNames = r.altNames)
                    else CardQuery(setCode = set, collectorNumber = r.collectorNumber, lang = lang, printedName = r.name),
                status = if (canLook) ItemStatus.RESOLVING else ItemStatus.NOT_FOUND,
                language = r.language ?: "",
                // Bij een blad begint elk vak gewoon; foil zet je in het voorbeeld. Bij één kaart telt de knop.
                finish = if (scanFoil && single) Finish.FOIL else Finish.NONFOIL,
            )
        }
        viewModelScope.launch {
            if (binder.isNotBlank() && page.isBlank()) page = repo.nextPage(binder).toString()
        }
        if (!single) foilTipOnce()
        resolveAll()
    }

    private fun android.graphics.Bitmap.asImageBitmapSafe() = this.asImageBitmap()

    /** Stand van de laatste scan, voor de balk op het camerascherm. */
    data class ScanSummary(val busy: Boolean, val found: Int, val empty: Int, val doubtful: Int, val waiting: Int, val total: Int) {
        val allGood get() = !busy && doubtful == 0 && waiting == 0 && found > 0
    }

    val scanSummary: ScanSummary?
        get() {
            if (importSource != ImportSource.SCAN || pending.isEmpty()) return null
            val emptySlots = pending.count { it.status == ItemStatus.NOT_FOUND && it.label == str(R.string.scan_nothing_read) }
            return ScanSummary(
                busy = pending.any { it.status == ItemStatus.RESOLVING },
                found = pending.count { it.status == ItemStatus.FOUND },
                empty = emptySlots,
                doubtful = pending.count { (it.status == ItemStatus.FOUND && !it.confident) || it.status == ItemStatus.NOT_FOUND } - emptySlots,
                waiting = pending.count { it.status == ItemStatus.WAITING },
                total = pending.size,
            )
        }

    fun clearPending() { pending.clear(); crops.clear(); aiMarks.clear(); clearSavedPending() }

    private val pendingFile get() = java.io.File(getApplication<Application>().filesDir, "pending.json")
    private val pendingJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun savePendingToDisk() = runCatching {
        pendingFile.writeText(pendingJson.encodeToString(SavedPending.serializer(), SavedPending(importSource, destination, binder, page, pending.toList())))
    }

    private fun clearSavedPending() { runCatching { pendingFile.delete() } }

    /** Een scan die op internet wachtte (ook na het sluiten van de app) weer klaarzetten. */
    private fun loadSavedPending() = runCatching {
        if (!pendingFile.exists()) return@runCatching
        val saved = pendingJson.decodeFromString(SavedPending.serializer(), pendingFile.readText())
        if (saved.items.isEmpty()) return@runCatching
        pending.clear(); pending.addAll(saved.items)
        nextKey = (saved.items.maxOf { it.key } + 1)
        importSource = saved.source; destination = saved.destination; binder = saved.binder; page = saved.page
        message = str(R.string.msg_pending_restored)
    }

    private val connectivity = app.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            viewModelScope.launch {
                // Weer verbinding: wachtende kaarten alsnog opzoeken.
                if (pending.any { it.status == ItemStatus.WAITING }) {
                    pending.indices.forEach { i -> if (pending[i].status == ItemStatus.WAITING) pending[i] = pending[i].copy(status = ItemStatus.RESOLVING) }
                    resolveAll()
                }
            }
        }
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    fun startSingle(print: CardPrint) {
        pending.clear()
        importSource = ImportSource.SEARCH
        unreadableLines = emptyList()
        destination = Destination.COLLECTION
        pending += PendingItem(key = nextKey++, label = print.name, query = CardQuery(id = print.id), print = print, status = ItemStatus.FOUND)
    }

    /** Kaarten die nu al worden opgezocht (doorlopend scannen start steeds een nieuwe ronde). */
    private val inFlight = mutableSetOf<Int>()

    private fun resolveAll() {
        val todo = pending.filter { it.status == ItemStatus.RESOLVING && it.key !in inFlight }
        if (todo.isEmpty()) return
        inFlight += todo.map { it.key }
        viewModelScope.launch {
            busy = true
            val started = System.currentTimeMillis()
            val scan = importSource == ImportSource.SCAN
            if (scan) repo.scryfall.holdForeground()
            // Bij een scan: eerst namen verbeteren en controleren tegen de setlijst (grotendeels zonder internet).
            val queries = if (importSource == ImportSource.SCAN) runCatching { repo.preResolve(todo.map { it.query }) }.getOrElse { todo.map { it.query } }
                else todo.map { it.query }
            val result = runCatching { repo.resolve(queries) }
            val err = result.exceptionOrNull()
            if (err is java.io.IOException) {
                // Geen internet: bewaren en opzoeken zodra er weer verbinding is.
                todo.forEach { item ->
                    val idx = pending.indexOfFirst { it.key == item.key }
                    if (idx >= 0) pending[idx] = pending[idx].copy(status = ItemStatus.WAITING)
                }
                message = str(R.string.msg_waiting_for_network)
                savePendingToDisk()
                if (scan) repo.scryfall.releaseForeground()
                inFlight -= todo.map { it.key }.toSet()
                busy = false
                return@launch
            }
            if (err != null) message = errorText(err)
            val found = result.getOrElse { List(todo.size) { null } }.toMutableList()
            lastLookupMs = System.currentTimeMillis() - started - repo.lastNamesMs
            lastNamesMs = repo.lastNamesMs
            val tImages = System.currentTimeMillis()
            // Twijfel over de print (nummer niet gelezen of anders)? Vergelijk jouw foto met de prints met die naam
            // uit de setlijst op de telefoon. Alleen als er echt meer dan één print is.
            if (importSource == ImportSource.SCAN) todo.forEachIndexed { i, item ->
                val card = found[i] ?: return@forEachIndexed
                val photo = crops[item.key]?.asAndroidBitmap() ?: return@forEachIndexed
                val q = item.query
                if (q.collectorNumber != null && card.collectorNumber.equals(q.collectorNumber, true)) return@forEachIndexed
                val cands = repo.printCandidates(card.name, q.setCode ?: card.set)
                if (cands.size < 2) return@forEachIndexed
                runCatching {
                    val best = matcher.best(photo, cands.map { it.image })
                    if (best != null && cands[best].id != card.id) repo.scryfall.cardById(cands[best].id)?.let { found[i] = it }
                }
            }
            lastImagesMs = System.currentTimeMillis() - tImages
            // Niet-Engelse kaarten: de gelezen naam controleren tegen de gedrukte naam in die taal (één vraag per kaart).
            val printedOk = mutableMapOf<Int, Boolean>()
            todo.forEachIndexed { i, item ->
                val q = item.query; val card = found[i] ?: return@forEachIndexed
                if (q.lang == null || q.printedName == null) return@forEachIndexed
                val printed = runCatching { repo.scryfall.printedName(card.set, card.collectorNumber, q.lang) }
                printed.onSuccess { n -> printedOk[item.key] = n != null && repo.namesLookAlike(q.printedName, n) }
                // Geen internet: set en nummer kloppen, dat is genoeg.
            }
            if (scan) repo.scryfall.releaseForeground()
            lastResolveMs = System.currentTimeMillis() - started
            todo.forEachIndexed { i, item ->
                val idx = pending.indexOfFirst { it.key == item.key }
                if (idx >= 0) {
                    val card = found[i]
                    val q = item.query
                    val confident = card != null && printedOk[item.key] != false && (q.name != null || q.collectorNumber != null) &&
                        (q.name == null || repo.namesLookAlike(q.name, card.name)) &&
                        (q.collectorNumber == null || card.collectorNumber.equals(q.collectorNumber, true))
                    pending[idx] = pending[idx].copy(
                        print = card?.toPrint(),
                        status = if (card != null) ItemStatus.FOUND else ItemStatus.NOT_FOUND,
                        confident = confident || q.id != null,
                    )
                }
            }
            if (pending.none { it.status == ItemStatus.WAITING }) clearSavedPending()
            // AI-hulp bij twijfel (als aan): de oranje en niet gevonden vakjes van deze scan.
            if (scan) aiAssist(todo.mapNotNull { t -> pending.firstOrNull { it.key == t.key } })
            // Losse kaarten: dezelfde kaart nog eens = één erbij in plaats van een nieuwe regel.
            if (scan && pending.isNotEmpty() && pending.all { it.slot == null }) {
                val seen = mutableMapOf<Pair<String, String>, Int>()
                pending.toList().forEach { p ->
                    val id = p.print?.id ?: return@forEach
                    val k = id to p.finish
                    val idx = seen[k]
                    if (idx == null) seen[k] = pending.indexOfFirst { it.key == p.key }
                    else {
                        val keep = pending[idx]
                        pending[idx] = keep.copy(quantity = keep.quantity + p.quantity)
                        removePending(p.key)
                        seen.keys.forEach { key -> seen[key] = pending.indexOfFirst { it.print?.id == key.first && it.finish == key.second } }
                    }
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
            inFlight -= todo.map { it.key }.toSet()
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
            messageUndo = true
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
            crops.clear(); aiMarks.clear()
            clearSavedPending()
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
        snapshot()
    }

    fun deleteEntry(entry: Entry) = viewModelScope.launch {
        repo.deleteEntry(entry, str(R.string.log_deleted, entry.quantity, nameOf(entry)))
        snapshot()
    }

    fun moveEntry(entry: Entry, qty: Int, deckId: Long?, section: String?, binder: String?, page: Int?, slot: Int?) = viewModelScope.launch {
        val where = deckId?.let { id -> decks.value.firstOrNull { it.id == id }?.name } ?: str(R.string.collection)
        repo.moveEntry(entry, qty, deckId, section, binder, page, slot, str(R.string.log_moved, qty, nameOf(entry), where))
        message = str(R.string.msg_moved, where)
        snapshot()
    }

    fun updateDeck(deck: Deck) = viewModelScope.launch { repo.updateDeck(deck, str(R.string.log_deck_edited, deck.name)) }

    fun deleteDeck(deck: Deck, keepCards: Boolean) = viewModelScope.launch {
        repo.deleteDeck(deck.id, keepCards, str(R.string.log_deck_deleted, deck.name))
        snapshot()
    }

    fun undo() = viewModelScope.launch {
        message = if (repo.undoLatest()) str(R.string.msg_undone) else str(R.string.msg_nothing_to_undo)
        snapshot()
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
    fun setScanMode(v: String) = viewModelScope.launch { settingsStore.setScanMode(v) }
    fun setBackupAsked() = viewModelScope.launch { settingsStore.setBackupAsked(true) }
    fun setShowTiming(v: Boolean) = viewModelScope.launch { settingsStore.setShowTiming(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsStore.setHaptics(v) }
    fun setShutterSound(v: Boolean) = viewModelScope.launch { settingsStore.setShutterSound(v) }
    fun setAutoCapture(v: Boolean) = viewModelScope.launch { settingsStore.setAutoCapture(v) }
    fun setAutoSpeed(v: String) = viewModelScope.launch { settingsStore.setAutoSpeed(v) }

    /** Laatste losse kaart uit de lijst halen (doorlopend scannen). */
    fun removePending(key: Int) {
        pending.removeAll { it.key == key }
        crops.remove(key); aiMarks.remove(key)
    }

    /**
     * Alles (of een deel) wissen. Eerst een back-up: in de back-upmap als die er is, anders naar [backupUri].
     */
    fun wipe(o: nl.spoonpaco.ninegrid.data.Repository.WipeOptions, backupUri: Uri?) = viewModelScope.launch {
        busy = true
        val ok = runCatching {
            val ctx = getApplication<Application>()
            val folder = settings.value.backupFolder
            when {
                backupUri != null -> {
                    val text = repo.backupToJson(repo.exportBackup())
                    ctx.contentResolver.openOutputStream(backupUri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }
                folder != null -> BackupWorker.writeBackup(ctx, repo, Uri.parse(folder))
                else -> error("no backup")
            }
            settingsStore.setLastBackup(System.currentTimeMillis())
        }.isSuccess
        if (!ok) { message = str(R.string.msg_wipe_no_backup); busy = false; return@launch }
        repo.wipe(o, str(R.string.log_wiped))
        if (o.images) images.clearAll()
        clearPending()
        message = str(R.string.msg_wiped)
        messageUndo = !o.changeLog && (o.collection || o.decks)
        busy = false
        snapshot()
    }

    /** Koersverschil: wat dezelfde kaarten nu waard zijn tegenover [days] dagen terug (zonder toegevoegde of weggedane kaarten). */
    data class PriceChange(val days: Int, val now: Double, val then: Double, val cards: Int) {
        val diff get() = now - then
        val pct get() = if (then > 0) diff / then * 100 else 0.0
    }

    var priceChanges by mutableStateOf<List<PriceChange>>(emptyList())

    private var priceChangesKey = ""

    fun computePriceChanges() = viewModelScope.launch {
        val cur = settings.value.currency
        // Uitkomst per dag bewaren: dezelfde dag, valuta en prijsupdate rekent niet opnieuw.
        val key = java.time.LocalDate.now().toString() + cur + settings.value.lastPriceUpdate
        if (key == priceChangesKey) return@launch
        priceChangesKey = key
        val list = entries.value.filter { !it.entry.proxy && it.print != null }
        val result = listOf(7, 30).mapNotNull { days ->
            val day = java.time.LocalDate.now().minusDays(days.toLong()).toString()
            val first = repo.firstPriceDay() ?: return@mapNotNull null
            if (first > day) return@mapNotNull null
            val old = repo.pricesAsOf(day)
            var now = 0.0; var then = 0.0; var n = 0
            for (e in list) {
                val p = old[e.entry.printId] ?: continue
                val oldPrint = e.print!!.copy(eur = p.eur, eurFoil = p.eurFoil, usd = p.usd, usdFoil = p.usdFoil, usdEtched = p.usdEtched)
                now += nl.spoonpaco.ninegrid.domain.Money.total(e.entry, e.print, cur)
                then += nl.spoonpaco.ninegrid.domain.Money.total(e.entry, oldPrint, cur)
                n += e.entry.quantity
            }
            if (n == 0) null else PriceChange(days, now, then, n)
        }
        priceChanges = result
    }
    /** Winner of the Week: de grootste stijging van afgelopen week (maandag tot maandag), elke maandag nieuw. */
    var winner by mutableStateOf<nl.spoonpaco.ninegrid.domain.Mover?>(null)
    var winnerWeek by mutableStateOf<java.time.LocalDate?>(null)
    private var winnerKey = ""

    fun computeWinner() = viewModelScope.launch {
        val cur = settings.value.currency
        // De week begint op de dag uit de instellingen (standaard volgens de telefoon).
        val monday = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(nl.spoonpaco.ninegrid.domain.Dates.weekStart(settings.value.dateStyle)))
        val key = monday.toString() + cur + entries.value.size
        if (key == winnerKey) return@launch
        winnerKey = key
        val first = repo.firstPriceDay()
        if (first == null || first > monday.minusDays(7).toString()) { winner = null; return@launch }
        val then = repo.pricesAsOf(monday.minusDays(7).toString())
        val now = repo.pricesAsOf(monday.toString())
        // Prijzen van maandag als "nu", zodat de winnaar de hele week dezelfde blijft.
        val list = entries.value.mapNotNull { e ->
            val p = e.print ?: return@mapNotNull null
            val n = now[p.id] ?: return@mapNotNull null
            e.entry to p.copy(eur = n.eur, eurFoil = n.eurFoil, usd = n.usd, usdFoil = n.usdFoil, usdEtched = n.usdEtched)
        }
        winner = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            nl.spoonpaco.ninegrid.domain.PriceMovers.winner(nl.spoonpaco.ninegrid.domain.PriceMovers.movers(list, then, cur))
        }
        winnerWeek = monday
    }
    fun setBreakoutAlerts(v: Boolean) = viewModelScope.launch { settingsStore.setBreakoutAlerts(v) }
    fun setFilter(screen: String, f: nl.spoonpaco.ninegrid.ui.components.CardFilter) = viewModelScope.launch { settingsStore.setFilter(screen, f.toJson()) }
    // ---------- AI-hulp ----------
    fun setAiEnabled(v: Boolean) = viewModelScope.launch { settingsStore.setAi(enabled = v) }
    fun setAiProvider(id: String) = viewModelScope.launch { settingsStore.setAi(provider = id, model = "", baseUrl = ""); aiTestResult = null }
    fun setAiModel(v: String) = viewModelScope.launch { settingsStore.setAi(model = v) }
    fun setAiBaseUrl(v: String) = viewModelScope.launch { settingsStore.setAi(baseUrl = v) }
    fun setAiLimit(v: Int) = viewModelScope.launch { settingsStore.setAi(limit = v.coerceIn(1, 1000)) }
    fun saveAiKey(v: String) { c.keyVault.save(settings.value.aiProvider, v); aiKeyVersion++ }
    var aiKeyVersion by mutableStateOf(0)
        private set
    fun hasAiKey(provider: String = settings.value.aiProvider) = c.keyVault.has(provider)
    var aiTestResult by mutableStateOf<String?>(null)
    var aiTesting by mutableStateOf(false)
        private set

    /** De ingestelde dienst, of null als AI uit staat of er geen sleutel is. */
    private fun aiClient(): nl.spoonpaco.ninegrid.data.ai.AiClient? {
        val s = settings.value
        if (!s.aiEnabled) return null
        val p = nl.spoonpaco.ninegrid.data.ai.AiProviders.byId(s.aiProvider)
        val key = c.keyVault.load(p.id) ?: return null
        return nl.spoonpaco.ninegrid.data.ai.AiClient(p, key, s.aiModel, s.aiBaseUrl)
    }

    /** Knop Test: één getekende voorbeeldkaart sturen en laten zien wat de AI leest. */
    fun testAi() = viewModelScope.launch {
        val s = settings.value
        val p = nl.spoonpaco.ninegrid.data.ai.AiProviders.byId(s.aiProvider)
        val key = c.keyVault.load(p.id)
        if (key == null) { aiTestResult = str(R.string.ai_no_key); return@launch }
        aiTesting = true
        aiTestResult = runCatching {
            val r = nl.spoonpaco.ninegrid.data.ai.AiClient(p, key, s.aiModel, s.aiBaseUrl)
                .readCard(nl.spoonpaco.ninegrid.data.ai.AiClient.testCard(), retries = nl.spoonpaco.ninegrid.data.ai.AiClient.TEST_RETRIES)
            val ok = r.name?.contains("Lightning Bolt", true) == true && r.set == "M11" && r.number == "146"
            str(if (ok) R.string.ai_test_ok else R.string.ai_test_partial, r.name ?: "?", r.set ?: "?", r.number ?: "?", r.lang ?: "?")
        }.getOrElse { str(R.string.ai_test_failed, aiErrorText(it)) }
        aiTesting = false
    }

    /** Begrijpelijke uitleg bij een AI-fout, met de ruwe melding klein erachter. */
    fun aiErrorText(e: Throwable): String {
        val name = nl.spoonpaco.ninegrid.data.ai.AiProviders.byId(settings.value.aiProvider).label
        val kind = (e as? nl.spoonpaco.ninegrid.data.ai.AiException)?.kind
            ?: if (e is java.io.IOException) nl.spoonpaco.ninegrid.data.ai.AiError.NETWORK else nl.spoonpaco.ninegrid.data.ai.AiError.OTHER
        val raw = e.message ?: e.javaClass.simpleName
        return when (kind) {
            nl.spoonpaco.ninegrid.data.ai.AiError.OVERLOADED -> str(R.string.ai_err_overloaded, name)
            nl.spoonpaco.ninegrid.data.ai.AiError.RATE_LIMIT -> str(R.string.ai_err_rate, name)
            nl.spoonpaco.ninegrid.data.ai.AiError.BAD_KEY -> str(R.string.ai_err_key, name)
            nl.spoonpaco.ninegrid.data.ai.AiError.MODEL -> str(R.string.ai_err_model, name)
            nl.spoonpaco.ninegrid.data.ai.AiError.NETWORK -> str(R.string.ai_err_network)
            nl.spoonpaco.ninegrid.data.ai.AiError.ANSWER -> str(R.string.ai_err_answer)
            nl.spoonpaco.ninegrid.data.ai.AiError.OTHER -> raw
        } + if (kind != nl.spoonpaco.ninegrid.data.ai.AiError.OTHER && kind != nl.spoonpaco.ninegrid.data.ai.AiError.NETWORK) " ($raw)".take(140) else ""
    }

    /** Wat de AI bij het scannen met een vak deed: bezig, gelukt of niet gelukt (met reden). */
    enum class AiState { BUSY, DONE, FAILED }
    data class AiMark(val state: AiState, val reason: String? = null)
    val aiMarks = androidx.compose.runtime.mutableStateMapOf<Int, AiMark>()
    val aiBusyCount get() = aiMarks.values.count { it.state == AiState.BUSY }

    /** AI-verzoeken van deze sessie die zijn tegengehouden door het maximum per dag. */
    private var aiLimitWarned = false
    private val aiTried = mutableSetOf<Int>()

    /**
     * AI bij twijfel: alleen voor oranje of niet gevonden vakjes, één uitsnede per kaart. Wat de AI leest
     * wordt bij Scryfall opgezocht; alleen als naam, set en nummer daar kloppen wordt het vak groen.
     */
    private fun aiAssist(items: List<PendingItem>) = viewModelScope.launch {
        val client = aiClient() ?: return@launch
        val todo = items.filter { (it.status == ItemStatus.NOT_FOUND || !it.confident) && it.key !in aiTried && crops[it.key] != null }
        if (todo.isEmpty()) return@launch
        todo.forEach { aiMarks[it.key] = AiMark(AiState.BUSY) }
        var helped = 0
        for ((i, item) in todo.withIndex()) {
            aiTried += item.key
            if (!settingsStore.countAiUse()) {
                val why = str(R.string.ai_limit_reached)
                if (!aiLimitWarned) { message = why; aiLimitWarned = true }
                todo.drop(i).forEach { aiMarks[it.key] = AiMark(AiState.FAILED, why) }
                break
            }
            val bmp = crops[item.key]?.asAndroidBitmap() ?: run { aiMarks[item.key] = AiMark(AiState.FAILED, str(R.string.ai_mark_unreadable)); null } ?: continue
            val attempt = runCatching { client.readCard(bmp) }
            if (attempt.isFailure) {
                // Lukt het na het opnieuw proberen niet, dan geldt dat ook voor de rest: niet verder vragen.
                val why = aiErrorText(attempt.exceptionOrNull()!!)
                message = str(R.string.ai_failed, why)
                todo.drop(i).forEach { aiMarks[it.key] = AiMark(AiState.FAILED, why) }
                break
            }
            val read = attempt.getOrThrow()
            val lang = nl.spoonpaco.ninegrid.domain.ScanParser.scryfallLang(read.lang)
            val q = when {
                read.set != null && read.number != null -> if (lang == null) CardQuery(setCode = read.set, collectorNumber = read.number, name = read.name) else CardQuery(setCode = read.set, collectorNumber = read.number, lang = lang, printedName = read.name)
                read.name != null && lang == null -> CardQuery(setCode = read.set, name = read.name)
                else -> null
            }
            if (q == null) { aiMarks[item.key] = AiMark(AiState.FAILED, str(R.string.ai_mark_unreadable)); continue }
            val card = runCatching { repo.resolve(listOf(q)).firstOrNull() }.getOrNull()
            if (card == null) { aiMarks[item.key] = AiMark(AiState.FAILED, str(R.string.ai_mark_not_at_scryfall, listOfNotNull(read.name, read.set, read.number).joinToString(" "))); continue }
            val nameOk = lang != null || read.name == null || repo.namesLookAlike(read.name, card.name)
            val numberOk = read.number == null || card.collectorNumber.equals(read.number, true)
            val idx = pending.indexOfFirst { it.key == item.key }
            if (idx < 0) { aiMarks.remove(item.key); continue }
            val sure = nameOk && numberOk && read.set != null
            pending[idx] = pending[idx].copy(
                print = card.toPrint(), status = ItemStatus.FOUND,
                query = q, confident = sure,
                language = read.lang?.takeIf { it != "EN" } ?: pending[idx].language,
            )
            aiMarks[item.key] = if (sure) AiMark(AiState.DONE) else AiMark(AiState.FAILED, str(R.string.ai_mark_unsure))
            helped++
        }
        if (helped > 0) message = str(R.string.ai_helped, helped)
    }

    fun setDecksSort(v: String) = viewModelScope.launch { settingsStore.setDecksSort(v) }
    fun setCompletionist(v: Boolean) = viewModelScope.launch { settingsStore.setCompletionist(v) }
    fun setCollectionSort(v: String) = viewModelScope.launch { settingsStore.setCollectionSort(v) }
    fun setTimeFormat(v: String) = viewModelScope.launch { settingsStore.setTimeFormat(v) }
    fun setDateFormat(v: String) = viewModelScope.launch { settingsStore.setDateFormat(v) }
    fun setWeekStart(v: String) = viewModelScope.launch { settingsStore.setWeekStart(v); winnerKey = ""; computeWinner() }
    fun setShowWeekday(v: Boolean) = viewModelScope.launch { settingsStore.setShowWeekday(v) }

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
        when (val r = UpdateChecker.check()) {
            is nl.spoonpaco.ninegrid.data.UpdateResult.Available -> update = r.info
            nl.spoonpaco.ninegrid.data.UpdateResult.UpToDate -> if (manual) message = str(R.string.msg_up_to_date)
            is nl.spoonpaco.ninegrid.data.UpdateResult.Failed -> if (manual) message = when (r.reason) {
                "offline" -> str(R.string.msg_update_offline)
                "GitHub 404" -> str(R.string.msg_update_not_found)
                else -> str(R.string.msg_update_failed, r.reason)
            }
        }
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
        loadSavedPending()
    }

    // ---------- Importeren uit de Sheet (.xlsx of .csv) ----------
    enum class ImportStep { IDLE, READING, MAPPING, READY, RESOLVING, SAVING, DONE, FAILED }

    var importStep by mutableStateOf(ImportStep.IDLE)
    var importData by mutableStateOf<nl.spoonpaco.ninegrid.domain.ImportData?>(null)
    var importProgress by mutableStateOf(0f)
    var importNotFound by mutableStateOf<List<nl.spoonpaco.ninegrid.domain.ImportCard>>(emptyList())
    var importWrongNumber by mutableStateOf<List<nl.spoonpaco.ninegrid.domain.ImportCard>>(emptyList())
    var importResult by mutableStateOf("")

    private var importUri: Uri? = null
    private var importName = ""
    /** Zelf aangewezen kolommen voor een onbekend formaat (op kolomkoppen onthouden). */
    var importMapping by mutableStateOf<Map<Int, nl.spoonpaco.ninegrid.domain.SheetImport.Role>>(emptyMap())

    private val mappingPrefs get() = getApplication<Application>().getSharedPreferences("import_mappings", android.content.Context.MODE_PRIVATE)

    private fun savedMapping(headers: List<String>): Map<Int, nl.spoonpaco.ninegrid.domain.SheetImport.Role>? =
        mappingPrefs.getString(headers.joinToString("|"), null)?.split(",")?.mapNotNull { p ->
            val (i, r) = p.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            i.toIntOrNull()?.let { it to nl.spoonpaco.ninegrid.domain.SheetImport.Role.valueOf(r) }
        }?.toMap()

    fun readImport(uri: Uri, mapping: Map<Int, nl.spoonpaco.ninegrid.domain.SheetImport.Role>? = null) = viewModelScope.launch {
        importStep = ImportStep.READING
        importNotFound = emptyList()
        importUri = uri
        runCatching {
            val ctx = getApplication<Application>()
            var name = androidx.documentfile.provider.DocumentFile.fromSingleUri(ctx, uri)?.name ?: "import.xlsx"
            // Een Google Sheet uit Drive is een "virtueel" bestand: Android levert hem dan als Excel aan.
            val xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            val virtual = runCatching { ctx.contentResolver.getStreamTypes(uri, "*/*")?.contains(xlsx) == true && ctx.contentResolver.getType(uri)?.startsWith("application/vnd.google-apps") == true }.getOrDefault(false)
            if (virtual && !name.endsWith(".xlsx")) name += ".xlsx"
            importName = name
            fun open(): java.io.InputStream =
                if (virtual) ctx.contentResolver.openTypedAssetFileDescriptor(uri, xlsx, null)!!.createInputStream()
                else ctx.contentResolver.openInputStream(uri)!!
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val first = open().use { nl.spoonpaco.ninegrid.domain.SheetImport.read(name, it, mapping) }
                // Onbekend formaat dat je eerder al aanwees? Dan die indeling meteen gebruiken.
                val saved = if (first.needsMapping && mapping == null) savedMapping(first.headers) else null
                if (saved != null) open().use { nl.spoonpaco.ninegrid.domain.SheetImport.read(name, it, saved) } else first
            }
        }.onSuccess {
            importData = it
            importStep = when {
                it.needsMapping -> { importMapping = emptyMap(); ImportStep.MAPPING }
                it.collection.isEmpty() && it.decks.isEmpty() -> ImportStep.FAILED
                else -> ImportStep.READY
            }
        }.onFailure { importStep = ImportStep.FAILED }
    }

    /** Zelf aangewezen kolommen toepassen en onthouden. */
    fun applyMapping() {
        val uri = importUri ?: return
        val headers = importData?.headers.orEmpty()
        mappingPrefs.edit().putString(headers.joinToString("|"), importMapping.entries.joinToString(",") { "${it.key}=${it.value.name}" }).apply()
        readImport(uri, importMapping)
    }

    fun cancelImport() {
        if (importStep == ImportStep.DONE) nl.spoonpaco.ninegrid.data.ImportWorker.clearOutcome(getApplication())
        importStep = ImportStep.IDLE; importData = null; importNotFound = emptyList()
    }

    /** Een lopende import stoppen (wat al is opgezocht, wordt niet opgeslagen). */
    fun stopImport() {
        androidx.work.WorkManager.getInstance(getApplication()).cancelUniqueWork(nl.spoonpaco.ninegrid.data.ImportWorker.NAME)
        importStep = ImportStep.IDLE
    }

    /** Tot wanneer Scryfall om pauze vroeg (voor de aftelling), 0 = niet. */
    var importWaitUntil by mutableStateOf(0L)
    var importFound by mutableStateOf(0)
    var importTotal by mutableStateOf(0)
    var importPass by mutableStateOf(1)
    /** Via de melding geopend: meteen naar Importeren. */
    var openImport by mutableStateOf(false)

    /** Start de import als achtergrondtaak; je kunt de app intussen gebruiken of sluiten. */
    fun runImport(replace: Boolean, asDeck: String? = null, skipOffer: Boolean = false) {
        val data = importData ?: return
        // Grote import: eerst aanbieden om de offline kaartdatabase te gebruiken (één keer).
        val n = data.collection.size + data.decks.sumOf { it.cards.size }
        val s = settings.value
        if (!skipOffer && n > 500 && s.offlineDb == nl.spoonpaco.ninegrid.data.OfflineDb.OFF && !s.offlineDbOffered) {
            pendingImport = replace to asDeck
            offerOfflineDb = OfflineOffer.IMPORT
            return
        }
        nl.spoonpaco.ninegrid.data.ImportWorker.start(getApplication(), nl.spoonpaco.ninegrid.data.ImportJob(data, replace, asDeck))
        importStep = ImportStep.RESOLVING
        importProgress = 0f
    }

    // ---------- Offline kaartdatabase ----------
    enum class OfflineOffer { IMPORT, THROTTLED }
    var offerOfflineDb by mutableStateOf<OfflineOffer?>(null)
    private var pendingImport: Pair<Boolean, String?>? = null
    var offlineDbRunning by mutableStateOf(false)
        private set
    var offlineDbProgress by mutableStateOf(0)
        private set
    /** Voortgang in procenten (-1 = onbekend). */
    var offlineDbPct by mutableStateOf(-1)
        private set
    /** Klaargezet, maar nog niet begonnen: wacht op wifi (of op het opladen). */
    var offlineDbWaiting by mutableStateOf(false)
        private set
    var offlineDbFailed by mutableStateOf(false)
        private set
    var offlineDbInfo by mutableStateOf<nl.spoonpaco.ninegrid.data.offline.BulkInfo?>(null)
        private set
    var offlineDbBytes by mutableStateOf(0L)
        private set

    init { watchOfflineDb() }

    fun loadOfflineDbInfo() = viewModelScope.launch {
        if (offlineDbInfo == null) offlineDbInfo = nl.spoonpaco.ninegrid.data.offline.OfflineDbWorker.fetchInfo()
        offlineDbBytes = c.offline.sizeBytes()
    }

    /** Uit / bij nieuwe set / handmatig. Aanzetten downloadt meteen (op wifi); uitzetten haalt alles weg. */
    fun setOfflineDb(mode: String) = viewModelScope.launch {
        val before = settings.value.offlineDb
        settingsStore.setOfflineDb(mode)
        settingsStore.setOfflineDbOffered(true)
        if (mode == nl.spoonpaco.ninegrid.data.OfflineDb.OFF) {
            nl.spoonpaco.ninegrid.data.offline.OfflineDbWorker.cancel(getApplication())
            c.offline.delete()
            settingsStore.setOfflineDbDone(0, 0)
            offlineDbBytes = 0
        } else if (before == nl.spoonpaco.ninegrid.data.OfflineDb.OFF) {
            updateOfflineDbNow()
        }
    }

    /** Vraag om bevestiging voor een download via mobiele data (met de grootte). */
    var confirmMobileDownload by mutableStateOf(false)

    /** Nu bijwerken. Op mobiel netwerk met "ook mobiele data" aan: eerst bevestigen; anders wachten op wifi. */
    fun updateOfflineDbNow(confirmed: Boolean = false) {
        val s = settings.value
        val metered = c.images.onMeteredNetwork()
        if (s.offlineDbMobile && metered && !confirmed) {
            confirmMobileDownload = true
            loadOfflineDbInfo()
            return
        }
        confirmMobileDownload = false
        nl.spoonpaco.ninegrid.data.offline.OfflineDbWorker.start(getApplication(), manual = true, anyNetwork = s.offlineDbMobile && metered)
    }
    fun setOfflineDbMobile(v: Boolean) = viewModelScope.launch { settingsStore.setOfflineDbMobile(v) }
    fun setOfflineDbMobileAuto(v: Boolean) = viewModelScope.launch { settingsStore.setOfflineDbMobileAuto(v) }

    /** Antwoord op het aanbod: aanzetten (bij een import: daarna pas importeren) of niet. */
    fun answerOfflineOffer(enable: Boolean) = viewModelScope.launch {
        val offer = offerOfflineDb
        offerOfflineDb = null
        settingsStore.setOfflineDbOffered(true)
        if (enable) setOfflineDb(nl.spoonpaco.ninegrid.data.OfflineDb.NEW_SET)
        if (offer == OfflineOffer.IMPORT) {
            val p = pendingImport
            if (!enable && p != null) { pendingImport = null; runImport(p.first, p.second, skipOffer = true) }
            // Met aanzetten: de import start zodra de download klaar is (watchOfflineDb).
        }
    }

    private fun watchOfflineDb() = viewModelScope.launch {
        val wm = androidx.work.WorkManager.getInstance(getApplication())
        wm.getWorkInfosForUniqueWorkFlow(nl.spoonpaco.ninegrid.data.offline.OfflineDbWorker.NAME).collect { infos ->
            val w = infos.firstOrNull() ?: return@collect
            offlineDbRunning = w.state == androidx.work.WorkInfo.State.RUNNING
            offlineDbWaiting = w.state == androidx.work.WorkInfo.State.ENQUEUED || w.state == androidx.work.WorkInfo.State.BLOCKED
            offlineDbProgress = w.progress.getInt("count", if (offlineDbRunning) offlineDbProgress else 0)
            offlineDbPct = w.progress.getInt("pct", -1)
            offlineDbFailed = w.state == androidx.work.WorkInfo.State.FAILED
            if (w.state.isFinished) {
                offlineDbBytes = c.offline.sizeBytes()
                pendingImport?.let { p -> pendingImport = null; runImport(p.first, p.second, skipOffer = true) }
            }
        }
    }

    private fun watchImport() = viewModelScope.launch {
        val wm = androidx.work.WorkManager.getInstance(getApplication())
        wm.getWorkInfosForUniqueWorkFlow(nl.spoonpaco.ninegrid.data.ImportWorker.NAME).collect { infos ->
            val w = infos.firstOrNull() ?: return@collect
            when (w.state) {
                androidx.work.WorkInfo.State.RUNNING, androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED -> {
                    importStep = ImportStep.RESOLVING
                    importFound = w.progress.getInt("done", importFound)
                    importTotal = w.progress.getInt("total", importTotal)
                    importPass = w.progress.getInt("pass", importPass)
                    importProgress = if (importTotal == 0) 0f else importFound / importTotal.toFloat()
                }
                androidx.work.WorkInfo.State.SUCCEEDED, androidx.work.WorkInfo.State.FAILED -> {
                    val o = nl.spoonpaco.ninegrid.data.ImportWorker.outcome(getApplication())
                    if (o != null) {
                        importNotFound = o.notFound
                        importWrongNumber = o.wrongNumber
                        importResult = if (o.failed != null) str(R.string.msg_import_failed) else str(R.string.import_done, o.cards, o.decks, o.notFound.sumOf { it.quantity })
                        importStep = ImportStep.DONE
                    }
                }
                else -> Unit
            }
        }
    }

    /** Aftelling bijwerken als Scryfall om pauze vraagt. */
    private fun watchThrottle() = viewModelScope.launch {
        while (true) {
            importWaitUntil = repo.scryfall.throttledUntil
            kotlinx.coroutines.delay(1000)
        }
    }

    /** Niet gevonden kaarten als plaklijst, om ze later via Lijst plakken toe te voegen. */
    fun notFoundAsList(): String = importNotFound.joinToString("\n") { c ->
        "${c.quantity} ${c.name} (${c.setCode}) ${c.number}" + (if (c.etched) " *E*" else if (c.foil) " *F*" else "")
    }

    // ---------- Set compleet ----------
    var setList by mutableStateOf<nl.spoonpaco.ninegrid.data.SetList?>(null)
    var setLoading by mutableStateOf(false)

    fun loadSet(code: String, refresh: Boolean = false) = viewModelScope.launch {
        setLoading = true
        setList = c.cache.cachedSetList(code)?.takeIf { !refresh }
        runCatching { c.cache.setList(code, refresh) }.onSuccess { setList = it }.onFailure { if (setList == null) message = errorText(it) }
        setLoading = false
    }

    /** Een ontbrekende kaart toevoegen: opzoeken en naar het controlescherm. */
    fun addMissing(id: String, onReady: () -> Unit) = viewModelScope.launch {
        busy = true
        runCatching { repo.scryfall.cardById(id) }.onSuccess { card ->
            if (card != null) { startSingle(card.toPrint()); onReady() }
        }.onFailure { message = errorText(it) }
        busy = false
    }

    // ---------- Combo's (Commander Spellbook) ----------
    var comboReport by mutableStateOf<nl.spoonpaco.ninegrid.data.ComboReport?>(null)
    var comboDeckId by mutableStateOf<Long?>(null)
    var comboLoading by mutableStateOf(false)
    /** Deck veranderd sinds de laatste zoekactie: oude uitkomst tonen met "zoek opnieuw". */
    var comboStale by mutableStateOf(false)

    private fun deckLists(deckId: Long): Pair<List<Pair<String, Int>>, List<Pair<String, Int>>> {
        val list = entries.value.filter { it.entry.deckId == deckId && it.print != null }
        fun group(l: List<EntryWithPrint>) = l.groupBy { it.print!!.name }.map { (n, v) -> n to v.sumOf { it.entry.quantity } }
        return group(list.filter { it.entry.section == Section.COMMANDER }) to group(list.filter { it.entry.section != Section.COMMANDER && it.entry.section != Section.SIDEBOARD })
    }

    /** Bewaarde uitkomst laden als het deck niet veranderde. */
    fun loadCachedCombos(deckId: Long) {
        val (cmd, main) = deckLists(deckId)
        comboDeckId = deckId
        val cached = c.combos.cached(deckId, c.combos.hashOf(cmd, main))
        comboReport = cached?.report
        comboStale = cached?.stale == true
    }

    fun findCombos(deckId: Long) = viewModelScope.launch {
        val (cmd, main) = deckLists(deckId)
        comboLoading = true
        comboDeckId = deckId
        runCatching { c.combos.find(deckId, cmd, main) }
            .onSuccess { comboReport = it; comboStale = false }
            .onFailure { message = if (it is java.io.IOException && it.message?.startsWith("Commander") != true) str(R.string.msg_network_error) else str(R.string.msg_combo_failed, it.message ?: "") }
        comboLoading = false
    }

    /** Ligt deze kaart (los) in je collectie? Geeft het exemplaar terug, of null. */
    fun inCollectionByName(name: String) = entries.value.firstOrNull { it.entry.deckId == null && !it.entry.proxy && it.print?.name.equals(name, true) }

    init {
        watchImport()
        watchThrottle()
    }

    // ---------- Suggesties per deck ----------
    private val rulesPrefs get() = getApplication<Application>().getSharedPreferences("deck_rules", android.content.Context.MODE_PRIVATE)
    private val rulesJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Eigen regels van een deck; staat er nog niets, dan raadt de app het creaturetype van de commander. */
    fun deckRules(deckId: Long): nl.spoonpaco.ninegrid.domain.DeckRules =
        rulesPrefs.getString(deckId.toString(), null)?.let { runCatching { rulesJson.decodeFromString(nl.spoonpaco.ninegrid.domain.DeckRules.serializer(), it) }.getOrNull() }
            ?: nl.spoonpaco.ninegrid.domain.DeckRules(tribe = nl.spoonpaco.ninegrid.domain.Suggestions.guessTribe(deckCards(deckId)))

    fun setDeckRules(deckId: Long, r: nl.spoonpaco.ninegrid.domain.DeckRules) {
        rulesPrefs.edit().putString(deckId.toString(), rulesJson.encodeToString(nl.spoonpaco.ninegrid.domain.DeckRules.serializer(), r)).apply()
        computeSuggestions(deckId)
    }

    private fun deckCards(deckId: Long) = Formats.toDeckCards(entries.value.filter { it.entry.deckId == deckId }.map { it.entry to it.print })

    var suggestions by mutableStateOf<nl.spoonpaco.ninegrid.domain.SuggestionReport?>(null)
    var suggestionsDeckId by mutableStateOf<Long?>(null)
    var suggestionsFailed by mutableStateOf(false)

    fun computeSuggestions(deckId: Long) = viewModelScope.launch {
        val deck = decks.value.firstOrNull { it.id == deckId } ?: return@launch
        val rules = deckRules(deckId)
        val all = entries.value
        val deckNames = decks.value.associate { it.id to it.name }
        suggestionsFailed = false
        val report = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val mine = all.filter { it.entry.deckId == deckId }
            val pool = all.filter { e ->
                e.print != null && !e.entry.proxy && e.entry.deckId != deckId && (e.entry.deckId == null || rules.includeOtherDecks)
            }.map { e ->
                val p = e.print!!
                nl.spoonpaco.ninegrid.domain.Candidate(e.entry.id, p.name, p.typeLine, p.oracleText, p.manaCost, p.cmc, p.colorIdentity, p.legalities, p.gameChanger,
                    e.entry.deckId?.let { deckNames[it] }, p.producedMana)
            }
            nl.spoonpaco.ninegrid.domain.Suggestions.suggest(
                deck.format, Formats.toDeckCards(mine.map { it.entry to it.print }),
                mine.filter { it.print != null }.associate { it.print!!.name to it.entry.id }, pool, rules,
            )
        } }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; android.util.Log.e("NineGrid", "suggesties", it) }.getOrNull()
        suggestionsFailed = report == null
        suggestions = report
        suggestionsDeckId = deckId
    }

    // ---------- AI bij decksuggesties ----------
    data class AiSwap(val inName: String, val outName: String?, val reason: String)
    data class AiBuy(val print: CardPrint, val outName: String?, val reason: String)
    data class AiDeckResult(val deckId: Long, val owned: List<AiSwap>, val buy: List<AiBuy>, val dropped: Int)
    var aiDeck by mutableStateOf<AiDeckResult?>(null)
        private set
    var aiDeckBusy by mutableStateOf(false)
        private set
    var aiDeckError by mutableStateOf<String?>(null)
        private set

    /**
     * Vraag de AI om ruilvoorstellen. Alleen tekst gaat weg: decklijst, format, regels en (uit je collectie) de
     * kandidaten die al door de controle kwamen. Elk voorstel wordt daarna gecontroleerd; wat niet past valt weg.
     */
    fun askAiDeck(deckId: Long, outside: Boolean) = viewModelScope.launch {
        val client = aiClient() ?: run { aiDeckError = str(R.string.ai_no_key_or_off); return@launch }
        val deck = decks.value.firstOrNull { it.id == deckId } ?: return@launch
        if (!settingsStore.countAiUse()) { aiDeckError = str(R.string.ai_limit_reached); return@launch }
        aiDeckBusy = true; aiDeckError = null
        val rules = deckRules(deckId)
        val all = entries.value
        val mine = all.filter { it.entry.deckId == deckId }
        val deckCards = Formats.toDeckCards(mine.map { it.entry to it.print })
        val allowed = nl.spoonpaco.ninegrid.domain.Suggestions.allowedFilter(deck.format, deckCards, rules)
        fun cand(id: Long, p: CardPrint) = nl.spoonpaco.ninegrid.domain.Candidate(id, p.name, p.typeLine, p.oracleText, p.manaCost, p.cmc, p.colorIdentity, p.legalities, p.gameChanger, null, p.producedMana)
        val deckNames = mine.mapNotNull { it.print?.name }.toSet()
        val pool = all.filter { e -> e.print != null && !e.entry.proxy && e.entry.deckId != deckId && (e.entry.deckId == null || rules.includeOtherDecks) }
            .distinctBy { it.print!!.name }.filter { it.print!!.name !in deckNames && allowed(cand(it.entry.id, it.print!!)) }
            .sortedByDescending { it.print!!.eur ?: 0.0 }.take(300)
        val poolByName = pool.associateBy { it.print!!.name.lowercase() }
        val dutch = java.util.Locale.getDefault().language == "nl"
        val prompt = buildString {
            appendLine("You help improve a Magic: The Gathering deck. Format: ${deck.format}.")
            mine.firstOrNull { it.entry.section == Section.COMMANDER }?.print?.let { appendLine("Commander: ${it.name}.") }
            if (rules.tribe.isNotBlank()) appendLine("Creature type to focus on: ${rules.tribe}.")
            if (rules.maxBracket > 0) appendLine("Stay within Commander bracket ${rules.maxBracket} or lower.")
            appendLine("Deck list (quantity name, section):")
            mine.forEach { e -> e.print?.let { appendLine("${e.entry.quantity} ${it.name} (${e.entry.section ?: "MAIN"})") } }
            if (outside) {
                appendLine("Suggest up to 8 swaps that make the deck clearly better. You may use cards from this list the player owns, or any other real card:")
            } else {
                appendLine("Suggest up to 8 swaps that make the deck clearly better, using ONLY cards from this list the player owns:")
            }
            pool.forEach { appendLine("- " + it.print!!.name) }
            appendLine("For each swap name the card to cut from the deck (exact name from the deck list, never the commander), or null to just add.")
            appendLine("Answer ONLY with JSON: {\"swaps\":[{\"in\":\"card name\",\"out\":\"card name or null\",\"reason\":\"one short sentence\"}]}.")
            appendLine(if (dutch) "Write the reason in Dutch." else "Write the reason in English.")
        }
        val answer = runCatching { client.ask(prompt, null, maxTokens = 1200) }
        val text = answer.getOrElse { e -> aiDeckError = str(R.string.ai_failed, aiErrorText(e)); aiDeckBusy = false; return@launch }
        val swaps = runCatching {
            nl.spoonpaco.ninegrid.data.ai.AiClient.extractJson(text)!!.jsonObjectOrNull()?.get("swaps")?.let { it as kotlinx.serialization.json.JsonArray }.orEmpty()
                .mapNotNull { el ->
                    val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    fun f(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }
                    val inName = f("in") ?: return@mapNotNull null
                    Triple(inName, f("out"), f("reason") ?: "")
                }
        }.getOrElse { emptyList() }
        val commanderName = mine.firstOrNull { it.entry.section == Section.COMMANDER }?.print?.name
        val owned = mutableListOf<AiSwap>(); val buy = mutableListOf<AiBuy>(); var dropped = 0
        for ((inName, outRaw, reason) in swaps) {
            val outName = outRaw?.let { o -> deckNames.firstOrNull { it.equals(o, true) || it.substringBefore(" // ").equals(o, true) } }?.takeIf { it != commanderName }
            if (outRaw != null && outName == null) { dropped++; continue }
            val ownedHit = poolByName[inName.lowercase()]
            if (ownedHit != null) { owned += AiSwap(ownedHit.print!!.name, outName, reason); continue }
            if (!outside || inName in deckNames) { dropped++; continue }
            // Daarbuiten: bestaat de kaart, en past hij in kleur, format en bracket?
            val card = runCatching { repo.scryfall.cardByName(inName, null) }.getOrNull()
            val p = card?.toPrint()
            if (p == null || p.name in deckNames || !allowed(cand(-1, p))) { dropped++; continue }
            buy += AiBuy(p, outName, reason)
        }
        aiDeck = AiDeckResult(deckId, owned, buy, dropped)
        aiDeckBusy = false
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() = this as? kotlinx.serialization.json.JsonObject

    /**
     * Ruil in een deck als één wijziging: [ins] gaan het deck in (main), [outs] gaan terug naar de collectie.
     * Paren zijn (exemplaar-id, aantal). Eén keer ongedaan maken zet alles terug.
     */
    fun swapCards(deckId: Long, ins: List<Pair<Long, Int>>, outs: List<Pair<Long, Int>>) = viewModelScope.launch {
        if (ins.isEmpty() && outs.isEmpty()) return@launch
        val all = entries.value.associateBy { it.entry.id }
        fun names(l: List<Pair<Long, Int>>) = l.groupBy { all[it.first]?.print?.name ?: "?" }.map { (n, v) -> "${v.sumOf { it.second }}× $n" }.joinToString(", ")
        val deckName = decks.value.firstOrNull { it.id == deckId }?.name ?: ""
        val text = listOfNotNull(names(ins).takeIf { ins.isNotEmpty() }?.let { "+ $it" }, names(outs).takeIf { outs.isNotEmpty() }?.let { "− $it" }).joinToString("  ")
        val moves = ins.map { nl.spoonpaco.ninegrid.data.Repository.Move(it.first, it.second, deckId, Section.MAIN) } +
            outs.map { nl.spoonpaco.ninegrid.data.Repository.Move(it.first, it.second, null) }
        runCatching { repo.moveMany(moves, str(R.string.log_swap, deckName, text)) }
            .onSuccess { message = str(R.string.msg_swapped) }
            .onFailure { message = str(R.string.msg_swap_failed) }
        snapshot()
        kotlinx.coroutines.delay(300); computeSuggestions(deckId)
    }

}
