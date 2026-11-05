package nl.spoonpaco.ninegrid.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Eén lijn: punten met tijd (millis) en waarde. */
data class Series(
    val key: String, val label: String, val color: Color, val points: List<Pair<Long, Double>>,
    /** Punten tot en met dit tijdstip komen uit de Sheet-historie en krijgen een lichtere tint. */
    val sheetUntil: Long? = null,
)

/**
 * Eenvoudige lijngrafiek, zelf getekend. Tik of veeg over de grafiek om de waarden van die dag te zien.
 */
@Composable
fun HistoryChart(
    series: List<Series>,
    fromDay: Long,
    toDay: Long,
    /** Toon tijden in plaats van datums (bij een korte periode). */
    showTime: Boolean = false,
    formatY: (Double) -> String,
    axisColor: Color,
    textColor: Color,
    /** Meer dan dit tussen twee punten (buiten de Sheet-historie) is een gat: stippellijn. 0 = nooit. */
    gapMs: Long = 0,
    modifier: Modifier = Modifier,
) {
    var selected by remember(series, fromDay) { mutableStateOf<Long?>(null) }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val dateFmt = remember(showTime) {
        if (showTime) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    }
    fun label(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).format(dateFmt)
    val allPoints = series.flatMap { it.points }
    val rawMax = allPoints.maxOfOrNull { it.second } ?: 1.0
    val rawMin = minOf(0.0, allPoints.minOfOrNull { it.second } ?: 0.0)
    val maxY = if (rawMax <= 0 && rawMin >= 0) 1.0 else if (rawMax <= 0) 0.0 else rawMax * 1.08
    val minY = if (rawMin < 0) rawMin * 1.08 else 0.0
    val rangeY = (maxY - minY).let { if (it <= 0) 1.0 else it }
    val span = (toDay - fromDay).coerceAtLeast(1)

    Canvas(
        modifier.fillMaxWidth().height(200.dp)
            .pointerInput(series, fromDay, toDay) {
                fun pick(x: Float) {
                    val left = 52.dp.toPx(); val w = size.width - left - 8.dp.toPx()
                    val day = fromDay + (((x - left) / w).coerceIn(0f, 1f).toDouble() * span).toLong()
                    // De dichtstbijzijnde dag waarop er een meting is.
                    selected = allPoints.map { it.first }.distinct().minByOrNull { kotlin.math.abs(it - day) }
                }
                detectTapGestures { pick(it.x) }
            }
            .pointerInput(series, fromDay, toDay) {
                detectDragGestures { change, _ ->
                    val left = 52.dp.toPx(); val w = size.width - left - 8.dp.toPx()
                    val day = fromDay + (((change.position.x - left) / w).coerceIn(0f, 1f).toDouble() * span).toLong()
                    selected = allPoints.map { it.first }.distinct().minByOrNull { kotlin.math.abs(it - day) }
                }
            },
    ) {
        val left = 52.dp.toPx(); val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx(); val bottom = size.height - 22.dp.toPx()
        val w = right - left; val h = bottom - top
        fun x(day: Long) = left + w * ((day - fromDay).toDouble() / span).toFloat()
        fun y(v: Double) = bottom - h * ((v - minY) / rangeY).toFloat()
        val paint = android.graphics.Paint().apply { color = textColor.toArgb(); textSize = 10.dp.toPx(); isAntiAlias = true }

        // Hulplijnen met waarden.
        if (minY < 0) drawLine(axisColor.copy(alpha = 0.6f), Offset(left, y(0.0)), Offset(right, y(0.0)), strokeWidth = 1.5f)
        for (i in 0..3) {
            val v = minY + rangeY * i / 3.0
            val yy = y(v)
            drawLine(axisColor.copy(alpha = 0.25f), Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(formatY(v), left - 4.dp.toPx(), yy + 3.dp.toPx(), paint)
        }
        // Begin- en einddatum.
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(label(fromDay), left, size.height - 4.dp.toPx(), paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(label(toDay), right, size.height - 4.dp.toPx(), paint)

        series.forEach { s ->
            val pts = s.points.sortedBy { it.first }
            if (pts.size == 1) {
                drawCircle(s.color, 4.dp.toPx(), Offset(x(pts[0].first), y(pts[0].second)))
            } else if (pts.size > 1) {
                // Per stuk tekenen: Sheet-historie lichter, gaten als stippellijn, de rest doorgetrokken.
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                var path = Path(); var kind = -1
                fun flush() {
                    if (kind < 0) return
                    drawPath(path, if (kind == 1) s.color.copy(alpha = 0.4f) else s.color,
                        style = Stroke(width = 2.5f.dp.toPx(), pathEffect = if (kind == 2) dash else null))
                }
                for (i in 1 until pts.size) {
                    val (d0, v0) = pts[i - 1]; val (d1, v1) = pts[i]
                    val k = when {
                        s.sheetUntil != null && d1 <= s.sheetUntil -> 1
                        gapMs > 0 && d1 - d0 > gapMs -> 2
                        else -> 0
                    }
                    if (k != kind) { flush(); path = Path(); path.moveTo(x(d0), y(v0)); kind = k }
                    path.lineTo(x(d1), y(v1))
                }
                flush()
            }
        }

        // Geselecteerde dag: lijn en kader met de waarden.
        selected?.let { day ->
            val xx = x(day)
            drawLine(axisColor.copy(alpha = 0.6f), Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.5f)
            val rows = listOf(label(day) to Color.Unspecified) +
                series.mapNotNull { s -> s.points.firstOrNull { it.first == day }?.let { "${s.label}: ${formatY(it.second)}" to s.color } }
            val tp = android.graphics.Paint().apply { textSize = 11.dp.toPx(); isAntiAlias = true }
            val lineH = 14.dp.toPx()
            val boxW = rows.maxOf { tp.measureText(it.first) } + 16.dp.toPx()
            val boxH = rows.size * lineH + 8.dp.toPx()
            val bx = if (xx + boxW + 8.dp.toPx() < right) xx + 6.dp.toPx() else xx - boxW - 6.dp.toPx()
            drawRoundRect(Color.Black.copy(alpha = 0.78f), Offset(bx, top), Size(boxW, boxH), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            rows.forEachIndexed { i, (t, c) ->
                tp.color = if (c == Color.Unspecified) android.graphics.Color.WHITE else c.toArgb()
                drawContext.canvas.nativeCanvas.drawText(t, bx + 8.dp.toPx(), top + 4.dp.toPx() + lineH * (i + 1) - 3.dp.toPx(), tp)
            }
            series.forEach { s -> s.points.firstOrNull { it.first == day }?.let { drawCircle(s.color, 3.5f.dp.toPx(), Offset(xx, y(it.second))) } }
        }
    }
}
