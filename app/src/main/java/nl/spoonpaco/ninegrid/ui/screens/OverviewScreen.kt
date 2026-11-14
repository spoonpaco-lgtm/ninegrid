package nl.spoonpaco.ninegrid.ui.screens

import nl.spoonpaco.ninegrid.ui.theme.NgSpace
import nl.spoonpaco.ninegrid.ui.theme.NgText
import nl.spoonpaco.ninegrid.ui.theme.NgShape
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
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
fun OverviewScreen(vm: AppViewModel, onSet: (String) -> Unit = {}, onSets: () -> Unit = {}, onDecks: () -> Unit = {}, onFriend: (String) -> Unit = {}, onShare: () -> Unit = {}, onBoosters: () -> Unit = {}) {
    val boosterList by vm.boosters.collectAsState()
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val settings by vm.settings.collectAsState()
    val snaps by vm.snapshots.collectAsState()
    val moments by vm.moments.collectAsState()
    val latest by vm.latestPerKey.collectAsState()
    val cur = settings.currency
    val extra = LocalNgExtra.current
    var choosing by remember { mutableStateOf(false) }
    var pieMode by rememberSaveable { mutableStateOf(false) }
    var pieSplit by rememberSaveable { mutableStateOf("set") }

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
    val latestLabel = remember(latest) { latest.associate { it.key to it.label } }
    val available = (listOf("total", "sets", "decks") + decks.map { "deck:${it.id}" } + sets.map { "set:${it.first}" }).distinct()
    fun setLabel(code: String): String {
        val n = vm.setInfo[code.lowercase()]?.name ?: vm.setInfo[code]?.name
        return if (n.isNullOrBlank()) code else "$code · $n"
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.overview), fontWeight = FontWeight.Bold)
                    Text(
                        if (settings.lastPriceUpdate > 0) stringResource(R.string.prices_updated_at, nl.spoonpaco.ninegrid.domain.Dates.dateTime(settings.lastPriceUpdate, settings.dateStyle))
                        else stringResource(R.string.prices_from_import),
                        style = MaterialTheme.typography.bodySmall, color = extra.subtle,
                    )
                }
            },
            actions = {
                nl.spoonpaco.ninegrid.ui.components.PrivacyButton(vm)
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("overview")
                IconButton(onClick = { vm.refreshPrices() }, enabled = !vm.busy) { Icon(Icons.Filled.Refresh, stringResource(R.string.update_prices)) }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = NgSpace.screen)) }

            // Totaal bovenaan.
            item {
                val totalSnaps = snaps.filter { it.key == "total" }.sortedBy { it.day }
                val monthAgo = LocalDate.now().minusDays(30).toString()
                val base = totalSnaps.lastOrNull { it.day <= monthAgo } ?: totalSnaps.firstOrNull()?.takeIf { it.day < LocalDate.now().toString() }
                val diff = base?.let { grand.value - (if (cur == "USD") it.usd else it.eur) }
                // Alleen bij het openen van Overzicht, en hoogstens één keer per dag (of na een prijsupdate).
                androidx.compose.runtime.LaunchedEffect(cur, settings.lastPriceUpdate) { vm.computePriceChanges(); vm.computeWinner() }
                Panel {
                    Text(stringResource(R.string.total_collection), color = extra.subtle, fontSize = NgText.body)
                    Text(nl.spoonpaco.ninegrid.ui.components.money(grand.value, cur), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = extra.price, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, grand.total, grand.total), fontSize = NgText.body)
                        if (diff != null) {
                            val up = diff >= 0
                            Text(
                                "  ·  " + (if (up) "▲ " else "▼ ") + nl.spoonpaco.ninegrid.ui.components.money(kotlin.math.abs(diff), cur) + " " + stringResource(R.string.since_days, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(base.day), LocalDate.now()).toInt()),
                                fontSize = NgText.body, color = if (up) NgColors.Synced else NgColors.Error, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Koersverschil: alleen door prijzen, niet door kaarten die je toevoegde of wegdeed.
                    Text(stringResource(R.string.price_change_title), fontWeight = FontWeight.Bold, fontSize = NgText.body)
                    if (vm.priceChanges.isEmpty()) {
                        Text(stringResource(R.string.price_change_later), fontSize = NgText.body, color = extra.subtle)
                    } else vm.priceChanges.forEach { pc ->
                        val up = pc.diff >= 0
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                            Text(stringResource(if (pc.days <= 7) R.string.price_change_week else R.string.price_change_month), fontSize = NgText.body, modifier = Modifier.weight(1f))
                            val amount = nl.spoonpaco.ninegrid.ui.components.money(kotlin.math.abs(pc.diff), cur) + String.format(java.util.Locale.getDefault(), " (%+.1f%%)", pc.pct)
                            if (nl.spoonpaco.ninegrid.ui.components.LocalEffects.current) nl.spoonpaco.ninegrid.ui.components.LoyaltyShield((if (up) "+" else "−") + amount, up)
                            else Text((if (up) "▲ " else "▼ ") + amount, fontSize = NgText.body, fontWeight = FontWeight.Bold, color = if (up) NgColors.Synced else NgColors.Error)
                        }
                    }
                    Text(stringResource(R.string.price_change_explain), fontSize = NgText.caption, color = extra.subtle)
                }
            }

            // Winner of the Week: één kaart, de grootste stijging in geld over je eigen exemplaren.
            vm.winner?.let { w ->
                item {
                    Panel {
                        Text("Winner of the Week", fontWeight = FontWeight.Bold, color = extra.price)
                        vm.winnerWeek?.let { mon ->
                            val ds = settings.dateStyle
                            Text(stringResource(R.string.winner_week, nl.spoonpaco.ninegrid.domain.Dates.date(mon.minusDays(7), ds), nl.spoonpaco.ninegrid.domain.Dates.date(mon, ds)), fontSize = NgText.caption, color = extra.subtle)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            nl.spoonpaco.ninegrid.ui.components.CardThumb(w.print, vm.images, settings, width = 64)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.print.name + if (w.finish != nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL) " (${w.finish})" else "", fontWeight = FontWeight.Bold)
                                Text("${w.print.setCode} ${w.print.collectorNumber}", fontSize = NgText.body, color = extra.subtle)
                                Text(nl.spoonpaco.ninegrid.ui.components.money(w.oldUnit, cur) + " → " + nl.spoonpaco.ninegrid.ui.components.money(w.newUnit, cur) + String.format(java.util.Locale.getDefault(), "  (%+.0f%%)", w.pct), fontSize = NgText.body)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                    if (nl.spoonpaco.ninegrid.ui.components.LocalEffects.current) nl.spoonpaco.ninegrid.ui.components.LoyaltyShield("+" + nl.spoonpaco.ninegrid.ui.components.money(w.totalGain, cur), up = true)
                                    else Text("▲ " + nl.spoonpaco.ninegrid.ui.components.money(w.totalGain, cur), fontSize = NgText.body, fontWeight = FontWeight.Bold, color = NgColors.Synced)
                                    if (w.quantity > 1) Text("  ·  " + stringResource(R.string.winner_copies, w.quantity), fontSize = NgText.body, color = extra.subtle)
                                }
                            }
                        }
                    }
                }
            }

            // Boosters: alleen als je er een hebt opgeslagen.
            if (boosterList.isNotEmpty()) item { Panel { nl.spoonpaco.ninegrid.ui.screens.BoostersSummary(vm, onBoosters) } }

            // Ruilen: per vriend hoeveel kaarten jouw decks beter maken. Tik = meteen de ruilmatch.
            item {
                val friends by vm.friends.collectAsState()
                val fp by vm.friendPackages.collectAsState()
                val fc by vm.friendCards.collectAsState()
                androidx.compose.runtime.LaunchedEffect(friends, fp, fc.size, decks, all.size) { vm.computeTradeSummaries() }
                Panel {
                    Text(stringResource(R.string.trade_panel_title), fontWeight = FontWeight.Bold, color = extra.price)
                    if (friends.isEmpty()) {
                        Text(stringResource(R.string.trade_panel_none), fontSize = NgText.body, modifier = Modifier.padding(top = 4.dp))
                        androidx.compose.material3.TextButton(onClick = onShare) { Text(stringResource(R.string.share_title) + " ›") }
                    } else vm.tradeSummaries.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { onFriend(t.friend.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.friend.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                if (t.cards == 0) stringResource(R.string.trade_panel_nothing)
                                else stringResource(R.string.trade_panel_line, t.cards, Money.format(t.value, cur)),
                                fontSize = NgText.body, color = if (t.cards > 0) NgColors.Synced else extra.subtle,
                            )
                            Text("  ›", color = extra.subtle)
                        }
                    }
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
                // Alleen opnieuw uitrekenen als de gegevens of de keuzes veranderen.
                val series = remember(snaps, moments, lines, cur, settings.chartMetric, settings.chartRange, latestLabel) { lines.mapIndexed { i, key ->
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
                } }
                // Bij Waarde: het aantal kaarten als tweede lijn, met een eigen as rechts (van de eerste lijn in de grafiek).
                val countLabel = stringResource(R.string.chart_count_line)
                val countSeries = remember(snaps, moments, lines, settings.chartMetric, settings.chartRange, settings.chartCount, countLabel) {
                    if (!byValue || byGain || !settings.chartCount) null else {
                        val key = lines.first()
                        val daily = snaps.filter { it.key == key }.map { noon(it.day) to it.count.toDouble() }
                        val pts = if (intraday) {
                            val ms = moments.filter { it.key == key }.map { it.ts to it.count.toDouble() }
                            val momentDays = ms.map { java.time.Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate().toString() }.toSet()
                            ms + snaps.filter { it.key == key && it.day !in momentDays }.map { noon(it.day) to it.count.toDouble() }
                        } else daily
                        Series("count", countLabel, COUNT_LINE_COLOR, pts.filter { it.first >= from }.sortedBy { it.first })
                    }
                }
                Panel {
                    Text(stringResource(R.string.chart_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !pieMode, onClick = { pieMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.chart_mode_line)) }
                        SegmentedButton(selected = pieMode, onClick = { pieMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.chart_mode_pie)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (pieMode) {
                        val countMode = settings.chartMetric == "count"
                        val real = all.filter { !it.entry.proxy }
                        fun rv(r: String?) = real.filter { if (r == null) it.print?.rarity !in listOf("mythic", "rare", "uncommon", "common") else it.print?.rarity == r }
                            .sumOf { Money.total(it.entry, it.print, cur) }
                        val rawSlices: List<Slice> = when (pieSplit) {
                            "rarity" -> listOf(
                                Slice("Mythic", if (countMode) grand.m.toDouble() else rv("mythic"), NgColors.Mythic, null),
                                Slice("Rare", if (countMode) grand.r.toDouble() else rv("rare"), NgColors.Rare, null),
                                Slice("Uncommon", if (countMode) grand.u.toDouble() else rv("uncommon"), NgColors.Uncommon, null),
                                Slice("Common", if (countMode) grand.c.toDouble() else rv("common"), NgColors.Common, null),
                                Slice(stringResource(R.string.pie_other), if (countMode) grand.o.toDouble() else rv(null), NgColors.Special, null),
                            )
                            "deck" -> listOf(Slice(stringResource(R.string.pie_loose), if (countMode) setTotal.total.toDouble() else setTotal.value, NgColors.Amber, onSets)) +
                                deckRows.map { (n, c) -> Slice(n, if (countMode) c.total.toDouble() else c.value, NgColors.Amber, onDecks) }
                            else -> sets.map { (code, c) -> Slice(setLabel(code), if (countMode) c.total.toDouble() else c.value, NgColors.Amber, { onSet(code) }) }
                        }
                        val slices = if (pieSplit == "rarity") rawSlices.filter { it.amount > 0 }
                            else pieSlices(rawSlices, 6, stringResource(R.string.pie_other), extra.subtle)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("set" to stringResource(R.string.chart_group_sets), "rarity" to stringResource(R.string.pie_split_rarity), "deck" to stringResource(R.string.decks)).forEach { (k, l) ->
                                FilterChip(selected = pieSplit == k, onClick = { pieSplit = k }, label = { Text(l) })
                            }
                            Spacer(Modifier.width(6.dp))
                            FilterChip(selected = !countMode, onClick = { vm.setChartMetric("value") }, label = { Text(stringResource(R.string.chart_value)) })
                            FilterChip(selected = countMode, onClick = { vm.setChartMetric("count") }, label = { Text(stringResource(R.string.chart_cards)) })
                        }
                        Spacer(Modifier.height(8.dp))
                        PieBlock(slices, countMode, cur)
                    } else {
                    if (byGain && series.all { it.points.size < 2 }) {
                        Text(stringResource(R.string.chart_gain_empty), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(vertical = 8.dp))
                    } else if (series.all { it.points.size < 2 }) {
                        Text(stringResource(R.string.chart_empty), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    val hideMoney = nl.spoonpaco.ninegrid.ui.components.LocalPrivacy.current.hidden
                    HistoryChart(
                        series = series, fromDay = from, toDay = maxOf(now, from + 60_000), showTime = days in 1..7,
                        // In het kader het precieze bedrag; langs de as zoveel decimalen als de stap tussen de lijnen vraagt.
                        formatY = { v -> if (hideMoney && byValue) "••••" else if (byGain) (if (v > 0.005) "+" else if (v < -0.005) "−" else "") + Money.format(kotlin.math.abs(v), cur) else if (byValue) Money.format(v, cur) else v.toInt().toString() },
                        formatAxis = { v, step -> if (hideMoney && byValue) "••••" else if (byGain) (if (v > 0.005) "+" else if (v < -0.005) "−" else "") + axisMoney(kotlin.math.abs(v), step, cur) else if (byValue) axisMoney(v, step, cur) else v.toInt().toString() },
                        secondary = countSeries,
                        gapMs = 36L * 3600 * 1000,
                        formatX = { ms, withTime -> if (withTime) nl.spoonpaco.ninegrid.domain.Dates.dateTime(ms, settings.dateStyle) else nl.spoonpaco.ninegrid.domain.Dates.date(ms, settings.dateStyle) },
                        axisColor = MaterialTheme.colorScheme.onSurface, textColor = extra.subtle,
                    )
                    if (byGain) Text(stringResource(R.string.chart_gain_explain), fontSize = NgText.caption, color = extra.subtle)
                    else if (series.any { s -> s.sheetUntil != null && s.points.any { it.first <= s.sheetUntil } })
                        Text(stringResource(R.string.chart_sheet_note), fontSize = NgText.caption, color = extra.subtle)
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
                    Row(Modifier.fillMaxWidth().height(18.dp).clip(NgShape.Normal).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        if (sum > 0) parts.filter { it.third > 0 }.forEach { (_, c, value) ->
                            Box(Modifier.weight((value / sum).toFloat()).fillMaxHeight().background(c))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        parts.forEach { (l, c, value) ->
                            val pct = if (sum > 0) Math.round(value / sum * 100) else 0
                            Text("$l $pct%", color = c, fontWeight = FontWeight.Bold, fontSize = NgText.body)
                        }
                    }
                }
            }

            // Sets en decks, twee regels per rij.
            // Sets: één regel; de lijst zelf staat in een eigen scherm.
            item { SectionTitle(stringResource(R.string.set)) }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onSets).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sets_title), fontWeight = FontWeight.Bold)
                        Text(
                            androidx.compose.ui.res.pluralStringResource(R.plurals.n_sets, sets.size, sets.size) + " · " +
                                androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, setTotal.total, setTotal.total),
                            fontSize = NgText.body, color = extra.subtle,
                        )
                    }
                    Text(nl.spoonpaco.ninegrid.ui.components.money(setTotal.value, cur), fontWeight = FontWeight.Bold, color = extra.price, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
                    Text("  ›", fontWeight = FontWeight.Bold)
                }
            }
            item { SectionTitle(stringResource(R.string.decks)) }
            // Decks: één regel, net als de sets; tik opent het tabblad Decks.
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onDecks).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.decks), fontWeight = FontWeight.Bold)
                        Text(
                            androidx.compose.ui.res.pluralStringResource(R.plurals.n_decks, deckRows.size, deckRows.size) + " · " +
                                androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, deckTotal.total, deckTotal.total),
                            fontSize = NgText.body, color = extra.subtle,
                        )
                    }
                    Text(nl.spoonpaco.ninegrid.ui.components.money(deckTotal.value, cur), fontWeight = FontWeight.Bold, color = extra.price, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
                    Text("  ›", fontWeight = FontWeight.Bold)
                }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                TwoLineRow(stringResource(R.string.total_collection), grand, cur, bold = true, highlight = true)
                Text(stringResource(R.string.price_explain), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (choosing) {
        LineChooser(
            totals = listOf("total", "sets", "decks").map { it to labelOf(it, null) },
            deckOptions = decks.map { "deck:${it.id}" }.map { it to labelOf(it, null) },
            setOptions = sets.sortedByDescending { it.second.value }.map { "set:${it.first}" to setLabel(it.first) },
            selected = settings.chartLines.filter { it in available }.ifEmpty { listOf("total") },
            count = settings.chartCount,
            countEnabled = settings.chartMetric != "count" && settings.chartMetric != "gain",
            onDone = { lines, count -> vm.setChartLines(lines); vm.setChartCount(count); choosing = false },
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

/** Kleur van de lijn met het aantal kaarten (rechter as): licht en koel, zodat hij niet op een waardelijn lijkt. */
private val COUNT_LINE_COLOR = Color(0xFF8FB8DE)

/** Bedrag langs de as: zoveel decimalen als de stap tussen twee hulplijnen nodig heeft. */
private fun axisMoney(v: Double, step: Double, cur: String): String {
    val sym = if (cur == "USD") "$" else "€"
    return when {
        step >= 500 && v >= 10_000 -> "$sym${Math.round(v / 1000)}k"
        step >= 50 && v >= 1_000 -> "$sym" + String.format(java.util.Locale.US, "%.1fk", v / 1000)
        step >= 5 -> "$sym${Math.round(v)}"
        step >= 0.5 -> "$sym" + String.format(java.util.Locale.US, "%.1f", v)
        else -> "$sym" + String.format(java.util.Locale.US, "%.2f", v)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalNgExtra.current.cardSurface),
        shape = NgShape.Large,
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
            Text(nl.spoonpaco.ninegrid.ui.components.money(c.value, currency), fontWeight = FontWeight.Bold, color = extra.price, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("M" to c.m to NgColors.Mythic, "R" to c.r to NgColors.Rare, "U" to c.u to NgColors.Uncommon, "C" to c.c to NgColors.Common, "O" to c.o to NgColors.Special)
                .forEach { (lv, col) -> Text("${lv.first} ${lv.second}", color = col, fontSize = NgText.body) }
            Text("· " + androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, c.total, c.total), fontSize = NgText.body, color = extra.subtle)
        }
    }
}

/**
 * Lijnen kiezen voor de grafiek, met zoeken; hoogstens 6 tegelijk. Bovenin het vakje Aantal kaarten (telt niet mee),
 * dan wat al gekozen was, dan de groepen Totaal, Decks en Sets (Sets ingeklapt; zoeken klapt alles open).
 */
@Composable
private fun LineChooser(
    totals: List<Pair<String, String>>, deckOptions: List<Pair<String, String>>, setOptions: List<Pair<String, String>>,
    selected: List<String>, count: Boolean, countEnabled: Boolean,
    onDone: (List<String>, Boolean) -> Unit, onDismiss: () -> Unit,
) {
    val all = totals + deckOptions + setOptions
    val labels = all.toMap()
    var chosen by remember { mutableStateOf(selected) }
    var countOn by remember { mutableStateOf(count) }
    val first = remember { selected }   // de groep Gekozen blijft staan zoals bij het openen, zodat de lijst niet springt
    var q by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf(false) }
    var setsOpen by remember { mutableStateOf(false) }
    var decksOpen by remember { mutableStateOf(true) }
    val searching = q.isNotBlank()
    fun match(o: Pair<String, String>) = !searching || o.second.contains(q.trim(), ignoreCase = true)
    fun toggle(key: String) {
        if (key in chosen) { chosen = chosen - key; warn = false }
        else if (chosen.size >= MAX_LINES) warn = true
        else chosen = chosen + key
    }
    @Composable
    fun LineRow(key: String, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { toggle(key) }) {
            Checkbox(checked = key in chosen, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    @Composable
    fun Header(title: String, n: Int, open: Boolean?, onTap: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = open != null, onClick = onTap).padding(top = 10.dp, bottom = 2.dp)) {
            Text("$title ($n)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (open != null) Text(if (open) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
        }
    }
    val rest = all.filter { it.first !in first }
    val restTotals = totals.filter { it in rest && match(it) }
    val restDecks = deckOptions.filter { it in rest && match(it) }
    val restSets = setOptions.filter { it in rest && match(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chart_choose_lines)) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(stringResource(R.string.search_hint)) })
                if (warn) Text(stringResource(R.string.chart_max_lines, MAX_LINES), color = NgColors.Error, fontSize = NgText.body)
                LazyColumn(Modifier.height(360.dp)) {
                    if (!searching) item(key = "count") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = countEnabled) { countOn = !countOn }.padding(top = 6.dp)) {
                            Checkbox(checked = countOn && countEnabled, onCheckedChange = null, enabled = countEnabled)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.chart_count_line), color = if (countEnabled) MaterialTheme.colorScheme.onSurface else LocalNgExtra.current.subtle)
                                if (!countEnabled) Text(stringResource(R.string.chart_count_only_value), fontSize = NgText.caption, color = LocalNgExtra.current.subtle)
                            }
                        }
                    }
                    val firstShown = first.mapNotNull { k -> labels[k]?.let { k to it } }.filter { match(it) }
                    if (firstShown.isNotEmpty()) {
                        item(key = "h:chosen") { Header(stringResource(R.string.chart_group_chosen), firstShown.size, null) {} }
                        items(firstShown, key = { "c:" + it.first }) { (k, l) -> LineRow(k, l) }
                    }
                    if (restTotals.isNotEmpty()) {
                        item(key = "h:totals") { Header(stringResource(R.string.chart_group_totals), restTotals.size, null) {} }
                        items(restTotals, key = { "t:" + it.first }) { (k, l) -> LineRow(k, l) }
                    }
                    if (restDecks.isNotEmpty()) {
                        val open = searching || decksOpen
                        item(key = "h:decks") { Header(stringResource(R.string.decks), restDecks.size, if (searching) null else open) { decksOpen = !decksOpen } }
                        if (open) items(restDecks, key = { "d:" + it.first }) { (k, l) -> LineRow(k, l) }
                    }
                    if (restSets.isNotEmpty()) {
                        val open = searching || setsOpen
                        item(key = "h:sets") { Header(stringResource(R.string.chart_group_sets), restSets.size, if (searching) null else open) { setsOpen = !setsOpen } }
                        if (open) items(restSets, key = { "s:" + it.first }) { (k, l) -> LineRow(k, l) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen.ifEmpty { listOf("total") }, countOn) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class Slice(val label: String, val amount: Double, val color: Color, val onTap: (() -> Unit)?)

/** De [max] grootste plakken, de rest samen als "Overig"; plakken zonder waarde vallen weg. Kleuren van de grote komen uit LINE_COLORS en daarna uit een vaste reeks. */
private fun pieSlices(all: List<Slice>, max: Int, otherLabel: String, otherColor: Color): List<Slice> {
    val sorted = all.filter { it.amount > 0 }.sortedByDescending { it.amount }
    val palette = LINE_COLORS + listOf(Color(0xFF5B8DEF), Color(0xFFE08AB4), Color(0xFF9AA5B1))
    val top = sorted.take(max).mapIndexed { i, sl -> sl.copy(color = palette[i % palette.size]) }
    val rest = sorted.drop(max)
    return if (rest.isEmpty()) top else top + Slice(otherLabel, rest.sumOf { it.amount }, otherColor, null)
}

/** Taart met legenda: kleurstip, naam, percentage en bedrag (of aantal). Tik op een regel gaat naar die set of dat deck. */
@Composable
private fun PieBlock(slices: List<Slice>, byCount: Boolean, currency: String) {
    val extra = LocalNgExtra.current
    val sum = slices.sumOf { it.amount }
    if (sum <= 0) {
        Text(stringResource(R.string.pie_empty), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(170.dp)) {
            var start = -90f
            slices.forEach { sl ->
                val sweep = (sl.amount / sum * 360.0).toFloat()
                drawArc(sl.color, start, sweep, useCenter = true)
                drawArc(track, start, sweep, useCenter = true, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                start += sweep
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    slices.forEach { sl ->
        val pct = Math.round(sl.amount / sum * 100)
        Row(
            Modifier.fillMaxWidth().then(if (sl.onTap != null) Modifier.clickable { sl.onTap?.invoke() } else Modifier).padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(sl.color))
            Spacer(Modifier.width(8.dp))
            Text(sl.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$pct%", color = extra.subtle, fontSize = NgText.body)
            Spacer(Modifier.width(10.dp))
            if (byCount) Text(sl.amount.toInt().toString(), fontWeight = FontWeight.Bold)
            else Text(nl.spoonpaco.ninegrid.ui.components.money(sl.amount, currency), fontWeight = FontWeight.Bold, color = extra.price, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
        }
    }
}
