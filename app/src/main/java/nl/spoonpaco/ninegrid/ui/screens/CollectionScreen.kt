package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardRow
import nl.spoonpaco.ninegrid.ui.components.CardSearchDialog
import nl.spoonpaco.ninegrid.ui.components.EmptyState
import nl.spoonpaco.ninegrid.ui.components.SectionHeader
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    vm: AppViewModel,
    onCard: (String) -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onReview: () -> Unit,
    onImport: () -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // Filters en sortering worden onthouden, ook na het afsluiten.
    val filter = remember(settings.filters["collection"]) { nl.spoonpaco.ninegrid.ui.components.CardFilter.fromJson(settings.filters["collection"]) }
    val sort = settings.collectionSort
    var menu by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var fabVisible by remember { mutableStateOf(true) }
    var lastIndex by remember { mutableStateOf(0) }
    var lastOffset by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) ->
            val down = i > lastIndex || (i == lastIndex && o > lastOffset + 8)
            val up = i < lastIndex || (i == lastIndex && o < lastOffset - 8)
            if (down) fabVisible = false else if (up || i == 0) fabVisible = true
            lastIndex = i; lastOffset = o
        }
    }
    var search by remember { mutableStateOf(false) }
    val deckNames = remember(decks) { decks.associate { it.id to it.name } }

    // Alleen opnieuw uitrekenen als de kaarten of de filters echt veranderen (scheelt veel bij duizenden kaarten).
    val filtered = remember(all, query, filter) { all.filter { filter.matches(it, query) } }
    val count = remember(filtered) { filtered.sumOf { it.entry.quantity } }
    val value = remember(filtered, settings.currency) { filtered.sumOf { Money.total(it.entry, it.print, settings.currency) } }
    data class Group(val set: String, val name: String, val count: Int, val value: Double, val cards: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>)
    val groups = remember(filtered, sort, settings.currency) {
        if (sort != "set") emptyList() else filtered.groupBy { it.print?.setCode ?: "?" }.toSortedMap().map { (set, list) ->
            Group(
                set, list.firstOrNull()?.print?.setName ?: "", list.sumOf { it.entry.quantity }, list.sumOf { Money.total(it.entry, it.print, settings.currency) },
                list.sortedWith(compareBy({ it.print?.collectorNumber?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 0 }, { it.print?.collectorNumber })),
            )
        }
    }
    val sortedFlat = remember(filtered, sort, settings.currency) {
        when (sort) {
            "price" -> filtered.sortedByDescending { Money.total(it.entry, it.print, settings.currency) }
            "name" -> filtered.sortedBy { it.print?.name ?: "" }
            else -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.collection), fontWeight = FontWeight.Bold)
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, count, count) + " · " + Money.format(value, settings.currency),
                        style = MaterialTheme.typography.bodySmall, color = LocalNgExtra.current.subtle,
                    )
                }
            }, actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection") })
        },
        floatingActionButton = {
            // Verdwijnt bij naar beneden scrollen, komt terug bij een stukje omhoog.
            androidx.compose.animation.AnimatedVisibility(fabVisible, enter = androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.scaleOut()) {
            FloatingActionButton(onClick = { menu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, stringResource(R.string.add_cards))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.scan_page)) }, leadingIcon = { Icon(Icons.Filled.CameraAlt, null) }, onClick = { menu = false; onScan() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.paste_list)) }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null) }, onClick = { menu = false; onPaste() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.search_card)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, onClick = { menu = false; search = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_title)) }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) }, onClick = { menu = false; onImport() })
                }
            }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            nl.spoonpaco.ninegrid.ui.components.FilterSearchBar(
                query = query, onQuery = { query = it }, placeholder = stringResource(R.string.search_hint),
                filter = filter, onFilter = { vm.setFilter("collection", it) },
                groups = nl.spoonpaco.ninegrid.ui.components.FilterGroup.entries.toSet(),
                sortOptions = listOf("set" to stringResource(R.string.sort_set), "price" to stringResource(R.string.sort_price), "name" to stringResource(R.string.sort_name)),
                sort = sort, onSort = { vm.setCollectionSort(it) },
            )
            if (all.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState(stringResource(R.string.empty_collection_title), stringResource(R.string.empty_collection_body))
                    // Je hebt al een collectie in de Sheet? Dan in één keer importeren.
                    androidx.compose.material3.Button(onClick = onImport) {
                        Icon(Icons.Filled.FileOpen, null); androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.import_title))
                    }
                }
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (sort == "set") {
                        groups.forEach { g ->
                            item(key = "h_${g.set}") {
                                SectionHeader("${g.set} · ${g.name}", androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards, g.count, g.count) + " · " + Money.format(g.value, settings.currency))
                            }
                            items(g.cards, key = { it.entry.id }) { e ->
                                CardRow(e.entry, e.print, settings, vm.images, location = locationText(e.entry, deckNames)) { e.print?.let { onCard(it.id) } }
                            }
                        }
                    } else {
                        items(sortedFlat, key = { it.entry.id }) { e ->
                            CardRow(e.entry, e.print, settings, vm.images, location = locationText(e.entry, deckNames)) { e.print?.let { onCard(it.id) } }
                        }
                    }
                }
            }
        }
    }
    if (search) {
        CardSearchDialog(vm.repo.scryfall, vm.images, settings, onPick = { p -> search = false; vm.startSingle(p); onReview() }, onDismiss = { search = false })
    }
}

@Composable
fun locationText(e: nl.spoonpaco.ninegrid.data.db.Entry, deckNames: Map<Long, String>): String? = when {
    e.deckId != null -> stringResource(R.string.in_deck, deckNames[e.deckId] ?: "")
    e.binder != null && e.page != null && e.slot != null -> stringResource(R.string.binder_page_slot, e.binder, e.page, e.slot)
    e.binder != null && e.page != null -> stringResource(R.string.binder_page, e.binder, e.page)
    e.binder != null -> e.binder
    else -> null
}
