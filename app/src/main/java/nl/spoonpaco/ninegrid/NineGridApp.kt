package nl.spoonpaco.ninegrid

import android.app.Application

class NineGridApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Elke dag de prijzen bijwerken op de achtergrond (alleen via wifi).
        nl.spoonpaco.ninegrid.data.PriceWorker.schedule(this)
    }
}
