package nl.spoonpaco.ninegrid.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Tekent een QR-code (zwart op wit, met rand), of null als de tekst er niet in past. */
fun qrBitmap(text: String, size: Int = 900): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2)
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
    Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
