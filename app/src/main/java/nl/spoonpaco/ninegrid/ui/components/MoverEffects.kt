package nl.spoonpaco.ninegrid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val Mythic = Color(0xFFEF9F27)
private val Gold = Color(0xFFE8B84A)
private val Win = Color(0xFF3FB37F)
private val Lose = Color(0xFFE5484D)

/** Vaste "willekeur" (zelfde vorm elke keer), 0..1. */
private fun moverRnd(seed: Int, i: Int): Float {
    var x = seed * 374761393 + i * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/**
 * Het effect om de grote kaart van Top movers (Winner/Loser of the Week of of the Day). Speelt één keer
 * (ongeveer 1,5 seconde) bij openen en bij elke wissel ([playKey]), daarna staat het stil.
 * - [style] "glow": winnaar een oranje mythic-gloed, verliezer bloed dat van de onderkant van de kaart afdruipt
 *   (zelfde opbouw als de Completed-olie: rafelige rand, glans, druppels, met schaduw zodat het niet plat oogt).
 * - [style] "stamp": een stempel WINNER (goud) of DESTROYED (rood) die neerkomt.
 * Zonder Magic-effecten alleen een rustig groen of rood randje.
 */
@Composable
fun MoverFrame(rising: Boolean, style: String, playKey: Any, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    if (!LocalEffects.current) {
        Box(Modifier.border(1.dp, if (rising) Win else Lose, shape).padding(8.dp)) { content() }
        return
    }
    val p = remember { Animatable(0f) }
    LaunchedEffect(playKey, style, rising) { p.snapTo(0f); p.animateTo(1f, tween(1500, easing = LinearEasing)) }
    when {
        style == "stamp" -> Box(Modifier.border(1.dp, if (rising) Gold else Lose, shape).padding(8.dp)) {
            content()
            // De stempel valt van groot naar klein met een kleine terugvering, vanaf 0,2 tot 0,6 van de animatie.
            val t = ((p.value - 0.2f) / 0.4f).coerceIn(0f, 1f)
            val scale = if (t < 0.7f) 2.4f - (2.4f - 0.92f) * (t / 0.7f) else 0.92f + 0.08f * ((t - 0.7f) / 0.3f)
            if (t > 0f) Box(Modifier.align(Alignment.CenterEnd).graphicsLayer { scaleX = scale; scaleY = scale; alpha = (t * 1.6f).coerceAtMost(1f) }) {
                Stamp(if (rising) "Winner" else "Destroyed", if (rising) Gold else Lose, angle = -10f)
            }
        }
        rising -> Box(
            Modifier.drawBehind { drawGlow(p.value) }.border(1.5.dp, Mythic, shape).padding(8.dp),
        ) { content() }
        else -> Box(Modifier.drawWithContent {
            drawContent()
            drawBlood(p.value, extra = 30.dp.toPx())
        }.padding(bottom = 30.dp).border(1.dp, Color(0xFF7A1016), shape).padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 14.dp)) { content() }
    }
}

/** Mythic-gloed: een paar lagen oranje om de rand, die opzwellen en dan rustig blijven staan. */
private fun DrawScope.drawGlow(p: Float) {
    // Opzwellen tot het midden, dan terug naar een rustige gloed.
    val k = if (p < 0.5f) p / 0.5f else 1f - 0.4f * ((p - 0.5f) / 0.5f)
    val r = 10.dp.toPx()
    for (i in 1..6) {
        val grow = i * 2.2.dp.toPx() * k
        drawRoundRect(
            Mythic.copy(alpha = (0.32f - i * 0.045f).coerceAtLeast(0f) * k),
            topLeft = Offset(-grow, -grow), size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(r + grow), style = Stroke(2.4.dp.toPx()),
        )
    }
}

/**
 * Bloed langs de onderkant van de kaart dat naar beneden afdruipt, in de stijl van de Completed-olie:
 * een rafelige band met diepe kleur en een glanslijn, druppels met een bolletje onderaan, en een zachte
 * schaduw eronder. [extra] is de ruimte onder de kaart waar de druppels in hangen.
 */
private fun DrawScope.drawBlood(p: Float, extra: Float) {
    val w = size.width
    val bottom = size.height - extra
    val seed = kotlin.math.round(w).toInt() * 13 + 7
    val band = 10.dp.toPx() * (0.35f + 0.65f * p)
    val n = maxOf(10, (w / 18.dp.toPx()).toInt())
    val path = Path().apply {
        moveTo(0f, bottom)
        for (i in 0..n) lineTo(w * i / n, bottom - band * (0.45f + 0.55f * moverRnd(seed, i)))
        lineTo(w, bottom + 2.dp.toPx())
        for (i in n downTo 0) lineTo(w * i / n, bottom + 2.dp.toPx() + 3.dp.toPx() * moverRnd(seed, i + 40) * p)
        close()
    }
    // Druppels: verschillende lengtes, de langste pas aan het eind; elk met een dikke punt onderaan.
    val drips = (0 until 5).map { k ->
        val x = w * (0.08f + 0.2f * k + 0.1f * moverRnd(seed, 70 + k))
        val len = extra * (0.35f + 0.6f * moverRnd(seed, 80 + k)) * ((p - 0.15f * k / 4f).coerceIn(0f, 1f))
        Triple(x, len, 2.2.dp.toPx() + 1.6.dp.toPx() * moverRnd(seed, 90 + k))
    }
    val shadow = Color(0x66000000)
    val dy = 2.5.dp.toPx()
    // Schaduw eerst, een stukje lager.
    translate(top = dy) {
        drawPath(path, shadow)
        drips.forEach { (x, len, r) -> if (len > 0f) { drawLine(shadow, Offset(x, bottom), Offset(x, bottom + len), r * 1.6f, StrokeCap.Round); drawCircle(shadow, r * 1.25f, Offset(x, bottom + len)) } }
    }
    val dark = Color(0xFF3A0407); val mid = Color(0xFF8E1018); val deep = Color(0xFF240203)
    val gloss = Brush.verticalGradient(0f to dark, 0.45f to mid, 1f to deep, startY = bottom - band, endY = bottom + 4.dp.toPx())
    drips.forEach { (x, len, r) ->
        if (len > 0f) {
            drawLine(Brush.horizontalGradient(listOf(deep, mid, deep), startX = x - r, endX = x + r), Offset(x, bottom - 1f), Offset(x, bottom + len), r * 1.5f, StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB0242C), mid, deep), Offset(x - r * 0.35f, bottom + len - r * 0.35f), r * 1.6f), r * 1.2f, Offset(x, bottom + len))
            // Glimlicht op de druppel.
            drawCircle(Color(0x88FFD6D6), r * 0.32f, Offset(x - r * 0.4f, bottom + len - r * 0.45f))
        }
    }
    drawPath(path, gloss)
    // Glanslijn over de band, zoals de oliefilm bij Completed (hier rood-wit).
    val sheen = Path().apply {
        moveTo(w * 0.06f, bottom - band * 0.35f)
        quadraticBezierTo(w * 0.45f, bottom - band * 0.7f, w * 0.9f, bottom - band * 0.4f)
    }
    drawPath(sheen, Brush.horizontalGradient(listOf(Color.Transparent, Color(0x99FF9A9A), Color(0x66FFD0D0), Color.Transparent)), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
}
