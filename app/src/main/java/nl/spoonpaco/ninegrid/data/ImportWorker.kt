package nl.spoonpaco.ninegrid.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.MainActivity
import nl.spoonpaco.ninegrid.NineGridApp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.data.scryfall.CardQuery
import nl.spoonpaco.ninegrid.domain.DeckListParser
import nl.spoonpaco.ninegrid.domain.Formats
import nl.spoonpaco.ninegrid.domain.ImportCard
import nl.spoonpaco.ninegrid.domain.ImportData
import nl.spoonpaco.ninegrid.domain.ImportDeck
import nl.spoonpaco.ninegrid.domain.ParsedLine
import java.io.File

/** Een import die op de achtergrond loopt. */
@Serializable
data class ImportJob(val data: ImportData, val replace: Boolean, val asDeck: String?)

/** Voortgang, na elke ronde bewaard, zodat een import na een herstart verder kan. */
@Serializable
data class ImportProgress(
    val found: Map<String, CardPrint> = emptyMap(),
    val attempted: Map<String, Int> = emptyMap(),
    /** Set en nummer gaven een andere kaart dan de naam in het bestand. */
    val wrongNumber: Set<String> = emptySet(),
)

/** Uitkomst, voor het scherm Importeren. */
@Serializable
data class ImportOutcome(
    val cards: Int, val decks: Int, val notFound: List<ImportCard>, val failed: String? = null,
    /** Nummer klopte niet bij de naam; op naam gevonden. */
    val wrongNumber: List<ImportCard> = emptyList(),
)

/**
 * Importeert op de achtergrond, met een melding in de meldingenbalk. Zoekt in rondes van 75 kaarten:
 * eerst op Scryfall-ID of set en nummer, dan op naam en set, dan alleen op naam. Geduldig bij een weigering van Scryfall.
 */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir get() = File(applicationContext.filesDir, "import").apply { mkdirs() }
    private val jobFile get() = File(dir, "job.json")
    private val progressFile get() = File(dir, "progress.json")

    override suspend fun doWork(): Result {
        val c = (applicationContext as NineGridApp).container
        val job = runCatching { json.decodeFromString(ImportJob.serializer(), jobFile.readText()) }.getOrNull() ?: return Result.success()
        channel()
        runCatching { setForeground(info(0, 1, null)) }

        var data = job.data
        // Setnamen zonder code (Deckbox, TCGplayer) omzetten.
        if ((data.collection + data.decks.flatMap { it.cards }).any { it.setCode.isBlank() && it.setName.isNotBlank() && it.scryfallId == null }) {
            val byName = c.cache.setCodesByName()
            fun fix(x: ImportCard) = if (x.setCode.isBlank()) x.copy(setCode = byName[x.setName.lowercase()] ?: "") else x
            data = data.copy(collection = data.collection.map(::fix), decks = data.decks.map { d -> d.copy(cards = d.cards.map(::fix)) })
        }
        // Eén app-bestand als deck.
        if (job.asDeck != null && data.collection.isNotEmpty()) {
            data = data.copy(decks = data.decks + ImportDeck(job.asDeck, data.collection.map { it.copy(section = it.section ?: "MAIN") }), collection = emptyList())
        }
        val all = data.collection + data.decks.flatMap { it.cards }
        val byKey = all.groupBy { keyOf(it) }.mapValues { it.value.first() }
        val keys = byKey.keys.toList()
        var progress = runCatching { json.decodeFromString(ImportProgress.serializer(), progressFile.readText()) }.getOrNull() ?: ImportProgress()

        c.scryfall.holdPatient()
        try {
            for (pass in 1..3) {
                val todo = keys.filter { it !in progress.found && (progress.attempted[it] ?: 0) < pass }
                    .filter { k -> queryFor(byKey.getValue(k), pass) != null }
                for (chunk in todo.chunked(75)) {
                    val queries = chunk.map { queryFor(byKey.getValue(it), pass)!! }
                    val res = c.scryfall.collection(queries)
                    val found = progress.found.toMutableMap(); val att = progress.attempted.toMutableMap()
                    val wrong = progress.wrongNumber.toMutableSet()
                    chunk.forEachIndexed { i, k ->
                        res[i]?.let { card ->
                            val src = byKey.getValue(k)
                            // Ronde 1 op set en nummer: past de naam uit het bestand bij die kaart? Anders niet overnemen
                            // en verder zoeken op naam (ronde 2 en 3).
                            if (pass == 1 && src.scryfallId == null && !nameFits(src, card.name)) wrong += k
                            else found[k] = card.toPrint()
                        }
                        att[k] = pass
                    }
                    progress = ImportProgress(found, att, wrong)
                    runCatching { progressFile.writeText(json.encodeToString(ImportProgress.serializer(), progress)) }
                    setProgress(workDataOf("done" to progress.found.size, "total" to keys.size, "pass" to pass))
                    runCatching { setForeground(info(progress.found.size, keys.size, pass)) }
                }
            }
        } catch (e: java.io.IOException) {
            // Geen internet: later verder (de voortgang is bewaard).
            return Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < 5) return Result.retry()
            writeOutcome(ImportOutcome(0, 0, emptyList(), e.message ?: e.javaClass.simpleName))
            notifyDone(applicationContext.getString(R.string.msg_import_failed))
            return Result.failure()
        } finally {
            c.scryfall.releasePatient()
        }

        // Opslaan, als één wijziging.
        val notFound = mutableListOf<ImportCard>()
        fun toNew(x: ImportCard): NewEntry? {
            val print = progress.found[keyOf(x)] ?: run { notFound += x; return null }
            return NewEntry(print, Entry(
                printId = print.id, quantity = x.quantity, proxy = x.proxy, language = x.language, condition = x.condition,
                finish = when { x.etched -> Finish.ETCHED; x.foil -> Finish.FOIL; else -> Finish.NONFOIL },
                section = x.section,
            ))
        }
        val collection = data.collection.mapNotNull { toNew(it) }
        val decks = data.decks.map { d ->
            val cards = d.cards.mapNotNull { toNew(it) }
            val parsed = d.cards.map {
                ParsedLine(it.quantity, it.name, null, null, false, false,
                    when (it.section) { "COMMANDER" -> DeckListParser.COMMANDER; "SIDEBOARD" -> DeckListParser.SIDEBOARD; else -> DeckListParser.MAIN }, 0, "")
            }
            Deck(name = d.name, format = Formats.suggest(parsed, cards.associate { it.print.name to it.print.typeLine })) to cards
        }
        val history = data.history.map { h -> nl.spoonpaco.ninegrid.data.db.Snapshot(h.day, "total", SHEET_LABEL, h.value, 0.0, h.count) }
        val count = collection.sumOf { it.entry.quantity } + decks.sumOf { d -> d.second.sumOf { it.entry.quantity } }
        c.repository.importAll(collection, decks, history, job.replace, applicationContext.getString(R.string.log_imported, count, decks.size))
        runCatching { c.repository.takeSnapshot(); c.repository.recordPrices() }
        c.images.prefetch(c.repository.allPrintsOwned(), c.settings.current())
        val wrongNumber = all.filter { keyOf(it) in progress.wrongNumber && keyOf(it) in progress.found }.distinctBy { keyOf(it) }
        writeOutcome(ImportOutcome(count, decks.size, notFound, wrongNumber = wrongNumber))
        jobFile.delete(); progressFile.delete()
        notifyDone(applicationContext.getString(R.string.import_done, count, decks.size, notFound.sumOf { it.quantity }))
        return Result.success()
    }

    private fun writeOutcome(o: ImportOutcome) = runCatching { File(dir, "outcome.json").writeText(json.encodeToString(ImportOutcome.serializer(), o)) }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.import_title), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0, Intent(applicationContext, MainActivity::class.java).putExtra(EXTRA_OPEN_IMPORT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun info(done: Int, total: Int, pass: Int?): ForegroundInfo {
        val text = if (pass == null) applicationContext.getString(R.string.import_preparing)
            else applicationContext.getString(R.string.import_notif_progress, done, total, pass)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(nl.spoonpaco.ninegrid.R.drawable.ic_stat_ninegrid)
            .setContentTitle(applicationContext.getString(R.string.import_notif_title))
            .setContentText(text)
            .setProgress(total.coerceAtLeast(1), done, pass == null)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp())
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIF_ID, n)
    }

    private fun notifyDone(text: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(nl.spoonpaco.ninegrid.R.drawable.ic_stat_ninegrid)
            .setContentTitle(applicationContext.getString(R.string.import_notif_done))
            .setContentText(text).setAutoCancel(true).setContentIntent(openApp())
            .build()
        runCatching { applicationContext.getSystemService(NotificationManager::class.java).notify(NOTIF_ID + 1, n) }
    }

    companion object {
        const val NAME = "import"
        const val CHANNEL = "import"
        const val NOTIF_ID = 4201
        const val EXTRA_OPEN_IMPORT = "open_import"
        /** Label van dagstanden die uit de Sheet-historie komen. */
        const val SHEET_LABEL = "sheet"

        /**
         * Past de naam uit het bestand bij de gevonden kaart? Een taalcode erachter ("Lightning Bolt (FR)") telt niet mee,
         * de voorkant van een dubbelzijdige kaart is genoeg. Geen naam of een niet-Engelse kaart: niets te vergelijken.
         */
        fun nameFits(c: ImportCard, real: String): Boolean {
            val lang = c.language.trim().uppercase()
            if (c.name.isBlank() || (lang.isNotEmpty() && lang !in setOf("EN", "ENGLISH", "E"))) return true
            fun norm(x: String) = x.lowercase().replace(Regex("""\s*\([a-z]{2}\)\s*$"""), "").filter { it.isLetterOrDigit() }
            val a = norm(c.name)
            val full = norm(real); val front = norm(real.substringBefore(" // "))
            val fileFront = norm(c.name.substringBefore(" // ").substringBefore(" / "))
            return a == full || a == front || fileFront == front
        }

        fun keyOf(c: ImportCard) = c.scryfallId?.let { "id:$it" } ?: "${c.setCode.lowercase()}|${c.number.lowercase()}|${c.name.lowercase()}"

        /** Ronde 1: id of set en nummer. Ronde 2: naam en set. Ronde 3: alleen naam. */
        fun queryFor(c: ImportCard, pass: Int): CardQuery? = when (pass) {
            1 -> when {
                c.scryfallId != null -> CardQuery(id = c.scryfallId)
                c.setCode.isNotBlank() && c.number.isNotBlank() -> CardQuery(setCode = c.setCode, collectorNumber = c.number)
                else -> null
            }
            2 -> if (c.name.isNotBlank() && c.setCode.isNotBlank()) CardQuery(setCode = c.setCode, name = c.name) else null
            else -> if (c.name.isNotBlank()) CardQuery(name = c.name) else null
        }

        fun start(context: Context, job: ImportJob) {
            val dir = File(context.filesDir, "import").apply { mkdirs() }
            val j = Json { encodeDefaults = true }
            File(dir, "progress.json").delete(); File(dir, "outcome.json").delete()
            File(dir, "job.json").writeText(j.encodeToString(ImportJob.serializer(), job))
            val req = OneTimeWorkRequestBuilder<ImportWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun outcome(context: Context): ImportOutcome? = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(ImportOutcome.serializer(), File(context.filesDir, "import/outcome.json").readText())
        }.getOrNull()

        fun clearOutcome(context: Context) { File(context.filesDir, "import/outcome.json").delete() }
    }
}
