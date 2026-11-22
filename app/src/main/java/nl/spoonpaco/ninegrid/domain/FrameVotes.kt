package nl.spoonpaco.ninegrid.domain

/**
 * Kaart voor kaart: wat de camera in de laatste beelden las. Na de foto vult
 * [fill] alleen aan wat de foto zelf miste (set, nummer, of de naam), en alleen met wat meerdere beelden van dezelfde
 * kaart het eens waren. Wat de foto las, wint altijd: die is scherper dan de beelden van het meekijken.
 */
class FrameVotes(private val window: Int = 8, private val maxAgeMs: Long = 2500) {
    data class Read(val name: String?, val setCode: String?, val number: String?, val at: Long)

    private val reads = ArrayDeque<Read>()

    @Synchronized
    fun add(r: Read) {
        if (r.name == null && r.setCode == null && r.number == null) return
        reads.addLast(r)
        while (reads.size > window) reads.removeFirst()
    }

    @Synchronized
    fun clear() = reads.clear()

    /** De beelden van de laatste [maxAgeMs] voor [now]. */
    @Synchronized
    fun recent(now: Long): List<Read> = reads.filter { now - it.at in 0..maxAgeMs }

    companion object {
        const val MIN_VOTES = 2

        private fun key(n: String) = n.lowercase().filter { it.isLetter() }

        /** Wat het vaakst voorkomt, als dat minstens [min] keer is en er geen gelijkspel is. */
        fun <T> majority(xs: List<T>, min: Int = MIN_VOTES): T? {
            val counts = xs.groupingBy { it }.eachCount()
            val best = counts.maxByOrNull { it.value } ?: return null
            if (best.value < min || counts.count { it.value == best.value } > 1) return null
            return best.key
        }

        /**
         * Vult [photo] aan met [frames]. Zonder naam (op de foto of eensgezind in de beelden) gebeurt er niets:
         * dan weten we niet of de beelden over dezelfde kaart gingen.
         */
        fun fill(photo: SlotRead, frames: List<Read>): SlotRead {
            if (frames.isEmpty() || (photo.setCode != null && photo.collectorNumber != null && photo.name != null)) return photo
            val name = photo.name ?: majority(frames.mapNotNull { f -> f.name?.let(::key)?.takeIf { it.length >= 3 } })
                ?.let { k -> frames.first { it.name != null && key(it.name) == k }.name }
                ?: return photo
            // Alleen beelden van dezelfde kaart tellen mee.
            val same = frames.filter { it.name != null && NameMatch.alike(it.name, name) }
            val set = photo.setCode ?: majority(same.mapNotNull { it.setCode?.uppercase() })
            val number = photo.collectorNumber ?: majority(same.mapNotNull { it.number?.uppercase() })
            return photo.copy(name = name, setCode = set, collectorNumber = number)
        }
    }
}
