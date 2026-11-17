package nl.spoonpaco.ninegrid.domain

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Een deck als QR-code. De inhoud is de gewone decklijst ("aantal Naam (SET) nummer"), ingepakt en als tekst gezet:
 * "NG1:" + base64 van de ingepakte lijst. Past de lijst niet in één code, dan komen er meer: "NG1:2/3:" + deel.
 * Een commanderdeck past ruim in één code.
 */
object DeckQr {
    /** Tekens per code. Een QR-code van versie 40 met laag foutherstel houdt ruim 2900 bytes; hier blijven we bewust ruim onder. */
    const val MAX_CHARS = 1800
    private const val PREFIX = "NG1:"

    private fun pack(text: String): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(text.toByteArray(Charsets.UTF_8)); d.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun unpack(bytes: ByteArray): String? = runCatching {
        val i = Inflater(true); i.setInput(bytes)
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break
            out.write(buf, 0, n)
        }
        i.end()
        out.toString("UTF-8")
    }.getOrNull()

    /** De tekst(en) voor de QR-code(s). Eén element als alles past. */
    fun encode(deckText: String): List<String> {
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(pack(deckText))
        if (PREFIX.length + body.length <= MAX_CHARS) return listOf(PREFIX + body)
        val per = MAX_CHARS - 12
        val chunks = body.chunked(per)
        return chunks.mapIndexed { i, c -> "${PREFIX}${i + 1}/${chunks.size}:$c" }
    }

    fun isDeckCode(s: String) = s.startsWith(PREFIX)

    /** Verzamelt de gescande codes tot het deck compleet is. */
    class Assembler {
        private var total = 0
        private val parts = sortedMapOf<Int, String>()

        val have get() = parts.size
        val of get() = total
        val complete get() = total > 0 && parts.size == total

        /** Geeft false als de tekst geen NineGrid-code is, of bij een ander deck hoort dan de al gescande delen. */
        fun add(code: String): Boolean {
            if (!isDeckCode(code)) return false
            val rest = code.removePrefix(PREFIX)
            val m = Regex("""^(\d+)/(\d+):(.*)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(rest)
            val (idx, tot, body) = if (m != null) Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3]) else Triple(1, 1, rest)
            if (idx < 1 || idx > tot) return false
            if (total != 0 && total != tot) return false
            total = tot
            parts[idx] = body
            return true
        }

        /** De decklijst, zodra alle delen er zijn. */
        fun text(): String? {
            if (!complete) return null
            val bytes = runCatching { Base64.getUrlDecoder().decode(parts.values.joinToString("")) }.getOrNull() ?: return null
            return unpack(bytes)
        }
    }

    fun decode(codes: List<String>): String? = Assembler().also { a -> codes.forEach { a.add(it) } }.text()
}
