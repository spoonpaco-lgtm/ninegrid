package nl.spoonpaco.ninegrid

import android.app.Application

class NineGridApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
