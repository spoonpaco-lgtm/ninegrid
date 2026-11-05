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
            Result.success()
        }.getOrElse { if (runAttemptCount < 3) Result.retry() else Result.success() }
    }

    companion object {
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
