package nl.spoonpaco.ninegrid.ui.screens

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.spoonpaco.ninegrid.domain.TestsetScore
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.ItemStatus

/**
 * Draait de volledige scanroute op de bladen in een map op de telefoon: tekstherkenning, opnieuw lezen, opruimen
 * (verzonnen sets weg, set van het blad), lege vakken, en dan opzoeken in de offline database en bij Scryfall,
 * precies zoals na een echte scan. Vergeleken wordt het eindresultaat (de kaart die je zou zien op het controlescherm).
 * Elke submap heeft een uitslag.txt en foto.jpg (en eventueel foto-2.jpg): dezelfde indeling als testset/ in de repo.
 * Het scherm met een lopende scan wordt niet aangeraakt: staat er nog iets klaar, dan stopt de test.
 */
internal suspend fun runTestset(ctx: Context, tree: Uri, vm: AppViewModel, progress: (String) -> Unit): String {
    val root = DocumentFile.fromTreeUri(ctx, tree) ?: return "Map niet te openen."
    if (withContext(Dispatchers.Main) { vm.pending.isNotEmpty() }) return "Er staat nog een scan klaar op het controlescherm. Sla die eerst op of gooi hem weg, en draai dan de testset."
    val dirs = root.listFiles().filter { it.isDirectory && it.findFile("uitslag.txt") != null }.sortedBy { it.name }
    if (dirs.isEmpty()) return "Geen bladen gevonden. Kies de map met submappen die een uitslag.txt en foto.jpg bevatten (zoals testset/ in de repo)."
    val body = StringBuilder()
    var ok = 0; var total = 0; var sureWrong = 0; var checkRight = 0; var notFound = 0; var rawOk = 0
    // Ook als galerijfoto (zonder kader), alleen het totaal: zo blijft zichtbaar of een galerijfoto met een stuk buurpagina werkt.
    var galleryOk = 0; var galleryTotal = 0; var gallerySureWrong = 0
    val t0 = System.currentTimeMillis()
    val prevSet = vm.scanSet
    try {
        for (dir in dirs) {
            // Zoals Mick de app gebruikt: de set van het blad is ingevuld (mapnaam NN-SET-…); een gemengd blad heeft er geen.
            val folderSet = Regex("^\\d+-([A-Za-z0-9]{2,5})-").find(dir.name.orEmpty())?.groupValues?.get(1)?.uppercase()?.takeIf { it != "GEMENGD" } ?: ""
            withContext(Dispatchers.Main) { vm.scanSet = folderSet }
            val expected = TestsetScore.parse(ctx.contentResolver.openInputStream(dir.findFile("uitslag.txt")!!.uri)!!.bufferedReader().readLines())
            for (fname in listOf("foto.jpg", "foto-2.jpg")) {
                val file = dir.findFile(fname) ?: continue
                progress("${dir.name}/$fname")
                body.appendLine("\n== ${dir.name}/$fname")
                // Het kader zoals de camera het geeft: kader.txt (bij foto-2: kader-2.txt) met "links boven rechts onder" in pixels
                // van de foto, om het blad heen. Zonder bestand vult het blad de foto en is het kader de hele foto.
                val bmp = try { loadPhoto(ctx, file.uri) } catch (e: Exception) { body.appendLine("fout: ${e.message}"); continue }
                val kaderName = if (fname == "foto.jpg") "kader.txt" else "kader-2.txt"
                val kader = dir.findFile(kaderName)?.let { f ->
                    runCatching { ctx.contentResolver.openInputStream(f.uri)!!.bufferedReader().readText().trim().split(Regex("""\s+""")).map { it.toFloat() } }.getOrNull()
                }?.takeIf { it.size == 4 }?.let { nl.spoonpaco.ninegrid.domain.Area(it[0], it[1], it[2], it[3]) }
                // Standaard als galerijfoto: dat kwam op 18 nov overeen met wat de camera op dezelfde bladen las. Met een
                // kader.txt ook met dat kader (de met de hand gezette kaders van 18 nov maakten het slechter en zijn weg).
                for (camera in if (kader != null) listOf(false, true) else listOf(false)) {
                // 1. lezen, zoals ScanScreen.process
                val slots = try {
                    val first = readImage(bmp, if (camera) kader else null, false)
                    val (r00, _) = reread(bmp, first, false, vm.setInfo.keys)
                    val r0 = tidyReads(vm, r00, false)
                    markEmpty(bmp, r0).slots
                } catch (e: Exception) { body.appendLine("fout: ${e.message}"); continue }
                // 2. opzoeken, zoals na een scan
                withContext(Dispatchers.Main) { vm.startScan(slots) }
                var waited = 0
                while (waited < 180_000 && withContext(Dispatchers.Main) { vm.pending.any { it.status == ItemStatus.RESOLVING } }) { delay(500); waited += 500 }
                val items = withContext(Dispatchers.Main) { vm.pending.toList() }
                withContext(Dispatchers.Main) { vm.clearPending() }
                val bySlot = items.filter { it.slot != null }.associateBy { it.slot!! }
                var good = 0
                for ((slot, e) in expected.toSortedMap()) {
                    val item = bySlot[slot]
                    val p = item?.print
                    val blank = item == null || item.empty || (p == null && item.status != ItemStatus.FOUND)
                    val v = TestsetScore.judge(e, p?.name, p?.setCode, p?.collectorNumber, blank)
                    val sure = item?.confident == true && item.status == ItemStatus.FOUND
                    if (camera) {
                        galleryTotal++
                        if (v.ok) { galleryOk++; good++ }
                        if (!e.empty && sure && !v.ok) gallerySureWrong++
                        continue
                    }
                    total++
                    if (v.ok) { ok++; good++ }
                    if (!e.empty) {
                        if (sure && !v.ok) sureWrong++
                        if (!sure && v.ok) checkRight++
                        if (p == null) notFound++
                    }
                    if (!v.ok) {
                        val status = when { item == null -> "geen vak"; item.confident && item.status == ItemStatus.FOUND -> "groen (zeker)"; item.status == ItemStatus.FOUND -> "oranje"; else -> "niet gevonden" }
                        val got = if (p == null) "niets" else "${p.name} (${p.setCode.uppercase()} ${p.collectorNumber})"
                        val read = item?.query?.let { q -> " · las: naam=${q.name ?: "-"} set=${q.setCode ?: "-"} nr=${q.collectorNumber ?: "-"}" + (if (q.altNames.isNotEmpty()) " ook=${q.altNames.take(2).joinToString("/")}" else "") } ?: ""
                        body.appendLine("vak $slot: FOUT · verwacht ${TestsetScore.describe(e)} · kreeg $got · $status$read")
                    }
                }
                if (camera) { body.appendLine("met kader.txt: $good van ${expected.size} vakken goed"); continue }
                body.appendLine("$good van ${expected.size} vakken goed")
                }
            }
        }
    } finally {
        withContext(Dispatchers.Main) { if (vm.pending.isNotEmpty()) vm.clearPending(); vm.scanSet = prevSet }
    }
    val secs = (System.currentTimeMillis() - t0) / 1000
    return buildString {
        appendLine("NineGrid testset, volledige scanroute (versie ${nl.spoonpaco.ninegrid.BuildConfig.VERSION_NAME}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
        appendLine("Totaal: $total vakken, $ok helemaal goed na opzoeken · $secs s")
        appendLine("Groen maar fout (het ergste): $sureWrong · oranje maar goed: $checkRight · niet gevonden: $notFound")
        if (galleryTotal > 0) appendLine("Met kader.txt (alleen bladen met dat bestand): $galleryOk van $galleryTotal goed · groen maar fout: $gallerySureWrong")
        appendLine("Met de set van de mapnaam ingevuld, zoals jij de app gebruikt. Als galerijfoto (zonder kader), wat op 18 nov overeenkwam met de camera. Bij een fout staat erbij wat de scanner las. Namen tellen goed bij 80% gelijkenis. Heeft internet nodig voor kaarten die de offline database niet kent.")
        append(body)
    }
}
