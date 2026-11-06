package nl.spoonpaco.ninegrid.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Welke manier van praten: de meeste diensten volgen OpenAI, Gemini en Claude hebben een eigen manier. */
enum class AiKind { OPENAI, GEMINI, CLAUDE }

/** Een dienst met zijn adres en een standaardmodel dat foto's kan lezen (het model is aan te passen). */
data class AiProvider(val id: String, val label: String, val kind: AiKind, val baseUrl: String, val model: String, val free: Boolean)

object AiProviders {
    val all = listOf(
        AiProvider("gemini", "Google Gemini", AiKind.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "gemini-flash-latest", true),
        AiProvider("claude", "Claude (Anthropic)", AiKind.CLAUDE, "https://api.anthropic.com/v1", "claude-haiku-5-5", false),
        AiProvider("openai", "OpenAI", AiKind.OPENAI, "https://api.openai.com/v1", "gpt-4o-mini", false),
        AiProvider("deepseek", "DeepSeek", AiKind.OPENAI, "https://api.deepseek.com/v1", "deepseek-v4-flash-vision-exp", false),
        AiProvider("mistral", "Mistral", AiKind.OPENAI, "https://api.mistral.ai/v1", "mistral-small-latest", true),
        AiProvider("openrouter", "OpenRouter", AiKind.OPENAI, "https://openrouter.ai/api/v1", "openrouter/free", true),
        AiProvider("groq", "Groq", AiKind.OPENAI, "https://api.groq.com/openai/v1", "meta-llama/llama-4-scout-17b-16e-instruct", true),
        AiProvider("custom", "Eigen adres / Custom", AiKind.OPENAI, "", "", false),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

/**
 * De sleutel voor de AI-dienst, versleuteld met een sleutel uit de beveiligde opslag van Android (Keystore).
 * Staat alleen op deze telefoon: niet in de back-up, en na terugzetten op een andere telefoon niet leesbaar.
 */
class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("ai_vault", Context.MODE_PRIVATE)
    private val alias = "ninegrid_ai_key"

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }

    fun save(provider: String, key: String) {
        if (key.isBlank()) { prefs.edit().remove(provider).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val enc = c.doFinal(key.trim().toByteArray())
        prefs.edit().putString(provider, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    fun load(provider: String): String? = runCatching {
        val (iv, data) = (prefs.getString(provider, null) ?: return null).split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(data))
    }.getOrNull()

    fun has(provider: String) = prefs.contains(provider)
}

/** Wat de AI op een kaart las. */
data class AiCardRead(val name: String?, val set: String?, val number: String?, val lang: String?)

/** Soort fout, zodat de app een begrijpelijke melding kan geven. */
enum class AiError { OVERLOADED, RATE_LIMIT, BAD_KEY, MODEL, NETWORK, ANSWER, OTHER }

class AiException(message: String, val kind: AiError = AiError.OTHER) : Exception(message)

/** Praat met de gekozen dienst. Eén vraag tegelijk, met tekst en eventueel één foto. */
class AiClient(private val provider: AiProvider, private val key: String, model: String, baseUrl: String) {
    private val model = model.ifBlank { provider.model }
    private val base = baseUrl.ifBlank { provider.baseUrl }.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    /**
     * Vraag stellen. Is de dienst overbelast of te druk (503, 429 en verwante codes), dan stil opnieuw proberen
     * na de wachttijden in [retries]. Het geheel telt als één verzoek voor de daglimiet.
     */
    suspend fun ask(prompt: String, image: Bitmap? = null, maxTokens: Int = 400, retries: List<Long> = RETRIES): String {
        var attempt = 0
        while (true) {
            try {
                return askOnce(prompt, image, maxTokens)
            } catch (e: AiException) {
                if (!retryable(e.kind) || attempt >= retries.size) throw e
                kotlinx.coroutines.delay(retries[attempt++])
            }
        }
    }

    private suspend fun askOnce(prompt: String, image: Bitmap?, maxTokens: Int): String = withContext(Dispatchers.IO) {
        val b64 = image?.let { jpegBase64(it) }
        val req = when (provider.kind) {
            AiKind.OPENAI -> Request.Builder().url("$base/chat/completions").header("Authorization", "Bearer $key")
                .post(buildJsonObject {
                    put("model", model); put("max_tokens", maxTokens); put("temperature", 0)
                    putJsonArray("messages") {
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                add(buildJsonObject { put("type", "text"); put("text", prompt) })
                                if (b64 != null) add(buildJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$b64") } })
                            }
                        })
                    }
                }.toString().toRequestBody(jsonType)).build()
            AiKind.GEMINI -> Request.Builder().url("$base/models/$model:generateContent").header("x-goog-api-key", key)
                .post(buildJsonObject {
                    putJsonArray("contents") {
                        add(buildJsonObject {
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", prompt) })
                                if (b64 != null) add(buildJsonObject { putJsonObject("inline_data") { put("mime_type", "image/jpeg"); put("data", b64) } })
                            }
                        })
                    }
                    putJsonObject("generationConfig") { put("temperature", 0); put("maxOutputTokens", maxTokens) }
                }.toString().toRequestBody(jsonType)).build()
            AiKind.CLAUDE -> Request.Builder().url("$base/messages").header("x-api-key", key).header("anthropic-version", "2023-06-01")
                .post(buildJsonObject {
                    put("model", model); put("max_tokens", maxTokens)
                    putJsonArray("messages") {
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                if (b64 != null) add(buildJsonObject {
                                    put("type", "image")
                                    putJsonObject("source") { put("type", "base64"); put("media_type", "image/jpeg"); put("data", b64) }
                                })
                                add(buildJsonObject { put("type", "text"); put("text", prompt) })
                            }
                        })
                    }
                }.toString().toRequestBody(jsonType)).build()
        }
        val resp = try { http.newCall(req).execute() } catch (e: java.io.IOException) {
            throw AiException(e.message ?: e.javaClass.simpleName, AiError.NETWORK)
        }
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw AiException("HTTP ${r.code}: " + errorMessage(body), classify(r.code))
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw AiException(body.take(80), AiError.ANSWER) }
            val text = when (provider.kind) {
                AiKind.OPENAI -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.let { c ->
                    (c as? JsonPrimitive)?.contentOrNull ?: (c as? JsonArray)?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                }
                AiKind.GEMINI -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                AiKind.CLAUDE -> root["content"]?.jsonArray?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            }
            text?.takeIf { it.isNotBlank() } ?: throw AiException("Leeg antwoord / empty answer", AiError.ANSWER)
        }
    }

    /** Leest één kaart van een foto. Antwoord als JSON; onzekere velden leeg. */
    suspend fun readCard(image: Bitmap, retries: List<Long> = RETRIES): AiCardRead {
        val answer = ask(CARD_PROMPT, image, maxTokens = 200, retries = retries)
        val obj = extractJson(answer)?.jsonObject ?: throw AiException("Geen JSON / no JSON: ${answer.take(80)}", AiError.ANSWER)
        fun f(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }
        return AiCardRead(f("name"), f("set")?.uppercase(), f("number")?.trimStart('0')?.ifEmpty { "0" }, f("lang")?.uppercase())
    }

    companion object {
        /** Wachttijden bij overbelasting: na 2, 5 en 10 seconden opnieuw. */
        val RETRIES = listOf(2_000L, 5_000L, 10_000L)
        /** Bij de knop Test maar één keer opnieuw, zodat je snel antwoord hebt. */
        val TEST_RETRIES = listOf(2_000L)

        fun classify(code: Int) = when (code) {
            500, 502, 503, 504, 529 -> AiError.OVERLOADED
            429 -> AiError.RATE_LIMIT
            401, 403 -> AiError.BAD_KEY
            404 -> AiError.MODEL
            else -> AiError.OTHER
        }

        fun retryable(kind: AiError) = kind == AiError.OVERLOADED || kind == AiError.RATE_LIMIT

        private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

        const val CARD_PROMPT = "This image shows one Magic: The Gathering card (it may be cropped, rotated slightly or partly blurry). " +
            "Read it and answer ONLY with JSON, no other text: {\"name\": ..., \"set\": ..., \"number\": ..., \"lang\": ...}. " +
            "name = the card name exactly as printed (for non-English cards the printed name). " +
            "set = the set code printed at the bottom left (3 to 5 letters/digits, e.g. MH2), number = the collector number there " +
            "(without the total, e.g. 138 from 138/303), lang = the two-letter language code printed next to the set code (EN, FR, DE, JP ...). " +
            "Use null for anything you cannot read with confidence. Do not guess."

        fun extractJson(text: String) = runCatching {
            val s = text.indexOf('{'); val e = text.lastIndexOf('}')
            Json.parseToJsonElement(text.substring(s, e + 1))
        }.getOrNull()

        private fun errorMessage(body: String) = runCatching {
            val o = Json.parseToJsonElement(body).jsonObject
            (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: (o["error"] as? JsonPrimitive)?.contentOrNull ?: body.take(160)
        }.getOrElse { body.take(160) }

        /** Verkleinen tot hoogstens 768 px en als JPEG: genoeg om te lezen, en weinig data. */
        fun jpegBase64(b: Bitmap): String {
            val scale = 768f / maxOf(b.width, b.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        /** Een getekende voorbeeldkaart voor de knop Test, zodat er niets van jou wordt verstuurd. */
        fun testCard(): Bitmap {
            val w = 488; val h = 680
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.color = android.graphics.Color.rgb(30, 30, 30); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.color = android.graphics.Color.rgb(200, 70, 50); c.drawRoundRect(18f, 18f, w - 18f, h - 18f, 18f, 18f, p)
            p.color = android.graphics.Color.rgb(240, 230, 210); c.drawRoundRect(32f, 32f, w - 32f, 86f, 10f, 10f, p)
            c.drawRoundRect(32f, 400f, w - 32f, 600f, 10f, 10f, p)
            p.color = android.graphics.Color.BLACK; p.textSize = 34f; p.typeface = android.graphics.Typeface.SERIF
            c.drawText("Lightning Bolt", 46f, 72f, p)
            p.textSize = 24f
            c.drawText("Instant", 46f, 380f, p)
            c.drawText("Lightning Bolt deals 3 damage", 46f, 450f, p)
            c.drawText("to any target.", 46f, 482f, p)
            p.color = android.graphics.Color.WHITE; p.textSize = 20f; p.typeface = android.graphics.Typeface.MONOSPACE
            c.drawText("146/249 C", 40f, 640f, p)
            c.drawText("M11 • EN", 40f, 664f, p)
            return bmp
        }
    }
}
