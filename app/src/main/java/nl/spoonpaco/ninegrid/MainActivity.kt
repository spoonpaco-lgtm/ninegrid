package nl.spoonpaco.ninegrid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import nl.spoonpaco.ninegrid.data.AppSettings
import nl.spoonpaco.ninegrid.ui.AppNav
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.NineGridTheme

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    // Op het camerascherm zijn de volumeknoppen de ontspanknop.
    private fun isVolume(code: Int) = code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || code == android.view.KeyEvent.KEYCODE_VOLUME_UP

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Getikt op de melding van de import: naar het scherm Importeren.
        if (intent.getBooleanExtra(nl.spoonpaco.ninegrid.data.ImportWorker.EXTRA_OPEN_IMPORT, false)) vm.openImport = true
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val shutter = nl.spoonpaco.ninegrid.ui.screens.ScanKeys.onShutter
        if (shutter != null && isVolume(keyCode)) {
            if (event.repeatCount == 0) shutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (nl.spoonpaco.ninegrid.ui.screens.ScanKeys.onShutter != null && isVolume(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(nl.spoonpaco.ninegrid.data.ImportWorker.EXTRA_OPEN_IMPORT, false) == true) vm.openImport = true
        enableEdgeToEdge()
        setContent {
            // Wacht tot de instellingen geladen zijn, zodat de uitleg niet even opflitst.
            val loaded: AppSettings? by vm.settingsStore.flow.collectAsState(initial = null)
            val s = loaded
            NineGridTheme(themeMode = s?.theme ?: "system") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (s != null) {
                        val onboarding = remember { !s.onboardingDone }
                        AppNav(vm, showOnboarding = onboarding)
                    }
                }
            }
        }
    }
}
