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
    val dirs = root.listFiles().filter { it.isDirectory && it.name != SINGLES && it.findFile("uitslag.txt") != null }.sortedBy { it.name }
    val singlesDir = root.findFile(SINGLES)?.takeIf { it.isDirectory && it.findFile("uitslag.txt") != null }
    if (dirs.isEmpty() && singlesDir == null) return "Geen bladen gevonden. Kies de map met submappen die een uitslag.txt en foto.jpg bevatten (zoals testset/ in de repo), en eventueel losse-kaarten/."
    // Losse kaarten (één kaart per foto): apart geteld.
    var sOk = 0; var sTotal = 0; var sSureWrong = 0; var sCheckRight = 0; var sNotFound = 0
    val sBody = StringBuilder()
    val body = StringBuilder()
    var ok = 0; var total = 0; var sureWrong = 0; var checkRight = 0; var notFound = 0; var rawOk = 0
    // Ook als galerijfoto (zonder kader), alleen het totaal: zo blijft zichtbaar of een galerijfoto met een stuk buurpagina werkt.
    var galleryOk = 0; var galleryTotal = 0; var gallerySureWrong = 0
    // Herkennen op het plaatje (1h-scan): hoe vaak de beste kaart volgens het plaatje klopte, los van wat de app koos.
    var retries = 0; var stillWaiting = 0
    var picTotal = 0; var picRight = 0; var picSure = 0; var picSureWrong = 0
    val picRightScores = mutableListOf<Float>(); val picWrongScores = mutableListOf<Float>()
    val picRightMargins = mutableListOf<Float>(); val picWrongMargins = mutableListOf<Float>()
    val picSets = mutableListOf<String>()
    val t0 = System.currentTimeMillis()
    val failsBefore = vm.repo.scryfall.failures
    val prevSet = vm.scanSet
    try {
        for (dir in dirs) {
            // Zoals Mick de app gebruikt: de set van het blad is ingevuld (mapnaam NN-SET-…); een gemengd blad heeft er geen.
            val folderSet = Regex("^\\d+-([A-Za-z0-9]{2,5})-").find(dir.name.orEmpty())?.groupValues?.get(1)?.uppercase()?.takeIf { it != "GEMENGD" } ?: ""
            withContext(Dispatchers.Main) { vm.scanSet = folderSet }
            // Vingerafdrukken voor de set van het blad (eenmalig met internet; daarna van de telefoon).
            if (folderSet.isNotBlank()) { progress("${dir.name}: vingerafdrukken"); vm.ensureFingerprints(folderSet)?.let { n -> picSets += "$folderSet $n" } }
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
                val read = try {
                    val first = readImage(bmp, if (camera) kader else null, false, if (camera) emptyList() else vm.setNames(folderSet))
                    val (r00, _) = reread(bmp, first, false, vm.setInfo.keys)
                    val r0 = tidyReads(vm, r00, false)
                    markEmpty(bmp, r0)
                } catch (e: Exception) { body.appendLine("fout: ${e.message}"); continue }
                val slots = read.slots
                // De uitsnede per vak, zoals na een echte scan (voor printkeuze en het plaatje).
                val crops = withContext(Dispatchers.Default) { runCatching { cropsFor(bmp, read, false) }.getOrDefault(emptyMap()) }
                // 2. opzoeken, zoals na een scan
                withContext(Dispatchers.Main) { vm.startScan(slots, crops) }
                var waited = 0
                while (waited < 180_000 && withContext(Dispatchers.Main) { vm.pending.any { it.status == ItemStatus.RESOLVING } }) { delay(500); waited += 500 }
                // Storing bij Scryfall: dan wacht het hele blad op verbinding. Even wachten en opnieuw (max. 3 keer).
                for (attempt in 1..3) {
                    if (withContext(Dispatchers.Main) { vm.pending.none { it.status == ItemStatus.WAITING } }) break
                    progress("${dir.name}/$fname: opnieuw ($attempt)")
                    delay(3000L * attempt)
                    if (withContext(Dispatchers.Main) { vm.retryWaiting() } > 0) retries++
                    var w2 = 0
                    while (w2 < 60_000 && withContext(Dispatchers.Main) { vm.pending.any { it.status == ItemStatus.RESOLVING } }) { delay(500); w2 += 500 }
                }
                stillWaiting += withContext(Dispatchers.Main) { vm.pending.count { it.status == ItemStatus.WAITING } }
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
                    if (!e.empty && item?.pictureName != null) {
                        val pv = TestsetScore.judge(e, item.pictureName, folderSet.ifBlank { null }, item.pictureNumber, false)
                        picTotal++
                        // Naam goed telt: tussen prints met hetzelfde plaatje kiest het plaatje niet.
                        if (pv.nameOk) { picRight++; picRightScores += item.pictureScore; picRightMargins += item.pictureMargin }
                        else { picWrongScores += item.pictureScore; picWrongMargins += item.pictureMargin }
                        if (item.pictureSure) { picSure++; if (!pv.nameOk) picSureWrong++ }
                    }
                    if (!e.empty) {
                        if (sure && !v.ok) sureWrong++
                        if (!sure && v.ok) checkRight++
                        if (p == null) notFound++
                    }
                    if (!v.ok) {
                        val status = when { item == null -> "geen vak"; item.confident && item.status == ItemStatus.FOUND -> "groen (zeker)"; item.status == ItemStatus.FOUND -> "oranje"; else -> "niet gevonden" }
                        val got = if (p == null) "niets" else "${p.name} (${p.setCode.uppercase()} ${p.collectorNumber})"
                        val las = item?.query?.let { q -> " · las: naam=${q.name ?: "-"} set=${q.setCode ?: "-"} nr=${q.collectorNumber ?: "-"}" + (if (q.altNames.isNotEmpty()) " ook=${q.altNames.take(2).joinToString("/")}" else "") } ?: ""
                        val plaatje = item?.picture?.let { " · plaatje: $it" + (if (item.pictureSure) " [zeker]" else "") } ?: ""
                        body.appendLine("vak $slot: FOUT · verwacht ${TestsetScore.describe(e)} · kreeg $got · $status$las$plaatje")
                    } else if (!e.empty && !sure && item != null) {
                        // 0.9.17: ook waarom een goede kaart oranje bleef (om groen te maken wat veilig groen kan).
                        val q = item.query
                        body.appendLine("vak $slot: goed maar oranje · ${item.label} · las: naam=${q.name ?: "-"} set=${q.setCode ?: "-"} nr=${q.collectorNumber ?: "-"}" +
                            (vm.printWhyFor(item.key)?.let { " · printkiezer: $it" } ?: ""))
                    }
                }
                if (camera) { body.appendLine("met kader.txt: $good van ${expected.size} vakken goed"); continue }
                body.appendLine("$good van ${expected.size} vakken goed")
                }
            }
        }
        // Losse kaarten: zoals scannen kaart voor kaart (één kaart, eigen setveld per foto, het richtkader als dat bekend is).
        // Het stemmen over camerabeelden kan hier niet: uit een foto komt maar één beeld.
        if (singlesDir != null) {
            val items = nl.spoonpaco.ninegrid.domain.SingleTestset.parse(ctx.contentResolver.openInputStream(singlesDir.findFile("uitslag.txt")!!.uri)!!.bufferedReader().readLines())
            for (code in items.map { i -> i.set }.filter { c -> c.isNotBlank() }.distinct()) { progress("$SINGLES: vingerafdrukken $code"); vm.ensureFingerprints(code) }
            for (item in items) {
                val file = singlesDir.findFile("${item.id}.jpg") ?: run { sBody.appendLine("${item.id}: foto ontbreekt"); null } ?: continue
                progress("$SINGLES/${item.id}.jpg")
                withContext(Dispatchers.Main) { vm.scanSet = item.set; if (vm.pending.isNotEmpty()) vm.clearPending() }
                val bmp = try { loadPhoto(ctx, file.uri) } catch (e: Exception) { sBody.appendLine("${item.id}: fout ${e.message}"); continue }
                val read = try {
                    val first = readImage(bmp, item.crop, true)
                    val (r00, _) = reread(bmp, first, true, vm.setInfo.keys)
                    tidyReads(vm, r00, true)
                } catch (e: Exception) { sBody.appendLine("${item.id}: fout ${e.message}"); continue }
                val crops = withContext(Dispatchers.Default) { runCatching { cropsFor(bmp, read, true) }.getOrDefault(emptyMap()) }
                withContext(Dispatchers.Main) { vm.startScan(read.slots, crops, single = true) }
                var waited = 0
                while (waited < 60_000 && withContext(Dispatchers.Main) { vm.pending.any { it.status == ItemStatus.RESOLVING } }) { delay(300); waited += 300 }
                if (withContext(Dispatchers.Main) { vm.pending.any { it.status == ItemStatus.WAITING } }) {
                    delay(3000); withContext(Dispatchers.Main) { vm.retryWaiting() }
                    var w2 = 0
                    while (w2 < 30_000 && withContext(Dispatchers.Main) { vm.pending.any { it.status == ItemStatus.RESOLVING } }) { delay(300); w2 += 300 }
                }
                val got = withContext(Dispatchers.Main) { vm.pending.lastOrNull() }
                withContext(Dispatchers.Main) { vm.clearPending() }
                val p = got?.print
                val v = TestsetScore.judge(item.expect, p?.name, p?.setCode, p?.collectorNumber, p == null)
                val sure = got?.confident == true && got.status == ItemStatus.FOUND
                sTotal++
                if (v.ok) sOk++
                if (sure && !v.ok) sSureWrong++
                if (!sure && v.ok) sCheckRight++
                if (p == null) sNotFound++
                val status = when { got == null -> "niets"; sure -> "groen"; got.status == ItemStatus.FOUND -> "oranje"; else -> "niet gevonden" }
                if (!v.ok) {
                    val las = got?.query?.let { q -> " · las: naam=${q.name ?: "-"} set=${q.setCode ?: "-"} nr=${q.collectorNumber ?: "-"}" } ?: ""
                    val plaatje = got?.picture?.let { " · plaatje: $it" } ?: ""
                    sBody.appendLine("${item.id}: FOUT · verwacht ${TestsetScore.describe(item.expect)} · kreeg ${p?.let { "${it.name} (${it.setCode.uppercase()} ${it.collectorNumber})" } ?: "niets"} · $status" +
                        (if (item.set.isNotBlank()) " · set ingevuld ${item.set}" else "") + las + plaatje)
                } else sBody.appendLine("${item.id}: goed · $status" + (if (!sure && got != null) " · ${got.label} · las: naam=${got.query.name ?: "-"} set=${got.query.setCode ?: "-"} nr=${got.query.collectorNumber ?: "-"}" else "") +
                    (got?.let { g -> vm.printWhyFor(g.key) }?.let { " · printkiezer: $it" } ?: ""))
            }
        }
    } finally {
        withContext(Dispatchers.Main) { if (vm.pending.isNotEmpty()) vm.clearPending(); vm.scanSet = prevSet }
    }
    val secs = (System.currentTimeMillis() - t0) / 1000
    return buildString {
        appendLine("NineGrid testset, volledige scanroute (versie ${nl.spoonpaco.ninegrid.BuildConfig.VERSION_NAME}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
        val fails = vm.repo.scryfall.failures - failsBefore
        if (fails > 0) appendLine(if (stillWaiting == 0) "Scryfall was $fails keer niet bereikbaar; $retries keer opnieuw geprobeerd, alles gelukt. De uitslag telt dus."
            else "LET OP: $fails keer kon Scryfall niet bereikt worden; na $retries keer opnieuw proberen wachten nog $stillWaiting vakken op verbinding. Deze uitslag is dan te laag; draai later opnieuw.")
        appendLine("Bladen: $total vakken, $ok helemaal goed na opzoeken · $secs s")
        appendLine("Groen maar fout (het ergste): $sureWrong · oranje maar goed: $checkRight · niet gevonden: $notFound")
        if (sTotal > 0) {
            appendLine("Losse kaarten: $sTotal foto's, $sOk helemaal goed")
            appendLine("Groen maar fout (het ergste): $sSureWrong · oranje maar goed: $sCheckRight · niet gevonden: $sNotFound")
        }
        if (picTotal > 0) {
            fun avg(l: List<Float>) = if (l.isEmpty()) "-" else String.format(java.util.Locale.ROOT, "%.2f", l.average())
            fun lo(l: List<Float>) = l.minOrNull()?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "-"
            fun hi(l: List<Float>) = l.maxOrNull()?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "-"
            appendLine("Plaatje (beste kaart, alleen de naam): $picRight van $picTotal goed · zeker: $picSure, waarvan fout: $picSureWrong")
            appendLine("  score goed gem ${avg(picRightScores)} (laagste ${lo(picRightScores)}) · fout gem ${avg(picWrongScores)} (hoogste ${hi(picWrongScores)})")
            appendLine("  marge goed gem ${avg(picRightMargins)} (laagste ${lo(picRightMargins)}) · fout gem ${avg(picWrongMargins)} (hoogste ${hi(picWrongMargins)})")
            appendLine("  grenzen nu: score ${nl.spoonpaco.ninegrid.domain.PictureIndex.MIN_SCORE} en marge ${nl.spoonpaco.ninegrid.domain.PictureIndex.MIN_MARGIN} · sets: ${picSets.joinToString(", ")}")
        } else appendLine("Plaatje: niet gebruikt (uit in Instellingen → Scannen, geen beeldmodel in deze build, of geen vingerafdrukken).")
        if (galleryTotal > 0) appendLine("Met kader.txt (alleen bladen met dat bestand): $galleryOk van $galleryTotal goed · groen maar fout: $gallerySureWrong")
        appendLine("Met de set van de mapnaam ingevuld, zoals jij de app gebruikt. Als galerijfoto (zonder kader), wat op 18 nov overeenkwam met de camera. Bij een fout staat erbij wat de scanner las. Namen tellen goed bij 80% gelijkenis. Heeft internet nodig voor kaarten die de offline database niet kent.")
        append(body)
        if (sTotal > 0) { appendLine("\n== $SINGLES (zoals kaart voor kaart; zonder het stemmen over camerabeelden)"); append(sBody) }
    }
}

private const val SINGLES = "losse-kaarten"
