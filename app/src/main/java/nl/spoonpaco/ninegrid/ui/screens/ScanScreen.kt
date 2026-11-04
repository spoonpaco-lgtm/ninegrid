package nl.spoonpaco.ninegrid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.domain.Area
import nl.spoonpaco.ninegrid.domain.OcrLine
import nl.spoonpaco.ninegrid.domain.ScanResult
import nl.spoonpaco.ninegrid.domain.ScanParser
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

/** Leest alle regels met hun positie en verdeelt ze over de 9 vakken. Met crop telt alleen tekst binnen het kader. */
private suspend fun readSlots(image: InputImage, width: Int, height: Int, crop: Area? = null): ScanResult {
    val text = recognize(image)
    val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
        val box = l.boundingBox ?: return@mapNotNull null
        OcrLine(l.text, box.exactCenterX(), box.exactCenterY())
    }
    return ScanParser.analyze(lines, width, height, crop)
}

// Ruimte (px) die de balken boven en onder het beeld innemen; het kader valt daartussen.
private var guideTop = 0f
private var guideBottom = 0f

/** Het richtkader binnen een beeld van w x h, tussen de balken: de verhouding van 3x3 kaarten (63x88 mm). */
private fun guideRect(w: Float, h: Float, topPx: Float = guideTop, bottomPx: Float = guideBottom): Area {
    val ratio = 63f / 88f
    val availTop = topPx + 8f; val availH = (h - topPx - bottomPx - 16f).coerceAtLeast(h * 0.3f)
    val gw = minOf(w * 0.94f, availH * ratio); val gh = gw / ratio
    val left = (w - gw) / 2f; val top = availTop + (availH - gh) / 2f
    return Area(left, top, left + gw, top + gh)
}

/** Zet het kader op het scherm om naar pixels op de foto (het camerabeeld vult het scherm, midden uitgesneden). */
private fun guideOnImage(boxW: Float, boxH: Float, imgW: Int, imgH: Int): Area {
    val g = guideRect(boxW, boxH)
    val scale = maxOf(boxW / imgW, boxH / imgH)
    val dx = (boxW - imgW * scale) / 2f; val dy = (boxH - imgH * scale) / 2f
    val padX = g.width * 0.03f; val padY = g.height * 0.03f
    return Area(
        ((g.left - padX - dx) / scale).coerceAtLeast(0f), ((g.top - padY - dy) / scale).coerceAtLeast(0f),
        ((g.right + padX - dx) / scale).coerceAtMost(imgW.toFloat()), ((g.bottom + padY - dy) / scale).coerceAtMost(imgH.toFloat()),
    )
}

/** Scherpte van (een deel van) de foto: variantie van de Laplace-filter op een verkleinde grijze versie. */
private fun sharpness(bmp: android.graphics.Bitmap, area: Area?): Double {
    val a = area ?: Area(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
    val cropped = android.graphics.Bitmap.createBitmap(bmp, a.left.toInt(), a.top.toInt(), a.width.toInt().coerceAtLeast(1), a.height.toInt().coerceAtLeast(1))
    val w = 600; val h = (cropped.height * w / cropped.width.toFloat()).toInt().coerceAtLeast(3)
    val small = android.graphics.Bitmap.createScaledBitmap(cropped, w, h, true)
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

/** Waarom een foto waarschijnlijk niet goed is, of null. */
private enum class Quality { BLURRY, TOO_FAR }

private fun hasCamera(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: AppViewModel, onBack: () -> Unit, onResult: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(hasCamera(context)) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build(),
            )
            .build()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val noCardsText = stringResource(R.string.scan_nothing_found)
    val failText = stringResource(R.string.scan_failed)

    var quality by remember { mutableStateOf<Pair<Quality, List<SlotRead>>?>(null) }
    var boxW by remember { mutableStateOf(0f) }
    var boxH by remember { mutableStateOf(0f) }

    fun handle(result: ScanResult, sharp: Double, fromCamera: Boolean) {
        working = false
        val reads = result.slots
        val complete = reads.count { it.complete || it.name != null }
        val problem = when {
            complete >= 7 -> null
            sharp < 35.0 -> Quality.BLURRY
            fromCamera && result.coverage < 0.35f -> Quality.TOO_FAR
            else -> null
        }
        when {
            problem != null -> quality = problem to reads
            reads.none { it.lines.isNotEmpty() } -> error = noCardsText
            else -> { vm.startScan(reads); onResult() }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        scope.launch {
            runCatching {
                val bmp = context.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
                val img = InputImage.fromFilePath(context, uri)
                val sharp = runCatching { if (img.width == bmp.width && img.height == bmp.height) sharpness(bmp, null) else 100.0 }.getOrDefault(100.0)
                readSlots(img, img.width, img.height) to sharp
            }.onSuccess { (r, sharp) -> handle(r, sharp, fromCamera = false) }.onFailure { working = false; error = failText }
        }
    }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var editFields by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Ruimte boven en onder die door de balken op het beeld wordt ingenomen; het kader valt daartussen.
    val topBarPx = with(density) { 64.dp.toPx() } + androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    val bottomBarPx = with(density) { (132 + 26).dp.toPx() } + androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
    guideTop = topBarPx; guideBottom = bottomBarPx

    fun takePhoto() {
                working = true
                error = null
                val executor = ContextCompat.getMainExecutor(context)
                fun shoot() = capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // ImageCapture levert JPEG; ML Kit wil dan een bitmap (fromMediaImage werkt alleen met YUV).
                        val bitmap = runCatching {
                            val raw = image.toBitmap()
                            val rotation = image.imageInfo.rotationDegrees
                            if (rotation == 0) raw else android.graphics.Bitmap.createBitmap(
                                raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true,
                            )
                        }.getOrNull()
                        image.close()
                        if (bitmap == null) { working = false; error = failText; return }
                        val crop = if (boxW > 0 && boxH > 0) guideOnImage(boxW, boxH, bitmap.width, bitmap.height) else null
                        scope.launch {
                            runCatching {
                                val sharp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { sharpness(bitmap, crop) }.getOrDefault(100.0) }
                                readSlots(InputImage.fromBitmap(bitmap, 0), bitmap.width, bitmap.height, crop) to sharp
                            }
                                .onSuccess { (r, sharp) -> handle(r, sharp, fromCamera = true) }
                                .onFailure { working = false; error = failText }
                        }
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
                            val provider = future.get()
                            // Zelfde verhouding als de foto, zodat het kader op het scherm en op de foto hetzelfde stuk is.
                            val preview = Preview.Builder()
                                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                                .build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            runCatching {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                            }
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
            GridOverlay(topBarPx, bottomBarPx)
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_needed), textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
            }
        }
        if (working) CircularProgressIndicator(Modifier.align(Alignment.Center))

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
            androidx.compose.material3.AssistChip(
                onClick = { editFields = true },
                label = { Text(if (parts.isEmpty()) stringResource(R.string.scan_fields_empty) else parts.joinToString(" · "), maxLines = 1, color = Color.White) },
                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f),
            )
            Box { nl.spoonpaco.ninegrid.ui.guide.HelpButton("add") }
        }

        // Onderband: foto's, ontspanknop, zaklamp.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let { Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), textAlign = TextAlign.Center) }
            Row(
                Modifier.fillMaxWidth().height(if (error != null) 100.dp else 132.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !working, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) { Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.from_photos)) }
                FilledIconButton(
                    onClick = { takePhoto() },
                    enabled = permitted && !working,
                    modifier = Modifier.size(78.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Filled.CameraAlt, stringResource(R.string.take_photo), modifier = Modifier.size(36.dp)) }
                FilledIconButton(
                    onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                    enabled = permitted && camera?.cameraInfo?.hasFlashUnit() == true, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) { Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, stringResource(R.string.torch)) }
            }
        }
    }

    if (editFields) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { editFields = false }) {
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

    quality?.let { (q, reads) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { quality = null },
            title = { Text(stringResource(if (q == Quality.BLURRY) R.string.scan_blurry_title else R.string.scan_far_title)) },
            text = { Text(stringResource(if (q == Quality.BLURRY) R.string.scan_blurry_body else R.string.scan_far_body)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { quality = null }) { Text(stringResource(R.string.scan_retry)) } },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { quality = null; vm.startScan(reads); onResult() }) { Text(stringResource(R.string.scan_view_anyway)) }
            },
        )
    }
    DisposableEffect(Unit) { onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }
}

/**
 * Richtkader in de vorm van een verzamelblad, met alles erbuiten gedimd. Geen rasterlijnen: de randen
 * tussen de hoesjes verschillen per binder, dus lijnen zouden door de kaarten lopen. Alleen vaknummers in de hoeken.
 */
@Composable
private fun GridOverlay(topPx: Float, bottomPx: Float) {
    val frame = NgColors.Amber.copy(alpha = 0.85f)
    val hint = stringResource(R.string.scan_fill_frame)
    Canvas(Modifier.fillMaxSize()) {
        val g = guideRect(size.width, size.height, topPx, bottomPx)
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, g.top))
        drawRect(dim, Offset(0f, g.bottom), androidx.compose.ui.geometry.Size(size.width, size.height - g.bottom))
        drawRect(dim, Offset(0f, g.top), androidx.compose.ui.geometry.Size(g.left, g.height))
        drawRect(dim, Offset(g.right, g.top), androidx.compose.ui.geometry.Size(size.width - g.right, g.height))
        drawRect(frame, Offset(g.left, g.top), androidx.compose.ui.geometry.Size(g.width, g.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        val num = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(120, 255, 255, 255); textSize = 13.dp.toPx(); isAntiAlias = true; isFakeBoldText = true
        }
        for (r in 0..2) for (c in 0..2) {
            drawContext.canvas.nativeCanvas.drawText("${r * 3 + c + 1}", g.left + g.width * c / 3f + 6.dp.toPx(), g.top + g.height * r / 3f + 17.dp.toPx(), num)
        }
        val hp = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 255, 255, 255); textSize = 12.dp.toPx(); isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(hint, size.width / 2f, g.bottom + 16.dp.toPx(), hp)
    }
}
