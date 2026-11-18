package nl.spoonpaco.ninegrid.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/*
 * Vijf extra privacystijlen (Toxic, Lightning, Fog, Pixelized, Countered). Elke tekent over het bedrag in plaats van de tekst;
 * t loopt steeds van 0 naar 1 (6 seconden) en begint dan opnieuw, wobble heen en weer. Alles houdt de breedte van het bedrag.
 */

private fun seedOf(w: Float, h: Float) = round(w).toInt() * 31 + round(h).toInt() * 17

private fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

private fun smooth(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }

/** Een gesloten vloeiende vorm door de punten (kwadratische bochten via de middens). */
private fun smoothClosed(pts: List<Offset>): Path {
    val p = Path()
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val m0 = mid(pts.last(), pts[0])
    p.moveTo(m0.x, m0.y)
    pts.forEachIndexed { i, pt -> val m = mid(pt, pts[(i + 1) % pts.size]); p.quadraticBezierTo(pt.x, pt.y, m.x, m.y) }
    p.close()
    return p
}

/** Toxic: organische bijna zwarte groene ooze met heldere rand, gaten die zwellen, bellen die knappen en druppels die vallen. */
internal fun DrawScope.drawToxic(t: Float, wobble: Float) {
    val w = size.width; val h = size.height
    val seed = seedOf(w, h)
    val n = max(8, (w / (h * 0.32f)).toInt())
    val pts = ArrayList<Offset>()
    pts.add(Offset(-h * 0.12f, h * 0.5f))
    for (i in 0..n) pts.add(Offset(w * i / n, h * (0.1f + 0.2f * rnd(seed, i)) + sin((wobble + i * 0.17f) * 6.2832f) * h * 0.02f))
    pts.add(Offset(w + h * 0.12f, h * 0.5f))
    for (i in n downTo 0) pts.add(Offset(w * i / n, h * (0.9f - 0.2f * rnd(seed, i + 50)) + sin((wobble + i * 0.23f) * 6.2832f) * h * 0.02f))
    val body = smoothClosed(pts)
    // Gaten: de achtergrond schijnt erdoorheen (even-odd), ze zwellen en slinken.
    body.fillType = PathFillType.EvenOdd
    val holes = ArrayList<Pair<Offset, Float>>()
    for (k in 0 until 3) {
        val c = Offset(w * (0.18f + 0.64f * rnd(seed, 60 + k)), h * (0.4f + 0.2f * rnd(seed, 66 + k)))
        val r = h * (0.07f + 0.05f * rnd(seed, 72 + k)) * (1f + 0.25f * sin((t * 2f + k * 0.33f) * 6.2832f))
        holes.add(c to r)
        body.addOval(Rect(c, r))
    }
    // Druppels onder de veeg (donker, met bolletje), vallen af en groeien opnieuw.
    val dark = Color(0xFF04120A)
    for (k in 0 until 2) {
        val x = w * (0.2f + 0.45f * k + 0.2f * rnd(seed, 90 + k))
        val p = (t + k * 0.5f) % 1f
        val y0 = h * 0.85f
        if (p < 0.7f) {
            val len = h * 0.5f * smooth(p / 0.6f)
            drawLine(dark, Offset(x, y0), Offset(x, y0 + len), h * 0.07f, StrokeCap.Round)
            drawCircle(dark, h * 0.075f, Offset(x, y0 + len))
        } else {
            val f = (p - 0.7f) / 0.3f
            drawCircle(dark.copy(alpha = 1f - f), h * 0.075f, Offset(x, y0 + h * 0.5f + f * h * 0.35f))
        }
    }
    // Spatjes.
    for (i in 0 until 9) {
        val x = w * rnd(seed, 120 + i)
        val y = if (i % 2 == 0) h * (-0.12f + 0.16f * rnd(seed, 140 + i)) else h * (0.96f + 0.16f * rnd(seed, 140 + i))
        drawCircle(dark, h * (0.015f + 0.04f * rnd(seed, 160 + i)), Offset(x, y))
    }
    // Gloed, vulling, rand.
    drawPath(body, Color(0x402BE06A), style = Stroke(h * 0.14f))
    drawPath(body, Brush.verticalGradient(0f to Color(0xFF0A2214), 0.45f to Color(0xFF04120A), 1f to Color(0xFF020A05)))
    drawPath(body, Color(0xFF1FD655), style = Stroke(h * 0.035f))
    // Lange glanslijn.
    val sheen = Path().apply {
        moveTo(w * 0.08f, h * 0.36f)
        quadraticBezierTo(w * 0.45f, h * (0.28f + 0.05f * sin(wobble * 6.2832f)), w * 0.86f, h * 0.36f)
    }
    drawPath(sheen, Color(0x44B6FFCF), style = Stroke(h * 0.035f, cap = StrokeCap.Round))
    // Bellen die knappen: een ring die groeit en vervaagt.
    for (k in 0 until 3) {
        val p = (t * 2f + k / 3f) % 1f
        val c = Offset(w * (0.15f + 0.7f * rnd(seed, 200 + k)), h * (0.35f + 0.3f * rnd(seed, 210 + k)))
        drawCircle(Color(0xFF7DF0A8).copy(alpha = 0.9f * (1f - p)), h * 0.16f * p, c, style = Stroke(h * 0.02f))
    }
    holes.forEach { (c, r) -> drawCircle(Color(0xFF47E884), r, c, style = Stroke(h * 0.02f)) }
}

/** Lightning: een blauwe bliksemstroom die van links naar rechts schiet en dan twee keer na-flitst; geen achtergrond. */
internal fun DrawScope.drawLightning(t: Float) {
    val w = size.width; val h = size.height
    val seed = seedOf(w, h)
    val segs = max(8, (w / (h * 0.35f)).toInt())
    val pts = (0..segs).map { i ->
        val dy = if (i == 0 || i == segs) 0f else (rnd(seed, i) - 0.5f) * 0.7f
        Offset(w * i / segs, h * (0.5f + dy))
    }
    val travel = clamp01(t / 0.35f)
    val a = when {
        t < 0.35f -> 0.7f
        t < 0.41f -> 1f
        t < 0.44f -> 0.25f
        t < 0.5f -> 1f
        t < 0.8f -> 1f - 0.85f * ((t - 0.5f) / 0.3f)
        else -> 0.15f
    }
    val head = travel * segs
    val bolt = Path()
    bolt.moveTo(pts[0].x, pts[0].y)
    for (s in 0 until segs) {
        val f = clamp01(head - s)
        if (f <= 0f) break
        val x = pts[s].x + (pts[s + 1].x - pts[s].x) * f
        val y = pts[s].y + (pts[s + 1].y - pts[s].y) * f
        bolt.lineTo(x, y)
    }
    // Takjes, pas zichtbaar als de stroom er voorbij is.
    for (k in 0 until 3) {
        val i = 2 + (rnd(seed, 300 + k) * (segs - 4)).toInt()
        if (head < i + 1) continue
        val from = pts[i]
        val dir = if (rnd(seed, 310 + k) > 0.5f) 1f else -1f
        val to = Offset(from.x + h * 0.5f, from.y + dir * h * 0.4f)
        drawLine(Color(0xFF5AA0FF).copy(alpha = a * 0.8f), from, to, h * 0.035f, StrokeCap.Round)
    }
    drawPath(bolt, Color(0xFF2F6BFF).copy(alpha = a * 0.35f), style = Stroke(h * 0.2f, cap = StrokeCap.Round))
    drawPath(bolt, Color(0xFF5AA0FF).copy(alpha = a * 0.8f), style = Stroke(h * 0.08f, cap = StrokeCap.Round))
    drawPath(bolt, Color(0xFFEAF4FF).copy(alpha = a), style = Stroke(h * 0.035f, cap = StrokeCap.Round))
    // Vonkjes rechts na de flits.
    if (t in 0.35f..0.6f) {
        val p = (t - 0.35f) / 0.25f
        for (i in 0 until 5) {
            val ang = (rnd(seed, 330 + i) - 0.5f) * 3.0f
            val r = h * 0.5f * p
            drawCircle(Color(0xFFBFD8FF).copy(alpha = 1f - p), h * 0.03f, Offset(w + kotlin.math.cos(ang) * r, h * 0.5f + sin(ang) * r))
        }
    }
}

/** Fog: losse zachte mistwolken die langs drijven; geen achtergrond. */
internal fun DrawScope.drawFog(t: Float) {
    val w = size.width; val h = size.height
    val seed = seedOf(w, h)
    for (layer in 0 until 2) {
        val count = if (layer == 0) 5 else 4
        val tint = if (layer == 0) Color(0xFFC3CDD8) else Color(0xFF8B97A6)
        for (i in 0 until count) {
            val speedPhase = (t * (if (layer == 0) 1f else 2f) + i.toFloat() / count) % 1f
            val x = -h * 0.6f + (w + h * 1.2f) * speedPhase
            val y = h * (0.3f + 0.4f * rnd(seed, i + layer * 20)) + sin((t * 2f + i) * 6.2832f) * h * 0.08f
            val r = h * (0.75f + 0.5f * rnd(seed, 10 + i + layer * 20))
            val alpha = sin(speedPhase * PI.toFloat()) * (if (layer == 0) 0.6f else 0.4f)
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = alpha), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        }
    }
}

/** Pixelized: een raster van kleine vierkantjes dat in en uit flikkert en naar de randen toe vervaagt; geen leesbaar bedrag. */
internal fun DrawScope.drawPixelized(t: Float) {
    val w = size.width; val h = size.height
    val seed = seedOf(w, h)
    val cell = max(3f, h / 5f)
    val cols = ceil(w / cell).toInt()
    val rows = max(1, floor(h / cell).toInt())
    val top = (h - rows * cell) / 2f
    for (c in 0 until cols) {
        val cx = (c + 0.5f) * cell / w
        val edge = smooth(min(cx, 1f - cx) / 0.18f)
        if (edge <= 0f) continue
        for (r in 0 until rows) {
            val key = r * 1000 + c
            val ph = rnd(seed, key)
            val speed = 1f + floor(rnd(seed, key + 7) * 3f)
            val v = 0.5f + 0.5f * sin(6.2832f * (speed * t + ph))
            val a = v * v * edge * 0.9f
            if (a < 0.03f) continue
            val tone = rnd(seed, key + 13)
            val color = Color(0xFF6F86A8).copy(alpha = a)
            val light = Color(0xFFB9C8E0).copy(alpha = a)
            drawRect(if (tone > 0.5f) light else color, Offset(c * cell, top + r * cell), Size(cell - 1f, cell - 1f))
        }
    }
}

/** Countered: een blauwe stempel (zoals Legaal bij EDH-decks) met dichte vulling; valt neer met een rimpel. */
internal fun DrawScope.drawCountered(measurer: TextMeasurer, t: Float) {
    val w = size.width; val h = size.height
    val c = Offset(w / 2f, h / 2f)
    val blue = Color(0xFF2A6BFF)
    val k = when {
        t < 0.12f -> { val f = 1f - t / 0.12f; 1f + 0.7f * f * f }
        t < 0.2f -> 1f - 0.04f * sin((t - 0.12f) / 0.08f * PI.toFloat())
        else -> 1f
    }
    val appear = clamp01(t / 0.05f)
    val bw = w * 0.97f; val bh = h * 0.92f
    val tl = Offset(c.x - bw / 2f, c.y - bh / 2f)
    val corner = CornerRadius(h * 0.2f)
    rotate(-8f, c) {
        scale(k, c) {
            drawRoundRect(Color(0xFF0B1F4D).copy(alpha = appear), tl, Size(bw, bh), corner)
            drawRoundRect(blue.copy(alpha = appear), tl, Size(bw, bh), corner, style = Stroke(h * 0.07f))
            val inset = h * 0.11f
            drawRoundRect(blue.copy(alpha = 0.6f * appear), Offset(tl.x + inset, tl.y + inset), Size(bw - 2 * inset, bh - 2 * inset), CornerRadius(h * 0.12f), style = Stroke(h * 0.03f))
            var fs = h * 0.46f
            fun style(f: Float) = TextStyle(
                color = Color(0xFFBBD2FF).copy(alpha = appear), fontSize = f.toSp(), fontWeight = FontWeight.Black,
                letterSpacing = (f * 0.08f).toSp(), lineHeight = f.toSp(),
            )
            var layout = measurer.measure("COUNTERED", style(fs))
            val avail = bw - 2 * inset - h * 0.1f
            if (layout.size.width > avail && layout.size.width > 0) {
                fs *= avail / layout.size.width
                layout = measurer.measure("COUNTERED", style(fs))
            }
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
        }
    }
    // Rimpelring op het moment van inslaan.
    if (t in 0.1f..0.45f) {
        val p = (t - 0.1f) / 0.35f
        val e = h * 0.5f * p
        rotate(-8f, c) {
            drawRoundRect(blue.copy(alpha = 0.6f * (1f - p)), Offset(tl.x - e, tl.y - e), Size(bw + 2 * e, bh + 2 * e), CornerRadius(h * 0.2f + e), style = Stroke(h * 0.03f))
        }
    }
}
