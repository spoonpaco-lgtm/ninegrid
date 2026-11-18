package nl.spoonpaco.ninegrid.data.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import kotlin.math.max
import kotlin.math.min

/** Tekent de widgets in de stijl van het logo (donker, goud, donkerblauwe tegels). Het kader zelf zit in de layout. */
object WidgetArt {
    const val GOLD = 0xFFE0A93B.toInt()
    const val GOLD_LIGHT = 0xFFF5C04A.toInt()
    const val MUTED = 0xFFB9A98A.toInt()
    const val GREEN = 0xFF5FD48A.toInt()
    const val RED = 0xFFE5707A.toInt()
    const val TILE = 0xFF1F3D6B.toInt()
    const val TILE_S = 0xFF3F6FAE.toInt()
    const val TILE_DARK = 0xFF16294A.toInt()
    const val CREAM = 0xFFF0E4CC.toInt()
    private const val CENTER_BG = 0xFF2A1A0C.toInt()

    private fun paint(color: Int, size: Float = 0f, bold: Boolean = false, serif: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (size > 0f) textSize = size
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Tekst die zo nodig kleiner wordt om in [maxW] te passen. */
    private fun fitText(c: Canvas, text: String, x: Float, y: Float, size: Float, maxW: Float, color: Int, bold: Boolean = false, center: Boolean = false) {
        val p = paint(color, size, bold)
        val w = p.measureText(text)
        if (w > maxW && w > 0f) p.textSize = size * maxW / w
        if (center) p.textAlign = Paint.Align.CENTER
        c.drawText(text, x, y, p)
    }

    private fun ellipsized(text: String, p: Paint, maxW: Float) = TextUtils.ellipsize(text, android.text.TextPaint(p), maxW, TextUtils.TruncateAt.END).toString()

    /** Rijtje donkerblauwe tegeltjes (de afdekking in privacymodus), [n] stuks vanaf (x, y), [s] groot. Geeft de breedte terug. */
    fun tiles(c: Canvas, x: Float, y: Float, s: Float, n: Int): Float {
        val fill = paint(TILE); val line = paint(TILE_S).apply { style = Paint.Style.STROKE; strokeWidth = max(1.5f, s * 0.06f) }
        for (i in 0 until n) {
            val r = RectF(x + i * (s * 1.2f), y, x + i * (s * 1.2f) + s, y + s)
            c.drawRoundRect(r, s * 0.24f, s * 0.24f, fill)
            c.drawRoundRect(r, s * 0.24f, s * 0.24f, line)
        }
        return n * s * 1.2f - s * 0.2f
    }

    /** Het lijntje van de laatste dagen; in privacymodus alleen de vorm, gestippeld. */
    private fun sparkline(c: Canvas, x: Float, y: Float, w: Float, h: Float, series: List<Double>, dashed: Boolean) {
        if (series.size < 2) return
        val lo = series.minOrNull() ?: 0.0; val hi = series.maxOrNull() ?: 1.0
        val span = if (hi - lo < 1e-9) 1.0 else hi - lo
        val path = Path()
        series.forEachIndexed { i, v ->
            val px = x + w * i / (series.size - 1)
            val py = (y + h - h * ((v - lo) / span)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (!dashed) {
            val fill = Path(path); fill.lineTo(x + w, y + h); fill.lineTo(x, y + h); fill.close()
            c.drawPath(fill, paint(GOLD).apply { alpha = 46 })
        }
        val line = paint(if (dashed) TILE_S else GOLD_LIGHT).apply {
            style = Paint.Style.STROKE; strokeWidth = max(2f, h * 0.05f); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(h * 0.08f, h * 0.1f), 0f)
        }
        c.drawPath(path, line)
    }

    /** Waarde-widget: waarde, verschil met gisteren en het lijntje van 30 dagen. */
    fun value(w: Int, h: Int, label: String, money: String, delta: String?, deltaUp: Boolean, daysLabel: String, series: List<Double>, privacy: Boolean, privacyText: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val pad = h * 0.1f
        val chartW = w * 0.34f
        val textW = w - pad * 2 - chartW - pad
        fitText(c, label, pad, pad + h * 0.13f, h * 0.15f, textW, MUTED)
        if (privacy) {
            val s = h * 0.24f
            tiles(c, pad, pad + h * 0.26f, s, 4)
            fitText(c, privacyText, pad, h - pad * 1.4f, h * 0.13f, textW, MUTED)
        } else {
            fitText(c, money, pad, pad + h * 0.52f, h * 0.31f, textW, GOLD_LIGHT, bold = true)
            if (delta != null) fitText(c, delta, pad, h - pad * 1.4f, h * 0.13f, textW, if (deltaUp) GREEN else RED)
        }
        val cx = w - pad - chartW
        fitText(c, daysLabel, cx, pad + h * 0.13f, h * 0.13f, chartW, MUTED)
        sparkline(c, cx, pad + h * 0.26f, chartW, h - pad * 2 - h * 0.26f, series, privacy)
        return bmp
    }

    /** Kaarttegel voor Winner of the Week en Kaart van de dag: plaatje links, daarnaast titel, naam en een regel (bedrag of tegeltjes). */
    fun cardTile(w: Int, h: Int, title: String, name: String, line: String?, lineColor: Int, sub: String?, image: Bitmap?, privacy: Boolean, empty: String?): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val pad = h * 0.08f
        var textX = pad
        if (image != null) {
            val ih = h - pad * 2
            val iw = min(ih * image.width / image.height.toFloat(), w * 0.46f)
            val dst = RectF(pad, pad, pad + iw, pad + iw * image.height / image.width)
            val clip = Path().apply { addRoundRect(dst, iw * 0.06f, iw * 0.06f, Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(image, null, dst, paint(CREAM).apply { isFilterBitmap = true }); c.restore()
            textX = pad + iw + pad
        }
        val textW = w - textX - pad
        fitText(c, title, textX, pad + h * 0.1f, h * 0.1f, textW, GOLD)
        if (empty != null) {
            fitText(c, empty, textX, h * 0.5f, h * 0.1f, textW, MUTED)
            return bmp
        }
        val namePaint = paint(CREAM, h * 0.12f, true)
        c.drawText(ellipsized(name, namePaint, textW), textX, pad + h * 0.3f, namePaint)
        if (privacy) tiles(c, textX, h * 0.52f, h * 0.15f, 3)
        else if (line != null) fitText(c, line, textX, h * 0.7f, h * 0.17f, textW, lineColor, bold = true)
        if (sub != null) fitText(c, sub, textX, h * 0.86f, h * 0.1f, textW, MUTED)
        return bmp
    }

    /** Alarmen: de laatste meldingen als rijtjes met een gekleurd bolletje. */
    fun alerts(w: Int, h: Int, title: String, rows: List<Pair<Int, String>>, empty: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val pad = h * 0.08f
        fitText(c, title, pad, pad + h * 0.1f, h * 0.1f, w - pad * 2, GOLD, bold = true)
        if (rows.isEmpty()) { fitText(c, empty, pad, h * 0.5f, h * 0.11f, w - pad * 2, MUTED); return bmp }
        val rowH = (h - pad * 2 - h * 0.16f) / 4f
        val tp = paint(CREAM, min(rowH * 0.5f, h * 0.11f))
        rows.take(4).forEachIndexed { i, (color, text) ->
            val y = pad + h * 0.2f + rowH * i + rowH * 0.62f
            c.drawCircle(pad + rowH * 0.18f, y - rowH * 0.18f, rowH * 0.13f, paint(color))
            c.drawText(ellipsized(text, tp, w - pad * 2 - rowH * 0.6f), pad + rowH * 0.5f, y, tp)
        }
        return bmp
    }

    /** Verlanglijst: de kaarten het dichtst bij hun alarmprijs, met prijs nu en doel (of tegeltjes in privacymodus). */
    fun wish(w: Int, h: Int, title: String, rows: List<Triple<String, String, Boolean>>, privacy: Boolean, empty: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val pad = h * 0.08f
        fitText(c, title, pad, pad + h * 0.1f, h * 0.1f, w - pad * 2, GOLD, bold = true)
        if (rows.isEmpty()) { fitText(c, empty, pad, h * 0.5f, h * 0.11f, w - pad * 2, MUTED); return bmp }
        val rowH = (h - pad * 2 - h * 0.16f) / 3f
        val np = paint(CREAM, min(rowH * 0.42f, h * 0.12f))
        rows.take(3).forEachIndexed { i, (name, price, near) ->
            val y = pad + h * 0.2f + rowH * i + rowH * 0.6f
            val priceW = if (privacy) rowH * 1.4f else w * 0.36f
            c.drawText(ellipsized(name, np, w - pad * 2 - priceW - pad), pad, y, np)
            if (privacy) tiles(c, w - pad - rowH * 1.4f, y - rowH * 0.4f, rowH * 0.4f, 3)
            else {
                val pp = paint(if (near) GREEN else GOLD_LIGHT, min(rowH * 0.4f, h * 0.11f), true).apply { textAlign = Paint.Align.RIGHT }
                val s = price
                if (pp.measureText(s) > priceW) pp.textSize = pp.textSize * priceW / pp.measureText(s)
                c.drawText(s, w - pad, y, pp)
            }
        }
        return bmp
    }

    /** Mini-raster (3 bij 3 tegels, de 9 in het midden) in het logo, [s] groot, linksboven op (x, y). */
    private fun miniGrid(c: Canvas, x: Float, y: Float, s: Float) {
        val t = s / 3.4f; val gap = t * 0.2f
        for (r in 0 until 3) for (col in 0 until 3) {
            val rect = RectF(x + col * (t + gap), y + r * (t + gap), x + col * (t + gap) + t, y + r * (t + gap) + t)
            val centre = r == 1 && col == 1
            c.drawRoundRect(rect, t * 0.24f, t * 0.24f, paint(if (centre) CENTER_BG else TILE))
            c.drawRoundRect(rect, t * 0.24f, t * 0.24f, paint(if (centre) GOLD_LIGHT else TILE_S).apply { style = Paint.Style.STROKE; strokeWidth = max(1.5f, t * 0.08f) })
        }
    }

    /** Scanknop: [sheet] = het raster van 9 (blad), anders één kaart. [sub] staat onder het label (mag leeg). */
    fun scanButton(w: Int, h: Int, sheet: Boolean, label: String, sub: String?): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val r = min(w, h) * 0.2f
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.drawRoundRect(rect, r, r, paint(TILE_DARK))
        c.drawRoundRect(RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f), r, r, paint(TILE_S).apply { style = Paint.Style.STROKE; strokeWidth = 3f })
        val iconS = min(w * 0.5f, h * 0.5f)
        val ix = (w - iconS) / 2f; val iy = h * 0.12f
        if (sheet) miniGrid(c, ix, iy, iconS)
        else {
            val cw = iconS * 0.62f; val ch = iconS * 0.86f
            val card = RectF((w - cw) / 2f, iy, (w + cw) / 2f, iy + ch)
            c.drawRoundRect(card, cw * 0.1f, cw * 0.1f, paint(GOLD_LIGHT).apply { style = Paint.Style.STROKE; strokeWidth = max(3f, cw * 0.08f) })
            c.drawRoundRect(RectF(card.left + cw * 0.12f, card.top + ch * 0.1f, card.right - cw * 0.12f, card.top + ch * 0.45f), cw * 0.05f, cw * 0.05f, paint(TILE))
        }
        val ly = h * 0.78f
        fitText(c, label, w / 2f, if (sub != null) ly - h * 0.02f else ly + h * 0.04f, h * 0.17f, w * 0.88f, CREAM, bold = true, center = true)
        if (sub != null) fitText(c, sub, w / 2f, h * 0.93f, h * 0.1f, w * 0.88f, MUTED, center = true)
        return bmp
    }

    /** Kleine scanknop (1 bij 1): het logo-raster met "Scan" eronder. */
    fun scanSmall(w: Int, h: Int, label: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val s = min(w, h) * 0.58f
        miniGrid(c, (w - s) / 2f, h * 0.1f, s)
        fitText(c, label, w / 2f, h * 0.9f, h * 0.2f, w * 0.9f, GOLD_LIGHT, bold = true, center = true)
        return bmp
    }
}
