package nl.spoonpaco.ninegrid.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.spoonpaco.ninegrid.NineGridApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Maakt elke week een back-up in de map die de gebruiker heeft gekozen. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NineGridApp).container
        val folder = c.settings.current().backupFolder ?: return Result.success()
        return runCatching {
            writeBackup(applicationContext, c.repository, Uri.parse(folder), c.settings)
            c.settings.setLastBackup(System.currentTimeMillis())
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val NAME = "weekly_backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        suspend fun writeBackup(context: Context, repo: Repository, treeUri: Uri, settings: SettingsStore): BackupFile {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: error("no folder")
            val name = "ninegrid-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
            dir.findFile(name)?.delete()
            val file = dir.createFile("application/json", name) ?: error("cannot create")
            val (backup, text) = Backups.toJson(context, repo, settings)
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(text.toByteArray()) }
            return backup
        }
    }
}
