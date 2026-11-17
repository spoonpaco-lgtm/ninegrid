package nl.spoonpaco.ninegrid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.domain.DeckQr
import nl.spoonpaco.ninegrid.ui.AppViewModel
import java.util.concurrent.Executors

/** Scant de QR-code(s) van een gedeeld deck. Is het deck compleet, dan gaat het naar het controlescherm, zoals een geplakte lijst. */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QrScanScreen(vm: AppViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val assembler = remember { DeckQr.Assembler() }
    var progress by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val partFmt = stringResource(R.string.qr_scan_part)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    var done by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown(); runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permitted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(executor) { proxy ->
                                val media = proxy.image
                                if (media == null || done) { proxy.close(); return@setAnalyzer }
                                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                    .addOnSuccessListener { codes ->
                                        for (c in codes) {
                                            val raw = c.rawValue ?: continue
                                            if (!DeckQr.isDeckCode(raw)) { wrong = true; continue }
                                            wrong = false
                                            if (assembler.add(raw)) {
                                                progress = if (assembler.of > 1) partFmt.replace("%1\$d", assembler.have.toString()).replace("%2\$d", assembler.of.toString()) else ""
                                                if (assembler.complete && !done) {
                                                    val text = assembler.text()
                                                    if (text != null) { done = true; vm.startPaste(text, null, true); onNext() }
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                when { wrong -> stringResource(R.string.qr_scan_wrong); progress.isNotEmpty() -> progress; else -> stringResource(R.string.qr_scan_hint) },
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_needed), textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }
}
