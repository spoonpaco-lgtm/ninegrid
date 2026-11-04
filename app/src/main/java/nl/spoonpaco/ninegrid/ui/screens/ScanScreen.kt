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
import nl.spoonpaco.ninegrid.domain.OcrLine
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

/** Leest alle regels met hun positie en verdeelt ze over de 9 vakken. */
private suspend fun readSlots(image: InputImage, width: Int, height: Int): List<SlotRead> {
    val text = recognize(image)
    val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
        val box = l.boundingBox ?: return@mapNotNull null
        OcrLine(l.text, box.exactCenterX(), box.exactCenterY())
    }
    return ScanParser.parse(lines, width, height)
}

private fun hasCamera(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: AppViewModel, onResult: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(hasCamera(context)) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
            .build()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val noCardsText = stringResource(R.string.scan_nothing_found)
    val failText = stringResource(R.string.scan_failed)

    fun handle(reads: List<SlotRead>) {
        working = false
        if (reads.none { it.lines.isNotEmpty() }) {
            error = noCardsText
        } else {
            vm.startScan(reads)
            onResult()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        scope.launch {
            runCatching {
                val img = InputImage.fromFilePath(context, uri)
                readSlots(img, img.width, img.height)
            }.onSuccess { handle(it) }.onFailure { working = false; error = failText }
        }
    }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torch by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_page), fontWeight = FontWeight.Bold) }, actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("add") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Set, map en blad bovenaan, zodat je bij meerdere bladen achter elkaar ziet waar je bent.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.scanSet, onValueChange = { vm.scanSet = it.take(6).uppercase() },
                    label = { Text(stringResource(R.string.set)) }, placeholder = { Text("MH1") },
                    singleLine = true, modifier = Modifier.weight(0.9f),
                )
                OutlinedTextField(
                    value = vm.binder, onValueChange = { vm.binder = it },
                    label = { Text(stringResource(R.string.binder_short)) },
                    singleLine = true, modifier = Modifier.weight(1.3f),
                )
                OutlinedTextField(
                    value = vm.page, onValueChange = { vm.page = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.page)) },
                    singleLine = true, modifier = Modifier.weight(0.8f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            }
            Text(stringResource(R.string.scan_fields_hint), fontSize = 11.sp, color = LocalNgExtra.current.subtle, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp))
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
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
                                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
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
                    GridOverlay()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.camera_needed), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
                    }
                }
                if (working) CircularProgressIndicator()
            }
            error?.let { Text(it, color = NgColors.Error, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center) }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            ) {
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !working) {
                    Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.from_photos))
                }
                FilledIconButton(
                    onClick = {
                        working = true
                        error = null
                        capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
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
                                scope.launch {
                                    runCatching { readSlots(InputImage.fromBitmap(bitmap, 0), bitmap.width, bitmap.height) }
                                        .onSuccess { handle(it) }
                                        .onFailure { working = false; error = failText }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                working = false
                                error = failText
                            }
                        })
                    },
                    enabled = permitted && !working,
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Filled.CameraAlt, stringResource(R.string.take_photo), modifier = Modifier.size(32.dp)) }
                IconButton(
                    onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                    enabled = permitted && camera?.cameraInfo?.hasFlashUnit() == true,
                ) { Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, stringResource(R.string.torch)) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }
}

/**
 * Richtkader in de vorm van een verzamelblad (3x3 kaarten van 63x88 mm), met alles erbuiten gedimd
 * en dunne rasterlijnen en vaknummers erin.
 */
@Composable
private fun GridOverlay() {
    val line = NgColors.Amber.copy(alpha = 0.35f)
    val frame = NgColors.Amber.copy(alpha = 0.8f)
    Canvas(Modifier.fillMaxSize()) {
        val ratio = 63f / 88f
        val maxW = size.width * 0.96f; val maxH = size.height * 0.96f
        val w = minOf(maxW, maxH * ratio); val h = w / ratio
        val left = (size.width - w) / 2f; val top = (size.height - h) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(dim, Offset(0f, top + h), androidx.compose.ui.geometry.Size(size.width, size.height - top - h))
        drawRect(dim, Offset(0f, top), androidx.compose.ui.geometry.Size(left, h))
        drawRect(dim, Offset(left + w, top), androidx.compose.ui.geometry.Size(size.width - left - w, h))
        for (i in 1..2) {
            drawLine(line, Offset(left + w * i / 3f, top), Offset(left + w * i / 3f, top + h), strokeWidth = 2f)
            drawLine(line, Offset(left, top + h * i / 3f), Offset(left + w, top + h * i / 3f), strokeWidth = 2f)
        }
        drawRect(frame, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(115, 255, 255, 255); textSize = 13.dp.toPx(); isAntiAlias = true; isFakeBoldText = true
        }
        for (r in 0..2) for (c in 0..2) {
            drawContext.canvas.nativeCanvas.drawText("${r * 3 + c + 1}", left + w * c / 3f + 6.dp.toPx(), top + h * r / 3f + 17.dp.toPx(), paint)
        }
    }
}
