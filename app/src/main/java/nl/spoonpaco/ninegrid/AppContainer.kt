package nl.spoonpaco.ninegrid

import android.content.Context
import nl.spoonpaco.ninegrid.data.Images
import nl.spoonpaco.ninegrid.data.Repository
import nl.spoonpaco.ninegrid.data.SettingsStore
import nl.spoonpaco.ninegrid.data.db.AppDatabase
import nl.spoonpaco.ninegrid.data.scryfall.ScryfallClient

/** Eenvoudige afhankelijkheden voor de hele app. */
class AppContainer(val context: Context) {
    val db: AppDatabase = AppDatabase.create(context)
    val scryfall = ScryfallClient()
    val settings = SettingsStore(context)
    val images = Images(context)
    val cache = nl.spoonpaco.ninegrid.data.CardCache(context, scryfall)
    val combos = nl.spoonpaco.ninegrid.data.ComboService(context)
    val repository = Repository(db, scryfall) { removed -> images.remove(removed) }.also { it.cache = cache }
}
