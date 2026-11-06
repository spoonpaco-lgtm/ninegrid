package nl.spoonpaco.ninegrid.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import kotlin.math.cos
import kotlin.math.sin

/** Privacymodus: [hidden] = bedragen nu verborgen (aan, en niet even aan het kijken). */
data class PrivacyState(val hidden: Boolean = false, val style: String = PrivacyStyle.COMPLEATED)

object PrivacyStyle {
    const val COMPLEATED = "compleated"
    const val SEAL = "zegel"
    const val SHADOW = "schaduw"
    const val DOTS = "stippen"
    val all = listOf(COMPLEATED, SEAL, SHADOW, DOTS)
}

val LocalPrivacy = staticCompositionLocalOf { PrivacyState() }

/** Zet de privacystand klaar voor de hele app; na even kijken vanzelf weer dicht. */
@Composable
fun PrivacyProvider(vm: AppViewModel, content: @Composable () -> Unit) {
    val settings by vm.settings.collectAsState()
    val peeking = vm.peekUntil > System.currentTimeMillis()
    LaunchedEffect(vm.peekUntil) {
        val wait = vm.peekUntil - System.currentTimeMillis()
        if (wait > 0) { kotlinx.coroutines.delay(wait); vm.endPeek() }
    }
    CompositionLocalProvider(LocalPrivacy provides PrivacyState(settings.privacy && !peeking, settings.privacyStyle), content = content)
}

/** Een bedrag voor op het scherm: verborgen als € •••• zolang de privacymodus aan staat. */
@Composable
fun money(v: Double, currency: String): String =
    if (LocalPrivacy.current.hidden) (if (currency == "USD") "$ ••••" else "€ ••••") else Money.format(v, currency)

/**
 * Over een bedrag: in de gekozen stijl een vlek (compleated), een zegel of rook in plaats van de tekst.
 * Bij stippen blijft de tekst (€ ••••) gewoon staan.
 */
fun Modifier.privacyMask(): Modifier = composed {
    val p = LocalPrivacy.current
    if (!p.hidden || p.style == PrivacyStyle.DOTS) return@composed this
    val effects = LocalEffects.current
    val wobble = if (effects && p.style != PrivacyStyle.SEAL) {
        rememberInfiniteTransition(label = "mask").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "w").value
    } else 0f
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    drawWithContent {
        when (p.style) {
            PrivacyStyle.SEAL -> drawSeal(measurer)
            PrivacyStyle.SHADOW -> drawShadow(wobble)
            else -> drawOil(wobble)
        }
    }
}

/** Vaste "willekeur" (0..1), zodat een vlek op dezelfde plek er altijd hetzelfde uitziet. */
private fun rnd(seed: Int, i: Int): Float {
    var x = seed * 374761393 + i * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** Compleated: één lange veeg zwarte Phyrexische olie met rafelige randen, druppels en een paarsblauwe oliefilm. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOil(wobble: Float) {
    val w = size.width; val h = size.height
    val seed = kotlin.math.round(w).toInt() * 31 + kotlin.math.round(h).toInt() * 17
    val n = maxOf(8, (w / (h * 0.22f)).toInt())
    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(-h * 0.15f, h * 0.5f)
    for (i in 0..n) path.lineTo(w * i / n, h * (0.02f + 0.22f * rnd(seed, i)))
    path.lineTo(w + h * 0.15f, h * 0.5f)
    for (i in n downTo 0) path.lineTo(w * i / n, h * (0.98f - 0.2f * rnd(seed, i + 50)))
    path.close()
    // Druppels die onder de veeg hangen.
    val drips = 1 + (seed and 1)
    for (k in 0 until drips) {
        val x = w * (0.2f + 0.3f * k + 0.25f * rnd(seed, 90 + k))
        val end = h * (1.12f + 0.18f * rnd(seed, 95 + k))
        drawLine(Color(0xFF0C0A10), Offset(x, h * 0.8f), Offset(x, end), h * 0.07f)
        drawCircle(Color(0xFF0C0A10), h * 0.065f, Offset(x, end))
    }
    drawPath(path, Brush.verticalGradient(0f to Color(0xFF0D0B12), 0.4f to Color(0xFF24202D), 1f to Color(0xFF050407)))
    // Oliefilm: een dunne paarsblauwe glans die langzaam meebeweegt.
    val shift = wobble * w * 0.25f
    val film = Brush.horizontalGradient(
        listOf(Color.Transparent, Color(0x997C8CFF), Color(0xAAC48BFF), Color(0x667C8CFF), Color.Transparent),
        startX = w * 0.05f + shift - w * 0.1f, endX = w * 0.9f + shift,
    )
    val sheen = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.08f, h * 0.44f)
        quadraticBezierTo(w * 0.45f, h * (0.30f + 0.08f * sin(wobble * 6.28f)), w * 0.86f, h * 0.38f)
    }
    drawPath(sheen, film, style = Stroke(h * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    val sheen2 = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.25f, h * 0.62f)
        quadraticBezierTo(w * 0.5f, h * 0.68f, w * 0.7f, h * 0.6f)
    }
    drawPath(sheen2, film, alpha = 0.45f, style = Stroke(h * 0.035f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** Zegel: gouden was met golvende rand, een vierkant met een 9 erin, en een koord door de hele breedte. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeal(measurer: androidx.compose.ui.text.TextMeasurer) {
    val w = size.width; val h = size.height
    val c = Offset(w / 2, h / 2)
    // Koord.
    drawLine(Color(0xFF7A5518), Offset(0f, c.y), Offset(w, c.y), h * 0.12f)
    drawLine(Color(0xFFB08030), Offset(0f, c.y - h * 0.03f), Offset(w, c.y - h * 0.03f), h * 0.035f)
    // Was: 18 punten met een vaste golving, vloeiend verbonden.
    val r = h * 0.72f
    val pts = (0 until 18).map { i ->
        val a = i * 2 * Math.PI / 18
        val rr = r * (0.9f + 0.14f * rnd(5, i))
        Offset(c.x + (rr * kotlin.math.cos(a)).toFloat(), c.y + (rr * kotlin.math.sin(a)).toFloat())
    }
    val wax = androidx.compose.ui.graphics.Path()
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    wax.moveTo(mid(pts.last(), pts[0]).x, mid(pts.last(), pts[0]).y)
    pts.forEachIndexed { i, pt -> val m = mid(pt, pts[(i + 1) % pts.size]); wax.quadraticBezierTo(pt.x, pt.y, m.x, m.y) }
    wax.close()
    drawPath(wax, Brush.radialGradient(listOf(Color(0xFFFBE08A), Color(0xFFE3A82B), Color(0xFFA9720F)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.4f))
    drawPath(wax, Color(0xFF8A5C10), style = Stroke(r * 0.05f))
    // Het vierkant met de 9.
    val s = r * 1.0f; val tl = Offset(c.x - s / 2, c.y - s / 2)
    drawRoundRect(Color(0xFFD99B22), tl, Size(s, s), CornerRadius(s * 0.15f))
    drawRoundRect(Color(0xFF7A4F0C), tl, Size(s, s), CornerRadius(s * 0.15f), style = Stroke(r * 0.08f))
    val nine = measurer.measure(
        "9",
        androidx.compose.ui.text.TextStyle(
            color = Color(0xFF6A430A), fontSize = (s * 0.78f).toSp(), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, lineHeight = (s * 0.78f).toSp(),
        ),
    )
    drawText(nine, topLeft = Offset(c.x - nine.size.width / 2f, c.y - nine.size.height / 2f))
}

/** Schaduw: donkerpaarse wolkjes die langzaam schuiven. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShadow(wobble: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 5) {
        val x = w * (0.1f + i * 0.2f) + sin((wobble + i * 0.3f) * 6.28f) * w * 0.04f
        val y = h / 2 + cos((wobble + i * 0.2f) * 6.28f) * h * 0.12f
        val r = h * (0.55f + (i % 2) * 0.15f)
        drawCircle(Brush.radialGradient(listOf(Color(0xEE2A1640), Color(0x992F1B4A), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
    }
}

/** Zoekt de activity achter een context (voor het vingerafdrukvenster). */
fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
    return null
}

object PrivacyGate {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Kan de telefoon ons vragen om vingerafdruk of schermvergrendeling? Zonder vergrendeling is er niets te beveiligen. */
    fun available(context: Context) = BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** Vraagt vingerafdruk of de schermvergrendeling; [onOk] alleen bij succes. Zonder vergrendeling meteen door. */
    fun ask(context: Context, title: String, subtitle: String, onOk: () -> Unit) {
        val activity = context.findFragmentActivity()
        if (activity == null || !available(context)) { onOk(); return }
        val prompt = BiometricPrompt(activity, androidx.core.content.ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(AUTH).build(),
        )
    }
}

/** Het oog rechtsboven: tik = aan/uit (uit alleen na vingerafdruk), ingedrukt houden = even kijken. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrivacyButton(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val p = LocalPrivacy.current
    val title = stringResource(R.string.privacy_gate_title)
    val offSub = stringResource(R.string.privacy_gate_off)
    val peekSub = stringResource(R.string.privacy_gate_peek)
    val tip = stringResource(if (settings.privacy) R.string.privacy_on else R.string.privacy_off)
    Box(
        Modifier.size(48.dp).combinedClickable(
            onClick = {
                if (!settings.privacy) vm.setPrivacy(true)
                else PrivacyGate.ask(context, title, offSub) { vm.setPrivacy(false) }
            },
            onLongClick = { if (settings.privacy) PrivacyGate.ask(context, title, peekSub) { vm.peek() } },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (p.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, tip,
            tint = if (settings.privacy && !p.hidden) nl.spoonpaco.ninegrid.ui.theme.NgColors.Amber else androidx.compose.material3.LocalContentColor.current,
        )
    }
}

/** Hetzelfde als Modifier.privacyMask(), te gebruiken zonder import. */
fun privacyMask(): Modifier = Modifier.privacyMask()
