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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.NineGridApp
import nl.spoonpaco.ninegrid.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Wat Scryfall over de bulk-download zegt: waar hij staat, hoe groot hij is en wanneer bijgewerkt. */
@Serializable
data class BulkInfo(
    @SerialName("download_uri") val downloadUri: String,
    val size: Long = 0,
    @SerialName("updated_at") val updatedAt: String = "",
)

/**
 * Downloadt de kaartgegevens van Scryfall (Default Cards) en leest ze stuk voor stuk in de offline
 * kaartdatabase. Draait op de voorgrond met een melding, want het kan een paar minuten duren.
 */
class OfflineDbWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NineGridApp).container
        channel()
        runCatching { setForeground(info(0)) }
        return try {
            val meta = fetchInfo() ?: return Result.retry()
            val count = withContext(Dispatchers.IO) {
                val resp = http.newCall(Request.Builder().url(meta.downloadUri).header("User-Agent", UA).build()).execute()
                resp.use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    r.body!!.byteStream().buffered(1 shl 16).use { input ->
                        c.offline.importFrom(input) { n ->
                            setProgress(workDataOf("count" to n))
                            if (n % 5000 == 0) runCatching { setForeground(info(n)) }
                        }
                    }
                }
            }
            c.settings.setOfflineDbDone(System.currentTimeMillis(), count)
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
                .createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.offline_db), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun info(count: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.offline_db_notif))
            .setContentText(applicationContext.getString(R.string.offline_db_progress, count))
            .setProgress(0, 0, true)
            .setOngoing(true).setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val NAME = "offline_db"
        private const val CHANNEL = "offline_db"
        private const val NOTIF_ID = 4401
        private val UA = "NineGrid/${BuildConfig.VERSION_NAME} (+https://github.com/spoonpaco-lgtm/ninegrid)"
        private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
        private val json = Json { ignoreUnknownKeys = true }

        /** Grootte en datum van de download, zonder hem te downloaden (klein verzoekje). */
        suspend fun fetchInfo(): BulkInfo? = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("https://api.scryfall.com/bulk-data/default-cards").header("User-Agent", UA).header("Accept", "application/json").build())
                    .execute().use { r -> if (r.isSuccessful) json.decodeFromString(BulkInfo.serializer(), r.body!!.string()) else null }
            }.getOrNull()
        }

        /**
         * Starten. Automatisch: tijdens het opladen. Handmatig: meteen. [anyNetwork] = ook via mobiele data
         * (alleen als je dat zelf hebt gekozen); anders wacht hij op wifi.
         */
        fun start(context: Context, manual: Boolean, anyNetwork: Boolean = false) {
            val cons = Constraints.Builder().setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .apply { if (!manual) setRequiresCharging(true) }
                .setRequiresStorageNotLow(true)
                .build()
            val req = OneTimeWorkRequestBuilder<OfflineDbWorker>().setConstraints(cons).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)

        /**
         * Moet de database worden bijgewerkt? Bij "nieuwe set": als er sinds de laatste download een set
         * is verschenen (geen digitale), of als hij ouder is dan een maand (vangnet).
         */
        fun due(updatedAt: Long, newestRelease: String?, now: Long = System.currentTimeMillis()): Boolean {
            if (updatedAt == 0L) return true
            if (now - updatedAt > 30L * 24 * 3600 * 1000) return true
            val last = java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            return newestRelease != null && newestRelease > last
        }
    }
}
