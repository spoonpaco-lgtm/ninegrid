package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Snapshot
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.overview.HistoryChart
import nl.spoonpaco.ninegrid.ui.overview.Series
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private data class Counts(val m: Int, val r: Int, val u: Int, val c: Int, val o: Int, val value: Double) {
    val total get() = m + r + u + c + o
    operator fun plus(x: Counts) = Counts(m + x.m, r + x.r, u + x.u, c + x.c, o + x.o, value + x.value)
    companion object { val ZERO = Counts(0, 0, 0, 0, 0, 0.0) }
}

private fun count(list: List<EntryWithPrint>, currency: String): Counts {
    val real = list.filter { !it.entry.proxy }
    fun n(r: String) = real.filter { it.print?.rarity == r }.sumOf { it.entry.quantity }
    val m = n("mythic"); val r = n("rare"); val u = n("uncommon"); val c = n("common")
    val all = real.sumOf { it.entry.quantity }
    return Counts(m, r, u, c, all - m - r - u - c, real.sumOf { Money.total(it.entry, it.print, currency) })
}

private val LINE_COLORS = listOf(
    NgColors.Amber, Color(0xFF6EC1E4), Color(0xFF3FB37F), Color(0xFFD9412B), Color(0xFF8E6CC2), Color(0xFFD4AF37),
)
private const val MAX_LINES = 6

/** Zoals het tabblad Overzicht in de Sheet, plus het totaal bovenaan, de grafiek en waarde per rarity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(vm: AppViewModel, onSet: (String) -> Unit = {}) {
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val settings by vm.settings.collectAsState()
    val snaps by vm.snapshots.collectAsState()
    val moments by vm.moments.collectAsState()
    val cur = settings.currency
    val extra = LocalNgExtra.current
    var choosing by remember { mutableStateOf(false) }

    // Alleen opnieuw uitrekenen als de kaarten, decks of valuta veranderen.
    val sets = remember(all, cur) {
        all.filter { it.entry.deckId == null }.groupBy { it.print?.setCode ?: "?" }.toSortedMap().map { (k, v) -> k to count(v, cur) }
    }
    val setTotal = remember(sets) { sets.fold(Counts.ZERO) { a, b -> a + b.second } }
    val deckRows = remember(all, decks, cur) {
        val byDeck = all.filter { it.entry.deckId != null }.groupBy { it.entry.deckId }
        decks.map { d -> d.name to count(byDeck[d.id].orEmpty(), cur) }
    }
    val deckTotal = remember(deckRows) { deckRows.fold(Counts.ZERO) { a, b -> a + b.second } }
    val grand = setTotal + deckTotal

    // Namen van alle lijnen die je kunt kiezen.
    val labelTotal = stringResource(R.string.line_total)
    val labelSets = stringResource(R.string.total_sets)
    val labelDecks = stringResource(R.string.total_decks)
    val deckNames = decks.associate { "deck:${it.id}" to it.name }
    fun labelOf(key: String, snapLabel: String?): String = when {
        key == "total" -> labelTotal
        key == "sets" -> labelSets
        key == "decks" -> labelDecks
        key.startsWith("deck:") -> deckNames[key] ?: snapLabel ?: key
        else -> key.removePrefix("set:")
    }
    val latestLabel = snaps.groupBy { it.key }.mapValues { (_, v) -> v.maxBy { it.day }.label }
    val available = (listOf("total", "sets", "decks") + decks.map { "deck:${it.id}" } + sets.map { "set:${it.first}" } + latestLabel.keys).distinct()

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.overview), fontWeight = FontWeight.Bold)
                    Text(
                        if (settings.lastPriceUpdate > 0) stringResource(R.string.prices_updated_at, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(settings.lastPriceUpdate)))
                        else stringResource(R.string.prices_from_import),
                        style = MaterialTheme.typography.bodySmall, color = extra.subtle,
                    )
                }
            },
            actions = {
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("overview")
                IconButton(onClick = { vm.refreshPrices() }, enabled = !vm.busy) { Icon(Icons.Filled.Refresh, stringResource(R.string.update_prices)) }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }

            // Totaal bovenaan.
            item {
                val totalSnaps = snaps.filter { it.key == "total" }.sortedBy { it.day }
                val monthAgo = LocalDate.now().minusDays(30).toString()
                val base = totalSnaps.lastOrNull { it.day <= monthAgo } ?: totalSnaps.firstOrNull()?.takeIf { it.day < LocalDate.now().toString() }
                val diff = base?.let { grand.value - (if (cur == "USD") it.usd else it.eur) }
                // Alleen bij het openen van Overzicht, en hoogstens één keer per dag (of na een prijsupdate).
                androidx.compose.runtime.LaunchedEffect(cur, settings.lastPriceUpdate) { vm.computePriceChanges() }
                Panel {
                    Text(stringResource(R.string.total_collection), color = extra.subtle, fontSize = 13.sp)
                    Text(Money.format(grand.value, cur), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = extra.price)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, grand.total, grand.total), fontSize = 13.sp)
                        if (diff != null) {
                            val up = diff >= 0
                            Text(
                                "  ·  " + (if (up) "▲ " else "▼ ") + Money.format(kotlin.math.abs(diff), cur) + " " + stringResource(R.string.since_days, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(base.day), LocalDate.now()).toInt()),
                                fontSize = 13.sp, color = if (up) NgColors.Synced else NgColors.Error, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Koersverschil: alleen door prijzen, niet door kaarten die je toevoegde of wegdeed.
                    Text(stringResource(R.string.price_change_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (vm.priceChanges.isEmpty()) {
                        Text(stringResource(R.string.price_change_later), fontSize = 12.sp, color = extra.subtle)
                    } else vm.priceChanges.forEach { pc ->
                        val up = pc.diff >= 0
                        Row {
                            Text(stringResource(if (pc.days <= 7) R.string.price_change_week else R.string.price_change_month), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                (if (up) "▲ " else "▼ ") + Money.format(kotlin.math.abs(pc.diff), cur) + String.format(java.util.Locale.getDefault(), " (%+.1f%%)", pc.pct),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (up) NgColors.Synced else NgColors.Error,
                            )
                        }
                    }
                    Text(stringResource(R.string.price_change_explain), fontSize = 11.sp, color = extra.subtle)
                }
            }

            // Grafiek.
            item {
                val ranges = listOf("1D" to 1L, "1W" to 7L, "1M" to 30L, "3M" to 91L, "1Y" to 365L, "ALL" to 0L)
                val dayMs = 24L * 3600 * 1000
                val now = System.currentTimeMillis()
                val zone = java.time.ZoneId.systemDefault()
                fun noon(day: String) = LocalDate.parse(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                val firstMs = (snaps.minOfOrNull { noon(it.day) } ?: now).coerceAtMost(moments.minOfOrNull { it.ts } ?: now)
                val days = ranges.firstOrNull { it.first == settings.chartRange }?.second ?: 91L
                val from = if (days == 0L) firstMs else maxOf(firstMs, now - days * dayMs)
                val byGain = settings.chartMetric == "gain"
                val intraday = days in 1..31 && !byGain
                val lines = settings.chartLines.filter { it in available }.ifEmpty { listOf("total") }
                val byValue = settings.chartMetric != "count"
                fun isSheet(sn: Snapshot) = sn.label == nl.spoonpaco.ninegrid.data.ImportWorker.SHEET_LABEL || (sn.key == "total" && sn.usd == 0.0 && sn.eur > 0 && sn.gainEur == null)
                fun pick(eur: Double, usd: Double, count: Int) = if (byValue) (if (cur == "USD") usd else eur) else count.toDouble()
                val series = lines.mapIndexed { i, key ->
                    if (byGain) {
                        // Koers: optellen vanaf €0 op de eerste dag in beeld; alleen dagen waarvan de koers bekend is.
                        var sum = 0.0
                        val g = snaps.filter { it.key == key && it.gainEur != null && noon(it.day) >= from }.sortedBy { it.day }
                        val pts = g.mapIndexed { j, sn -> if (j > 0) sum += (if (cur == "USD") sn.gainUsd else sn.gainEur) ?: 0.0; noon(sn.day) to sum }
                        return@mapIndexed Series(key, labelOf(key, latestLabel[key]), LINE_COLORS[i % LINE_COLORS.size], pts)
                    }
                    val sheetUntil = snaps.filter { it.key == key && isSheet(it) }.maxOfOrNull { noon(it.day) }
                    val daily = snaps.filter { it.key == key }.map { noon(it.day) to pick(it.eur, it.usd, it.count) }
                    val pts = if (intraday) {
                        // Alle momenten van binnen de dag, aangevuld met dagstanden voor dagen zonder momenten (bijv. geïmporteerde historie).
                        val ms = moments.filter { it.key == key }.map { it.ts to pick(it.eur, it.usd, it.count) }
                        val momentDays = ms.map { java.time.Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate().toString() }.toSet()
                        ms + snaps.filter { it.key == key && it.day !in momentDays }.map { noon(it.day) to pick(it.eur, it.usd, it.count) }
                    } else daily
                    Series(key, labelOf(key, latestLabel[key]), LINE_COLORS[i % LINE_COLORS.size], pts.filter { it.first >= from }.sortedBy { it.first }, sheetUntil)
                }
                Panel {
                    Text(stringResource(R.string.chart_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (byGain && series.all { it.points.size < 2 }) {
                        Text(stringResource(R.string.chart_gain_empty), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(vertical = 8.dp))
                    } else if (series.all { it.points.size < 2 }) {
                        Text(stringResource(R.string.chart_empty), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    HistoryChart(
                        series = series, fromDay = from, toDay = maxOf(now, from + 60_000), showTime = days in 1..7,
                        formatY = { v -> if (byGain) (if (v > 0.005) "+" else if (v < -0.005) "−" else "") + compactMoney(kotlin.math.abs(v), cur) else if (byValue) compactMoney(v, cur) else v.toInt().toString() },
                        gapMs = 36L * 3600 * 1000,
                        axisColor = MaterialTheme.colorScheme.onSurface, textColor = extra.subtle,
                    )
                    if (byGain) Text(stringResource(R.string.chart_gain_explain), fontSize = 11.sp, color = extra.subtle)
                    else if (series.any { s -> s.sheetUntil != null && s.points.any { it.first <= s.sheetUntil } })
                        Text(stringResource(R.string.chart_sheet_note), fontSize = 11.sp, color = extra.subtle)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ranges.forEach { (k, _) ->
                            FilterChip(selected = settings.chartRange == k, onClick = { vm.setChartRange(k) }, label = { Text(rangeLabel(k)) })
                        }
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = byValue && !byGain, onClick = { vm.setChartMetric("value") }, label = { Text(stringResource(R.string.chart_value)) })
                        FilterChip(selected = !byValue, onClick = { vm.setChartMetric("count") }, label = { Text(stringResource(R.string.chart_cards)) })
                        FilterChip(selected = byGain, onClick = { vm.setChartMetric("gain") }, label = { Text(stringResource(R.string.chart_gain)) })
                    }
                    // Legenda: tik om een lijn uit te zetten.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        series.forEach { s ->
                            InputChip(
                                selected = true,
                                onClick = { vm.setChartLines(lines - s.key) },
                                label = { Text(s.label, maxLines = 1) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(s.color)) },
                                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
                            )
                        }
                        TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.chart_choose_lines)) }
                    }
                }
            }

            // Waarde per rarity.
            item {
                val real = all.filter { !it.entry.proxy }
                fun v(r: String?) = real.filter { if (r == null) it.print?.rarity !in listOf("mythic", "rare", "uncommon", "common") else it.print?.rarity == r }
                    .sumOf { Money.total(it.entry, it.print, cur) }
                val parts = listOf(
                    Triple("M", NgColors.Mythic, v("mythic")), Triple("R", NgColors.Rare, v("rare")),
                    Triple("U", NgColors.Uncommon, v("uncommon")), Triple("C", NgColors.Common, v("common")), Triple("O", NgColors.Special, v(null)),
                )
                val sum = parts.sumOf { it.third }
                Panel {
                    Text(stringResource(R.string.value_per_rarity), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        if (sum > 0) parts.filter { it.third > 0 }.forEach { (_, c, value) ->
                            Box(Modifier.weight((value / sum).toFloat()).fillMaxHeight().background(c))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        parts.forEach { (l, c, value) ->
                            val pct = if (sum > 0) Math.round(value / sum * 100) else 0
                            Text("$l $pct%", color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Sets en decks, twee regels per rij.
            item { SectionTitle(stringResource(R.string.set)) }
            items(sets, key = { "s_" + it.first }) { (k, c) -> TwoLineRow(k, c, cur, onClick = { onSet(k) }) }
            item { TwoLineRow(labelSets, setTotal, cur, bold = true) }
            item { SectionTitle(stringResource(R.string.decks)) }
            items(deckRows, key = { "d_" + it.first + it.second.total }) { (k, c) -> TwoLineRow(k, c, cur) }
            item { TwoLineRow(labelDecks, deckTotal, cur, bold = true) }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                TwoLineRow(stringResource(R.string.total_collection), grand, cur, bold = true, highlight = true)
                Text(stringResource(R.string.price_explain), fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (choosing) {
        LineChooser(
            options = available.map { it to labelOf(it, latestLabel[it]) },
            selected = settings.chartLines.filter { it in available }.ifEmpty { listOf("total") },
            onDone = { vm.setChartLines(it); choosing = false },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun rangeLabel(k: String) = when (k) {
    "1D" -> stringResource(R.string.range_1d); "1W" -> stringResource(R.string.range_1w)
    "1M" -> stringResource(R.string.range_1m); "3M" -> stringResource(R.string.range_3m)
    "1Y" -> stringResource(R.string.range_1y); else -> stringResource(R.string.range_all)
}

private fun compactMoney(v: Double, cur: String): String {
    val sym = if (cur == "USD") "$" else "€"
    return when {
        v >= 10_000 -> "$sym${Math.round(v / 1000)}k"
        v >= 1_000 -> "$sym" + String.format(java.util.Locale.US, "%.1fk", v / 1000)
        else -> "$sym${Math.round(v)}"
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalNgExtra.current.cardSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
}

/** Optie A: naam en waarde op de eerste regel, aantallen per rarity klein eronder. */
@Composable
private fun TwoLineRow(name: String, c: Counts, currency: String, bold: Boolean = false, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    val extra = LocalNgExtra.current
    val base = if (highlight) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier
    val mod = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(mod.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(Money.format(c.value, currency), fontWeight = FontWeight.Bold, color = extra.price)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("M" to c.m to NgColors.Mythic, "R" to c.r to NgColors.Rare, "U" to c.u to NgColors.Uncommon, "C" to c.c to NgColors.Common, "O" to c.o to NgColors.Special)
                .forEach { (lv, col) -> Text("${lv.first} ${lv.second}", color = col, fontSize = 12.sp) }
            Text("· " + androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, c.total, c.total), fontSize = 12.sp, color = extra.subtle)
        }
    }
}

/** Lijnen kiezen voor de grafiek, met zoeken; hoogstens 6 tegelijk. */
@Composable
private fun LineChooser(options: List<Pair<String, String>>, selected: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(selected) }
    var q by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chart_choose_lines)) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(stringResource(R.string.search_hint)) })
                if (warn) Text(stringResource(R.string.chart_max_lines, MAX_LINES), color = NgColors.Error, fontSize = 12.sp)
                LazyColumn(Modifier.height(320.dp)) {
                    items(options.filter { q.isBlank() || it.second.contains(q.trim(), ignoreCase = true) }, key = { it.first }) { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            if (key in chosen) { chosen = chosen - key; warn = false }
                            else if (chosen.size >= MAX_LINES) warn = true
                            else chosen = chosen + key
                        }) {
                            Checkbox(checked = key in chosen, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen.ifEmpty { listOf("total") }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
