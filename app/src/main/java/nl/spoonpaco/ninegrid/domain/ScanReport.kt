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
    /** Omstandigheden en instellingen tijdens de scan. */
    val setField: String = "",
    val torch: Boolean = false,
    val autoCapture: Boolean = false,
    val aiHelp: Boolean = false,
    val offlineDb: Boolean = false,
    val language: String = "",
    /** Herkennen op het plaatje aan, en of er vingerafdrukken voor de set op de telefoon stonden. */
    val pictureMatch: Boolean = false,
    val pictureReady: Boolean = false,
    /** Kaart voor kaart: camerabeelden die meestemden, en vakken die daarmee zijn aangevuld. */
    val frameVotes: Int = 0,
    val filledFromFrames: Int = 0,
    /** 0.9.17: wat elk camerabeeld las (naam, set, nummer), om te zien waarom het stemmen wel of niet aanvulde. */
    val frames: List<String> = emptyList(),
    /** Het kader op de foto ("links boven rechts onder" in pixels), leeg = de hele foto. Voor de testset. */
    val crop: String = "",
)

/**
 * Tekstrapport van een scan voor het Ontwikkelaar-menu: per vak wat de tekstherkenning las en wat de app ervan maakte.
 * Bedoeld om een scanfout terug te vinden zonder rondvragen. Bevat geen gegevens over de gebruiker.
 */
object ScanReport {
    /**
     * [outcomes]: per vak wat er uiteindelijk van werd ("gevonden: Island (SLD 12)", "niet gevonden", "leeg vak").
     * [firstGuess]: wat de app er zelf van maakte, vóór de gebruiker iets corrigeerde; verschilt het, dan staat het erbij.
     */
    fun build(meta: ScanMeta, reads: List<SlotRead>, outcomes: Map<Int, String>, firstGuess: Map<Int, String> = emptyMap()): String = buildString {
        appendLine("NineGrid scanrapport")
        appendLine("versie: ${meta.version} · toestel: ${meta.device}")
        appendLine("soort: ${if (meta.single) "één kaart" else "blad"} · ${if (meta.fromCamera) "camera" else "galerij"} · foto ${meta.photoWidth}x${meta.photoHeight}")
        appendLine("instellingen: set ${meta.setField.ifBlank { "leeg (automatisch)" }} · lamp ${if (meta.torch) "aan" else "uit"} · automatisch afdrukken ${if (meta.autoCapture) "aan" else "uit"} · AI-hulp ${if (meta.aiHelp) "aan" else "uit"} · offline database ${if (meta.offlineDb) "aan" else "uit"} · taal ${meta.language.ifBlank { "-" }} · plaatje ${if (!meta.pictureMatch) "uit" else if (meta.pictureReady) "aan" else "aan, geen vingerafdrukken voor deze set"}")
        if (meta.single) appendLine("camerabeelden: ${meta.frameVotes} · aangevuld uit de beelden: ${meta.filledFromFrames}")
        if (meta.single) meta.frames.forEachIndexed { i, f -> appendLine("  beeld ${i + 1}: $f") }
        if (meta.crop.isNotBlank()) appendLine("kader: ${meta.crop}")
        appendLine("scherpte: ${"%.1f".format(meta.sharpness)} · dekking: ${"%.2f".format(meta.coverage)} · lezen: ${meta.readMs} ms · opnieuw gelezen: ${meta.rereadSlots}")
        val read = reads.count { it.complete || it.name != null }
        appendLine("vakken met naam of nummer en set: $read van ${reads.size}")
        appendLine()
        reads.sortedBy { it.slot }.forEach { r ->
            appendLine("vak ${r.slot}: ${outcomes[r.slot] ?: "-"}")
            val first = firstGuess[r.slot]
            if (first != null && first != outcomes[r.slot]) appendLine("  app dacht eerst: $first")
            appendLine("  set=${r.setCode ?: "-"} nummer=${r.collectorNumber ?: "-"} taal=${r.language ?: "-"} naam=${r.name ?: "-"}" +
                (if (r.foilStar) " ster" else "") + (if (r.looksEmpty) " leeg?" else ""))
            if (r.altNames.isNotEmpty()) appendLine("  andere namen: ${r.altNames.joinToString(" | ")}")
            r.lines.forEach { appendLine("  > ${it.replace('\n', ' ')}") }
        }
    }
}
