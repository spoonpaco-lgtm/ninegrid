package nl.spoonpaco.ninegrid.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.MainActivity
import nl.spoonpaco.ninegrid.NineGridApp
import nl.spoonpaco.ninegrid.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bijwerken vanuit de app: de APK van GitHub downloaden en via de Android-installer installeren.
 * De eerste keer vraagt Android om toestemming ("apps installeren uit deze bron") en om één tik op Bijwerken.
 * Vanaf Android 12 mag een app die zichzelf eerder installeerde zich daarna bijwerken zonder vraag; lukt dat niet,
 * dan blijft het één tik.
 */
object Updater {
    const val CHANNEL = "update"
    const val NOTIF_ID = 4302
    const val EXTRA_INSTALL = "install_update"
    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(c: Context) = File(c.filesDir, "update").apply { mkdirs() }
    private fun apkFile(c: Context, version: String) = File(dir(c), "NineGrid-$version.apk")
    private fun infoFile(c: Context) = File(dir(c), "update.json")

    /** De klaarstaande update (al gedownload), als die nieuwer is dan wat er draait. */
    fun ready(c: Context): UpdateInfo? = runCatching { json.decodeFromString(UpdateInfo.serializer(), infoFile(c).readText()) }.getOrNull()
        ?.takeIf { UpdateChecker.isNewer(it.version, BuildConfig.VERSION_NAME) && apkFile(c, it.version).let { f -> f.exists() && f.length() > 0 } }

    /** Ruimt oude downloads op (na een geslaagde update of als ze verouderd zijn). */
    fun cleanup(c: Context) {
        dir(c).listFiles()?.forEach { f ->
            val v = f.name.removePrefix("NineGrid-").removeSuffix(".apk")
            if (f.name.endsWith(".apk") && !UpdateChecker.isNewer(v, BuildConfig.VERSION_NAME)) f.delete()
        }
    }

    /** Download met voortgang (0..1). Bestaat hij al helemaal, dan meteen klaar. */
    suspend fun download(c: Context, info: UpdateInfo, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: error("no apk")
        val target = apkFile(c, info.version)
        if (target.exists() && info.apkSize > 0 && target.length() == info.apkSize) return@withContext target
        val tmp = File(target.path + ".part")
        val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        http.newCall(Request.Builder().url(url).header("User-Agent", "NineGrid/${BuildConfig.VERSION_NAME}").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("GitHub ${r.code}")
            val body = r.body ?: throw java.io.IOException("empty")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target.delete(); tmp.renameTo(target)
        infoFile(c).writeText(json.encodeToString(UpdateInfo.serializer(), info))
        target
    }

    /** Mag NineGrid apps installeren? Zo niet, dan eerst de instelling openen. */
    fun canInstall(c: Context) = Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    fun permissionIntent(c: Context) = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + c.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Installeren via een PackageInstaller-sessie. [quiet] = proberen zonder tik (Android 12+, als NineGrid zelf de installer is);
     * Android geeft anders "gebruiker moet bevestigen" terug, en dat handelt [InstallReceiver] af.
     */
    fun install(c: Context, apk: File, quiet: Boolean) {
        val pi = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(
                if (quiet) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED else PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED,
            )
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("ninegrid.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val intent = Intent(c, InstallReceiver::class.java).putExtra("quiet", quiet)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(c, id, intent, flags).intentSender)
        }
    }

    fun channel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Melding "NineGrid 0.9.1 – Kaya staat klaar · Bijwerken". [confirm] = het bevestigingsvenster van Android (na een stille poging). */
    fun notifyReady(c: Context, info: UpdateInfo, confirm: Intent? = null) {
        channel(c)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val tap = if (confirm != null) PendingIntent.getActivity(c, 2, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        else PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).putExtra(EXTRA_INSTALL, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val title = info.title.removePrefix("NineGrid ").ifBlank { "v" + info.version }
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ninegrid)
            .setContentTitle(c.getString(R.string.update_ready_title, title))
            .setContentText(c.getString(R.string.update_ready_body))
            .setContentIntent(tap).setAutoCancel(true)
            .addAction(0, c.getString(R.string.update_now), tap)
            .build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(c).notify(NOTIF_ID, n) }
    }
}

/** Uitkomst van de installatie. Moet de gebruiker bevestigen, dan het venster openen (of een melding als de app op de achtergrond is). */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent) ?: return
                val info = Updater.ready(context)
                if (intent.getBooleanExtra("quiet", false) && info != null) Updater.notifyReady(context, info, confirm)
                else runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { if (info != null) Updater.notifyReady(context, info, confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                android.util.Log.w("NineGrid", "update niet geïnstalleerd: $msg")
                UpdateEvents.failed = msg ?: "?"
            }
        }
    }
}

/** Laatste mislukte installatie, voor een melding in de app. */
object UpdateEvents { @Volatile var failed: String? = null }

/** Eén keer per dag: kijken of er een update is en hem (standaard alleen via wifi) alvast downloaden. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NineGridApp).container
        val st = c.settings.current()
        if (st.updateAuto == "off") return Result.success()
        Updater.cleanup(applicationContext)
        val info = (UpdateChecker.check() as? UpdateResult.Available)?.info ?: return Result.success()
        c.settings.setLastUpdateCheck(System.currentTimeMillis())
        if (info.apkUrl == null) { Updater.notifyReady(applicationContext, info); return Result.success() }
        val apk = runCatching { Updater.download(applicationContext, info) }.getOrElse { return Result.retry() }
        // Stil proberen kan alleen met toestemming en vanaf Android 12; anders een melding om te tikken.
        if (Build.VERSION.SDK_INT >= 31 && Updater.canInstall(applicationContext)) {
            runCatching { Updater.install(applicationContext, apk, quiet = true) }.onFailure { Updater.notifyReady(applicationContext, info) }
        } else Updater.notifyReady(applicationContext, info)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily_update"
        fun schedule(context: Context, mode: String) {
            val wm = WorkManager.getInstance(context)
            if (mode == "off") { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (mode == "any") NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
