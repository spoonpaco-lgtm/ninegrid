package nl.spoonpaco.ninegrid.data.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.NineGridApp
import nl.spoonpaco.ninegrid.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloadt de combodatabase van Commander Spellbook (~30 MB ingepakt) en leest hem stuk voor stuk in.
 * Is het bestand niet veranderd sinds de vorige keer (Last-Modified), dan wordt er niets gedownload.
 */
class ComboDbWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = (applicationContext as NineGridApp).container.comboDb
        channel()
        runCatching { setForeground(info(0, -1)) }
        return try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(ComboDb.URL).header("User-Agent", UA)
                    .apply { if (db.ready()) db.lastModified?.let { header("If-Modified-Since", it) } }.build()
                http.newCall(req).execute().use { r ->
                    if (r.code == 304) { db.touch(); return@withContext }
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val total = r.body!!.contentLength()
                    val counting = Counting(r.body!!.byteStream())
                    counting.use { input ->
                        db.importFrom(input) { n ->
                            val pct = if (total > 0) (counting.read * 100 / total).toInt().coerceIn(0, 99) else -1
                            setProgress(workDataOf("count" to n, "pct" to pct))
                            if (n % 5000 == 0) runCatching { setForeground(info(n, pct)) }
                        }
                    }
                    db.lastModified = r.header("Last-Modified")
                }
            }
            Result.success()
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.combo_db), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private class Counting(inner: java.io.InputStream) : java.io.FilterInputStream(inner) {
        @Volatile var read = 0L
        override fun read(): Int = super.read().also { if (it >= 0) read++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) read += it }
    }

    private fun info(count: Int, pct: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ninegrid)
            .setContentTitle(applicationContext.getString(R.string.combo_db_notif))
            .setContentText(applicationContext.getString(R.string.combo_db_progress, count))
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .setOngoing(true).setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val NAME = "combo_db"
        private const val CHANNEL = "combo_db"
        private const val NOTIF_ID = 4402
        private val UA = "NineGrid/${BuildConfig.VERSION_NAME} (+https://github.com/spoonpaco-lgtm/ninegrid)"
        private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

        /** Handmatig: meteen (wifi, of ook mobiel als [anyNetwork]). Automatisch: tijdens het opladen op wifi. */
        fun start(context: Context, manual: Boolean, anyNetwork: Boolean = false) {
            val cons = Constraints.Builder().setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .apply { if (!manual) setRequiresCharging(true) }
                .setRequiresStorageNotLow(true).build()
            val req = OneTimeWorkRequestBuilder<ComboDbWorker>().setConstraints(cons).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)

        /** Na een week opnieuw kijken (alleen downloaden als Spellbook iets nieuws heeft). */
        fun due(updated: Long, now: Long = System.currentTimeMillis()) = updated > 0L && now - updated > 7L * 24 * 3600 * 1000
    }
}
