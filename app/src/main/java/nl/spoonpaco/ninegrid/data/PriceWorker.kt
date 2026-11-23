package nl.spoonpaco.ninegrid.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.spoonpaco.ninegrid.NineGridApp
import java.util.concurrent.TimeUnit

/**
 * Ververst elke dag de prijzen (via wifi, of na 48 uur zonder wifi via mobiele data; niet bij een bijna lege batterij) en maakt daarna
 * een momentopname, zodat de grafiek ook doorloopt als je de app een tijd niet opent.
 * Scryfall ververst zijn prijzen zelf ook één keer per dag.
 */
class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NineGridApp).container
        // De reserve-taak (elk netwerk) doet alleen iets als er 48 uur geen prijzen zijn opgehaald.
        if (inputData.getBoolean(KEY_FALLBACK, false) &&
            System.currentTimeMillis() - c.settings.current().lastPriceUpdate < 48L * 3600 * 1000) return Result.success()
        return runCatching {
            c.repository.refreshPrices(background = true)
            c.settings.setLastPriceUpdate(System.currentTimeMillis())
            c.repository.takeSnapshot()
            runCatching { notifyBreakout(c) }
            runCatching { notifyWishAlarms(c) }
            runCatching { notifyWatch(c) }
            runCatching { checkOfflineDb(c) }
            // Combodatabase (0.9.18): eens per week kijken of Commander Spellbook iets nieuws heeft (alleen als hij gedownload is).
            runCatching { if (nl.spoonpaco.ninegrid.data.offline.ComboDbWorker.due(c.comboDb.updated)) nl.spoonpaco.ninegrid.data.offline.ComboDbWorker.start(applicationContext, manual = false) }
            // Vingerafdrukken (1h-scan): sets op de telefoon aanvullen met nieuwe kaarten; niet op mobiel internet bij "alleen via wifi".
            runCatching {
                val st = c.settings.current()
                if (st.pictureMatch && c.images.networkAllowed(st)) c.fingerprints.refreshAll()
            }
            runCatching { nl.spoonpaco.ninegrid.data.widget.Widgets.updateAll(applicationContext) }
            Result.success()
        }.getOrElse { if (runAttemptCount < 3) Result.retry() else Result.success() }
    }

    /** Breakout [type]: hoogstens één melding per dag, voor de kaart die vandaag het hardst steeg. */
    private suspend fun notifyBreakout(c: nl.spoonpaco.ninegrid.AppContainer) {
        val s = c.settings.current()
        val today = java.time.LocalDate.now().toString()
        if (!s.breakoutAlerts || s.lastBreakoutDay == today) return
        val m = c.repository.breakout(s.currency) ?: return
        val ctx = applicationContext
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, ctx.getString(nl.spoonpaco.ninegrid.R.string.breakout_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        // Privacymodus: geen bedragen in de melding (die is op het vergrendelscherm te zien).
        val money = { v: Double -> if (s.privacy || s.privacyNotify) "••••" else nl.spoonpaco.ninegrid.domain.Money.format(v, s.currency) }
        val open = android.app.PendingIntent.getActivity(
            ctx, 0, android.content.Intent(ctx, nl.spoonpaco.ninegrid.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(nl.spoonpaco.ninegrid.R.drawable.ic_stat_ninegrid)
            .setContentTitle("Breakout " + nl.spoonpaco.ninegrid.domain.PriceMovers.mainType(m.print.typeLine))
            .setContentText(ctx.getString(nl.spoonpaco.ninegrid.R.string.breakout_text, m.print.name, "+" + money(m.unitGain),
                String.format(java.util.Locale.getDefault(), "%+.0f%%", m.pct), money(m.newUnit)))
            .setContentIntent(open).setAutoCancel(true)
            .build()
        nm.notify(BREAKOUT_ID, n)
        val pctText = String.format(java.util.Locale.getDefault(), "%+.0f%%", m.pct)
        nl.spoonpaco.ninegrid.data.widget.AlertLog.add(ctx, "breakout", m.print.name + " +" + nl.spoonpaco.ninegrid.domain.Money.format(m.unitGain, s.currency) + " (" + pctText + ")", m.print.name + " " + pctText)
        c.settings.setLastBreakoutDay(today)
    }

    /** Prijsalarm: één melding voor alle kaarten van de verlanglijst die onder hun doelprijs kwamen. */
    private suspend fun notifyWishAlarms(c: nl.spoonpaco.ninegrid.AppContainer) {
        val hits = c.repository.checkWishAlarms()
        if (hits.isEmpty()) return
        val ctx = applicationContext
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, ctx.getString(nl.spoonpaco.ninegrid.R.string.breakout_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = android.content.Intent(ctx, nl.spoonpaco.ninegrid.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (hits.size == 1) intent.putExtra(EXTRA_OPEN_CARD, hits[0].first.printId) else intent.putExtra(EXTRA_OPEN_WISH, true)
        val open = android.app.PendingIntent.getActivity(ctx, 3, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val w = hits[0].first
        val title = if (hits.size == 1) ctx.getString(nl.spoonpaco.ninegrid.R.string.wish_alarm_one, w.name)
        else ctx.getString(nl.spoonpaco.ninegrid.R.string.wish_alarm_many, hits.size)
        val hide = c.settings.current().let { it.privacy || it.privacyNotify }
        val text = hits.joinToString(" · ") { (x, p) -> x.name + if (hide) "" else " " + nl.spoonpaco.ninegrid.domain.Money.format(p, x.alarmCurrency) }
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(nl.spoonpaco.ninegrid.R.drawable.ic_stat_ninegrid)
            .setContentTitle(title).setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .build()
        nm.notify(WISH_ID, n)
        nl.spoonpaco.ninegrid.data.widget.AlertLog.add(ctx, "wish", title + ": " + hits.joinToString(" · ") { (x, p) -> x.name + " " + nl.spoonpaco.ninegrid.domain.Money.format(p, x.alarmCurrency) }, title)
    }

    /**
     * Bans en reprints (punt 40): één melding voor de bans, één voor de reprints. Tik op een melding met één kaart opent die kaart.
     * Privacymodus: geen bedragen (de melding is op het vergrendelscherm te zien).
     */
    private suspend fun notifyWatch(c: nl.spoonpaco.ninegrid.AppContainer) {
        val s = c.settings.current()
        val r = c.watcher.check(s)
        if (r.legal.isEmpty() && r.reprints.isEmpty()) return
        val ctx = applicationContext
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel(WATCH_CHANNEL, ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        val money = { v: Double -> if (s.privacy || s.privacyNotify) "••••" else nl.spoonpaco.ninegrid.domain.Money.format(v, s.currency) }
        fun formatName(f: String) = f.replaceFirstChar { it.uppercase() }
        fun post(id: Int, req: Int, title: String, lines: List<String>, printId: String?) {
            val intent = android.content.Intent(ctx, nl.spoonpaco.ninegrid.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (printId != null) intent.putExtra(EXTRA_OPEN_CARD, printId)
            val open = android.app.PendingIntent.getActivity(ctx, req, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            val text = lines.joinToString("\n")
            val n = androidx.core.app.NotificationCompat.Builder(ctx, WATCH_CHANNEL)
                .setSmallIcon(nl.spoonpaco.ninegrid.R.drawable.ic_stat_ninegrid)
                .setContentTitle(title).setContentText(lines.first())
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true)
                .build()
            nm.notify(id, n)
            nl.spoonpaco.ninegrid.data.widget.AlertLog.add(ctx, if (id == WATCH_LEGAL_ID) "ban" else "reprint", lines.first(), title)
        }
        if (r.legal.isNotEmpty()) {
            val heads = r.legal.map { a ->
                ctx.getString(when (a.kind) {
                    nl.spoonpaco.ninegrid.domain.Watch.Kind.BANNED -> nl.spoonpaco.ninegrid.R.string.watch_banned
                    nl.spoonpaco.ninegrid.domain.Watch.Kind.RESTRICTED -> nl.spoonpaco.ninegrid.R.string.watch_restricted
                    nl.spoonpaco.ninegrid.domain.Watch.Kind.UNBANNED -> nl.spoonpaco.ninegrid.R.string.watch_unbanned
                    nl.spoonpaco.ninegrid.domain.Watch.Kind.NOT_LEGAL -> nl.spoonpaco.ninegrid.R.string.watch_not_legal
                    nl.spoonpaco.ninegrid.domain.Watch.Kind.NOW_LEGAL -> nl.spoonpaco.ninegrid.R.string.watch_now_legal
                }, a.name, formatName(a.format))
            }
            val lines = r.legal.mapIndexed { i, a ->
                heads[i] + ". " + ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_you_have, a.copies, money(a.value)) +
                    if (a.decks.isNotEmpty()) " " + ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_in_decks, a.decks.joinToString(", ")) else ""
            }
            val title = if (r.legal.size == 1) heads.first() else ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_legal_many, r.legal.size)
            post(WATCH_LEGAL_ID, 5, title, lines, r.legal.map { it.printId }.distinct().singleOrNull())
        }
        if (r.reprints.isNotEmpty()) {
            val df = c.settings.current().dateStyle
            val lines = r.reprints.map { a ->
                val day = runCatching { nl.spoonpaco.ninegrid.domain.Dates.date(java.time.LocalDate.parse(a.releasedAt), df) }.getOrDefault(a.releasedAt)
                ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_reprint_line, a.name, a.setName, day,
                    a.yourSets.joinToString("/"), money(a.unitValue))
            }
            val title = if (r.reprints.size == 1) ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_reprint_one, r.reprints.first().name)
            else ctx.getString(nl.spoonpaco.ninegrid.R.string.watch_reprint_many, r.reprints.size)
            post(WATCH_REPRINT_ID, 6, title, lines, r.reprints.singleOrNull()?.printId)
        }
    }

    /** Offline kaartdatabase "bij nieuwe set": bijwerken als er een nieuwe set uit is, of na een maand. */
    private suspend fun checkOfflineDb(c: nl.spoonpaco.ninegrid.AppContainer) {
        val s = c.settings.current()
        if (s.offlineDb != OfflineDb.NEW_SET) return
        runCatching { c.cache.ensureNames() }
        val today = java.time.LocalDate.now().toString()
        val newest = c.cache.setInfo().values.filter { !it.digital }.mapNotNull { it.releasedAt }.filter { it <= today }.maxOrNull()
        if (nl.spoonpaco.ninegrid.data.offline.OfflineDbWorker.due(s.offlineDbUpdated, newest)) {
            nl.spoonpaco.ninegrid.data.offline.OfflineDbWorker.start(applicationContext, manual = false, anyNetwork = s.offlineDbMobileAuto)
        }
    }

    companion object {
        private const val CHANNEL = "breakout"
        private const val BREAKOUT_ID = 4301
        private const val WISH_ID = 4303
        private const val WATCH_CHANNEL = "watch"
        private const val WATCH_LEGAL_ID = 4305
        private const val WATCH_REPRINT_ID = 4306
        const val EXTRA_OPEN_CARD = "open_card"
        const val EXTRA_OPEN_WISH = "open_wish"
        private const val NAME = "daily_prices"
        private const val NAME_FALLBACK = "daily_prices_any_network"
        private const val KEY_FALLBACK = "fallback"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<PriceWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            // Reserve: geen wifi gehad? Na 48 uur dan via mobiele data (een paar honderd KB), zodat de tijdlijn geen gat krijgt.
            val fallback = PeriodicWorkRequestBuilder<PriceWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInputData(androidx.work.workDataOf(KEY_FALLBACK to true))
                .setInitialDelay(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME_FALLBACK, ExistingPeriodicWorkPolicy.KEEP, fallback)
        }
    }
}
