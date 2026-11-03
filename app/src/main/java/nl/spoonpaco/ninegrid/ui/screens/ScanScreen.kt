package nl.spoonpaco.ninegrid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
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

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_page), fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.scan_explain), fontSize = 13.sp, color = LocalNgExtra.current.subtle, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f), contentAlignment = Alignment.Center) {
                if (permitted) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { view ->
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                                    runCatching {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
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
            error?.let { Text(it, color = NgColors.Error, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !working) {
                    Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.from_photos))
                }
                FilledIconButton(
                    onClick = {
                        working = true
                        error = null
                        capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val media = image.image
                                if (media == null) { image.close(); working = false; error = failText; return }
                                val rotation = image.imageInfo.rotationDegrees
                                val input = InputImage.fromMediaImage(media, rotation)
                                val (w, h) = if (rotation == 90 || rotation == 270) image.height to image.width else image.width to image.height
                                scope.launch {
                                    runCatching { readSlots(input, w, h) }
                                        .onSuccess { handle(it) }
                                        .onFailure { working = false; error = failText }
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                working = false
                                error = failText
                            }
                        })
                    },
                    enabled = permitted && !working,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Filled.CameraAlt, stringResource(R.string.take_photo), modifier = Modifier.size(32.dp)) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }
}

/** Raster van 3x3 over de camera, met de vaknummers, zodat het blad recht in beeld komt. */
@Composable
private fun GridOverlay() {
    val line = NgColors.Amber.copy(alpha = 0.85f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            for (i in 1..2) {
                drawLine(line, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 3f)
                drawLine(line, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 3f)
            }
            drawRect(line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
        Column(Modifier.fillMaxSize()) {
            for (r in 0..2) Row(Modifier.weight(1f)) {
                for (c in 0..2) Box(Modifier.weight(1f).fillMaxSize().padding(6.dp)) {
                    Text("${r * 3 + c + 1}", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
