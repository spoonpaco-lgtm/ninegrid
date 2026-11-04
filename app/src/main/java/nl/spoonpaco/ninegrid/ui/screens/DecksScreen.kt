package nl.spoonpaco.ninegrid.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.domain.DeckIssue
import nl.spoonpaco.ninegrid.domain.Export
import nl.spoonpaco.ninegrid.domain.Formats
import nl.spoonpaco.ninegrid.domain.IssueLevel
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardRow
import nl.spoonpaco.ninegrid.ui.components.EmptyState
import nl.spoonpaco.ninegrid.ui.components.ManaPips
import nl.spoonpaco.ninegrid.ui.components.SectionHeader
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

@Composable
fun formatLabel(f: String): String = when (f) {
    DeckFormat.COMMANDER -> "Commander"
    DeckFormat.MODERN -> "Modern"
    DeckFormat.PIONEER -> "Pioneer"
    DeckFormat.STANDARD -> "Standard"
    DeckFormat.LEGACY -> "Legacy"
    DeckFormat.PAUPER -> "Pauper"
    else -> stringResource(R.string.format_casual)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(vm: AppViewModel, onDeck: (Long) -> Unit, onNewDeck: () -> Unit) {
    val decks by vm.decks.collectAsState()
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.decks), fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewDeck, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, stringResource(R.string.new_deck)) }
        },
    ) { pad ->
        if (decks.isEmpty()) {
            EmptyState(stringResource(R.string.empty_decks_title), stringResource(R.string.empty_decks_body), Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(decks, key = { it.id }) { d ->
                val list = all.filter { it.entry.deckId == d.id }
                val check = Formats.check(d.format, Formats.toDeckCards(list.map { it.entry to it.print }))
                val value = list.sumOf { Money.total(it.entry, it.print, settings.currency) }
                Card(
                    onClick = { onDeck(d.id) },
                    colors = CardDefaults.cardColors(containerColor = extra.cardSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    formatLabel(d.format),
                                    stringResource(R.string.n_cards, list.sumOf { it.entry.quantity }),
                                    check.bracketHint?.let { stringResource(R.string.bracket_short, it) },
                                ).joinToString(" · "),
                                fontSize = 12.sp, color = extra.subtle,
                            )
                        }
                        if (check.issues.any { it.level == IssueLevel.ERROR }) Icon(Icons.Filled.Warning, null, tint = NgColors.Error)
                        Spacer(Modifier.width(8.dp))
                        ManaPips(check.colorIdentity, size = 16)
                        Spacer(Modifier.width(10.dp))
                        Text(Money.format(value, settings.currency), color = extra.price, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(vm: AppViewModel, deckId: Long, onBack: () -> Unit, onCard: (String) -> Unit, onAddCards: (Long) -> Unit) {
    val deck by vm.repo.deck(deckId).collectAsState(initial = null)
    val list by vm.repo.entriesForDeck(deckId).collectAsState(initial = emptyList())
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val extra = LocalNgExtra.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var formatPicker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val d = deck
    val text = Export.deckText(list, d?.format == DeckFormat.COMMANDER)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.writeText(uri, text)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(d?.name ?: "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, d?.name) }
                    context.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.pick_from_collection)) }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { menu = false; picking = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.paste_list)) }, onClick = { menu = false; onAddCards(deckId) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.change_format)) }, onClick = { menu = false; formatPicker = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_text)) }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) }, onClick = { menu = false; saveLauncher.launch("${d?.name ?: "deck"}.txt") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_deck)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; deleting = true })
                }
            },
        )
    }) { pad ->
        if (d == null) return@Scaffold
        val check = Formats.check(d.format, Formats.toDeckCards(list.map { it.entry to it.print }))
        val sections = listOf(Section.COMMANDER, Section.MAIN, Section.SIDEBOARD)
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatLabel(d.format), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ManaPips(check.colorIdentity)
                    }
                    Spacer(Modifier.height(4.dp))
                    sections.forEach { s ->
                        val part = list.filter { (it.entry.section ?: Section.MAIN) == s }
                        if (part.isNotEmpty()) Row {
                            Text(sectionLabel(s), modifier = Modifier.weight(1f), color = extra.subtle)
                            Text(stringResource(R.string.count_value, part.filter { !it.entry.proxy }.sumOf { it.entry.quantity }, Money.format(part.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency)), color = extra.subtle)
                        }
                    }
                    Row {
                        Text(stringResource(R.string.total), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(Money.format(list.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency), color = extra.price, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Button(onClick = { picking = true }) {
                            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.pick_from_collection))
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { onAddCards(deckId) }) { Text(stringResource(R.string.paste_list)) }
                    }
                    if (d.format == DeckFormat.COMMANDER) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.bracket_line, check.bracketHint ?: "?", check.gameChangers.size), fontSize = 13.sp)
                        if (check.gameChangers.isNotEmpty()) Text(check.gameChangers.joinToString(", "), fontSize = 12.sp, color = extra.subtle)
                        Text(stringResource(R.string.bracket_note), fontSize = 11.sp, color = extra.subtle)
                    }
                    if (check.issues.isEmpty() && d.format != DeckFormat.CASUAL) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.deck_ok), color = NgColors.Synced, fontSize = 13.sp)
                    }
                    check.issues.forEach { i -> Text("• " + issueText(i), color = if (i.level == IssueLevel.ERROR) NgColors.Error else extra.subtle, fontSize = 13.sp) }
                }
            }
            sections.forEach { s ->
                val part = list.filter { (it.entry.section ?: Section.MAIN) == s }.sortedBy { it.print?.name ?: "" }
                if (part.isNotEmpty()) {
                    item(key = "h_$s") { SectionHeader(sectionLabel(s), stringResource(R.string.n_cards, part.sumOf { it.entry.quantity })) }
                    items(part, key = { it.entry.id }) { e -> CardRow(e.entry, e.print, settings, vm.images) { e.print?.let { onCard(it.id) } } }
                }
            }
        }
    }

    if (picking && d != null) {
        PickFromCollectionDialog(vm, d, onDismiss = { picking = false })
    }
    if (renaming && d != null) {
        var name by remember { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.updateDeck(d.copy(name = name.trim().ifBlank { d.name })); renaming = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (formatPicker && d != null) {
        AlertDialog(
            onDismissRequest = { formatPicker = false },
            title = { Text(stringResource(R.string.change_format)) },
            text = {
                Column {
                    DeckFormat.all.forEach { f ->
                        TextButton(onClick = { vm.updateDeck(d.copy(format = f)); formatPicker = false }) {
                            Text(formatLabel(f), fontWeight = if (f == d.format) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { formatPicker = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleting && d != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_deck)) },
            text = { Text(stringResource(R.string.delete_deck_body)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.deleteDeck(d, keepCards = true); deleting = false; onBack() }) { Text(stringResource(R.string.delete_keep_cards)) }
                    TextButton(onClick = { vm.deleteDeck(d, keepCards = false); deleting = false; onBack() }) { Text(stringResource(R.string.delete_with_cards), color = NgColors.Error) }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun issueText(i: DeckIssue): String = when (i.code) {
    "no_commander" -> stringResource(R.string.issue_no_commander)
    "commander_size" -> stringResource(R.string.issue_commander_size, i.detail)
    "singleton" -> stringResource(R.string.issue_singleton, i.detail)
    "color_identity" -> stringResource(R.string.issue_color_identity, i.detail)
    "main_size" -> stringResource(R.string.issue_main_size, i.detail)
    "side_size" -> stringResource(R.string.issue_side_size, i.detail)
    "max_four" -> stringResource(R.string.issue_max_four, i.detail)
    "banned" -> stringResource(R.string.issue_banned, i.detail)
    "not_legal" -> stringResource(R.string.issue_not_legal, i.detail)
    "game_changers_many" -> stringResource(R.string.issue_game_changers, i.detail)
    else -> i.code
}
