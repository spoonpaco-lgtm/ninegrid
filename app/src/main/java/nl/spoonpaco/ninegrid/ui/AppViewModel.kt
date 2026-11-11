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
import nl.spoonpaco.ninegrid.BuildConfig
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
    /** Leeg vak op een blad (op de foto geen kaart te zien): telt niet als controleren. */
    val empty: Boolean = false,
    /** Naam, set en nummer kloppen met elkaar: kan zonder controle worden opgeslagen. */
    val confident: Boolean = false,
    /** De scanner las een ster (★) in de verzamelregel en zette de kaart daarom op foil: even controleren. */
    val foilHint: Boolean = false,
) {
    /** Oranje in de scanner: gevonden, maar nog even naar kijken. */
    val needsCheck get() = !confident || foilHint
}

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
    /** Eigen tekst voor de knop Ongedaan maken (bijv. "Terughalen uit ballingschap"); null = standaard. */
    var messageUndoLabel by mutableStateOf<String?>(null)
    /** Wat de knop in de melding doet; null = de laatste wijziging ongedaan maken. */
    var messageAction by mutableStateOf<(() -> Unit)?>(null)
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
        if (importSource == ImportSource.SCAN) pending.indices.forEach { i -> if (pending[i].slot != null) pending[i] = pending[i].copy(finish = if (v) Finish.FOIL else Finish.NONFOIL, foilHint = false) }
    }

    /** Eén vak van het blad foil of niet (tikken op het vak in de camera). */
    fun toggleSlotFoil(slot: Int) {
        val i = pending.indexOfFirst { it.slot == slot }
        if (i < 0) return
        val p = pending[i]
        // Een foil die de scanner zelf aanzette (★ gelezen): de eerste tik bevestigt hem, pas de volgende zet hem uit.
        pending[i] = if (p.foilHint) p.copy(foilHint = false) else p.copy(finish = if (p.finish == Finish.NONFOIL) Finish.FOIL else Finish.NONFOIL)
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
            val emptySlot = !single && r.looksEmpty && r.name == null && !r.complete
            val starFoil = r.foilStar && !emptySlot
            slotCrops[r.slot]?.let { crops[key] = it.asImageBitmapSafe() }
            pending += PendingItem(
                key = key,
                slot = if (single) null else r.slot,
                empty = emptySlot,
                include = !emptySlot,
                label = if (emptySlot) str(R.string.scan_slot_empty)
                    else if (readParts.isEmpty() && r.lines.isEmpty()) str(R.string.scan_nothing_read)
                    else str(R.string.scan_read_as, (readParts.ifEmpty { r.lines.take(2).map { it.take(22) } }).joinToString(" · ")) +
                        (if (starFoil) " · " + str(R.string.scan_foil_star) else ""),
                query = if (lang == null) CardQuery(setCode = set, collectorNumber = r.collectorNumber, name = r.name, altNames = r.altNames)
                    else CardQuery(setCode = set, collectorNumber = r.collectorNumber, lang = lang, printedName = r.name),
                status = if (canLook) ItemStatus.RESOLVING else ItemStatus.NOT_FOUND,
                language = r.language ?: "",
                // Bij een blad begint elk vak gewoon; foil zet je in het voorbeeld. Bij één kaart telt de knop.
                // Een ster (★) in de verzamelregel betekent foil: alvast aangezet, maar oranje om te controleren.
                finish = if ((scanFoil && single) || starFoil) Finish.FOIL else Finish.NONFOIL,
                foilHint = starFoil && !(scanFoil && single),
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
            val emptySlots = pending.count { it.empty }
            return ScanSummary(
                busy = pending.any { it.status == ItemStatus.RESOLVING },
                found = pending.count { it.status == ItemStatus.FOUND },
                empty = emptySlots,
                doubtful = pending.count { (it.status == ItemStatus.FOUND && it.needsCheck) || it.status == ItemStatus.NOT_FOUND } - emptySlots,
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
                fillIncomplete()
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
                    // Een opgegeven of gelezen set is een grens: staat de kaart in een andere set, dan altijd oranje, met de gevonden set erbij.
                    val otherSet = scan && card != null && nl.spoonpaco.ninegrid.domain.SetCheck.otherSet(q.setCode, card.set)
                    // Geen nummer gelezen en meerdere prints met die naam in de set (gewoon, borderless, showcase): dat is gokken, dus controleren.
                    val ambiguous = scan && card != null && nl.spoonpaco.ninegrid.domain.SetCheck.ambiguous(q.collectorNumber != null, repo.printCandidates(card.name, q.setCode ?: card.set).size)
                    val confident = card != null && printedOk[item.key] != false && (q.name != null || q.collectorNumber != null) &&
                        (q.name == null || repo.namesLookAlike(q.name, card.name)) &&
                        (q.collectorNumber == null || card.collectorNumber.equals(q.collectorNumber, true)) &&
                        !otherSet && !ambiguous
                    pending[idx] = pending[idx].copy(
                        label = if (otherSet) pending[idx].label + " · " + str(R.string.scan_other_set, q.setCode!!.uppercase(), card!!.set.uppercase())
                            else if (ambiguous) pending[idx].label + " · " + str(R.string.scan_ambiguous_print) else pending[idx].label,
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

    /** De exemplaren die voor deze kaart uit de collectie gehaald zouden worden, in volgorde (zelfde regels als bij opslaan). */
    private fun takeCandidates(print: CardPrint, finish: String): List<nl.spoonpaco.ninegrid.data.db.Entry> {
        val all = entries.value
        return nl.spoonpaco.ninegrid.domain.DeckTake.candidates(
            nl.spoonpaco.ninegrid.data.db.Entry(printId = print.id, finish = finish), print.oracleId, Formats.isBasicLand(print.typeLine),
            all.map { it.entry }, all.mapNotNull { e -> e.print?.let { it.id to it.oracleId } }.toMap(),
        )
    }

    /** Hoeveel exemplaren van deze kaart nu uit de collectie te halen zijn (bij basic lands alleen precies deze print). */
    fun inCollection(print: CardPrint?, finish: String = Finish.NONFOIL): Int {
        if (print == null) return 0
        return takeCandidates(print, finish).sumOf { it.quantity }
    }

    /** Andere prints die bij [quantity] stuks uit de collectie gepakt worden, als tekst ("5ED 430 ×2"); leeg als het dezelfde print is. */
    fun otherPrintsTaken(print: CardPrint, finish: String, quantity: Int): String {
        val byId = entries.value.mapNotNull { it.print }.associateBy { it.id }
        return nl.spoonpaco.ninegrid.domain.DeckTake.plan(takeCandidates(print, finish), quantity)
            .filter { it.first.printId != print.id }
            .groupBy { it.first.printId }.map { (id, l) -> (byId[id]?.let { it.setCode + " " + it.collectorNumber } ?: "?") + " ×" + l.sumOf { it.second } }
            .joinToString(", ")
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
                    val fs = forcedSlot; forcedSlot = null; savedForcedSlot = fs != null
                    val list = items.map { it.toNew(deckId = null, binder = b, page = pg, slot = if (b != null && importSource == ImportSource.SCAN) (fs ?: it.slot) else null) }
                    repo.addEntries(list, str(R.string.log_added_collection, count))
                    if (boosterOn) saveBoosterFrom(list)
                    fillIncomplete()
                    message = (if (importSource == ImportSource.SCAN && b != null && pg != null) str(R.string.msg_page_saved, b, pg, count)
                    else getApplication<Application>().resources.getQuantityString(R.plurals.msg_added, count, count)) + strikeAfterSave(list)
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
                    message = (if (isNew) str(R.string.msg_deck_created, name) + " · " else "") + str(R.string.msg_taken_added, r.taken, r.added) +
                        strikeAfterSave(list.filter { !it.fromCollection }.map { it.item })
                    images.prefetch(list.map { it.item.print }, settings.value)
                    fillIncomplete()
                    onDone(r.deckId)
                }
            }
            // Alleen na een heel blad (kaarten met een vak) verder naar het volgende blad; losse kaarten blijven op dit blad.
            if (destination == Destination.COLLECTION && importSource == ImportSource.SCAN && binder.isNotBlank() && !savedForcedSlot && items.any { it.slot != null }) {
                val done = page.toIntOrNull() ?: 0
                page = (done + 1).toString()
                pageAdvanced = Triple(binder, done, repo.latestChangeId())
            } else pageAdvanced = null
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
        messageUndo = true
        messageUndoLabel = str(R.string.undo_exile)
        message = str(R.string.msg_exiled, nameOf(entry))
        snapshot()
    }

    fun moveEntry(entry: Entry, qty: Int, deckId: Long?, section: String?, binder: String?, page: Int?, slot: Int?) = viewModelScope.launch {
        val where = deckId?.let { id -> decks.value.firstOrNull { it.id == id }?.name } ?: str(R.string.collection)
        repo.moveEntry(entry, qty, deckId, section, binder, page, slot, str(R.string.log_moved, qty, nameOf(entry), where))
        message = str(R.string.msg_moved, where)
        snapshot()
    }

    /** Wisselt de commander van een deck (of voegt een tweede toe); de kleuren, bracket en regels volgen vanzelf uit de kaarten. */
    fun changeCommander(deck: Deck, newEntry: Entry?, newPrint: CardPrint?, replace: Boolean, old: nl.spoonpaco.ninegrid.data.Repository.OldCommander) = viewModelScope.launch {
        val current = entries.value.filter { it.entry.deckId == deck.id && it.entry.section == Section.COMMANDER }.map { it.entry.id }
        val name = newPrint?.name ?: entries.value.firstOrNull { it.entry.id == newEntry?.id }?.print?.name ?: ""
        repo.changeCommander(deck.id, newEntry?.id, newPrint, if (replace) current else emptyList(), old, str(R.string.log_commander_changed, deck.name, name))
        val tribe = deckRules(deck.id).tribe
        message = str(R.string.msg_commander_changed, name) + if (tribe.isNotBlank()) " " + str(R.string.msg_commander_tribe, tribe) else ""
        messageUndo = true
        snapshot()
    }

    fun updateDeck(deck: Deck) = viewModelScope.launch { repo.updateDeck(deck, str(R.string.log_deck_edited, deck.name)) }

    fun deleteDeck(deck: Deck, keepCards: Boolean) = viewModelScope.launch {
        repo.deleteDeck(deck.id, keepCards, str(R.string.log_deck_deleted, deck.name))
        snapshot()
    }

    /** Het laatst opgeslagen blad (binder, blad, wijziging): ongedaan maken zet de paginateller terug. */
    private var pageAdvanced: Triple<String, Int, Long?>? = null

    fun undo() = viewModelScope.launch {
        boosterResult = null
        val before = repo.latestChangeId()
        val ok = repo.undoLatest()
        pageAdvanced?.let { (b, p, change) ->
            if (ok && change != null && change == before && binder == b && page == (p + 1).toString()) page = p.toString()
        }
        if (ok) pageAdvanced = null
        message = if (ok) str(R.string.msg_undone) else str(R.string.msg_nothing_to_undo)
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
    /** Wat er in een back-up zit, in één regel: "1.069 kaarten · 6 decks · 1.243 prijspunten · 3 vrienden · instellingen". */
    fun backupSummary(b: nl.spoonpaco.ninegrid.data.BackupFile): String = listOfNotNull(
        str(R.string.backup_sum_cards, b.cardCount),
        str(R.string.backup_sum_decks, b.decks.size),
        str(R.string.backup_sum_prices, b.prices.size),
        b.friends?.let { str(R.string.backup_sum_friends, it.size) },
        if (b.hasSettings) str(R.string.backup_sum_settings) else null,
    ).joinToString(" · ")

    fun backupTo(uri: Uri) = viewModelScope.launch {
        runCatching {
            val (backup, text) = nl.spoonpaco.ninegrid.data.Backups.toJson(getApplication(), repo, settingsStore)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
            settingsStore.setLastBackup(System.currentTimeMillis())
            backup
        }.onSuccess { message = str(R.string.msg_backup_saved) + ": " + backupSummary(it) }.onFailure { message = str(R.string.msg_backup_failed) }
    }

    /** De back-up die klaarstaat om terug te zetten: eerst zien wat erin zit en kiezen wat je terugzet. */
    var restorePreview by mutableStateOf<nl.spoonpaco.ninegrid.data.BackupFile?>(null)

    fun previewRestore(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
            repo.parseBackup(text)
        }.onSuccess { restorePreview = it }.onFailure { message = str(R.string.msg_restore_failed) }
    }

    fun restoreBackup(parts: Set<nl.spoonpaco.ninegrid.data.BackupPart>) = viewModelScope.launch {
        val b = restorePreview ?: return@launch
        restorePreview = null
        busy = true
        runCatching {
            nl.spoonpaco.ninegrid.data.Backups.restore(getApplication(), repo, settingsStore, b, parts)
        }.onSuccess {
            message = str(R.string.msg_restored)
            clearPending()
            images.prefetch(repo.allPrintsOwned(), settings.value)
        }.onFailure { message = str(R.string.msg_restore_failed) }
        busy = false
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
            runCatching { BackupWorker.writeBackup(getApplication(), repo, uri, settingsStore) }
                .onSuccess { settingsStore.setLastBackup(System.currentTimeMillis()); message = str(R.string.msg_backup_saved) + ": " + backupSummary(it) }
        }
    }

    // ---------- Instellingen ----------
    fun setTheme(v: String) = viewModelScope.launch { settingsStore.setTheme(v) }
    fun setEffects(v: Boolean) = viewModelScope.launch { settingsStore.setEffects(v) }
    fun setAccent(v: String) = viewModelScope.launch { settingsStore.setAccent(v) }
    fun setPrivacyStyle(v: String) = viewModelScope.launch { settingsStore.setPrivacyStyle(v) }
    fun setPrivacy(v: Boolean) = viewModelScope.launch { settingsStore.setPrivacy(v) }
    /** Even kijken in de privacymodus: tot dit moment zijn de bedragen zichtbaar. */
    var peekUntil by mutableStateOf(0L)
        private set
    fun peek() { peekUntil = System.currentTimeMillis() + 15_000 }
    fun endPeek() { peekUntil = 0L }
    fun setBulkThreshold(v: Double) = viewModelScope.launch { settingsStore.setBulkThreshold(v) }
    fun setChartLines(v: List<String>) = viewModelScope.launch { settingsStore.setChartLines(v) }
    fun setChartMetric(v: String) = viewModelScope.launch { settingsStore.setChartMetric(v) }
    fun setChartRange(v: String) = viewModelScope.launch { settingsStore.setChartRange(v) }
    fun setChartCount(v: Boolean) = viewModelScope.launch { settingsStore.setChartCount(v) }
    fun setScanMode(v: String) = viewModelScope.launch { settingsStore.setScanMode(v) }
    fun setBackupAsked() = viewModelScope.launch { settingsStore.setBackupAsked(true) }
    fun setShowTiming(v: Boolean) = viewModelScope.launch { settingsStore.setShowTiming(v) }
    fun setNewDays(v: Int) = viewModelScope.launch { settingsStore.setNewDays(v) }
    fun markNewSeen() = viewModelScope.launch { settingsStore.markNewSeen() }
    fun setDevScanReport(v: Boolean) = viewModelScope.launch { settingsStore.setDevScanReport(v) }

    // Ontwikkelaar: de laatste scan, voor het scanrapport.
    var lastScanReads by mutableStateOf<List<SlotRead>>(emptyList()); private set
    private var lastScanMeta: nl.spoonpaco.ninegrid.domain.ScanMeta? = null
    private var lastScanPhoto: ByteArray? = null

    /** Onthoudt de foto (verkleind) en wat er gelezen is, zodat het rapport erna gedeeld kan worden. */
    fun recordScan(bmp: android.graphics.Bitmap, reads: List<SlotRead>, meta: nl.spoonpaco.ninegrid.domain.ScanMeta) {
        lastScanReads = reads
        lastScanMeta = meta
        lastScanPhoto = runCatching {
            val maxSide = 2000
            val f = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            val small = if (f < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true) else bmp
            java.io.ByteArrayOutputStream().also { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        }.getOrNull()
    }

    val hasScanReport get() = lastScanMeta != null && lastScanReads.isNotEmpty()

    /** Schrijft rapport en foto naar de deelmap; geeft de bestanden terug. */
    fun writeScanReport(dir: java.io.File): List<java.io.File> {
        val meta = lastScanMeta ?: return emptyList()
        val outcomes = pending.filter { it.slot != null }.associate { it.slot!! to scanOutcome(it) }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        dir.mkdirs()
        dir.listFiles()?.filter { it.name.startsWith("scanrapport-") }?.forEach { it.delete() }
        val txt = java.io.File(dir, "scanrapport-$stamp.txt").also { it.writeText(nl.spoonpaco.ninegrid.domain.ScanReport.build(meta, lastScanReads, outcomes)) }
        val jpg = lastScanPhoto?.let { b -> java.io.File(dir, "scanrapport-$stamp.jpg").also { it.writeBytes(b) } }
        return listOfNotNull(txt, jpg)
    }

    private fun scanOutcome(it: PendingItem): String = when {
        it.empty -> "leeg vak"
        it.status == ItemStatus.NOT_FOUND -> "niet gevonden (${it.label})"
        it.print != null -> "gevonden: ${it.print.name} (${it.print.setCode.uppercase()} ${it.print.collectorNumber})" + (if (it.needsCheck) " [controleren]" else "") + (if (it.finish != Finish.NONFOIL) " [${it.finish}]" else "")
        else -> "${it.status.name.lowercase()} (${it.label})"
    }
    fun setHaptics(v: Boolean) = viewModelScope.launch { settingsStore.setHaptics(v) }
    fun setShutterSound(v: Boolean) = viewModelScope.launch { settingsStore.setShutterSound(v) }
    fun setAutoCapture(v: Boolean) = viewModelScope.launch { settingsStore.setAutoCapture(v) }
    fun setAutoSpeed(v: String) = viewModelScope.launch { settingsStore.setAutoSpeed(v) }

    /** Laatste losse kaart uit de lijst halen (doorlopend scannen). */
    /** Weggegooide scan, om terug te zetten vanuit de melding. */
    private var discarded: Triple<List<PendingItem>, Map<Int, androidx.compose.ui.graphics.ImageBitmap>, Map<Int, AiMark>>? = null

    /** Weggooien: bij een blad de hele scan, bij 1 kaart alleen de laatste kaart. Terugzetten kan vanuit de melding. */
    fun discardScan(single: Boolean) {
        val gone = if (single) listOfNotNull(pending.lastOrNull()) else pending.toList()
        if (gone.isEmpty()) return
        val keys = gone.map { it.key }.toSet()
        discarded = Triple(gone, crops.filterKeys { it in keys }, aiMarks.filterKeys { it in keys })
        pending.removeAll { it.key in keys }
        keys.forEach { crops.remove(it); aiMarks.remove(it) }
        if (pending.isEmpty()) clearSavedPending() else savePendingToDisk()
        messageUndo = true
        messageUndoLabel = str(R.string.restore_discarded)
        messageAction = { restoreDiscarded() }
        message = str(if (single) R.string.msg_card_discarded else R.string.msg_page_discarded)
    }

    private fun restoreDiscarded() {
        val d = discarded ?: return
        discarded = null
        pending.addAll(d.first); crops.putAll(d.second); aiMarks.putAll(d.third)
        savePendingToDisk()
    }

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
                    val (_, text) = nl.spoonpaco.ninegrid.data.Backups.toJson(ctx, repo, settingsStore)
                    ctx.contentResolver.openOutputStream(backupUri)?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }
                folder != null -> BackupWorker.writeBackup(ctx, repo, Uri.parse(folder), settingsStore)
                else -> error("no backup")
            }
            settingsStore.setLastBackup(System.currentTimeMillis())
        }.isSuccess
        if (!ok) { message = str(R.string.msg_wipe_no_backup); busy = false; return@launch }
        if (o.factory) { factoryReset(o); return@launch }
        repo.wipe(o, str(R.string.log_wiped))
        if (o.images) images.clearAll()
        clearPending()
        message = str(R.string.msg_wiped)
        messageUndo = !o.changeLog && (o.collection || o.decks)
        busy = false
        snapshot()
    }

    /** Terug naar een verse installatie en opnieuw starten bij de introductie. De back-up is al gemaakt. */
    private suspend fun factoryReset(o: nl.spoonpaco.ninegrid.data.Repository.WipeOptions) {
        val ctx = getApplication<Application>()
        runCatching { androidx.work.WorkManager.getInstance(ctx).cancelAllWork() }
        clearPending()
        repo.clearEverything()
        listOf("ai_vault", "import_mappings", "deck_rules", "deck_ui", "wish_cheapest").forEach {
            ctx.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
        if (!o.keepOffline) runCatching { c.offline.delete() }
        images.clearAll()
        settingsStore.resetAll(o.keepOffline)
        busy = false
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component
        if (launch != null) ctx.startActivity(android.content.Intent.makeRestartActivityTask(launch))
        kotlinx.coroutines.delay(300)
        Runtime.getRuntime().exit(0)
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
        val todo = items.filter { !it.empty && (it.status == ItemStatus.NOT_FOUND || !it.confident) && it.key !in aiTried && crops[it.key] != null }
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

    /** "Wat is nieuw" tonen na een update (niet bij een nieuwe installatie). */
    var showWhatsNew by mutableStateOf(false)
    fun checkWhatsNew() = viewModelScope.launch {
        val s = settingsStore.current()
        if (s.lastSeenVersion == BuildConfig.VERSION_NAME) return@launch
        if (s.onboardingDone) showWhatsNew = true else settingsStore.setLastSeenVersion(BuildConfig.VERSION_NAME)
    }
    fun closeWhatsNew() = viewModelScope.launch { showWhatsNew = false; settingsStore.setLastSeenVersion(BuildConfig.VERSION_NAME) }

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

    // ---------- Bijwerken vanuit de app ----------
    /** Voortgang van het downloaden van een update (0..1), of null als er niets loopt. */
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    /** Android moet eerst toestemming geven om apps te installeren. */
    var updateNeedsPermission by mutableStateOf(false)

    fun setUpdateAuto(v: String) = viewModelScope.launch {
        settingsStore.setUpdateAuto(v)
        nl.spoonpaco.ninegrid.data.UpdateWorker.schedule(getApplication(), v)
    }

    /** Getikt op de melding: de klaarstaande update tonen. */
    fun showReadyUpdate() { nl.spoonpaco.ninegrid.data.Updater.ready(getApplication())?.let { update = it } }

    /** Downloaden (als dat nog niet gebeurd is) en het installatievenster van Android openen. */
    fun installUpdate() = viewModelScope.launch {
        val app = getApplication<Application>()
        val u = update ?: nl.spoonpaco.ninegrid.data.Updater.ready(app) ?: return@launch
        if (u.apkUrl == null) { message = str(R.string.update_no_apk); return@launch }
        if (!nl.spoonpaco.ninegrid.data.Updater.canInstall(app)) { updateNeedsPermission = true; return@launch }
        updateProgress = 0f
        val apk = runCatching { nl.spoonpaco.ninegrid.data.Updater.download(app, u) { p -> updateProgress = p } }
            .getOrElse { updateProgress = null; message = str(if (it is java.io.IOException) R.string.msg_update_offline else R.string.update_failed); return@launch }
        updateProgress = null
        update = null
        runCatching { nl.spoonpaco.ninegrid.data.Updater.install(app, apk, quiet = false) }.onFailure { message = str(R.string.update_failed) }
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
        fillIncomplete()
    }

    /** Kaarten met ontbrekende regeltekst of legaliteit (uit de offline database) stil aanvullen. */
    private var filling = false
    fun fillIncomplete() {
        if (filling) return
        filling = true
        viewModelScope.launch {
            runCatching { repo.fillIncomplete() }
            filling = false
        }
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
    fun onMeteredNetwork() = c.images.onMeteredNetwork()
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

    /** Een kaart uit een setlijst op de verlanglijst zetten: precies deze print. */
    fun wishSetCard(id: String) = viewModelScope.launch {
        runCatching { repo.scryfall.cardById(id) }.onSuccess { card ->
            if (card != null) { repo.addWish(card.toPrint(), anyPrint = false); message = str(R.string.wish_added, card.name) }
            else message = str(R.string.msg_network_error)
        }.onFailure { message = errorText(it) }
    }

    /** Een kaart uit een setlijst openen in het kaartscherm (eerst opzoeken en bewaren, want je hebt hem nog niet). */
    // ---------- Vegen op het kaartscherm ----------
    /** De lijst waaruit het kaartscherm is geopend (printId's in de volgorde van die lijst). Leeg = geen vegen. */
    var cardList: List<String> = emptyList(); private set
    fun setCardList(ids: List<String>) { cardList = ids.distinct() }
    fun clearCardList() { cardList = emptyList() }
    /** De kaart links (dir -1) of rechts (dir +1) van [current] in de lijst, of null aan het begin of einde. */
    fun cardNeighbour(current: String, dir: Int): String? = cardList.indexOf(current).takeIf { it >= 0 }?.let { cardList.getOrNull(it + dir) }
    /** Plek in de lijst (1-gebaseerd) en lengte, of null als vegen niet kan. */
    fun cardPosition(current: String): Pair<Int, Int>? = cardList.indexOf(current).takeIf { it >= 0 && cardList.size > 1 }?.let { (it + 1) to cardList.size }
    /** Zorgt dat de print bekend is (kaarten uit een setlijst zitten er nog niet in) en gaat dan verder. */
    fun openCardId(id: String, then: (String) -> Unit) = viewModelScope.launch {
        if (repo.hasPrint(id)) then(id) else openSetCard(id, then)
    }

    fun openSetCard(id: String, then: (String) -> Unit) = viewModelScope.launch {
        runCatching { repo.scryfall.cardById(id) }.onSuccess { card ->
            if (card != null) { repo.rememberPrint(card.toPrint()); then(card.id) }
        }.onFailure { message = errorText(it) }
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

    // Tabblad Kaarten van een deck: welke typegroepen dicht staan en hoe er gesorteerd wordt, per deck onthouden.
    private val deckUiPrefs get() = getApplication<Application>().getSharedPreferences("deck_ui", android.content.Context.MODE_PRIVATE)
    fun deckCollapsed(deckId: Long): Set<String> = deckUiPrefs.getStringSet("collapsed_$deckId", emptySet()).orEmpty().toSet()
    fun setDeckCollapsed(deckId: Long, v: Set<String>) = deckUiPrefs.edit().putStringSet("collapsed_$deckId", v).apply()
    /** Welke blokken op het tabblad Suggesties dicht staan (per deck). */
    fun suggCollapsed(deckId: Long): Set<String> = deckUiPrefs.getStringSet("sugg_closed_$deckId", emptySet()).orEmpty().toSet()
    fun setSuggCollapsed(deckId: Long, v: Set<String>) = deckUiPrefs.edit().putStringSet("sugg_closed_$deckId", v).apply()
    fun deckSort(deckId: Long): String = deckUiPrefs.getString("sort_$deckId", "mana") ?: "mana"
    /** Combo's als kaartjes (standaard) of als namen; één keuze voor alle decks. */
    fun comboCards(): Boolean = (deckUiPrefs.getString("combo_cards", "1") ?: "1") == "1"
    fun setComboCards(v: Boolean) = deckUiPrefs.edit().putString("combo_cards", if (v) "1" else "0").apply()
    fun setDeckSort(deckId: Long, v: String) = deckUiPrefs.edit().putString("sort_$deckId", v).apply()
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

    // ---------- Delen en ruilen ----------
    val friends = repo.friends.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val friendPackages = repo.friendPackages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val friendCards = repo.friendCards.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var shareBusy by mutableStateOf(false)
        private set

    /** Aantal kaarten op de verlanglijst (wordt gevuld zodra de verlanglijst er is). */
    fun wishlistCount(): Int = wishlist.value.sumOf { (it.item.wanted - it.item.got).coerceAtLeast(0) }

    fun sharePackages(choice: nl.spoonpaco.ninegrid.domain.ShareChoice) =
        nl.spoonpaco.ninegrid.domain.Share.packages(entries.value, decks.value, choice, wishlist.value)

    /** Maakt het deelbestand in de cache en geeft het terug; de naam wordt onthouden. */
    suspend fun buildShareFile(choice: nl.spoonpaco.ninegrid.domain.ShareChoice, name: String): java.io.File {
        settingsStore.setShareName(name)
        val f = nl.spoonpaco.ninegrid.domain.ShareFile(
            senderId = settingsStore.shareId(), senderName = name.trim(), createdAt = System.currentTimeMillis(),
            app = BuildConfig.VERSION_NAME, packages = sharePackages(choice),
        )
        val dir = java.io.File(getApplication<Application>().cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = java.io.File(dir, nl.spoonpaco.ninegrid.domain.Share.fileName(name))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.writeText(nl.spoonpaco.ninegrid.domain.Share.encode(f)) }
        return file
    }

    fun saveShareTo(uri: Uri, file: java.io.File) = viewModelScope.launch {
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        }.onSuccess { message = str(R.string.msg_exported) }.onFailure { message = str(R.string.msg_backup_failed) }
    }

    /** Geopend vanuit een andere app (een gedeeld bestand): naar Delen en ruilen. */
    var openShare by mutableStateOf(false)
    fun receiveShared(uri: Uri) { openShare = true; openShared(uri) }

    /** Een bestand van een ander uit een export van een andere app: wacht op een naam. */
    var pendingOtherApp by mutableStateOf<nl.spoonpaco.ninegrid.domain.ImportData?>(null)

    /** Een ontvangen bestand openen: een NineGrid-bestand meteen, een export van een andere app na het kiezen van een naam. */
    fun openShared(uri: Uri) = viewModelScope.launch {
        shareBusy = true
        try {
            val app = getApplication<Application>()
            val name = runCatching {
                app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur -> if (cur.moveToFirst()) cur.getString(0) else null }
            }.getOrNull() ?: "bestand"
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: run {
                message = str(R.string.share_open_failed); return@launch
            }
            val share = nl.spoonpaco.ninegrid.domain.Share.decode(bytes.decodeToString())
            if (share != null) {
                val r = repo.importShare(share)
                message = str(if (r.replaced) R.string.share_received_replaced else R.string.share_received, r.name, r.cards) +
                    if (r.notFound > 0) " · " + str(R.string.share_not_found, r.notFound) else ""
                return@launch
            }
            val data = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { nl.spoonpaco.ninegrid.domain.SheetImport.read(name, bytes.inputStream()) } }.getOrNull()
            if (data == null || data.needsMapping || (data.collection.isEmpty() && data.decks.isEmpty())) { message = str(R.string.share_open_unknown); return@launch }
            pendingOtherApp = data
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            message = if (e is java.io.IOException) str(R.string.msg_network_error) else str(R.string.share_open_failed)
        } finally { shareBusy = false }
    }

    fun importOtherAppAs(name: String) = viewModelScope.launch {
        val data = pendingOtherApp ?: return@launch
        pendingOtherApp = null
        shareBusy = true
        runCatching { repo.importOtherAppAsFriend(name.trim().ifBlank { "?" }, data) }
            .onSuccess { r -> message = str(R.string.share_received, r.name, r.cards) + if (r.notFound > 0) " · " + str(R.string.share_not_found, r.notFound) else "" }
            .onFailure { message = if (it is java.io.IOException) str(R.string.msg_network_error) else str(R.string.share_open_failed) }
        shareBusy = false
    }

    fun renameFriend(id: String, name: String) = viewModelScope.launch { if (name.isNotBlank()) repo.renameFriend(id, name.trim()) }
    fun deleteFriend(id: String) = viewModelScope.launch { repo.deleteFriend(id) }
    fun deleteFriendPackage(id: Long) = viewModelScope.launch { repo.deleteFriendPackage(id) }

    /** De kaarten van één pakket als gewone exemplaren, zodat prijzen, deckcontrole en suggesties gewoon werken. */
    fun friendEntries(packageIds: Set<Long>): List<EntryWithPrint> = friendCards.value.filter { it.card.packageId in packageIds }.map { fc ->
        EntryWithPrint(Entry(id = fc.card.id, printId = fc.card.printId, finish = fc.card.finish, language = fc.card.language,
            quantity = fc.card.quantity, section = fc.card.section, deckId = if (fc.card.section != null) -fc.card.packageId else null), fc.print)
    }

    /** Bij welke vrienden ligt deze kaart (los, niet in hun decks of verlanglijst)? */
    fun friendsHaving(name: String): List<Pair<nl.spoonpaco.ninegrid.data.db.Friend, Int>> {
        val packs = friendPackages.value.filter { it.kind != nl.spoonpaco.ninegrid.domain.ShareKind.DECK && it.kind != nl.spoonpaco.ninegrid.domain.ShareKind.WISH }.associateBy { it.id }
        val byFriend = friendCards.value.filter { it.card.packageId in packs && it.print?.name.equals(name, true) }
            .groupBy { packs.getValue(it.card.packageId).friendId }.mapValues { (_, v) -> v.sumOf { it.card.quantity } }
        val fr = friends.value.associateBy { it.id }
        return byFriend.mapNotNull { (id, n) -> fr[id]?.let { it to n } }.sortedByDescending { it.second }
    }

    /** Per vriend: hoeveel kaarten jouw decks beter maken en wat ze waard zijn (voor Overzicht). */
    data class TradeSummary(val friend: nl.spoonpaco.ninegrid.data.db.Friend, val cards: Int, val value: Double)
    var tradeSummaries by mutableStateOf<List<TradeSummary>>(emptyList())
        private set
    private var tradeSummaryKey = ""

    /** Kaarten van vrienden die precies dit deck beter maken (voor Suggesties → Bij vrienden). */
    data class FriendOffer(val item: nl.spoonpaco.ninegrid.domain.TradeMatch.Item, val friend: nl.spoonpaco.ninegrid.data.db.Friend)
    var friendOffers by mutableStateOf<List<FriendOffer>>(emptyList())
        private set
    var friendOffersDeck by mutableStateOf<Long?>(null)
        private set
    private var friendOffersKey = ""

    /** Sleutel van alles waar de ruilmatch van afhangt: alleen opnieuw rekenen als die verandert. */
    private fun tradeKey(extra: String) = extra + "|" + friends.value.joinToString { it.id + it.receivedAt } + "|" +
        decks.value.joinToString { "" + it.id + ":" + it.updatedAt } + "|" + entries.value.size + "|" + wishlist.value.size + "|" + settings.value.currency

    private fun looseOfFriends(): Pair<List<EntryWithPrint>, Map<Long, String>> {
        val packs = friendPackages.value.filter { it.kind != nl.spoonpaco.ninegrid.domain.ShareKind.DECK && it.kind != nl.spoonpaco.ninegrid.domain.ShareKind.WISH }
        val ids = packs.map { it.id }.toSet()
        val owner = packs.associate { it.id to it.friendId }
        val cardOwner = friendCards.value.filter { it.card.packageId in ids }.associate { it.card.id to owner.getValue(it.card.packageId) }
        return friendEntries(ids) to cardOwner
    }

    fun computeTradeSummaries() = viewModelScope.launch {
        val key = tradeKey("sum")
        if (key == tradeSummaryKey) return@launch
        tradeSummaryKey = key
        val all = entries.value
        val myDecks = decks.value.map { d -> nl.spoonpaco.ninegrid.domain.TradeMatch.DeckInput(d.name, d.format, all.filter { it.entry.deckId == d.id }, deckRules(d.id)) }
        val (loose, owner) = looseOfFriends()
        val currency = settings.value.currency
        val fr = friends.value
        tradeSummaries = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                fr.map { f ->
                    val pool = loose.filter { owner[it.entry.id] == f.id }
                    val skip = all.filter { it.entry.deckId == null && !it.entry.proxy }.mapNotNull { it.print?.name?.lowercase() }.toSet()
                    val items = nl.spoonpaco.ninegrid.domain.TradeMatch.forDecks(myDecks, pool, skip, currency, 8)
                    TradeSummary(f, items.size, items.sumOf { it.unit })
                }.sortedByDescending { it.cards }
            }
        }.getOrDefault(emptyList())
    }

    fun computeFriendOffers(deckId: Long) = viewModelScope.launch {
        val key = tradeKey("deck$deckId")
        if (key == friendOffersKey && friendOffersDeck == deckId) return@launch
        friendOffersKey = key
        val d = decks.value.firstOrNull { it.id == deckId } ?: return@launch
        val all = entries.value
        val input = nl.spoonpaco.ninegrid.domain.TradeMatch.DeckInput(d.name, d.format, all.filter { it.entry.deckId == d.id }, deckRules(d.id))
        val (loose, owner) = looseOfFriends()
        val fr = friends.value.associateBy { it.id }
        val currency = settings.value.currency
        friendOffers = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val skip = all.filter { it.entry.deckId == null && !it.entry.proxy }.mapNotNull { it.print?.name?.lowercase() }.toSet()
                nl.spoonpaco.ninegrid.domain.TradeMatch.forDecks(listOf(input), loose, skip, currency, 6)
                    .mapNotNull { i -> owner[i.entry.id]?.let { fr[it] }?.let { FriendOffer(i, it) } }
            }
        }.getOrDefault(emptyList())
        friendOffersDeck = deckId
    }

    var tradeMatch by mutableStateOf<nl.spoonpaco.ninegrid.domain.TradeMatch.Result?>(null)
        private set
    var tradeFriendId by mutableStateOf<String?>(null)
        private set
    var tradeBusy by mutableStateOf(false)
        private set

    fun computeTrade(friendId: String) = viewModelScope.launch {
        tradeBusy = true; tradeFriendId = friendId
        val packs = friendPackages.value.filter { it.friendId == friendId }
        val looseIds = packs.filter { it.kind != nl.spoonpaco.ninegrid.domain.ShareKind.DECK && it.kind != nl.spoonpaco.ninegrid.domain.ShareKind.WISH }.map { it.id }.toSet()
        val wishIds = packs.filter { it.kind == nl.spoonpaco.ninegrid.domain.ShareKind.WISH }.map { it.id }.toSet()
        val theirWants = friendCards.value.filter { it.card.packageId in wishIds && it.print != null }.map { fc ->
            nl.spoonpaco.ninegrid.domain.TradeMatch.Want(fc.print!!.name, fc.card.printId, fc.card.section == "ANY", fc.card.finish != nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL, fc.card.quantity)
        }
        val myWants = wishlist.value.filter { it.item.wanted > it.item.got }.map { w ->
            nl.spoonpaco.ninegrid.domain.TradeMatch.Want(w.item.name, w.item.printId, w.item.anyPrint, w.item.foilOnly, w.item.wanted - w.item.got)
        }
        val theirDecks = packs.filter { it.kind == nl.spoonpaco.ninegrid.domain.ShareKind.DECK }.map { p ->
            nl.spoonpaco.ninegrid.domain.TradeMatch.DeckInput(p.label, nl.spoonpaco.ninegrid.domain.TradeMatch.formatOf(p.format), friendEntries(setOf(p.id)))
        }
        val all = entries.value
        val myDecks = decks.value.map { d -> nl.spoonpaco.ninegrid.domain.TradeMatch.DeckInput(d.name, d.format, all.filter { it.entry.deckId == d.id }, deckRules(d.id)) }
        val theirLoose = friendEntries(looseIds)
        val currency = settings.value.currency
        tradeMatch = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                nl.spoonpaco.ninegrid.domain.TradeMatch.compute(all, myDecks, theirLoose, theirDecks, currency, myWants = myWants, theirWants = theirWants)
            }
        }.getOrNull()
        tradeBusy = false
    }

    /** Vanuit een melding: deze kaart openen, of Collectie in een bepaalde stand. */
    var openCardRequest by mutableStateOf<String?>(null)
    var collectionModeRequest by mutableStateOf<String?>(null)

    /** Scannen voor één vak in de bladweergave: stand 1 kaart, met binder, blad en vak ingevuld. */
    var forcedSlot: Int? = null
    private var savedForcedSlot = false
    fun scanForSlot(binderName: String, pageNr: Int, slotNr: Int) {
        binder = binderName; page = pageNr.toString(); forcedSlot = slotNr
        setScanMode("single")
    }

    // ---------- Verlanglijst ----------
    val wishlist = repo.wishlist.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Verlanglijst: goedkoopste prijs bij "elke print is goed" ----
    private val cheapPrefs get() = getApplication<Application>().getSharedPreferences("wish_cheapest", android.content.Context.MODE_PRIVATE)
    private fun cheapKey(w: nl.spoonpaco.ninegrid.data.db.WishItem, currency: String) = "${w.name.lowercase()}|${w.foilOnly}|$currency"
    /** Opgeslagen goedkoopste prijzen (sleutel naam|foil|valuta); NaN = geen enkele print heeft een prijs. */
    val cheapest = androidx.compose.runtime.mutableStateMapOf<String, Double>()

    /**
     * Prijs van een regel op de verlanglijst: bij "elke print" de goedkoopste print, anders de prijs van die print.
     * Null = (nog) geen prijs bekend.
     */
    fun wishUnitPrice(w: nl.spoonpaco.ninegrid.data.db.WishWithPrint, currency: String): Double? {
        if (w.item.anyPrint) return cheapest[cheapKey(w.item, currency)]?.takeIf { !it.isNaN() }
        val p = w.print ?: return null
        val usd = currency == "USD"
        return when {
            usd && w.item.foilOnly -> p.usdFoil ?: p.usdEtched
            usd -> p.usd
            w.item.foilOnly -> p.eurFoil
            else -> p.eur
        }
    }

    /** Goedkoopste prijzen laden: eerst wat bewaard is, daarna (hooguit één keer per 12 uur per kaart) vers ophalen. */
    private var cheapJob: kotlinx.coroutines.Job? = null
    fun loadCheapest() {
        if (cheapJob?.isActive == true) return
        val cur = settings.value.currency
        val list = wishlist.value.filter { it.item.anyPrint }.map { it.item }
        list.forEach { w -> cheapPrefs.getString(cheapKey(w, cur), null)?.substringBefore(';')?.toDoubleOrNull()?.let { cheapest[cheapKey(w, cur)] = it } }
        cheapJob = viewModelScope.launch {
            for (w in list) {
                val k = cheapKey(w, cur)
                val at = cheapPrefs.getString(k, null)?.substringAfter(';', "")?.toLongOrNull() ?: 0L
                if (System.currentTimeMillis() - at < 12 * 3600_000L && cheapest.containsKey(k)) continue
                val got = runCatching { repo.cheapestPrice(w.name, w.foilOnly, cur) }
                if (got.isFailure) continue
                val v = got.getOrNull() ?: Double.NaN
                cheapest[k] = v
                cheapPrefs.edit().putString(k, "$v;${System.currentTimeMillis()}").apply()
            }
        }
    }

    // ---- Boosters ----
    /** In het controlescherm: "Dit was een booster", wat je betaalde, het type en eventueel de box. */
    var boosterOn by mutableStateOf(false)
    var boosterPaid by mutableStateOf("")
    var boosterType by mutableStateOf("")
    var boosterBoxId by mutableStateOf<Long?>(null)
    val boosters = repo.boosters().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val boosterCards = repo.boosterCards().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val boosterBoxes = repo.boosterBoxes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val boosterPrints = repo.boosterPrints().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Uitslag na het opslaan van een booster, voor het venster met de hit. */
    data class BoosterResult(val booster: nl.spoonpaco.ninegrid.data.db.Booster, val cards: List<Pair<CardPrint, nl.spoonpaco.ninegrid.data.db.BoosterCard>>)
    var boosterResult by mutableStateOf<BoosterResult?>(null)

    fun parseMoney(t: String) = t.trim().replace("€", "").replace("$", "").replace(',', '.').toDoubleOrNull()

    /** Prijs per booster: uit de box (prijs gedeeld door het aantal) of wat je invulde. */
    fun boosterPrice(): Double {
        val box = boosterBoxId?.let { id -> boosterBoxes.value.firstOrNull { it.id == id } }
        return if (box != null && box.size > 0) box.price / box.size else parseMoney(boosterPaid) ?: 0.0
    }

    /** Waarde van een booster nu, en zonder bulk (kaarten onder de grens tellen niet mee). */
    fun boosterValue(cards: List<Pair<CardPrint?, nl.spoonpaco.ninegrid.data.db.BoosterCard>>, currency: String, noBulk: Boolean = false): Double {
        val limit = settings.value.bulkThreshold
        return cards.sumOf { (p, c) ->
            val u = nl.spoonpaco.ninegrid.domain.Money.unitPrice(Entry(printId = c.printId, finish = c.finish), p, currency)
            if (noBulk && u < limit) 0.0 else u * c.quantity
        }
    }

    private suspend fun saveBoosterFrom(list: List<nl.spoonpaco.ninegrid.data.NewEntry>) {
        val cur = settings.value.currency
        val cards = list.groupBy { it.print.id to it.entry.finish }.map { (k, v) ->
            v.first().print to nl.spoonpaco.ninegrid.data.db.BoosterCard(boosterId = 0, printId = k.first, finish = k.second, quantity = v.sumOf { it.entry.quantity })
        }
        val set = list.groupingBy { it.print.setCode }.eachCount().maxByOrNull { it.value }?.key?.uppercase() ?: ""
        val b = nl.spoonpaco.ninegrid.data.db.Booster(
            setCode = set, type = boosterType, paid = boosterPrice(), currency = cur, boxId = boosterBoxId,
            valueAtOpen = boosterValue(cards, cur), valueNoBulkAtOpen = boosterValue(cards, cur, noBulk = true),
        )
        val id = repo.saveBooster(b, cards.map { it.second })
        settingsStore.setLastBooster(b.paid, boosterType)
        boosterResult = BoosterResult(b.copy(id = id), cards)
        boosterOn = false
    }

    /** Volgende booster: zelfde prijs, type en box staan klaar. */
    fun nextBooster() { boosterResult = null; boosterOn = true }
    fun closeBoosterResult() { boosterResult = null }

    fun startBox(size: Int, price: Double) = viewModelScope.launch {
        val set = pending.mapNotNull { it.print?.setCode }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.uppercase() ?: ""
        boosterBoxId = repo.newBox(nl.spoonpaco.ninegrid.data.db.BoosterBox(setCode = set, type = boosterType, size = size, price = price, currency = settings.value.currency))
    }
    fun endBox() { boosterBoxId = null }
    fun updateBoosterPaid(b: nl.spoonpaco.ninegrid.data.db.Booster, paid: Double) = viewModelScope.launch { repo.updateBooster(b.copy(paid = paid)) }
    fun deleteBooster(b: nl.spoonpaco.ninegrid.data.db.Booster) = viewModelScope.launch { repo.deleteBooster(b.id); message = str(R.string.booster_deleted) }

    /** Bij het openen van het controlescherm: de laatst betaalde prijs staat klaar. */
    fun prefillBooster() {
        val s = settings.value
        if (boosterPaid.isBlank() && s.lastBoosterPrice > 0) boosterPaid = String.format(java.util.Locale.getDefault(), "%.2f", s.lastBoosterPrice)
        if (boosterType.isBlank()) boosterType = s.lastBoosterType
    }

    /** Op de verlanglijst; [anyPrint] = elke print is goed. */
    fun addToWishlist(print: CardPrint, anyPrint: Boolean = true, foilOnly: Boolean = false, wanted: Int = 1) = viewModelScope.launch {
        repo.addWish(print, anyPrint, foilOnly, wanted)
        message = str(R.string.wish_added, print.name)
    }

    /** Op de verlanglijst op naam (bijv. vanuit een bijna-combo): de nieuwste print als voorbeeld. */
    fun addToWishlistByName(name: String) = viewModelScope.launch {
        val card = runCatching { repo.resolve(listOf(CardQuery(name = name))).firstOrNull() }.getOrNull()
        if (card == null) { message = str(R.string.msg_network_error); return@launch }
        repo.addWish(card.toPrint())
        message = str(R.string.wish_added, card.name)
    }

    fun updateWish(w: nl.spoonpaco.ninegrid.data.db.WishItem, print: CardPrint? = null) = viewModelScope.launch { repo.updateWish(w, print) }
    fun deleteWish(id: Long) = viewModelScope.launch { repo.deleteWish(id) }

    fun boughtWish(w: nl.spoonpaco.ninegrid.data.db.WishWithPrint) = viewModelScope.launch {
        val p = w.print ?: return@launch
        repo.boughtWish(w.item, p, str(R.string.log_wish_bought, p.name))
        messageUndo = true
        message = str(R.string.wish_bought, p.name)
        snapshot()
    }

    /** Een lijst plakken naar de verlanglijst ("1 Ragavan", "4 Counterspell (MH2) 267"). */
    fun pasteToWishlist(text: String, onDone: (Int, Int) -> Unit) = viewModelScope.launch {
        val lines = nl.spoonpaco.ninegrid.domain.DeckListParser.parse(text).lines
        if (lines.isEmpty()) { onDone(0, 0); return@launch }
        val queries = lines.map { l -> CardQuery(setCode = l.setCode, collectorNumber = l.collectorNumber, name = l.name) }
        val found = runCatching { repo.resolve(queries) }.getOrElse { message = str(R.string.msg_network_error); return@launch }
        var ok = 0
        lines.forEachIndexed { i, l ->
            val c = found.getOrNull(i) ?: return@forEachIndexed
            repo.addWish(c.toPrint(), anyPrint = l.setCode == null || l.collectorNumber == null, foilOnly = l.foil || l.etched, wanted = l.quantity.coerceAtLeast(1))
            ok++
        }
        onDone(ok, lines.size - ok)
    }

    private suspend fun strikeAfterSave(list: List<NewEntry>): String {
        val names = runCatching { repo.strikeWishlist(list.map { it.print to it.entry }) }.getOrDefault(emptyList())
        return when (names.size) {
            0 -> ""
            1 -> " · " + str(R.string.wish_struck_one, names[0])
            else -> " · " + str(R.string.wish_struck_many, names.size)
        }
    }

    // ---------- Zoeken in alle kaarten ----------
    var allResults by mutableStateOf<List<CardPrint>>(emptyList())
        private set
    var allHasMore by mutableStateOf(false)
        private set
    var allLoading by mutableStateOf(false)
        private set
    var allOffline by mutableStateOf(false)
        private set
    var allFailed by mutableStateOf(false)
        private set
    private var allPage = 1
    private var allJob: kotlinx.coroutines.Job? = null
    private var allKey = ""

    /** Gebruikt Alle kaarten de offline database? (Regeltekst zit daar niet in: dan online.) */
    // De offline database kent geen Game Changers: met dat filter zoekt Alle kaarten bij Scryfall.
    fun allCardsOffline(f: nl.spoonpaco.ninegrid.ui.components.CardFilter) = c.offline.usable() && !f.inText && !f.gameChangers

    /**
     * Zoeken: minstens 2 letters of één filter. Online pas een halve seconde na de laatste letter,
     * zodat Scryfall niet bij elke letter een vraag krijgt.
     */
    fun searchAll(text: String, f: nl.spoonpaco.ninegrid.ui.components.CardFilter) {
        val t = text.trim()
        val hasFilter = f.rarities.isNotEmpty() || f.colors.isNotEmpty() || f.types.isNotEmpty() || f.notOwned || f.gameChangers
        val key = t + "|" + f.toJson()
        if (key == allKey) return
        allKey = key
        allJob?.cancel()
        if (t.length < 2 && !hasFilter) { allResults = emptyList(); allHasMore = false; allLoading = false; allFailed = false; return }
        val offline = allCardsOffline(f)
        allJob = viewModelScope.launch {
            if (!offline) kotlinx.coroutines.delay(500)
            allLoading = true; allFailed = false; allPage = 1
            val q = nl.spoonpaco.ninegrid.ui.components.CardFilter.scryfallQuery(t, f)
            val r = runCatching { repo.searchAllCards(t, q, offline, 1, f.rarities, f.colors, f.types) }
            r.onSuccess { page ->
                // De filters zitten al in de zoekopdracht (offline in de database, online bij Scryfall).
                allResults = page.cards
                allHasMore = page.hasMore; allOffline = page.offline
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; allFailed = true; allResults = emptyList() }
            allLoading = false
        }
    }

    fun moreAll(text: String, f: nl.spoonpaco.ninegrid.ui.components.CardFilter) {
        if (allLoading || !allHasMore) return
        allJob = viewModelScope.launch {
            allLoading = true
            val q = nl.spoonpaco.ninegrid.ui.components.CardFilter.scryfallQuery(text.trim(), f)
            runCatching { repo.searchAllCards(text.trim(), q, allOffline, allPage + 1, f.rarities, f.colors, f.types) }.onSuccess { p ->
                allPage++; allResults = allResults + p.cards; allHasMore = p.hasMore
            }
            allLoading = false
        }
    }

    /** Een kaart uit Alle kaarten openen: eerst bewaren, dan het kaartscherm. */
    fun openPrint(p: CardPrint, query: String = "", then: (String) -> Unit) = viewModelScope.launch {
        repo.rememberPrint(p)
        if (query.trim().length >= 2) settingsStore.addRecentSearch(query.trim())
        then(p.id)
    }

    /** Een kaart die je nog niet hebt toevoegen aan je collectie (1 stuk, gewoon, Engels). */
    fun addOneToCollection(p: CardPrint) = viewModelScope.launch {
        val list = listOf(NewEntry(p, Entry(printId = p.id)))
        repo.addEntries(list, str(R.string.log_added_collection, 1))
        messageUndo = true
        message = str(R.string.msg_added_one, p.name) + strikeAfterSave(list)
        snapshot()
    }

}
