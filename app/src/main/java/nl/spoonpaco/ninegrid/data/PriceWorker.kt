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
 * Ververst elke dag de prijzen (alleen via wifi, als de batterij niet bijna leeg is) en maakt daarna
 * een momentopname, zodat de grafiek ook doorloopt als je de app een tijd niet opent.
 * Scryfall ververst zijn prijzen zelf ook één keer per dag.
 */
class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NineGridApp).container
        return runCatching {
            c.repository.refreshPrices(background = true)
            c.settings.setLastPriceUpdate(System.currentTimeMillis())
            c.repository.takeSnapshot()
            Result.success()
        }.getOrElse { if (runAttemptCount < 3) Result.retry() else Result.success() }
    }

    companion object {
        private const val NAME = "daily_prices"

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
        }
    }
}
