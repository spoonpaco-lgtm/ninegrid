package nl.spoonpaco.ninegrid.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Magic-sfeer aan of uit (instelling Weergave → Effecten). */
val LocalEffects = staticCompositionLocalOf { true }

/** Hoe ver de telefoon gekanteld is, van −1 (links) tot 1 (rechts). Alleen gelezen tijdens het tekenen. */
val LocalTilt = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Leest de bewegingssensor zolang de app op het scherm staat (alleen als de effecten aan staan). */
@Composable
fun EffectsProvider(enabled: Boolean, content: @Composable () -> Unit) {
    val tilt = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // x ≈ −9,8…9,8 bij kantelen naar links/rechts; zacht volgen zodat het niet trilt.
                val target = (-e.values[0] / 6f).coerceIn(-1f, 1f)
                tilt.floatValue += (target - tilt.floatValue) * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    CompositionLocalProvider(LocalEffects provides enabled, LocalTilt provides tilt, content = content)
}

/** Regenboogglans over een foil die meebeweegt als je de telefoon kantelt. */
fun Modifier.foilShine(on: Boolean, strength: Float = 1f): Modifier = if (!on) this else composed {
    val effects = LocalEffects.current
    val tilt = LocalTilt.current
    if (!effects) this else drawWithContent {
        drawContent()
        val w = size.width; val shift = tilt.value * w * 0.8f
        // [strength] 1 = subtiel (lijsten), hoger = duidelijker (groot plaatje op het kaartscherm).
        val a = (0.33f * strength).coerceAtMost(0.75f)
        val colors = listOf(
            Color.Transparent, Color(0xFFFF7AD9).copy(alpha = a), Color(0xFF79D7FF).copy(alpha = a), Color(0xFFA8FF8C).copy(alpha = a),
            Color(0xFFFFE27A).copy(alpha = a), Color.Transparent,
        )
        drawRect(
            Brush.linearGradient(colors, start = Offset(-w * 0.6f + shift, 0f), end = Offset(w * 0.9f + shift, size.height)),
            blendMode = BlendMode.Screen,
        )
    }
}

/**
 * Verbannen: een wit-blauwe werveling die opkomt en wegtrekt. [onDone] na ±0,8 s.
 * Zonder effecten meteen klaar.
 */
@Composable
fun ExileSwirl(onDone: () -> Unit) {
    val effects = LocalEffects.current
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (effects) t.animateTo(1f, tween(800, easing = LinearEasing))
        onDone()
    }
    if (!effects) return
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val c = Offset(size.width / 2, size.height / 2)
        val maxR = size.minDimension * 0.45f
        val fade = if (p < 0.6f) 1f else (1f - (p - 0.6f) / 0.4f)
        drawCircle(Brush.radialGradient(listOf(Color(0xCCFFFFFF), Color(0x887FB8FF), Color.Transparent), c, maxR * (0.4f + p)), maxR * (0.4f + p), c, alpha = fade * 0.8f)
        for (i in 0 until 5) {
            val r = maxR * (0.25f + i * 0.17f) * (1.1f - p * 0.6f)
            rotate(p * 540f + i * 72f, c) {
                drawArc(
                    if (i % 2 == 0) Color.White else Color(0xFF8CC8FF), 0f, 120f, false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = (6 - i).dp.toPx()), alpha = fade,
                )
            }
        }
    }
}

/** Prijsbeweging als loyalty-schildje: ▲ +6 (groen, omhoog) of ▼ −3 (rood, omlaag). */
@Composable
fun LoyaltyShield(text: String, up: Boolean) {
    val color = if (up) Color(0xFF3FB37F) else Color(0xFFE5484D)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
        Canvas(Modifier.size(width = (24 + text.length * 7).dp, height = 26.dp)) {
            val w = size.width; val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                if (up) { moveTo(w * 0.5f, 0f); lineTo(w, h * 0.3f); lineTo(w, h); lineTo(0f, h); lineTo(0f, h * 0.3f) }
                else { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.7f); lineTo(w * 0.5f, h); lineTo(0f, h * 0.7f) }
                close()
            }
            drawPath(path, Color(0xFF241812))
            drawPath(path, color, style = Stroke(1.5.dp.toPx()))
        }
        Text((if (up) "▲ " else "▼ ") + text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = if (up) 4.dp else 0.dp, bottom = if (up) 0.dp else 4.dp))
    }
}

/** Een stempel: schuin, met dubbele rand, bijv. "Legaal" (groen), "Banned" (rood) of "Compleet". */
@Composable
fun Stamp(text: String, color: Color, icon: String? = null, angle: Float = -8f) {
    if (!LocalEffects.current) {
        Text((icon?.let { "$it " } ?: "") + text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        return
    }
    Box(
        Modifier.rotate(angle).border(2.dp, color, RoundedCornerShape(6.dp)).padding(2.dp)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text((icon?.let { "$it " } ?: "") + text.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
    }
}

/** Zon of maan (de dag/nacht-markering), zelf getekend. */
@Composable
fun DayNightIcon(night: Boolean, color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        if (!night) {
            drawCircle(color, r * 0.42f, c)
            for (i in 0 until 8) rotate(i * 45f, c) {
                drawLine(color, Offset(c.x, c.y - r * 0.62f), Offset(c.x, c.y - r * 0.95f), strokeWidth = r * 0.14f)
            }
        } else {
            val path = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c, r * 0.85f))
            }
            val cut = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.42f, c.y - r * 0.3f), r * 0.75f))
            }
            drawPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, path, cut), color)
        }
    }
}

/** Kleine gekleurde rondjes om te kiezen, bijv. de accentkleur. */
@Composable
fun ColorDot(color: Color, selected: Boolean, second: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (second == null) Brush.linearGradient(listOf(color, color)) else Brush.linearGradient(0.5f to color, 0.5f to second))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x55FFFFFF), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
    )
}
