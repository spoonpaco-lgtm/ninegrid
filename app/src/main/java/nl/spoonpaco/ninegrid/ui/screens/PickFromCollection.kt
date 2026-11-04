package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardRow
import nl.spoonpaco.ninegrid.ui.components.EmptyState
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

/**
 * Kaarten uit je collectie in een deck zetten. Je ziet alleen wat je hebt en waar het ligt;
 * de kaart verhuist van de collectie naar het deck, zodat niets dubbel telt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickFromCollectionDialog(vm: AppViewModel, deck: Deck, onDismiss: () -> Unit) {
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<EntryWithPrint?>(null) }
    val q = query.trim().lowercase()
    val list = all.filter { it.entry.deckId == null && it.print != null }
        .filter { q.isEmpty() || it.print!!.name.lowercase().contains(q) || it.print.typeLine.lowercase().contains(q) }
        .sortedWith(compareBy({ it.print!!.name }, { it.print!!.setCode }))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Column { Text(stringResource(R.string.pick_from_collection)); Text(deck.name, fontSize = 12.sp, color = LocalNgExtra.current.subtle) } },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.back)) } },
            )
        }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.pick_search_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(stringResource(R.string.pick_explain), fontSize = 12.sp, color = LocalNgExtra.current.subtle, modifier = Modifier.padding(horizontal = 16.dp))
                if (list.isEmpty()) {
                    EmptyState(stringResource(R.string.pick_empty_title), stringResource(R.string.pick_empty_body))
                } else {
                    LazyColumn {
                        items(list, key = { it.entry.id }) { e ->
                            CardRow(e.entry, e.print, settings, vm.images, location = locationText(e.entry, emptyMap()) ?: stringResource(R.string.collection_loose)) { picking = e }
                        }
                    }
                }
            }
        }
    }

    picking?.let { e ->
        var qty by remember(e.entry.id) { mutableStateOf(1) }
        val commander = deck.format == DeckFormat.COMMANDER
        var section by remember(e.entry.id) { mutableStateOf(Section.MAIN) }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(e.print?.name ?: "") },
            text = {
                Column {
                    Text(stringResource(R.string.pick_available, e.entry.quantity), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.quantity), modifier = Modifier.weight(1f))
                        IconButton(onClick = { qty = (qty - 1).coerceAtLeast(1) }) { Icon(Icons.Filled.Remove, stringResource(R.string.less)) }
                        Text("$qty", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { qty = (qty + 1).coerceAtMost(e.entry.quantity) }) { Icon(Icons.Filled.Add, stringResource(R.string.more_one)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val options = if (commander) listOf(Section.COMMANDER, Section.MAIN) else listOf(Section.MAIN, Section.SIDEBOARD)
                        options.forEach { s -> FilterChip(selected = section == s, onClick = { section = s }, label = { Text(sectionLabelPlain(s)) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.moveEntry(e.entry, qty, deck.id, section, null, null, null)
                    picking = null
                }) { Text(stringResource(R.string.pick_put_in_deck)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
