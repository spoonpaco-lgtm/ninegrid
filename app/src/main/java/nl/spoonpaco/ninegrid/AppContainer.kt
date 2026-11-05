package nl.spoonpaco.ninegrid

import android.content.Context
import nl.spoonpaco.ninegrid.data.Images
import nl.spoonpaco.ninegrid.data.Repository
import nl.spoonpaco.ninegrid.data.SettingsStore
import nl.spoonpaco.ninegrid.data.db.AppDatabase
import nl.spoonpaco.ninegrid.data.scryfall.ScryfallClient
import kotlinx.coroutines.launch

/** Eenvoudige afhankelijkheden voor de hele app. */
class AppContainer(val context: Context) {
    val db: AppDatabase = AppDatabase.create(context)
    val offline = nl.spoonpaco.ninegrid.data.offline.OfflineCards(context)
    val scryfall = ScryfallClient().also { it.offline = offline }
    val settings = SettingsStore(context)
    val images = Images(context)
    val cache = nl.spoonpaco.ninegrid.data.CardCache(context, scryfall)
    val combos = nl.spoonpaco.ninegrid.data.ComboService(context)
    val repository = Repository(db, scryfall) { removed -> images.remove(removed) }.also { it.cache = cache }

    init {
        // De offline kaartdatabase volgt de instelling, ook voor taken op de achtergrond.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            settings.flow.collect { offline.enabled = it.offlineDb != nl.spoonpaco.ninegrid.data.OfflineDb.OFF }
        }
    }
}
