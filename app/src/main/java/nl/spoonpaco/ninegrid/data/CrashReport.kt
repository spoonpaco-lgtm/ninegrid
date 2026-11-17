package nl.spoonpaco.ninegrid.data

import android.content.Context
import android.os.Build
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.domain.CrashText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bewaart bij een crash een klein rapport op de telefoon. Er wordt niets verstuurd: bij de volgende start kan de gebruiker
 * het delen via het deelmenu van Android (zie AppNav), en onder Ontwikkelaar staat het laatste rapport nog.
 */
object CrashReport {
    private fun dir(ctx: Context) = File(ctx.filesDir, "crash").also { it.mkdirs() }
    private fun pending(ctx: Context) = File(dir(ctx), "pending.txt")
    private fun last(ctx: Context) = File(dir(ctx), "last.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            runCatching {
                val text = CrashText.build(
                    BuildConfig.VERSION_NAME + " – " + BuildConfig.CODENAME, Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE,
                    thread.name, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()), CrashText.traceOf(t),
                )
                pending(app).writeText(text)
            }
            previous?.uncaughtException(thread, t)
        }
    }

    /** Is er een crash waar de gebruiker nog niet naar gevraagd is? */
    fun hasPending(ctx: Context) = pending(ctx).exists()

    /** Het laatste rapport (ook als er al naar gevraagd is), of null. */
    fun latest(ctx: Context): File? = pending(ctx).takeIf { it.exists() } ?: last(ctx).takeIf { it.exists() }

    /** De vraag is gesteld: het rapport blijft onder Ontwikkelaar staan, maar de vraag komt niet terug. */
    fun markAsked(ctx: Context) {
        val p = pending(ctx)
        if (p.exists()) { last(ctx).delete(); p.renameTo(last(ctx)) }
    }

    /** Kopie in de deelmap (alleen die map is met andere apps te delen). */
    fun shareCopy(ctx: Context): File? {
        val src = latest(ctx) ?: return null
        val out = File(File(ctx.cacheDir, "share").also { it.mkdirs() }, "crashrapport.txt")
        src.copyTo(out, overwrite = true)
        return out
    }

    /** Opent het deelmenu van Android met het rapport. Geeft false als er geen rapport is. */
    fun share(ctx: Context): Boolean {
        val f = shareCopy(ctx) ?: return false
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "NineGrid crashrapport")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun clear(ctx: Context) { pending(ctx).delete(); last(ctx).delete() }
}
