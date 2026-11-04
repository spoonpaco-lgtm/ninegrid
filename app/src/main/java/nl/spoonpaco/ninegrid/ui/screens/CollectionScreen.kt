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
    var rarity by rememberSaveable { mutableStateOf<String?>(null) }
    var foilOnly by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf<Char?>(null) }
    var includeDecks by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("set") }
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    val deckNames = decks.associate { it.id to it.name }

    val filtered = all.filter { e ->
        (includeDecks || e.entry.deckId == null) &&
            (query.isBlank() || (e.print?.name ?: "").contains(query.trim(), ignoreCase = true) ||
                (e.print?.setCode ?: "").equals(query.trim(), ignoreCase = true)) &&
            (rarity == null || e.print?.rarity == rarity) &&
            (!foilOnly || e.entry.finish != Finish.NONFOIL) &&
            (color == null || (if (color == 'C') (e.print?.colors ?: "").isEmpty() else (e.print?.colors ?: "").contains(color!!)))
    }
    val count = filtered.sumOf { it.entry.quantity }
    val value = filtered.sumOf { Money.total(it.entry, it.print, settings.currency) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.collection), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.count_value, count, Money.format(value, settings.currency)),
                        style = MaterialTheme.typography.bodySmall, color = LocalNgExtra.current.subtle,
                    )
                }
            }, actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { menu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, stringResource(R.string.add_cards))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.scan_page)) }, leadingIcon = { Icon(Icons.Filled.CameraAlt, null) }, onClick = { menu = false; onScan() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.paste_list)) }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null) }, onClick = { menu = false; onPaste() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.search_card)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, onClick = { menu = false; search = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_title)) }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) }, onClick = { menu = false; onImport() })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("mythic", "rare", "uncommon", "common").forEach { r ->
                    FilterChip(selected = rarity == r, onClick = { rarity = if (rarity == r) null else r }, label = { Text(r.replaceFirstChar { it.uppercase() }) })
                }
                FilterChip(selected = foilOnly, onClick = { foilOnly = !foilOnly }, label = { Text(stringResource(R.string.foil)) })
                "WUBRGC".forEach { ch ->
                    FilterChip(selected = color == ch, onClick = { color = if (color == ch) null else ch }, label = { Text(ch.toString()) })
                }
                FilterChip(selected = includeDecks, onClick = { includeDecks = !includeDecks }, label = { Text(stringResource(R.string.include_decks)) })
                FilterChip(selected = sort == "price", onClick = { sort = if (sort == "price") "set" else "price" }, label = { Text(stringResource(R.string.sort_price)) })
                FilterChip(selected = sort == "name", onClick = { sort = if (sort == "name") "set" else "name" }, label = { Text(stringResource(R.string.sort_name)) })
            }
            if (all.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState(stringResource(R.string.empty_collection_title), stringResource(R.string.empty_collection_body))
                    // Je hebt al een collectie in de Sheet? Dan in één keer importeren.
                    androidx.compose.material3.Button(onClick = onImport) {
                        Icon(Icons.Filled.FileOpen, null); androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.import_title))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (sort == "set") {
                        val groups = filtered.groupBy { it.print?.setCode ?: "?" }.toSortedMap()
                        groups.forEach { (set, list) ->
                            item(key = "h_$set") {
                                val name = list.firstOrNull()?.print?.setName ?: ""
                                SectionHeader(
                                    "$set · $name",
                                    stringResource(R.string.count_value, list.sumOf { it.entry.quantity }, Money.format(list.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency)),
                                )
                            }
                            items(list.sortedWith(compareBy({ it.print?.collectorNumber?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 0 }, { it.print?.collectorNumber })), key = { it.entry.id }) { e ->
                                CardRow(e.entry, e.print, settings, vm.images, location = locationText(e.entry, deckNames)) { e.print?.let { onCard(it.id) } }
                            }
                        }
                    } else {
                        val sorted = if (sort == "price") filtered.sortedByDescending { Money.total(it.entry, it.print, settings.currency) }
                        else filtered.sortedBy { it.print?.name ?: "" }
                        items(sorted, key = { it.entry.id }) { e ->
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
