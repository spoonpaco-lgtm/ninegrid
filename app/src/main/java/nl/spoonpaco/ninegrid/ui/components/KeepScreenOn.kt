package nl.spoonpaco.ninegrid.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Houdt het scherm aan zolang dit scherm open is, tot [idleMs] na het laatste gebruik ([lastUse]).
 * Daarna mag de telefoon het scherm weer zelf uitzetten, zoals normaal.
 */
@Composable
fun KeepScreenOn(lastUse: Long, idleMs: Long) {
    val window = LocalContext.current.findActivity()?.window
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(lastUse) { on = true; kotlinx.coroutines.delay(idleMs); on = false }
    DisposableEffect(window, on) {
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
