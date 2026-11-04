package nl.spoonpaco.ninegrid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.domain.Area
import nl.spoonpaco.ninegrid.domain.OcrLine
import nl.spoonpaco.ninegrid.domain.ScanParser
import nl.spoonpaco.ninegrid.domain.ScanResult
import nl.spoonpaco.ninegrid.domain.SlotRead
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun recognize(image: InputImage): MlText = suspendCancellableCoroutine { cont ->
    val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    client.process(image)
        .addOnSuccessListener { cont.resume(it); client.close() }
        .addOnFailureListener { cont.resumeWithException(it); client.close() }
}

/** Leest de foto. Met crop telt alleen tekst binnen het kader. */
private suspend fun readImage(bmp: Bitmap, crop: Area?, single: Boolean): ScanResult {
    val text = recognize(InputImage.fromBitmap(bmp, 0))
    val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
        val box = l.boundingBox ?: return@mapNotNull null
        OcrLine(l.text, box.exactCenterX(), box.exactCenterY())
    }
    return if (single) ScanParser.analyzeSingle(lines, bmp.width, bmp.height, crop)
    else ScanParser.analyze(lines, bmp.width, bmp.height, crop)
}

private const val CARD_RATIO = 63f / 88f

/** Wat er gebeurt als de kaart stilligt (doorlopend scannen); gezet door het camerascherm. */
private var watcherTrigger: () -> Unit = {}

/** Het richtkader binnen een beeld van w x h, tussen de balken boven en onder. */
private fun guideRect(w: Float, h: Float, topPx: Float, bottomPx: Float, single: Boolean): Area {
    val availTop = topPx + 8f
    val availH = (h - topPx - bottomPx - 16f).coerceAtLeast(h * 0.3f)
    // Blad: 3x3 kaarten heeft dezelfde verhouding als één kaart. Eén kaart: iets kleiner, zodat je hem makkelijk vult.
    val maxW = if (single) w * 0.78f else w * 0.94f
    val gw = minOf(maxW, availH * (if (single) 0.92f else 1f) * CARD_RATIO); val gh = gw / CARD_RATIO
    val left = (w - gw) / 2f; val top = availTop + (availH - gh) / 2f
    return Area(left, top, left + gw, top + gh)
}

/** Zet het kader op het scherm om naar pixels op de foto (het camerabeeld vult het scherm, midden uitgesneden). */
private fun guideOnImage(g: Area, boxW: Float, boxH: Float, imgW: Int, imgH: Int): Area {
    val scale = maxOf(boxW / imgW, boxH / imgH)
    val dx = (boxW - imgW * scale) / 2f; val dy = (boxH - imgH * scale) / 2f
    val padX = g.width * 0.03f; val padY = g.height * 0.03f
    return Area(
        ((g.left - padX - dx) / scale).coerceAtLeast(0f), ((g.top - padY - dy) / scale).coerceAtLeast(0f),
        ((g.right + padX - dx) / scale).coerceAtMost(imgW.toFloat()), ((g.bottom + padY - dy) / scale).coerceAtMost(imgH.toFloat()),
    )
}

/** Scherpte van (een deel van) de foto: variantie van de Laplace-filter op een verkleinde grijze versie. */
private fun sharpness(bmp: Bitmap, area: Area?): Double {
    val a = area ?: Area(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
    val cropped = Bitmap.createBitmap(bmp, a.left.toInt(), a.top.toInt(), a.width.toInt().coerceIn(1, bmp.width - a.left.toInt()), a.height.toInt().coerceIn(1, bmp.height - a.top.toInt()))
    val w = 600; val h = (cropped.height * w / cropped.width.toFloat()).toInt().coerceAtLeast(3)
    val small = Bitmap.createScaledBitmap(cropped, w, h, true)
    val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
    val g = DoubleArray(px.size) { i -> val c = px[i]; 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255) }
    var sum = 0.0; var sum2 = 0.0; var n = 0
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        val l = g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4 * g[i]
        sum += l; sum2 += l * l; n++
    }
    if (n == 0) return 0.0
    val mean = sum / n
    return sum2 / n - mean * mean
}

/** Leest een strook van de foto opnieuw, twee keer vergroot. */
private suspend fun readStrip(bmp: Bitmap, a: Area): List<String> {
    val l = a.left.toInt().coerceIn(0, bmp.width - 1); val t = a.top.toInt().coerceIn(0, bmp.height - 1)
    val w = a.width.toInt().coerceIn(1, bmp.width - l); val h = a.height.toInt().coerceIn(1, bmp.height - t)
    val part = Bitmap.createBitmap(bmp, l, t, w, h)
    val big = Bitmap.createScaledBitmap(part, (w * 2).coerceAtMost(2400), (h * 2).coerceAtMost(1200), true)
    return recognize(InputImage.fromBitmap(big, 0)).textBlocks.flatMap { it.lines }.map { it.text }
}

/** Vakken die iets missen opnieuw lezen: de naamstrook bovenaan en de strook met set en nummer onderaan. */
private suspend fun reread(bmp: Bitmap, result: ScanResult, single: Boolean): Pair<ScanResult, Int> {
    val f = result.frame ?: return result to 0
    var count = 0
    val slots = result.slots.map { r ->
        if (!ScanParser.needsReread(r)) return@map r
        val cell = if (single) f else {
            val cw = f.width / 3f; val ch = f.height / 3f
            val row = (r.slot - 1) / 3; val col = (r.slot - 1) % 3
            Area(f.left + col * cw, f.top + row * ch, f.left + (col + 1) * cw, f.top + (row + 1) * ch)
        }
        count++
        val top = runCatching { readStrip(bmp, Area(cell.left, cell.top, cell.right, cell.top + cell.height * 0.2f)) }.getOrDefault(emptyList())
        val bottom = runCatching { readStrip(bmp, Area(cell.left, cell.bottom - cell.height * 0.17f, cell.right, cell.bottom)) }.getOrDefault(emptyList())
        ScanParser.enrich(r, top, bottom)
    }
    return result.copy(slots = slots) to count
}

/** Uitsnede van een stuk van de foto, verkleind tot een kleine afbeelding voor het controlescherm. */
private fun cutOut(bmp: Bitmap, a: Area, maxW: Int = 260): Bitmap? = runCatching {
    val l = a.left.toInt().coerceIn(0, bmp.width - 1); val t = a.top.toInt().coerceIn(0, bmp.height - 1)
    val w = a.width.toInt().coerceIn(1, bmp.width - l); val h = a.height.toInt().coerceIn(1, bmp.height - t)
    val part = Bitmap.createBitmap(bmp, l, t, w, h)
    if (part.width <= maxW) part else Bitmap.createScaledBitmap(part, maxW, (part.height * maxW / part.width.toFloat()).toInt().coerceAtLeast(1), true)
}.getOrNull()

/** Per vak een uitsnede van de foto (blad), of één uitsnede (losse kaart). */
private fun cropsFor(bmp: Bitmap, result: ScanResult, single: Boolean): Map<Int, Bitmap> {
    val f = result.frame ?: return emptyMap()
    if (single) return listOfNotNull(cutOut(bmp, f)?.let { 1 to it }).toMap()
    val cw = f.width / 3f; val ch = f.height / 3f
    return (1..9).mapNotNull { slot ->
        val r = (slot - 1) / 3; val c = (slot - 1) % 3
        cutOut(bmp, Area(f.left + c * cw, f.top + r * ch, f.left + (c + 1) * cw, f.top + (r + 1) * ch))?.let { slot to it }
    }.toMap()
}

/** Foto uit de galerij: verkleind ingelezen (scheelt veel geheugen) en rechtop gedraaid volgens de EXIF-gegevens. */
private fun loadPhoto(context: Context, uri: Uri, maxSide: Int = 4000): Bitmap {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)!!.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("decode")
    val rotation = runCatching {
        context.contentResolver.openInputStream(uri)!!.use { s ->
            when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }.getOrDefault(0)
    return if (rotation == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
}

/** Waarom een foto waarschijnlijk niet goed is. */
private enum class Quality { BLURRY, TOO_FAR }

private fun hasCamera(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: AppViewModel, onBack: () -> Unit, onResult: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val single = settings.scanMode == "single"
    var permitted by remember { mutableStateOf(hasCamera(context)) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build(),
            )
            .build()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val noCardsText = stringResource(R.string.scan_nothing_found)
    val failText = stringResource(R.string.scan_failed)

    var quality by remember { mutableStateOf<Triple<Quality, List<SlotRead>, Map<Int, Bitmap>>?>(null) }
    var boxW by remember { mutableStateOf(0f) }
    var boxH by remember { mutableStateOf(0f) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var editFields by remember { mutableStateOf(false) }
    // Aanduiding dat de foto genomen is: flits, even stilstaand beeld, en een klein plaatje linksonder.
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    var frozen by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lastPhoto by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var showPhoto by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shutterSound = remember { android.media.MediaActionSound() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    // Doorlopend scannen bij 1 kaart.
    val auto = single && settings.autoCapture
    var autoPaused by remember { mutableStateOf(false) }
    var autoBlink by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // Ruimte boven en onder die door de balken op het beeld wordt ingenomen; het kader valt daartussen.
    val topBarPx = with(density) { 64.dp.toPx() } + WindowInsets.statusBars.getTop(density)
    val bandDp = 40 + 52 + 96
    val bottomBarPx = with(density) { (bandDp + 26).dp.toPx() } + WindowInsets.navigationBars.getBottom(density)

    fun startScan(reads: List<SlotRead>, crops: Map<Int, Bitmap>) {
        vm.startScan(reads, crops, single = single)
    }

    fun handle(result: ScanResult, sharp: Double, fromCamera: Boolean, crops: Map<Int, Bitmap>) {
        working = false
        val reads = result.slots
        val complete = reads.count { it.complete || it.name != null }
        val enough = if (single) complete >= 1 else complete >= 7
        val problem = when {
            enough -> null
            sharp < 35.0 -> Quality.BLURRY
            fromCamera && result.coverage < (if (single) 0.5f else 0.35f) -> Quality.TOO_FAR
            else -> null
        }
        when {
            problem != null -> quality = Triple(problem, reads, crops)
            reads.none { it.lines.isNotEmpty() } -> error = noCardsText
            else -> startScan(reads, crops)
        }
    }

    fun process(bmp: Bitmap, crop: Area?, fromCamera: Boolean) {
        scope.launch {
            runCatching {
                val t0 = System.currentTimeMillis()
                val sharp = withContext(Dispatchers.Default) { runCatching { sharpness(bmp, crop) }.getOrDefault(100.0) }
                val first = readImage(bmp, crop, single)
                val (r, reread) = reread(bmp, first, single)
                vm.lastReadMs = System.currentTimeMillis() - t0
                vm.lastRereadSlots = reread
                val crops = withContext(Dispatchers.Default) { cropsFor(bmp, r, single) }
                Triple(r, sharp, crops)
            }.onSuccess { (r, sharp, crops) -> handle(r, sharp, fromCamera, crops) }
                .onFailure { working = false; error = failText }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        scope.launch {
            // Een eigen foto heeft geen kader: dan telt de hele foto.
            runCatching { withContext(Dispatchers.IO) { loadPhoto(context, uri) } }
                .onSuccess { process(it, null, fromCamera = false) }
                .onFailure { working = false; error = failText }
        }
    }

    fun takePhoto() {
        working = true
        error = null
        val executor = ContextCompat.getMainExecutor(context)
        fun feedback() {
            scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, androidx.compose.animation.core.tween(180)) }
            if (settings.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            // Alleen geluid als je het aanzette én de telefoon niet op stil of trillen staat.
            if (settings.shutterSound && audio?.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) shutterSound.play(android.media.MediaActionSound.SHUTTER_CLICK)
        }
        fun shoot() = capture.also { feedback() }.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // ImageCapture levert JPEG; ML Kit wil dan een bitmap (fromMediaImage werkt alleen met YUV).
                val bitmap = runCatching {
                    val raw = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    if (rotation == 0) raw else Bitmap.createBitmap(
                        raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true,
                    )
                }.getOrNull()
                image.close()
                if (bitmap == null) { working = false; error = failText; return }
                val shown = bitmap.asImageBitmap()
                frozen = shown; lastPhoto = shown
                scope.launch { kotlinx.coroutines.delay(500); frozen = null }
                val crop = if (boxW > 0 && boxH > 0) guideOnImage(guideRect(boxW, boxH, topBarPx, bottomBarPx, single), boxW, boxH, bitmap.width, bitmap.height) else null
                process(bitmap, crop, fromCamera = true)
            }

            override fun onError(exception: ImageCaptureException) {
                working = false
                error = failText
            }
        })
        // Eerst scherpstellen op het midden, dan pas de foto.
        val cam = camera
        if (cam == null) shoot() else {
            val point = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
            val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
            var done = false
            val go: () -> Unit = { if (!done) { done = true; shoot() }; Unit }
            runCatching { cam.cameraControl.startFocusAndMetering(action).addListener({ go() }, executor) }.onFailure { go() }
            // Niet langer dan 1,5 seconde wachten op scherpstellen.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ go() }, 1500)
        }
    }

    val watcher = remember { AutoCapture(500) { watcherTrigger() } }
    watcher.stableMs = when (settings.autoSpeed) { "fast" -> 300L; "sure" -> 1000L; else -> 500L }
    watcher.busy = working
    val triggerRef = androidx.compose.runtime.rememberUpdatedState {
        if (!working) {
            autoBlink = true
            scope.launch { kotlinx.coroutines.delay(400); autoBlink = false }
            takePhoto()
        }
    }
    val analyzerExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysis = remember {
        androidx.camera.core.ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    // De camera opnieuw koppelen als de stand verandert: meekijken alleen bij 1 kaart met Automatisch aan.
    androidx.compose.runtime.LaunchedEffect(provider, preview, auto) {
        val p = provider ?: return@LaunchedEffect
        val pv = preview ?: return@LaunchedEffect
        runCatching {
            p.unbindAll()
            camera = if (auto) p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, pv, capture, analysis)
            else p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, pv, capture)
        }.onFailure {
            // Sommige telefoons kunnen geen drie dingen tegelijk: dan zonder meekijken.
            runCatching { p.unbindAll(); camera = p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, pv, capture) }
        }
        watcher.reset(); autoPaused = false
    }
    DisposableEffect(auto) {
        var last = 0L
        if (auto) analysis.setAnalyzer(analyzerExecutor) { proxy ->
            val now = System.currentTimeMillis()
            val media = proxy.image
            // Een paar beelden per seconde is genoeg, en niet als het meekijken gepauzeerd is.
            if (media == null || now - last < 300 || autoPaused || boxW <= 0f) { proxy.close(); return@setAnalyzer }
            last = now
            val rot = proxy.imageInfo.rotationDegrees
            val (w, h) = if (rot == 90 || rot == 270) proxy.height to proxy.width else proxy.width to proxy.height
            recognizer.process(InputImage.fromMediaImage(media, rot))
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { b -> OcrLine(l.text, b.exactCenterX(), b.exactCenterY()) } }
                    val crop = guideOnImage(guideRect(boxW, boxH, topBarPx, bottomBarPx, true), boxW, boxH, w, h)
                    val name = ScanParser.analyzeSingle(lines, w, h, crop).slots.firstOrNull()?.name
                    mainExecutor.execute {
                        watcher.onFrame(name?.lowercase()?.filter { it.isLetter() }?.takeIf { it.length >= 3 })
                        if (watcher.idle()) autoPaused = true
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }
        onDispose { analysis.clearAnalyzer() }
    }
    // Wat de watcher doet als de kaart stilligt.
    androidx.compose.runtime.SideEffect { watcherTrigger = { triggerRef.value() } }
    // De volumeknoppen als ontspanknop.
    DisposableEffect(Unit) {
        ScanKeys.onShutter = { if (!working) takePhoto() }
        onDispose { ScanKeys.onShutter = null }
    }

    // Volledig camerascherm: het beeld vult alles, de bediening zweeft erop.
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { boxW = it.width.toFloat(); boxH = it.height.toFloat() }) {
        if (permitted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        // TextureView in plaats van SurfaceView: blijft netjes binnen zijn vak in Compose.
                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            // Zelfde verhouding als de foto, zodat het kader op het scherm en op de foto hetzelfde stuk is.
                            preview = Preview.Builder()
                                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                                .build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            provider = future.get()
                        }, ContextCompat.getMainExecutor(ctx))
                        // Tik om scherp te stellen.
                        view.setOnTouchListener { v, ev ->
                            if (ev.action == android.view.MotionEvent.ACTION_UP) {
                                val point = view.meteringPointFactory.createPoint(ev.x, ev.y)
                                camera?.cameraControl?.startFocusAndMetering(androidx.camera.core.FocusMeteringAction.Builder(point).build())
                                v.performClick()
                            }
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val sum = vm.scanSummary
            val frameColor = when {
                autoBlink -> NgColors.Synced
                sum == null || sum.busy -> NgColors.Amber.copy(alpha = 0.9f)
                sum.allGood -> NgColors.Synced
                else -> Color(0xFFFF8C00)
            }
            frozen?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            GuideOverlay(topBarPx, bottomBarPx, single, frameColor)
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_needed), textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
            }
        }
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))

        // Bovenbalk: terug, set/map/blad, hulp.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding()
                .height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
            val parts = listOfNotNull(
                vm.scanSet.ifBlank { null }?.let { stringResource(R.string.set) + " " + it },
                vm.binder.ifBlank { null },
                vm.page.ifBlank { null }?.let { stringResource(R.string.page).lowercase() + " " + it },
            )
            AssistChip(
                onClick = { editFields = true },
                label = { Text(if (parts.isEmpty()) stringResource(R.string.scan_fields_empty) else parts.joinToString(" · "), maxLines = 1, color = Color.White) },
                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f),
            )
            Box { nl.spoonpaco.ninegrid.ui.guide.HelpButton("add") }
        }

        // Onderband: stand (blad of 1 kaart), uitkomst van de laatste scan, en de knoppen.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.height(40.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.Black)
                FilterChip(selected = !single, onClick = { vm.setScanMode("page") }, label = { Text(stringResource(R.string.scan_mode_page)) }, colors = colors)
                FilterChip(selected = single, onClick = { vm.setScanMode("single") }, label = { Text(stringResource(R.string.scan_mode_single)) }, colors = colors)
                Spacer(Modifier.width(8.dp))
                if (single) FilterChip(
                    selected = settings.autoCapture && !autoPaused,
                    onClick = { if (autoPaused) { autoPaused = false; watcher.reset() } else vm.setAutoCapture(!settings.autoCapture) },
                    label = { Text(stringResource(if (autoPaused) R.string.scan_auto_paused else R.string.scan_auto)) }, colors = colors,
                )
                FilterChip(selected = vm.scanFoil, onClick = { vm.scanFoil = !vm.scanFoil }, label = { Text(stringResource(if (single) R.string.scan_foil_card else R.string.scan_foil_page)) }, colors = colors)
            }
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                val err = error
                val sum = vm.scanSummary
                when {
                    err != null -> Text(err, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
                    sum != null -> ScanSummaryBar(vm, sum, single, onReview = onResult, onSave = {
                        scope.launch {
                            // Al eens gescand? Dan eerst kijken in het controlescherm.
                            if (vm.duplicateSlots().isNotEmpty()) onResult() else vm.savePending { }
                        }
                    })
                    else -> Text(stringResource(R.string.scan_fill_frame), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                // De laatst genomen foto; tik om hem groot te zien.
                Box(Modifier.size(52.dp)) {
                    lastPhoto?.let { p ->
                        androidx.compose.foundation.Image(
                            p, stringResource(R.string.last_photo),
                            Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable { showPhoto = true },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                }
                FilledIconButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !working, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) { Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.from_photos)) }
                Box(contentAlignment = Alignment.Center) {
                    // Ring om de knop zolang hij scherpstelt, leest of opzoekt.
                    if (working || vm.scanSummary?.busy == true) CircularProgressIndicator(Modifier.size(88.dp), color = Color.White, strokeWidth = 3.dp)
                    FilledIconButton(
                        onClick = { takePhoto() },
                        enabled = permitted && !working,
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Filled.CameraAlt, stringResource(R.string.take_photo), modifier = Modifier.size(36.dp)) }
                }
                FilledIconButton(
                    onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                    enabled = permitted && camera?.cameraInfo?.hasFlashUnit() == true, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) { Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, stringResource(R.string.torch)) }
            }
        }
    }

    if (editFields) {
        ModalBottomSheet(onDismissRequest = { editFields = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.scan_fields_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.scan_fields_hint), fontSize = 12.sp, color = LocalNgExtra.current.subtle)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(vm.scanSet, { vm.scanSet = it.take(6).uppercase() }, label = { Text(stringResource(R.string.set)) }, placeholder = { Text("MH1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vm.binder, { vm.binder = it }, label = { Text(stringResource(R.string.binder_short)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    vm.page, { vm.page = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.page)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { editFields = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (showPhoto) lastPhoto?.let { p ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPhoto = false }) {
            androidx.compose.foundation.Image(p, null, Modifier.fillMaxWidth().clickable { showPhoto = false }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
    }

    quality?.let { (q, reads, crops) ->
        AlertDialog(
            onDismissRequest = { quality = null },
            title = { Text(stringResource(if (q == Quality.BLURRY) R.string.scan_blurry_title else R.string.scan_far_title)) },
            text = { Text(stringResource(if (q == Quality.BLURRY) R.string.scan_blurry_body else R.string.scan_far_body)) },
            confirmButton = { TextButton(onClick = { quality = null }) { Text(stringResource(R.string.scan_retry)) } },
            dismissButton = { TextButton(onClick = { quality = null; startScan(reads, crops) }) { Text(stringResource(R.string.scan_view_anyway)) } },
        )
    }
    // Zolang het camerascherm open is, wacht de prijsupdate op de achtergrond.
    DisposableEffect(Unit) {
        vm.repo.scryfall.holdForeground()
        onDispose {
            vm.repo.scryfall.releaseForeground()
            runCatching { recognizer.close() }
            runCatching { analyzerExecutor.shutdown() }
            runCatching { shutterSound.release() }
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

/** De uitkomst van de laatste scan op het camerascherm, met Opslaan als alles klopt. */
@Composable
private fun ScanSummaryBar(vm: AppViewModel, sum: AppViewModel.ScanSummary, single: Boolean, onReview: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (sum.busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        val text = when {
            single -> pluralStringResource(R.plurals.scan_single_count, vm.pending.sumOf { it.quantity }, vm.pending.sumOf { it.quantity }) +
                (vm.pending.count { it.status == nl.spoonpaco.ninegrid.ui.ItemStatus.RESOLVING }.takeIf { it > 0 }?.let { " · " + stringResource(R.string.scan_looking_up_n, it) } ?: "")
            sum.busy -> stringResource(R.string.looking_up)
            sum.waiting > 0 -> stringResource(R.string.scan_waiting)
            sum.allGood -> stringResource(R.string.scan_all_found, sum.found, sum.total - sum.empty) +
                (if (sum.empty > 0) " · " + pluralStringResource(R.plurals.scan_empty_slots, sum.empty, sum.empty) else "")
            else -> stringResource(R.string.scan_some_found, sum.found, sum.doubtful.coerceAtLeast(0))
        }
        Column(Modifier.weight(1f)) {
            Text(text, color = Color.White, fontSize = 13.sp, maxLines = if (single) 1 else 2, overflow = TextOverflow.Ellipsis)
            // Losse kaarten: de laatste drie, met een kruisje om een vergissing weg te halen.
            if (single) Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.pending.takeLast(3).reversed().forEach { p ->
                    androidx.compose.material3.InputChip(
                        selected = false, onClick = { vm.removePending(p.key) },
                        label = { Text((p.print?.name ?: p.query.name ?: "?") + if (p.quantity > 1) " ×${p.quantity}" else "", fontSize = 11.sp, color = Color.White, maxLines = 1) },
                        trailingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Close, stringResource(R.string.undo), tint = Color.White, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.height(26.dp),
                    )
                }
            }
            val st by vm.settings.collectAsState()
            if (st.showTiming && vm.lastReadMs > 0 && !sum.busy) {
                Text(
                    stringResource(R.string.scan_timing_steps, vm.lastReadMs / 1000.0, vm.lastRereadSlots, vm.lastNamesMs / 1000.0, vm.lastLookupMs / 1000.0, vm.lastImagesMs / 1000.0),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1,
                )
            }
        }
        if (!sum.busy || single) {
            if (single) {
                Button(onClick = onReview) { Text(stringResource(R.string.done)) }
            } else {
                OutlinedButton(onClick = onReview) { Text(stringResource(R.string.review_short), color = Color.White) }
                if (sum.allGood) {
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = onSave) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/**
 * Richtkader zonder lijnen: L-vormige hoekjes, en bij een blad een klein kruisje midden in elk vak
 * met het vaknummer (nog doorzichtiger) ernaast. Alles buiten het kader is gedimd.
 */
@Composable
private fun GuideOverlay(topPx: Float, bottomPx: Float, single: Boolean, corner: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val g = guideRect(size.width, size.height, topPx, bottomPx, single)
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, g.top))
        drawRect(dim, Offset(0f, g.bottom), Size(size.width, size.height - g.bottom))
        drawRect(dim, Offset(0f, g.top), Size(g.left, g.height))
        drawRect(dim, Offset(g.right, g.top), Size(size.width - g.right, g.height))
        corners(g, corner, 26.dp.toPx(), 3.5f.dp.toPx())
        if (!single) {
            val cw = g.width / 3f; val ch = g.height / 3f
            val arm = 7.dp.toPx()
            val num = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(80, 255, 255, 255); textSize = 11.dp.toPx(); isAntiAlias = true
            }
            for (r in 0..2) for (c in 0..2) {
                val cx = g.left + cw * (c + 0.5f); val cy = g.top + ch * (r + 0.5f)
                // Kruisje: donker randje onder, wit erop, half doorzichtig.
                for ((col, w) in listOf(Color.Black.copy(alpha = 0.35f) to 3.5f.dp.toPx(), Color.White.copy(alpha = 0.55f) to 1.5f.dp.toPx())) {
                    drawLine(col, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = w)
                    drawLine(col, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = w)
                }
                drawContext.canvas.nativeCanvas.drawText("${r * 3 + c + 1}", cx + arm + 3.dp.toPx(), cy - 3.dp.toPx(), num)
            }
        }
    }
}

/** Vier L-vormige hoekjes om een rechthoek. */
private fun DrawScope.corners(g: Area, color: Color, len: Float, width: Float) {
    val pts = listOf(
        Triple(g.left, g.top, 1f to 1f), Triple(g.right, g.top, -1f to 1f),
        Triple(g.left, g.bottom, 1f to -1f), Triple(g.right, g.bottom, -1f to -1f),
    )
    for ((x, y, d) in pts) {
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, y), Offset(x + d.first * len, y), strokeWidth = width + 2f)
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, y), Offset(x, y + d.second * len), strokeWidth = width + 2f)
        drawLine(color, Offset(x, y), Offset(x + d.first * len, y), strokeWidth = width)
        drawLine(color, Offset(x, y), Offset(x, y + d.second * len), strokeWidth = width)
    }
}
