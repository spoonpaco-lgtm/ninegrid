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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
