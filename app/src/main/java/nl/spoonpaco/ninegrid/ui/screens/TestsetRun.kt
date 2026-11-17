package nl.spoonpaco.ninegrid.ui.screens

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import nl.spoonpaco.ninegrid.domain.ScanParser
import nl.spoonpaco.ninegrid.domain.TestsetScore

/**
 * Draait de echte leesstraat (tekstherkenning, eerste ronde, opnieuw lezen) op de bladen in een map op de telefoon.
 * Elke submap heeft een uitslag.txt en foto.jpg (en eventueel foto-2.jpg): dezelfde indeling als testset/ in de repo.
 * Geeft het rapport als tekst terug; [progress] krijgt de naam van het blad dat nu aan de beurt is.
 */
internal suspend fun runTestset(ctx: Context, tree: Uri, knownSets: Set<String>, progress: (String) -> Unit): String {
    val root = DocumentFile.fromTreeUri(ctx, tree) ?: return "Map niet te openen."
    val dirs = root.listFiles().filter { it.isDirectory && it.findFile("uitslag.txt") != null }.sortedBy { it.name }
    if (dirs.isEmpty()) return "Geen bladen gevonden. Kies de map met submappen die een uitslag.txt en foto.jpg bevatten (zoals testset/ in de repo)."
    val body = StringBuilder()
    var ok = 0; var total = 0; var nameOk = 0; var setOk = 0; var setN = 0; var numOk = 0; var numN = 0
    val t0 = System.currentTimeMillis()
    for (dir in dirs) {
        val expected = TestsetScore.parse(ctx.contentResolver.openInputStream(dir.findFile("uitslag.txt")!!.uri)!!.bufferedReader().readLines())
        for (fname in listOf("foto.jpg", "foto-2.jpg")) {
            val file = dir.findFile(fname) ?: continue
            progress("${dir.name}/$fname")
            body.appendLine("\n== ${dir.name}/$fname")
            val reads = try {
                val bmp = loadPhoto(ctx, file.uri)
                val first = readImage(bmp, null, false)
                reread(bmp, first, false, knownSets).first.slots.associateBy { it.slot }
            } catch (e: Exception) { body.appendLine("fout: ${e.message}"); null }
            if (reads == null) continue
            var good = 0
            for ((slot, e) in expected.toSortedMap()) {
                val r = reads[slot]
                val v = TestsetScore.judge(e, r)
                total++; if (v.ok) { ok++; good++ }
                if (!e.empty) { if (v.nameOk) nameOk++; if (e.set != null) { setN++; if (v.setOk) setOk++ }; if (e.number != null) { numN++; if (v.numberOk) numOk++ } }
                if (!v.ok) body.appendLine("vak $slot: FOUT · verwacht ${TestsetScore.describe(e)} · gelezen ${TestsetScore.describe(r)}")
            }
            body.appendLine("$good van ${expected.size} vakken goed")
        }
    }
    val secs = (System.currentTimeMillis() - t0) / 1000
    return buildString {
        appendLine("NineGrid testset (versie ${nl.spoonpaco.ninegrid.BuildConfig.VERSION_NAME}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
        appendLine("Totaal: $ok van $total vakken helemaal goed · set $setOk/$setN · nummer $numOk/$numN · $secs s")
        appendLine("Leest met de echte scanner (eerste ronde en opnieuw lezen). Namen tellen goed bij 80% gelijkenis: de app verbetert ze daarna met de database.")
        append(body)
    }
}
