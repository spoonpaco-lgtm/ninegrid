package nl.spoonpaco.ninegrid.data

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vergelijkt jouw foto van een kaart met de plaatjes van mogelijke prints, om bij twijfel de juiste te kiezen
 * (bijvoorbeeld gewone print tegenover extended art). Eenvoudig: beide verkleinen tot 24x33 pixels in kleur
 * en kijken hoe goed ze op elkaar lijken.
 */
class ImageMatch(private val images: Images) {
    private fun signature(bmp: Bitmap, inset: Float): FloatArray {
        val l = (bmp.width * inset).toInt(); val t = (bmp.height * inset).toInt()
        val w = (bmp.width - 2 * l).coerceAtLeast(1); val h = (bmp.height - 2 * t).coerceAtLeast(1)
        val part = Bitmap.createBitmap(bmp, l, t, w, h)
        val small = Bitmap.createScaledBitmap(part, 24, 33, true)
        val px = IntArray(24 * 33); small.getPixels(px, 0, 24, 0, 0, 24, 33)
        val v = FloatArray(px.size * 3)
        px.forEachIndexed { i, c ->
            v[i * 3] = ((c shr 16) and 255).toFloat(); v[i * 3 + 1] = ((c shr 8) and 255).toFloat(); v[i * 3 + 2] = (c and 255).toFloat()
        }
        val mean = v.average().toFloat()
        var norm = 0f
        for (i in v.indices) { v[i] -= mean; norm += v[i] * v[i] }
        val n = kotlin.math.sqrt(norm).coerceAtLeast(1e-3f)
        for (i in v.indices) v[i] /= n
        return v
    }

    private fun similarity(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    private suspend fun load(url: String): Bitmap? {
        val ctx = images.context
        val r = images.loader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build())
        return (r as? SuccessResult)?.drawable?.toBitmap()
    }

    /** Geeft de index van de beste kandidaat (urls van de plaatjes), of null als het niet duidelijk is. */
    suspend fun best(photo: Bitmap, candidateUrls: List<String?>): Int? = withContext(Dispatchers.Default) {
        if (candidateUrls.size < 2) return@withContext null
        // Het vak is ruimer dan de kaart: neem het midden van de foto.
        val mine = signature(photo, 0.06f)
        val scores = candidateUrls.map { u -> u?.let { runCatching { load(it) }.getOrNull() }?.let { similarity(mine, signature(it, 0f)) } }
        val ranked = scores.withIndex().filter { it.value != null }.sortedByDescending { it.value }
        if (ranked.size < 2) return@withContext ranked.firstOrNull()?.index
        // Alleen als de beste duidelijk beter is dan de tweede.
        if (ranked[0].value!! - ranked[1].value!! < 0.04f) null else ranked[0].index
    }
}
