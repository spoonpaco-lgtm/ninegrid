package nl.spoonpaco.ninegrid.data

import android.content.Context
import android.net.ConnectivityManager
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import nl.spoonpaco.ninegrid.data.db.CardPrint

/**
 * Afbeeldingen van kaarten. Alleen kaarten die je hebt worden bewaard; gaat een kaart uit je
 * collectie, dan verdwijnt zijn afbeelding van de telefoon.
 */
class Images(private val context: Context) {
    val loader: ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("card_images"))
                .maxSizeBytes(400L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    fun onMeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm?.isActiveNetworkMetered ?: false
    }

    /** Mag de app nu afbeeldingen downloaden? */
    fun networkAllowed(settings: AppSettings): Boolean = !(settings.wifiOnly && onMeteredNetwork())

    fun request(url: String?, settings: AppSettings): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .networkCachePolicy(if (networkAllowed(settings)) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .diskCachePolicy(if (settings.imageMode == ImageMode.OFF) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .build()

    /** Bij 'Volledig': grote afbeeldingen van alle kaarten alvast ophalen. */
    fun prefetch(prints: List<CardPrint>, settings: AppSettings) {
        if (settings.imageMode == ImageMode.OFF || !networkAllowed(settings)) return
        prints.forEach { p ->
            val urls = if (settings.imageMode == ImageMode.FULL) listOf(p.imageSmall, p.imageNormal) else listOf(p.imageSmall)
            urls.filterNotNull().forEach { u -> loader.enqueue(ImageRequest.Builder(context).data(u).build()) }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun remove(prints: List<CardPrint>) {
        prints.forEach { p ->
            listOfNotNull(p.imageSmall, p.imageNormal).forEach { u ->
                loader.diskCache?.remove(u)
                loader.memoryCache?.remove(MemoryCache.Key(u))
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearAll() {
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
    }

    @OptIn(ExperimentalCoilApi::class)
    fun diskUsageBytes(): Long = loader.diskCache?.size ?: 0
}
