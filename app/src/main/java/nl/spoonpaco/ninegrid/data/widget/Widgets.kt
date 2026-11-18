package nl.spoonpaco.ninegrid.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.spoonpaco.ninegrid.AppContainer
import nl.spoonpaco.ninegrid.MainActivity
import nl.spoonpaco.ninegrid.NineGridApp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.PriceWorker
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.domain.Money

/** Widgets voor het startscherm (punt 1e). Ze tekenen zelf een plaatje in de stijl van het logo; geen animatie, geen live prijzen. */
object Widgets {
    const val EXTRA_SCAN = "widget_scan"

    private val providers = listOf(
        ValueWidget::class.java, WinnerWidget::class.java, ScanWidget::class.java, ScanSmallWidget::class.java,
        AlertsWidget::class.java, WishWidget::class.java, DailyWidget::class.java,
    )

    /** Alle widgets op het startscherm opnieuw tekenen (na de prijsupdate, of als de privacymodus wisselt). */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (cls in providers) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) continue
            ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }

    internal fun open(ctx: Context, req: Int, extra: (Intent) -> Unit = {}): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).also(extra)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Het kaartplaatje uit de opslag van de app (of het net); na zes seconden zonder plaatje verder. */
    internal suspend fun cardImage(ctx: Context, c: AppContainer, p: CardPrint): Bitmap? = withTimeoutOrNull(6000) {
        runCatching {
            val req = coil.request.ImageRequest.Builder(ctx).data(c.images.urlFor(p, false)).allowHardware(false).size(300, 420).build()
            (c.images.loader.execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    internal fun money(v: Double, currency: String) = Money.format(v, currency)
}

/** Gemeenschappelijk: breedte en hoogte van het widget bepalen, tekenen op de achtergrond en doorgeven. */
abstract class NgWidget : AppWidgetProvider() {
    /** Het widget in beeld brengen; [w] en [h] in pixels. */
    abstract suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(context, mgr, ids)

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle) = refresh(context, mgr, intArrayOf(id))

    private fun refresh(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val c = (app as NineGridApp).container
                for (id in ids) {
                    val o = mgr.getAppWidgetOptions(id)
                    val d = app.resources.displayMetrics.density
                    val w = ((o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160).coerceAtLeast(60)) * d).toInt().coerceAtMost(900)
                    val h = ((o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70).coerceAtLeast(40)) * d).toInt().coerceAtMost(600)
                    mgr.updateAppWidget(id, build(app, c, w, h))
                }
            } catch (_: Throwable) {
            } finally { pending.finish() }
        }
    }

    /** Een widget met één plaatje, dat bij een tik de app opent. */
    protected fun single(ctx: Context, bmp: Bitmap, click: PendingIntent): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_single).apply {
            setImageViewBitmap(R.id.w_img, bmp)
            setOnClickPendingIntent(R.id.w_root, click)
        }
}

class ValueWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews {
        val s = c.settings.current()
        val v = WidgetData.value(c, s.currency)
        val delta = v.change?.let { (if (it >= 0) "▲ " else "▼ ") + Widgets.money(kotlin.math.abs(it), s.currency) + " " + ctx.getString(R.string.w_since_yesterday) }
        val bmp = WidgetArt.value(w, h, ctx.getString(R.string.w_collection), Widgets.money(v.total, s.currency), delta, (v.change ?: 0.0) >= 0, ctx.getString(R.string.w_30_days), v.series, s.privacy, ctx.getString(R.string.w_privacy_on))
        return single(ctx, bmp, Widgets.open(ctx, 20))
    }
}

class WinnerWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews {
        val s = c.settings.current()
        val m = WidgetData.winner(c, s.currency)
        val title = ctx.getString(R.string.w_winner)
        val bmp = if (m == null) WidgetArt.cardTile(w, h, title, "", null, 0, null, null, false, ctx.getString(R.string.w_no_winner))
        else WidgetArt.cardTile(w, h, title, m.print.name, "+" + Widgets.money(m.totalGain, s.currency), WidgetArt.GREEN,
            if (m.quantity > 1) ctx.getString(R.string.w_copies, m.quantity) else null, Widgets.cardImage(ctx, c, m.print), s.privacy, null)
        return single(ctx, bmp, Widgets.open(ctx, 21) { i -> m?.let { i.putExtra(PriceWorker.EXTRA_OPEN_CARD, it.print.id) } })
    }
}

class DailyWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews {
        val s = c.settings.current()
        val d = WidgetData.daily(c, s.currency)
        val title = ctx.getString(R.string.w_daily)
        val bmp = if (d == null) WidgetArt.cardTile(w, h, title, "", null, 0, null, null, false, ctx.getString(R.string.w_no_cards))
        else WidgetArt.cardTile(w, h, title, d.print.name, Widgets.money(d.unit, s.currency), WidgetArt.GOLD_LIGHT, d.print.setCode.uppercase() + " " + d.print.collectorNumber, Widgets.cardImage(ctx, c, d.print), s.privacy, null)
        return single(ctx, bmp, Widgets.open(ctx, 22) { i -> d?.let { i.putExtra(PriceWorker.EXTRA_OPEN_CARD, it.print.id) } })
    }
}

class AlertsWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews {
        val s = c.settings.current()
        val hide = s.privacy || s.privacyNotify
        val rows = AlertLog.recent(ctx, 4).map { a ->
            val color = when (a.kind) { "breakout" -> WidgetArt.GREEN; "wish" -> WidgetArt.GOLD_LIGHT; "ban" -> WidgetArt.RED; else -> WidgetArt.TILE_S }
            color to (if (hide) a.masked else a.text)
        }
        return single(ctx, WidgetArt.alerts(w, h, ctx.getString(R.string.w_alerts), rows, ctx.getString(R.string.w_no_alerts)), Widgets.open(ctx, 23))
    }
}

class WishWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews {
        val s = c.settings.current()
        val rows = WidgetData.wish(c).map { x ->
            val now = x.now?.let { Widgets.money(it, x.currency) } ?: "–"
            Triple(x.name, now + " → " + Widgets.money(x.target, x.currency), x.now != null && x.now <= x.target)
        }
        val bmp = WidgetArt.wish(w, h, ctx.getString(R.string.w_wish), rows, s.privacy, ctx.getString(R.string.w_no_wish))
        return single(ctx, bmp, Widgets.open(ctx, 24) { it.putExtra(PriceWorker.EXTRA_OPEN_WISH, true) })
    }
}

/** Scan-snelknop, breed: twee knoppen, Blad (9 kaarten) en Kaart (één tegelijk). */
class ScanWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews {
        val bw = (w * 0.46f).toInt().coerceAtLeast(40); val bh = (h * 0.8f).toInt().coerceAtLeast(40)
        return RemoteViews(ctx.packageName, R.layout.widget_scan).apply {
            setImageViewBitmap(R.id.w_scan_sheet, WidgetArt.scanButton(bw, bh, true, ctx.getString(R.string.w_scan_sheet), ctx.getString(R.string.w_scan_sheet_sub)))
            setImageViewBitmap(R.id.w_scan_card, WidgetArt.scanButton(bw, bh, false, ctx.getString(R.string.w_scan_card), null))
            setOnClickPendingIntent(R.id.w_scan_sheet, Widgets.open(ctx, 25) { it.putExtra(Widgets.EXTRA_SCAN, "page") })
            setOnClickPendingIntent(R.id.w_scan_card, Widgets.open(ctx, 26) { it.putExtra(Widgets.EXTRA_SCAN, "single") })
        }
    }
}

/** Scan-snelknop, klein (1 bij 1): opent de scanner zoals hij stond. */
class ScanSmallWidget : NgWidget() {
    override suspend fun build(ctx: Context, c: AppContainer, w: Int, h: Int): RemoteViews =
        single(ctx, WidgetArt.scanSmall(w, h, ctx.getString(R.string.w_scan)), Widgets.open(ctx, 27) { it.putExtra(Widgets.EXTRA_SCAN, "") })
}
