package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors
import java.text.DateFormat
import java.util.Date

private data class Counts(val m: Int, val r: Int, val u: Int, val c: Int, val o: Int, val value: Double) {
    val total get() = m + r + u + c + o
    operator fun plus(x: Counts) = Counts(m + x.m, r + x.r, u + x.u, c + x.c, o + x.o, value + x.value)
}

private fun count(list: List<EntryWithPrint>, currency: String): Counts {
    val real = list.filter { !it.entry.proxy }
    fun n(r: String) = real.filter { it.print?.rarity == r }.sumOf { it.entry.quantity }
    val m = n("mythic"); val r = n("rare"); val u = n("uncommon"); val c = n("common")
    val all = real.sumOf { it.entry.quantity }
    return Counts(m, r, u, c, all - m - r - u - c, real.sumOf { Money.total(it.entry, it.print, currency) })
}

/** Zoals het tabblad Overzicht in de Sheet: per set en per deck aantallen per rarity en waarde. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(vm: AppViewModel) {
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val settings by vm.settings.collectAsState()
    val cur = settings.currency
    val extra = LocalNgExtra.current

    val collection = all.filter { it.entry.deckId == null }
    val sets = collection.groupBy { it.print?.setCode ?: "?" }.toSortedMap().map { (k, v) -> k to count(v, cur) }
    val setTotal = sets.fold(Counts(0, 0, 0, 0, 0, 0.0)) { a, b -> a + b.second }
    val deckRows = decks.map { d -> d.name to count(all.filter { it.entry.deckId == d.id }, cur) }
    val deckTotal = deckRows.fold(Counts(0, 0, 0, 0, 0, 0.0)) { a, b -> a + b.second }
    val grand = setTotal + deckTotal

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
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("overview"); IconButton(onClick = { vm.refreshPrices() }, enabled = !vm.busy) { Icon(Icons.Filled.Refresh, stringResource(R.string.update_prices)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (vm.busy) LinearProgressIndicator(Modifier.padding(horizontal = 12.dp))
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                HeaderRow(stringResource(R.string.set))
                sets.forEach { (k, c) -> CountRow(k, c, cur) }
                CountRow(stringResource(R.string.total_sets), setTotal, cur, bold = true)
                Spacer(Modifier.height(16.dp))
                HeaderRow(stringResource(R.string.decks))
                deckRows.forEach { (k, c) -> CountRow(k, c, cur) }
                CountRow(stringResource(R.string.total_decks), deckTotal, cur, bold = true)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                CountRow(stringResource(R.string.total_collection), grand, cur, bold = true, highlight = true)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.price_explain),
                fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private val W_NAME = 150.dp
private val W_NUM = 44.dp
private val W_VAL = 92.dp

@Composable
private fun HeaderRow(first: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(first, Modifier.width(W_NAME), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        listOf("M" to NgColors.Mythic, "R" to NgColors.Rare, "U" to NgColors.Uncommon, "C" to NgColors.Common, "O" to NgColors.Special).forEach { (l, col) ->
            Text(l, Modifier.width(W_NUM), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = col)
        }
        Text("#", Modifier.width(W_NUM), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        Text("€/$", Modifier.width(W_VAL), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CountRow(name: String, c: Counts, currency: String, bold: Boolean = false, highlight: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    val mod = if (highlight) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier
    Row(mod.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(W_NAME), fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOf(c.m, c.r, c.u, c.c, c.o, c.total).forEach { Text("$it", Modifier.width(W_NUM), textAlign = TextAlign.End, fontWeight = weight) }
        Text(Money.format(c.value, currency), Modifier.width(W_VAL), textAlign = TextAlign.End, fontWeight = weight, color = LocalNgExtra.current.price)
    }
}
