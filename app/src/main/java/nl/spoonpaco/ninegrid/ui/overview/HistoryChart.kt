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
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Eén lijn: punten met tijd (millis) en waarde. */
data class Series(
    val key: String, val label: String, val color: Color, val points: List<Pair<Long, Double>>,
    /** Punten tot en met dit tijdstip komen uit de Sheet-historie en krijgen een lichtere tint. */
    val sheetUntil: Long? = null,
)

/** Onder- en bovengrens van een as die de waarden volgt (niet vanaf 0), met wat ruimte erboven en eronder. */
fun fitAxis(values: List<Double>): Pair<Double, Double> {
    if (values.isEmpty()) return 0.0 to 1.0
    val lo = values.min(); val hi = values.max()
    // Alles gelijk (of bijna): een band eromheen, zodat de lijn in het midden loopt.
    val spread = hi - lo
    val pad = if (spread < 1e-9) maxOf(kotlin.math.abs(hi) * 0.05, 1.0) else spread * 0.12
    var min = lo - pad; val max = hi + pad
    // Niet onder nul zakken als de waarden zelf niet negatief zijn.
    if (lo >= 0 && min < 0) min = 0.0
    return min to max
}

/**
 * Eenvoudige lijngrafiek, zelf getekend. Tik of veeg over de grafiek om de waarden van die dag te zien.
 * De verticale as volgt de laagste en hoogste waarde in beeld, zodat ook kleine veranderingen zichtbaar zijn.
 * Met [secondary] komt er een tweede lijn bij met een eigen as rechts (bijvoorbeeld het aantal kaarten).
 */
@Composable
fun HistoryChart(
    series: List<Series>,
    fromDay: Long,
    toDay: Long,
    /** Toon tijden in plaats van datums (bij een korte periode). */
    showTime: Boolean = false,
    /** Waarde in het kader bij een geselecteerde dag. */
    formatY: (Double) -> String,
    axisColor: Color,
    textColor: Color,
    /** Datum (en tijd) onder de grafiek en in het kader, volgens de instellingen. */
    formatX: ((Long, Boolean) -> String)? = null,
    /** Meer dan dit tussen twee punten (buiten de Sheet-historie) is een gat: stippellijn. 0 = nooit. */
    gapMs: Long = 0,
    modifier: Modifier = Modifier,
    /** Getal langs de as: waarde en de stap tussen twee hulplijnen (voor het aantal decimalen). Standaard [formatY]. */
    formatAxis: ((Double, Double) -> String)? = null,
    /** Tweede lijn met een eigen as rechts. */
    secondary: Series? = null,
    formatSecondary: (Double) -> String = { it.toInt().toString() },
) {
    var selected by remember(series, fromDay, secondary) { mutableStateOf<Long?>(null) }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val dateFmt = remember(showTime) {
        if (showTime) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    }
    fun label(ms: Long) = formatX?.invoke(ms, showTime) ?: java.time.Instant.ofEpochMilli(ms).atZone(zone).format(dateFmt)
    val allPoints = series.flatMap { it.points }
    val (minY, maxY) = fitAxis(allPoints.map { it.second })
    val rangeY = (maxY - minY).let { if (it <= 0) 1.0 else it }
    val second = secondary?.takeIf { it.points.isNotEmpty() }
    val (minY2, maxY2) = fitAxis(second?.points.orEmpty().map { it.second })
    val rangeY2 = (maxY2 - minY2).let { if (it <= 0) 1.0 else it }
    val span = (toDay - fromDay).coerceAtLeast(1)
    val pickPoints = allPoints + second?.points.orEmpty()
    val rightPadDp = if (second != null) 38 else 8

    Canvas(
        modifier.fillMaxWidth().height(200.dp)
            .pointerInput(series, fromDay, toDay, secondary) {
                fun pick(x: Float) {
                    val left = 52.dp.toPx(); val w = size.width - left - rightPadDp.dp.toPx()
                    val day = fromDay + (((x - left) / w).coerceIn(0f, 1f).toDouble() * span).toLong()
                    // De dichtstbijzijnde dag waarop er een meting is.
                    selected = pickPoints.map { it.first }.distinct().minByOrNull { kotlin.math.abs(it - day) }
                }
                detectTapGestures { pick(it.x) }
            }
            .pointerInput(series, fromDay, toDay, secondary) {
                detectDragGestures { change, _ ->
                    val left = 52.dp.toPx(); val w = size.width - left - rightPadDp.dp.toPx()
                    val day = fromDay + (((change.position.x - left) / w).coerceIn(0f, 1f).toDouble() * span).toLong()
                    selected = pickPoints.map { it.first }.distinct().minByOrNull { kotlin.math.abs(it - day) }
                }
            },
    ) {
        val left = 52.dp.toPx(); val right = size.width - rightPadDp.dp.toPx()
        val top = 8.dp.toPx(); val bottom = size.height - 22.dp.toPx()
        val w = right - left; val h = bottom - top
        fun x(day: Long) = left + w * ((day - fromDay).toDouble() / span).toFloat()
        fun y(v: Double) = bottom - h * ((v - minY) / rangeY).toFloat()
        fun y2(v: Double) = bottom - h * ((v - minY2) / rangeY2).toFloat()
        val paint = android.graphics.Paint().apply { color = textColor.toArgb(); textSize = 10.sp.toPx(); isAntiAlias = true }
        // Met twee assen krijgen de getallen de kleur van hun eigen lijn, zodat duidelijk is wat bij wat hoort.
        val leftColor = if (second != null && series.size == 1) series[0].color.toArgb() else textColor.toArgb()

        // Hulplijnen met waarden. De nullijn alleen als nul in beeld is (bijvoorbeeld bij prijsbeweging).
        if (minY < 0 && maxY > 0) drawLine(axisColor.copy(alpha = 0.6f), Offset(left, y(0.0)), Offset(right, y(0.0)), strokeWidth = 1.5f)
        for (i in 0..3) {
            val v = minY + rangeY * i / 3.0
            val yy = y(v)
            drawLine(axisColor.copy(alpha = 0.25f), Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            paint.color = leftColor
            drawContext.canvas.nativeCanvas.drawText(formatAxis?.invoke(v, rangeY / 3.0) ?: formatY(v), left - 4.dp.toPx(), yy + 3.dp.toPx(), paint)
            if (second != null) {
                paint.textAlign = android.graphics.Paint.Align.LEFT
                paint.color = second.color.toArgb()
                drawContext.canvas.nativeCanvas.drawText(formatSecondary(minY2 + rangeY2 * i / 3.0), right + 4.dp.toPx(), yy + 3.dp.toPx(), paint)
            }
        }
        paint.color = textColor.toArgb()
        // Begin- en einddatum.
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(label(fromDay), left, size.height - 4.dp.toPx(), paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(label(toDay), right, size.height - 4.dp.toPx(), paint)

        // De tweede lijn eerst (dunner), zodat de hoofdlijn erbovenop ligt.
        second?.let { s ->
            val pts = s.points.sortedBy { it.first }
            if (pts.size == 1) drawCircle(s.color, 3.dp.toPx(), Offset(x(pts[0].first), y2(pts[0].second)))
            else {
                val path = Path()
                pts.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y2(v)) else path.lineTo(x(d), y2(v)) }
                drawPath(path, s.color, style = Stroke(width = 1.8f.dp.toPx()))
            }
        }

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
                series.mapNotNull { s -> s.points.firstOrNull { it.first == day }?.let { "${s.label}: ${formatY(it.second)}" to s.color } } +
                listOfNotNull(second?.let { s -> s.points.firstOrNull { it.first == day }?.let { "${s.label}: ${formatSecondary(it.second)}" to s.color } })
            val tp = android.graphics.Paint().apply { textSize = 11.sp.toPx(); isAntiAlias = true }
            val lineH = 14.sp.toPx()
            val boxW = rows.maxOf { tp.measureText(it.first) } + 16.dp.toPx()
            val boxH = rows.size * lineH + 8.dp.toPx()
            val bx = if (xx + boxW + 8.dp.toPx() < right) xx + 6.dp.toPx() else (xx - boxW - 6.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(Color.Black.copy(alpha = 0.78f), Offset(bx, top), Size(boxW, boxH), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            rows.forEachIndexed { i, (t, c) ->
                tp.color = if (c == Color.Unspecified) android.graphics.Color.WHITE else c.toArgb()
                drawContext.canvas.nativeCanvas.drawText(t, bx + 8.dp.toPx(), top + 4.dp.toPx() + lineH * (i + 1) - 3.dp.toPx(), tp)
            }
            series.forEach { s -> s.points.firstOrNull { it.first == day }?.let { drawCircle(s.color, 3.5f.dp.toPx(), Offset(xx, y(it.second))) } }
            second?.let { s -> s.points.firstOrNull { it.first == day }?.let { drawCircle(s.color, 3f.dp.toPx(), Offset(xx, y2(it.second))) } }
        }
    }
}
