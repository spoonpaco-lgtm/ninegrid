package nl.spoonpaco.ninegrid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val url: String,
    val name: String? = null,
)

data class UpdateInfo(val version: String, val url: String)

/** Uitkomst van een controle: nieuwe versie, al de nieuwste, of controleren lukte niet (met reden). */
sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/** Kijkt op GitHub Releases of er een nieuwere versie is. */
object UpdateChecker {
    const val REPO = "spoonpaco-lgtm/ninegrid"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NineGrid/${BuildConfig.VERSION_NAME}")
                .build()
            OkHttpClient().newCall(req).execute().use { resp ->
                when {
                    // Een privé-repository geeft zonder inloggen ook "niet gevonden".
                    resp.code == 404 -> UpdateResult.Failed("GitHub 404")
                    resp.code == 403 || resp.code == 429 -> UpdateResult.Failed("GitHub ${resp.code}")
                    !resp.isSuccessful -> UpdateResult.Failed("GitHub ${resp.code}")
                    else -> {
                        val r = json.decodeFromString(GhRelease.serializer(), resp.body?.string().orEmpty())
                        val latest = r.tag.removePrefix("v")
                        if (isNewer(latest, BuildConfig.VERSION_NAME)) UpdateResult.Available(UpdateInfo(latest, r.url)) else UpdateResult.UpToDate
                    }
                }
            }
        } catch (e: java.io.IOException) {
            UpdateResult.Failed("offline")
        } catch (e: Exception) {
            UpdateResult.Failed(e.javaClass.simpleName)
        }
    }

    fun isNewer(a: String, b: String): Boolean {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
