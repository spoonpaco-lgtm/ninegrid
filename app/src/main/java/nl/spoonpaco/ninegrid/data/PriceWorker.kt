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
            runCatching { checkOfflineDb(c) }
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
        val money = { v: Double -> nl.spoonpaco.ninegrid.domain.Money.format(v, s.currency) }
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
        c.settings.setLastBreakoutDay(today)
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
