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
import androidx.compose.ui.graphics.drawscope.clipPath
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

/** Toxic: organische bijna zwarte groene ooze met grillige rand, groene oliefilm, gaten die zwellen, bellen die knappen en druppels met rand die uit de veeg groeien. */
internal fun DrawScope.drawToxic(t: Float, wobble: Float) {
    val w = size.width; val h = size.height
    val seed = seedOf(w, h)
    val n = max(10, (w / (h * 0.2f)).toInt())
    val pts = ArrayList<Offset>()
    pts.add(Offset(-h * 0.22f, h * (0.38f + 0.24f * rnd(seed, 500))))
    for (i in 0..n) {
        val u = i.toFloat() / n
        val lump = sin(u * 9.4f + rnd(seed, 501) * 6f) * 0.07f + (rnd(seed, i) - 0.5f) * 0.36f
        pts.add(Offset(w * u, h * (0.14f + lump) + sin((wobble + i * 0.19f) * 6.2832f) * h * 0.025f))
    }
    pts.add(Offset(w + h * 0.22f, h * (0.38f + 0.24f * rnd(seed, 502))))
    for (i in n downTo 0) {
        val u = i.toFloat() / n
        val lump = sin(u * 8.1f + rnd(seed, 503) * 6f) * 0.07f + (rnd(seed, i + 50) - 0.5f) * 0.36f
        pts.add(Offset(w * u, h * (0.86f - lump) + sin((wobble + i * 0.27f) * 6.2832f) * h * 0.025f))
    }
    val body = smoothClosed(pts)
    // Gaten: de achtergrond schijnt erdoorheen (even-odd); ze zwellen en slinken. Dazu een paar kleine bellen.
    body.fillType = PathFillType.EvenOdd
    val holes = ArrayList<Pair<Offset, Float>>()
    for (k in 0 until 4) {
        val big = k < 2
        val c = Offset(w * (0.12f + 0.76f * rnd(seed, 60 + k)), h * (0.36f + 0.28f * rnd(seed, 66 + k)))
        val base = if (big) h * (0.09f + 0.07f * rnd(seed, 72 + k)) else h * (0.035f + 0.02f * rnd(seed, 72 + k))
        val r = base * (1f + 0.25f * sin((t * 2f + k * 0.33f) * 6.2832f))
        holes.add(c to r)
        body.addOval(Rect(c, r))
    }
    val dark = Color(0xFF04120A)
    val green = Color(0xFF1FD655)
    // Druppels die uit de veeg groeien (smalle hals, ronde bol, groene rand); ze vallen af en groeien opnieuw.
    for (k in 0 until 2) {
        val x = w * (0.18f + 0.42f * k + 0.2f * rnd(seed, 90 + k))
        val p = (t + k * 0.5f) % 1f
        val y0 = h * 0.8f
        if (p < 0.72f) {
            val len = h * 0.55f * smooth(p / 0.62f)
            drawLine(green, Offset(x, y0), Offset(x, y0 + len), h * 0.1f, StrokeCap.Round)
            drawCircle(green, h * 0.115f, Offset(x, y0 + len))
            drawLine(dark, Offset(x, y0), Offset(x, y0 + len), h * 0.06f, StrokeCap.Round)
            drawCircle(dark, h * 0.09f, Offset(x, y0 + len))
        } else {
            val f = (p - 0.72f) / 0.28f
            val c = Offset(x, y0 + h * 0.55f + f * h * 0.4f)
            drawCircle(green.copy(alpha = 1f - f), h * 0.115f, c)
            drawCircle(dark.copy(alpha = 1f - f), h * 0.09f, c)
        }
    }
    // Spatjes rond de veeg.
    for (i in 0 until 10) {
        val x = w * rnd(seed, 120 + i)
        val y = if (i % 2 == 0) h * (-0.16f + 0.18f * rnd(seed, 140 + i)) else h * (0.98f + 0.16f * rnd(seed, 140 + i))
        drawCircle(dark, h * (0.015f + 0.045f * rnd(seed, 160 + i)), Offset(x, y))
    }
    // Gloed, vulling en rand.
    drawPath(body, Color(0x382BE06A), style = Stroke(h * 0.18f))
    drawPath(body, Brush.verticalGradient(0f to Color(0xFF0B2616), 0.5f to Color(0xFF04120A), 1f to Color(0xFF020A05)))
    // Groene oliefilm die over de veeg schuift.
    clipPath(body) {
        val shift = wobble * w * 0.5f
        val film = Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0x8838E87A), Color(0xBB7DF0A8), Color(0x6638E87A), Color.Transparent),
            startX = -w * 0.3f + shift, endX = w * 0.55f + shift,
        )
        val sheen = Path().apply {
            moveTo(w * 0.04f, h * 0.4f)
            quadraticBezierTo(w * 0.45f, h * (0.28f + 0.07f * sin(wobble * 6.2832f)), w * 0.92f, h * 0.4f)
        }
        drawPath(sheen, film, style = Stroke(h * 0.09f, cap = StrokeCap.Round))
        val sheen2 = Path().apply {
            moveTo(w * 0.2f, h * 0.68f)
            quadraticBezierTo(w * 0.5f, h * 0.76f, w * 0.78f, h * 0.66f)
        }
        drawPath(sheen2, film, alpha = 0.55f, style = Stroke(h * 0.05f, cap = StrokeCap.Round))
    }
    drawPath(body, green, style = Stroke(h * 0.045f))
    // Bellen die knappen: een ring die groeit en vervaagt, met kleine druppels.
    for (k in 0 until 3) {
        val p = (t * 2f + k / 3f) % 1f
        val c = Offset(w * (0.12f + 0.76f * rnd(seed, 200 + k)), h * (0.34f + 0.32f * rnd(seed, 210 + k)))
        drawCircle(Color(0xFF7DF0A8).copy(alpha = 0.9f * (1f - p)), h * 0.18f * p, c, style = Stroke(h * 0.022f))
        if (p > 0.35f) for (j in 0 until 4) {
            val ang = j * 1.5708f + k
            val d = h * 0.26f * p
            drawCircle(Color(0xFF7DF0A8).copy(alpha = 0.8f * (1f - p)), h * 0.02f, Offset(c.x + kotlin.math.cos(ang) * d, c.y + sin(ang) * d))
        }
    }
    holes.forEach { (c, r) -> drawCircle(Color(0xFF47E884), r, c, style = Stroke(h * 0.022f)) }
}

/** Lightning: echte bliksem. Een paar keer per halve minuut, op onregelmatige momenten (soms twee vlak na elkaar), elke keer met een andere vorm en een knipperende nagloed. */
internal fun DrawScope.drawLightning(t: Float) {
    val w = size.width; val h = size.height
    val base = seedOf(w, h)
    // Wanneer (0..1 over de 24 seconden) een slag begint; elke slag duurt ongeveer een seconde.
    val starts = floatArrayOf(0.03f, 0.20f, 0.232f, 0.47f, 0.69f, 0.715f, 0.905f)
    val dur = 0.042f
    var striking = false
    for ((idx, s0) in starts.withIndex()) {
        if (t < s0 || t >= s0 + dur) continue
        striking = true
        val u = (t - s0) / dur
        val seed = base + idx * 977
        val segs = max(8, (w / (h * 0.3f)).toInt())
        val pts = (0..segs).map { i ->
            val j = if (i == 0 || i == segs) 0.12f * (rnd(seed, 400 + i) - 0.5f) else (rnd(seed, i) - 0.5f) * 0.9f
            Offset(w * i / segs, h * (0.5f + j))
        }
        val travel = clamp01(u / 0.22f)
        // Knipperen: vaste aan/uit-momenten die per slag iets verschillen, zoals echte bliksem.
        val flick = when {
            u < 0.22f -> 0.85f
            u < 0.36f -> 1f
            u < 0.42f -> 0.1f
            u < 0.5f -> 0.95f
            u < 0.56f + 0.06f * rnd(seed, 900) -> 0.08f
            u < 0.74f -> 0.7f * (1f - (u - 0.56f) / 0.2f).coerceAtLeast(0.2f)
            else -> 0.1f * (1f - (u - 0.74f) / 0.26f)
        }
        val head = travel * segs
        val bolt = Path()
        bolt.moveTo(pts[0].x, pts[0].y)
        for (sg in 0 until segs) {
            val f = clamp01(head - sg)
            if (f <= 0f) break
            bolt.lineTo(pts[sg].x + (pts[sg + 1].x - pts[sg].x) * f, pts[sg].y + (pts[sg + 1].y - pts[sg].y) * f)
        }
        for (k in 0 until 3) {
            val i = 2 + (rnd(seed, 300 + k) * (segs - 4)).toInt()
            if (head < i + 1) continue
            val from = pts[i]
            val dir = if (rnd(seed, 310 + k) > 0.5f) 1f else -1f
            val mid = Offset(from.x + h * 0.3f, from.y + dir * h * 0.25f)
            val to = Offset(from.x + h * 0.55f, from.y + dir * h * (0.4f + 0.2f * rnd(seed, 320 + k)))
            val fork = Path().apply { moveTo(from.x, from.y); lineTo(mid.x, mid.y); lineTo(to.x, to.y) }
            drawPath(fork, Color(0xFF5AA0FF).copy(alpha = flick * 0.8f), style = Stroke(h * 0.035f, cap = StrokeCap.Round))
        }
        drawPath(bolt, Color(0xFF2F6BFF).copy(alpha = flick * 0.35f), style = Stroke(h * 0.22f, cap = StrokeCap.Round))
        drawPath(bolt, Color(0xFF5AA0FF).copy(alpha = flick * 0.85f), style = Stroke(h * 0.08f, cap = StrokeCap.Round))
        drawPath(bolt, Color(0xFFEAF4FF).copy(alpha = flick), style = Stroke(h * 0.035f, cap = StrokeCap.Round))
        if (u in 0.2f..0.5f) {
            val p = (u - 0.2f) / 0.3f
            for (i in 0 until 5) {
                val ang = (rnd(seed, 330 + i) - 0.5f) * 3.0f
                val r = h * 0.5f * p
                drawCircle(Color(0xFFBFD8FF).copy(alpha = 1f - p), h * 0.03f, Offset(w + kotlin.math.cos(ang) * r, h * 0.5f + sin(ang) * r))
            }
        }
    }
    // Tussen de slagen: een heel zwakke blauwe schaduw van het spoor, zodat het bedrag bedekt blijft en je ziet welke stijl het is.
    if (!striking) {
        val segs = max(8, (w / (h * 0.3f)).toInt())
        val ghost = Path()
        for (i in 0..segs) {
            val j = if (i == 0 || i == segs) 0f else (rnd(base, i) - 0.5f) * 0.9f
            val x = w * i / segs; val y = h * (0.5f + j)
            if (i == 0) ghost.moveTo(x, y) else ghost.lineTo(x, y)
        }
        drawPath(ghost, Color(0xFF5AA0FF).copy(alpha = 0.16f), style = Stroke(h * 0.03f, cap = StrokeCap.Round))
    }
}

/** Fog: zoals Schaduw, maar mist: lichtgrijze wolken die langzaam schuiven en elkaar overlappen. */
internal fun DrawScope.drawFog(wobble: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 6) {
        val x = w * (0.06f + i * 0.17f) + sin((wobble + i * 0.3f) * 6.2832f) * w * 0.05f
        val y = h / 2 + kotlin.math.cos((wobble + i * 0.2f) * 6.2832f) * h * 0.14f
        val r = h * (0.6f + (i % 3) * 0.14f)
        drawCircle(Brush.radialGradient(listOf(Color(0xEEC8D2DC), Color(0x99A9B6C4), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
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
