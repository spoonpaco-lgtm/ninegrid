package nl.spoonpaco.ninegrid.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nl.spoonpaco.ninegrid.domain.PictureGuess
import nl.spoonpaco.ninegrid.domain.PictureIndex
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Het beeldmodel (MobileNet, assets/embedder.tflite, door de build erin gezet). Rekent op de telefoon, zonder internet.
 * Leest zelf de invoermaat en het soort getallen uit het model.
 */
class Embedder private constructor(private val interp: Interpreter) {
    private val inT = interp.getInputTensor(0)
    private val outT = interp.getOutputTensor(0)
    private val h = inT.shape()[1]
    private val w = inT.shape()[2]
    val dim = outT.numElements()

    @Synchronized
    fun embed(bmp: Bitmap): FloatArray {
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        val float = inT.dataType() == DataType.FLOAT32
        val input = ByteBuffer.allocateDirect(w * h * 3 * (if (float) 4 else 1)).order(ByteOrder.nativeOrder())
        for (c in px) {
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            if (float) { input.putFloat(r / 127.5f - 1f); input.putFloat(g / 127.5f - 1f); input.putFloat(b / 127.5f - 1f) }
            else { input.put(r.toByte()); input.put(g.toByte()); input.put(b.toByte()) }
        }
        input.rewind()
        val out = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())
        interp.run(input, out)
        out.rewind()
        val q = outT.quantizationParams()
        return FloatArray(dim) {
            when (outT.dataType()) {
                DataType.FLOAT32 -> out.float
                DataType.UINT8 -> ((out.get().toInt() and 255) - q.zeroPoint) * q.scale
                else -> (out.get().toInt() - q.zeroPoint) * q.scale
            }
        }
    }

    companion object {
        const val ASSET = "embedder.tflite"

        fun load(context: Context): Embedder? = runCatching {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
            Embedder(Interpreter(buf, Interpreter.Options().setNumThreads(2)))
        }.getOrNull()
    }
}

/**
 * Vingerafdrukken van de plaatjes per set (punt 1h-scan). Eén keer gemaakt uit de kleine Scryfall-plaatjes en dan
 * bewaard in filesDir/fingerprints/SET.bin (enkele honderden KB per set); de plaatjes zelf blijven niet nodig.
 * Daarna werkt herkennen op het plaatje zonder internet. Zonder vingerafdrukken leest de scanner alleen tekst.
 */
class Fingerprints(private val context: Context, private val images: Images, private val cache: CardCache) {
    private val dir get() = File(context.filesDir, "fingerprints").apply { mkdirs() }
    private val loaded = ConcurrentHashMap<String, PictureIndex>()
    private val lock = Mutex()
    private val embedder by lazy { Embedder.load(context) }

    /** Het model zit in de app (als de build het kon ophalen). */
    val available get() = embedder != null

    private fun file(code: String) = File(dir, code.uppercase() + ".bin")

    /** Er staan bruikbare vingerafdrukken (van deze versie) op de telefoon. */
    fun has(code: String) = index(code) != null

    /** Sets met vingerafdrukken op de telefoon. */
    fun sets(): List<String> = dir.listFiles().orEmpty().filter { it.name.endsWith(".bin") }.map { it.name.removeSuffix(".bin") }.sorted()

    fun sizeBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    fun clear() { loaded.clear(); dir.listFiles().orEmpty().forEach { it.delete() } }

    private fun read(code: String): PictureIndex? = runCatching {
        DataInputStream(file(code).inputStream().buffered()).use { s ->
            require(s.readInt() == MAGIC && s.readInt() == VERSION)
            val dim = s.readInt(); val n = s.readInt()
            val ids = ArrayList<String>(n); val names = ArrayList<String>(n); val numbers = ArrayList<String>(n)
            val vectors = ByteArray(n * dim)
            for (i in 0 until n) {
                ids += s.readUTF(); names += s.readUTF(); numbers += s.readUTF()
                s.readFully(vectors, i * dim, dim)
            }
            PictureIndex(code.uppercase(), ids, names, numbers, dim, vectors)
        }
    }.getOrNull()

    private fun write(ix: PictureIndex) {
        val tmp = File(dir, ix.code + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { s ->
            s.writeInt(MAGIC); s.writeInt(VERSION); s.writeInt(ix.dim); s.writeInt(ix.size)
            for (i in 0 until ix.size) {
                s.writeUTF(ix.ids[i]); s.writeUTF(ix.names[i]); s.writeUTF(ix.numbers[i])
                s.write(ix.vectors, i * ix.dim, ix.dim)
            }
        }
        tmp.renameTo(file(ix.code))
    }

    /** De vingerafdrukken van een set, alleen van de telefoon (geen internet). */
    fun index(code: String): PictureIndex? {
        val key = code.uppercase()
        loaded[key]?.let { return it }
        if (!file(key).exists()) return null
        return read(key)?.also { loaded[key] = it }
    }

    private suspend fun load(url: String): Bitmap? {
        val r = images.loader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        return (r as? SuccessResult)?.drawable?.toBitmap()
    }

    /**
     * Maakt (of vult aan) de vingerafdrukken van een set: alleen kaarten die er nog niet in staan, dus na de eerste keer
     * kost het bijna niets. Geeft het aantal kaarten in de set terug, of null als het niet lukte (geen model, geen setlijst).
     */
    suspend fun ensure(code: String, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int? = lock.withLock {
        val e = embedder ?: return@withLock null
        val key = code.uppercase()
        val list = runCatching { cache.scanList(key) }.getOrNull() ?: return@withLock index(key)?.size
        val old = index(key)?.takeIf { it.dim == e.dim }
        val have = old?.ids?.withIndex()?.associate { it.value to it.index }.orEmpty()
        val cards = list.cards.filter { it.image != null }
        val todo = cards.filter { it.id !in have }
        if (todo.isEmpty() && old != null) return@withLock old.size
        var done = 0
        val gate = Semaphore(4)
        val made: List<Pair<SetCard, ByteArray>?> = coroutineScope {
            todo.map { c ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val bmp = runCatching { load(c.image!!) }.getOrNull()
                        val v = bmp?.let { b -> withContext(Dispatchers.Default) { runCatching { PictureIndex.quantize(e.embed(art(b))) }.getOrNull() } }
                        synchronized(this@Fingerprints) { done++; progress(done, todo.size) }
                        v?.let { c to it }
                    }
                }
            }.awaitAll()
        }
        val rows = ArrayList<Triple<SetCard, ByteArray?, Int?>>()
        val newVec = made.filterNotNull().associate { it.first.id to it.second }
        for (c in cards) {
            val oldRow = have[c.id]
            when {
                oldRow != null -> rows += Triple(c, null, oldRow)
                newVec[c.id] != null -> rows += Triple(c, newVec[c.id], null)
            }
        }
        if (rows.isEmpty()) return@withLock null
        val dim = e.dim
        val vectors = ByteArray(rows.size * dim)
        rows.forEachIndexed { i, (_, v, oldRow) ->
            if (v != null) System.arraycopy(v, 0, vectors, i * dim, dim)
            else System.arraycopy(old!!.vectors, oldRow!! * dim, vectors, i * dim, dim)
        }
        val ix = PictureIndex(key, rows.map { it.first.id }, rows.map { it.first.name }, rows.map { it.first.number }, dim, vectors)
        withContext(Dispatchers.IO) { runCatching { write(ix) } }
        loaded[key] = ix
        ix.size
    }

    /** Vult de sets aan die al op de telefoon staan (nieuwe kaarten in een set die nog uitkomt). Voor de nachtelijke update. */
    suspend fun refreshAll() { for (s in sets()) runCatching { ensure(s) } }

    /**
     * Wat het plaatje zegt over [photo] (de uitsnede van één vak), binnen [code]. Alleen wat al op de telefoon staat:
     * geen internet, geen wachten. Null zonder model of zonder vingerafdrukken voor die set.
     */
    suspend fun match(code: String, photo: Bitmap): PictureGuess? = withContext(Dispatchers.Default) {
        val e = embedder ?: return@withContext null
        val ix = index(code)?.takeIf { it.dim == e.dim } ?: return@withContext null
        // Het vak is iets ruimer dan de kaart: neem het midden, zoals ImageMatch.
        val l = (photo.width * INSET).toInt(); val t = (photo.height * INSET).toInt()
        val part = runCatching { Bitmap.createBitmap(photo, l, t, (photo.width - 2 * l).coerceAtLeast(1), (photo.height - 2 * t).coerceAtLeast(1)) }.getOrDefault(photo)
        runCatching { ix.best(PictureIndex.quantize(e.embed(art(part)))) }.getOrNull()
    }

    /**
     * Alleen de illustratie: binnen een set hebben alle kaarten hetzelfde kader en tekstvak, dus de hele kaart
     * vergelijken laat alles op elkaar lijken (testset 22 nov: 33% goed). Voor de Scryfall-plaatjes en de foto gelijk.
     */
    private fun art(b: Bitmap): Bitmap = runCatching {
        val l = (b.width * ART_LEFT).toInt(); val t = (b.height * ART_TOP).toInt()
        Bitmap.createBitmap(b, l, t, (b.width * (ART_RIGHT - ART_LEFT)).toInt().coerceAtLeast(1), (b.height * (ART_BOTTOM - ART_TOP)).toInt().coerceAtLeast(1))
    }.getOrDefault(b)

    private companion object {
        const val MAGIC = 0x4E474650 // "NGFP"
        /** 2 = alleen de illustratie (0.9.15, 22 nov); oudere bestanden worden opnieuw gemaakt. */
        const val VERSION = 2
        const val INSET = 0.04f
        // Waar de illustratie ongeveer zit, als deel van de kaart (modern en oud kader).
        const val ART_LEFT = 0.08f
        const val ART_RIGHT = 0.92f
        const val ART_TOP = 0.11f
        const val ART_BOTTOM = 0.55f
    }
}
