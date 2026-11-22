package nl.spoonpaco.ninegrid.domain

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Wat het plaatje zegt: de best passende kaart uit de set, met de score en de afstand tot de beste kaart met een andere naam. */
data class PictureGuess(
    val id: String,
    val name: String,
    val number: String,
    /** Gelijkenis (ongeveer de cosinus, 0 tot 1). */
    val score: Float,
    /** Verschil met de beste kaart met een andere naam; prints met dezelfde naam (zelfde plaatje) tellen niet. */
    val margin: Float,
    val runnerUp: String?,
) {
    /** Duidelijk genoeg om mee te beslissen. */
    val sure get() = PictureIndex.sure(score, margin)

    /** Voor het scanrapport en de testset. */
    fun describe() = "$name #$number " + String.format(java.util.Locale.ROOT, "%.2f/%.2f", score, margin) + (runnerUp?.let { " (2e: $it)" } ?: "")
}

/**
 * De vingerafdrukken van één set: per kaart een rijtje getallen van het beeldmodel, ingepakt als int8. Zoeken = de kaart met het grootste inproduct.
 */
class PictureIndex(
    val code: String,
    val ids: List<String>,
    val names: List<String>,
    val numbers: List<String>,
    val dim: Int,
    /** ids.size * dim bytes, rij voor rij. */
    val vectors: ByteArray,
) {
    init { require(ids.size == names.size && ids.size == numbers.size && vectors.size == ids.size * dim) }

    val size get() = ids.size

    fun best(query: ByteArray): PictureGuess? {
        if (size == 0 || query.size != dim) return null
        val scores = FloatArray(size) { i -> dot(query, vectors, i * dim, dim) / (127f * 127f) }
        val top = scores.indices.maxByOrNull { scores[it] } ?: return null
        val other = scores.indices.filter { !names[it].equals(names[top], true) }.maxByOrNull { scores[it] }
        val margin = if (other == null) scores[top] else scores[top] - scores[other]
        return PictureGuess(ids[top], names[top], numbers[top], scores[top], margin, other?.let { names[it] })
    }

    companion object {
        /** Geschat; de testset geeft per vak score/marge, zodat deze grenzen bijgesteld kunnen worden. */
        const val MIN_SCORE = 0.55f
        const val MIN_MARGIN = 0.05f

        fun sure(score: Float, margin: Float) = score >= MIN_SCORE && margin >= MIN_MARGIN

        /** Lengte 1 en dan naar -127..127. */
        fun quantize(v: FloatArray): ByteArray {
            var n = 0.0
            for (x in v) n += x * x
            val len = sqrt(n).toFloat().coerceAtLeast(1e-6f)
            return ByteArray(v.size) { i -> (v[i] / len * 127f).roundToInt().coerceIn(-127, 127).toByte() }
        }

        fun dot(a: ByteArray, b: ByteArray, offset: Int, dim: Int): Float {
            var s = 0
            for (i in 0 until dim) s += a[i] * b[offset + i]
            return s.toFloat()
        }
    }
}
