package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/** Set compleet: hoeveel van de set je hebt, en wat je nog mist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetScreen(vm: AppViewModel, code: String, onBack: () -> Unit, onReview: () -> Unit) {
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    var variants by remember { mutableStateOf(false) }
    var showMissing by remember { mutableStateOf(true) }
    LaunchedEffect(code) { vm.loadSet(code) }
    val list = vm.setList?.takeIf { it.code.equals(code, true) }
    // Wat je hebt: elk nummer in deze set, in je collectie of in een deck (geen proxies).
    // Completionist: alleen wat los in je binders ligt telt; wat alleen in een deck zit staat bij Ontbreekt, met de decknaam.
    val inSet = all.filter { !it.entry.proxy && it.print?.setCode.equals(code, true) }
    val owned = inSet.filter { !settings.completionist || it.entry.deckId == null }.mapNotNull { it.print?.collectorNumber?.lowercase() }.toSet()
    val decksById by vm.decks.collectAsState()
    val deckOf = if (!settings.completionist) emptyMap() else inSet.filter { it.entry.deckId != null }
        .groupBy { it.print!!.collectorNumber.lowercase() }
        .mapValues { (_, l) -> l.mapNotNull { e -> decksById.firstOrNull { it.id == e.entry.deckId }?.name }.distinct().joinToString(", ") }
    var query by remember { mutableStateOf("") }
    val filter = remember(settings.filters["set"]) { nl.spoonpaco.ninegrid.ui.components.CardFilter.fromJson(settings.filters["set"]) }
    val cards = list?.cards.orEmpty().filter { variants || it.regular }
    // Zoeken en filteren gelden voor de lijst, niet voor de telling van hoe compleet je bent.
    fun shownOf(l: List<nl.spoonpaco.ninegrid.data.SetCard>) = l.filter { c ->
        (query.isBlank() || c.name.contains(query.trim(), true) || c.number.equals(query.trim(), true)) && (filter.rarities.isEmpty() || c.rarity in filter.rarities)
    }
    val have = cards.filter { it.number.lowercase() in owned }
    val missing = cards.filter { it.number.lowercase() !in owned }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(code.uppercase(), fontWeight = FontWeight.Bold); list?.let { Text(it.name, fontSize = 12.sp, color = extra.subtle) } } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection")
                IconButton(onClick = { vm.loadSet(code, refresh = true) }, enabled = !vm.setLoading) { Icon(Icons.Filled.Refresh, null) }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    if (vm.setLoading && list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (list != null) {
                        Text(stringResource(R.string.set_complete, have.size, cards.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { if (cards.isEmpty()) 0f else have.size / cards.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        val missingValue = missing.sumOf { (if (settings.currency == "USD") it.usd else it.eur) ?: 0.0 }
                        Text(stringResource(R.string.set_missing_value, Money.format(missingValue, settings.currency)), fontSize = 13.sp, color = extra.subtle)
                        Spacer(Modifier.height(8.dp))
                        nl.spoonpaco.ninegrid.ui.components.FilterSearchBar(
                            query = query, onQuery = { query = it }, placeholder = stringResource(R.string.sets_search_card),
                            filter = filter, onFilter = { vm.setFilter("set", it) },
                            groups = setOf(nl.spoonpaco.ninegrid.ui.components.FilterGroup.RARITY),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = showMissing, onClick = { showMissing = true }, label = { Text(stringResource(R.string.set_show_missing)) })
                            FilterChip(selected = !showMissing, onClick = { showMissing = false }, label = { Text(stringResource(R.string.set_show_have)) })
                            FilterChip(selected = variants, onClick = { variants = !variants }, label = { Text(stringResource(R.string.set_variants)) })
                        }
                        CompletionistToggle(settings.completionist, onChange = { vm.setCompletionist(it) })
                    }
                }
            }
            items(shownOf(if (showMissing) missing else have), key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = {
                        Column {
                            Text("#${c.number} · ${c.rarity.replaceFirstChar { it.uppercase() }}" + if (!c.regular) " · " + stringResource(R.string.set_variant) else "", color = NgColors.rarity(c.rarity))
                            if (showMissing) deckOf[c.number.lowercase()]?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.in_deck_label, it), fontSize = 12.sp, color = extra.subtle) }
                        }
                    },
                    trailingContent = {
                        Row {
                            Text(((if (settings.currency == "USD") c.usd else c.eur))?.let { Money.format(it, settings.currency) } ?: "–", color = extra.price)
                            if (showMissing) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { vm.addMissing(c.id, onReview) }) { Icon(Icons.Filled.Add, stringResource(R.string.add_cards)) }
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
