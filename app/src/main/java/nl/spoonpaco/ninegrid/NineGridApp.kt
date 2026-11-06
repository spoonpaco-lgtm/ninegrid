package nl.spoonpaco.ninegrid

import android.app.Application
import kotlinx.coroutines.launch

class NineGridApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Elke dag de prijzen bijwerken op de achtergrond (alleen via wifi).
        nl.spoonpaco.ninegrid.data.PriceWorker.schedule(this)
        // Elke dag kijken of er een nieuwe versie is, en die (standaard via wifi) alvast downloaden.
        val app = this
        kotlinx.coroutines.MainScope().launch {
            nl.spoonpaco.ninegrid.data.UpdateWorker.schedule(app, container.settings.current().updateAuto)
            nl.spoonpaco.ninegrid.data.Updater.cleanup(app)
        }
    }
}
