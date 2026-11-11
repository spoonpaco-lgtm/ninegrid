package nl.spoonpaco.ninegrid.domain

/** Gegevens van één scan die niet in de vakken zelf zitten. */
data class ScanMeta(
    val version: String,
    val single: Boolean,
    val fromCamera: Boolean,
    val sharpness: Double,
    val coverage: Float,
    val readMs: Long,
    val rereadSlots: Int,
    val photoWidth: Int,
    val photoHeight: Int,
    val device: String,
)

/**
 * Tekstrapport van een scan voor het Ontwikkelaar-menu: per vak wat de tekstherkenning las en wat de app ervan maakte.
 * Bedoeld om een scanfout terug te vinden zonder rondvragen. Bevat geen gegevens over de gebruiker.
 */
object ScanReport {
    /** [outcomes]: per vak wat er uiteindelijk van werd ("gevonden: Island (SLD 12)", "niet gevonden", "leeg vak"). */
    fun build(meta: ScanMeta, reads: List<SlotRead>, outcomes: Map<Int, String>): String = buildString {
        appendLine("NineGrid scanrapport")
        appendLine("versie: ${meta.version} · toestel: ${meta.device}")
        appendLine("soort: ${if (meta.single) "één kaart" else "blad"} · ${if (meta.fromCamera) "camera" else "galerij"} · foto ${meta.photoWidth}x${meta.photoHeight}")
        appendLine("scherpte: ${"%.1f".format(meta.sharpness)} · dekking: ${"%.2f".format(meta.coverage)} · lezen: ${meta.readMs} ms · opnieuw gelezen: ${meta.rereadSlots}")
        val read = reads.count { it.complete || it.name != null }
        appendLine("vakken met naam of nummer en set: $read van ${reads.size}")
        appendLine()
        reads.sortedBy { it.slot }.forEach { r ->
            appendLine("vak ${r.slot}: ${outcomes[r.slot] ?: "-"}")
            appendLine("  set=${r.setCode ?: "-"} nummer=${r.collectorNumber ?: "-"} taal=${r.language ?: "-"} naam=${r.name ?: "-"}" +
                (if (r.foilStar) " ster" else "") + (if (r.looksEmpty) " leeg?" else ""))
            if (r.altNames.isNotEmpty()) appendLine("  andere namen: ${r.altNames.joinToString(" | ")}")
            r.lines.forEach { appendLine("  > ${it.replace('\n', ' ')}") }
        }
    }
}
