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
    drawWithContent {
        val w = size.width; val h = size.height
        when (p.style) {
            PrivacyStyle.SEAL -> {
                // Gouden was, met het 9-vakje van het logo erin gedrukt.
                val r = h * 0.62f
                val c = Offset(w / 2, h / 2)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF1D27A), Color(0xFFC9962E), Color(0xFF8E6418)), c, r), r, c)
                drawCircle(Color(0xFF6E4C12), r, c, style = Stroke(r * 0.12f))
                val s = r * 0.95f; val tl = Offset(c.x - s / 2, c.y - s / 2)
                drawRoundRect(Color(0xFF6E4C12), tl, Size(s, s), CornerRadius(s * 0.12f), style = Stroke(r * 0.08f))
                for (i in 1..2) {
                    drawLine(Color(0xFF6E4C12), Offset(tl.x + s * i / 3, tl.y), Offset(tl.x + s * i / 3, tl.y + s), r * 0.05f)
                    drawLine(Color(0xFF6E4C12), Offset(tl.x, tl.y + s * i / 3), Offset(tl.x + s, tl.y + s * i / 3), r * 0.05f)
                }
                drawCircle(Color(0xFF6E4C12), s * 0.11f, c)
            }
            PrivacyStyle.SHADOW -> {
                // Rook: donkerpaarse wolkjes die langzaam schuiven.
                for (i in 0 until 5) {
                    val x = w * (0.1f + i * 0.2f) + sin((wobble + i * 0.3f) * 6.28f) * w * 0.04f
                    val y = h / 2 + cos((wobble + i * 0.2f) * 6.28f) * h * 0.12f
                    val r = h * (0.55f + (i % 2) * 0.15f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xEE2A1640), Color(0x992F1B4A), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
                }
            }
            else -> {
                // Compleated: een olieachtige vlek, vast per plek, licht bewegend.
                val seed = (w.toInt() * 31 + h.toInt()) % 7
                val n = 6
                for (i in 0 until n) {
                    val x = w * (i + 0.5f) / n
                    val r = h * (0.42f + ((seed + i) % 3) * 0.08f) * (1f + 0.06f * sin((wobble + i * 0.17f) * 6.28f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF1B1220), Color(0xFF3B2A44), Color(0xFF14101A)), Offset(x, h / 2), r), r, Offset(x, h / 2))
                }
                // Glans van de olie.
                drawLine(Color(0x66C9A8FF), Offset(w * 0.2f, h * 0.35f), Offset(w * (0.55f + wobble * 0.1f), h * 0.3f), h * 0.07f)
                // De lijntjes van Phyrexisch metaal (zelf getekend, geen lettertype).
                drawLine(Color(0x88B8C2CC), Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.9f), h * 0.05f)
                drawCircle(Color(0x88B8C2CC), h * 0.18f, Offset(w * 0.5f, h * 0.5f), style = Stroke(h * 0.05f))
            }
        }
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
