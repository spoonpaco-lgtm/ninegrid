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

/** Kijkt op GitHub Releases of er een nieuwere versie is. */
object UpdateChecker {
    const val REPO = "spoonpaco-lgtm/ninegrid"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NineGrid/${BuildConfig.VERSION_NAME}")
                .build()
            OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val r = json.decodeFromString(GhRelease.serializer(), resp.body?.string() ?: return@use null)
                val latest = r.tag.removePrefix("v")
                if (isNewer(latest, BuildConfig.VERSION_NAME)) UpdateInfo(latest, r.url) else null
            }
        }.getOrNull()
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
