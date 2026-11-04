package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.DeckFormat
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.Destination
import nl.spoonpaco.ninegrid.ui.ImportSource
import nl.spoonpaco.ninegrid.ui.ItemStatus
import nl.spoonpaco.ninegrid.ui.PendingItem
import nl.spoonpaco.ninegrid.ui.components.CardSearchDialog
import nl.spoonpaco.ninegrid.ui.components.CardThumb
import nl.spoonpaco.ninegrid.ui.components.FoilBadge
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/** Bevestigen wat er bij komt: na scannen, plakken of zoeken. Er komt niets in de collectie zonder deze stap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: (Long?) -> Unit) {
    val settings by vm.settings.collectAsState()
    val decks by vm.decks.collectAsState()
    val binders by vm.binders.collectAsState()
    val extra = LocalNgExtra.current
    var searchFor by remember { mutableStateOf<PendingItem?>(null) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var duplicates by remember { mutableStateOf<List<Int>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val items = vm.pending
    val found = items.count { it.status == ItemStatus.FOUND && it.include }
    val missing = items.count { it.status == ItemStatus.NOT_FOUND && it.include }
    val isCommander = vm.destination == Destination.NEW_DECK && vm.newDeckFormat == DeckFormat.COMMANDER
    val noCommanderChosen = isCommander && items.none { it.include && it.section == Section.COMMANDER }

    fun save() {
        scope.launch {
            val d = vm.duplicateSlots()
            if (d.isNotEmpty()) duplicates = d else vm.savePending(onSaved)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("review") },
            )
        },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                if (missing > 0) Text(stringResource(R.string.review_missing, missing), color = NgColors.Error, fontSize = 13.sp)
                if (noCommanderChosen) Text(stringResource(R.string.review_pick_commander), color = NgColors.Error, fontSize = 13.sp)
                Button(
                    onClick = { save() },
                    enabled = found > 0 && !vm.busy && (vm.destination != Destination.EXISTING_DECK || vm.targetDeckId != null),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.review_save, items.filter { it.include && it.print != null }.sumOf { it.quantity })) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.destination), fontWeight = FontWeight.Bold)
                    DestinationOption(vm.destination == Destination.COLLECTION, stringResource(R.string.collection)) { vm.destination = Destination.COLLECTION }
                    if (vm.importSource != ImportSource.SCAN) {
                        DestinationOption(vm.destination == Destination.NEW_DECK, stringResource(R.string.new_deck)) { vm.destination = Destination.NEW_DECK }
                        if (decks.isNotEmpty()) DestinationOption(vm.destination == Destination.EXISTING_DECK, stringResource(R.string.existing_deck)) { vm.destination = Destination.EXISTING_DECK }
                    }
                    when (vm.destination) {
                        Destination.COLLECTION -> {
                            OutlinedTextField(vm.binder, { vm.binder = it }, label = { Text(stringResource(R.string.binder_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (binders.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                binders.take(4).forEach { b ->
                                    FilterChip(selected = vm.binder == b, onClick = {
                                        vm.binder = b
                                        scope.launch { if (vm.importSource == ImportSource.SCAN) vm.page = vm.repo.nextPage(b).toString() }
                                    }, label = { Text(b) })
                                }
                            }
                            if (vm.binder.isNotBlank()) {
                                OutlinedTextField(vm.page, { vm.page = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.page)) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Destination.NEW_DECK -> {
                            OutlinedTextField(vm.newDeckName, { vm.newDeckName = it }, label = { Text(stringResource(R.string.deck_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.format_suggested), fontSize = 13.sp, color = extra.subtle)
                            ChoiceRow(stringResource(R.string.format), DeckFormat.all, vm.newDeckFormat, { formatLabelPlain(it) }) { vm.newDeckFormat = it }
                            if (isCommander) Text(stringResource(R.string.review_commander_hint), fontSize = 13.sp, color = extra.subtle)
                        }
                        Destination.EXISTING_DECK -> {
                            decks.forEach { d -> DestinationOption(vm.targetDeckId == d.id, d.name) { vm.targetDeckId = d.id } }
                        }
                    }
                }
            }
            if (vm.unreadableLines.isNotEmpty()) item {
                Text(
                    stringResource(R.string.review_unreadable, vm.unreadableLines.joinToString("\n")),
                    color = NgColors.Error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(items, key = { it.key }) { item ->
                PendingRow(
                    item, vm, settings, expanded == item.key,
                    showSection = vm.destination != Destination.COLLECTION,
                    isCommanderDeck = isCommander || (vm.destination == Destination.EXISTING_DECK && decks.firstOrNull { it.id == vm.targetDeckId }?.format == DeckFormat.COMMANDER),
                    onToggle = { expanded = if (expanded == item.key) null else item.key },
                    onSearch = { searchFor = item },
                )
            }
        }
    }

    searchFor?.let { item ->
        CardSearchDialog(
            vm.repo.scryfall, vm.images, settings, initial = item.query.name ?: "",
            onPick = { p -> vm.setPendingPrint(item.key, p); searchFor = null },
            onDismiss = { searchFor = null },
        )
    }
    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { duplicates = emptyList() },
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = { Text(stringResource(R.string.duplicate_body, duplicates.joinToString(", "))) },
            confirmButton = { TextButton(onClick = { duplicates = emptyList(); vm.savePending(onSaved) }) { Text(stringResource(R.string.duplicate_add_anyway)) } },
            dismissButton = { TextButton(onClick = { duplicates = emptyList() }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

fun formatLabelPlain(f: String) = when (f) {
    DeckFormat.COMMANDER -> "Commander"; DeckFormat.MODERN -> "Modern"; DeckFormat.PIONEER -> "Pioneer"
    DeckFormat.STANDARD -> "Standard"; DeckFormat.LEGACY -> "Legacy"; DeckFormat.PAUPER -> "Pauper"; else -> "Casual"
}

@Composable
private fun DestinationOption(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun PendingRow(
    item: PendingItem,
    vm: AppViewModel,
    settings: nl.spoonpaco.ninegrid.data.AppSettings,
    expanded: Boolean,
    showSection: Boolean,
    isCommanderDeck: Boolean,
    onToggle: () -> Unit,
    onSearch: () -> Unit,
) {
    val extra = LocalNgExtra.current
    val p = item.print
    Card(
        colors = CardDefaults.cardColors(containerColor = extra.cardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.include, onCheckedChange = { vm.updatePending(item.copy(include = it)) })
                if (item.slot != null) Text("${item.slot}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp))
                CardThumb(p, vm.images, settings, 30)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                    when (item.status) {
                        ItemStatus.FOUND -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p?.name ?: "", fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            FoilBadge(item.finish)
                        }
                        ItemStatus.RESOLVING -> Text(stringResource(R.string.looking_up), color = extra.subtle)
                        ItemStatus.NOT_FOUND -> Text(stringResource(R.string.not_found), color = NgColors.Error, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (p != null) "${p.setCode} ${p.collectorNumber} · ${p.rarity}" + (if (showSection) " · " + sectionLabelPlain(item.section) else "")
                        else item.label,
                        fontSize = 12.sp, color = extra.subtle, maxLines = 2,
                    )
                }
                if (item.status == ItemStatus.NOT_FOUND || expanded) {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search_card)) }
                }
                IconButton(onClick = { vm.updatePending(item.copy(quantity = (item.quantity - 1).coerceAtLeast(1))) }) { Icon(Icons.Filled.Remove, stringResource(R.string.less)) }
                Text("${item.quantity}", fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.updatePending(item.copy(quantity = item.quantity + 1)) }) { Icon(Icons.Filled.Add, stringResource(R.string.more_one)) }
            }
            if (showSection && p != null && !item.proxy) {
                val have = remember(p.id) { vm.inCollection(p) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 40.dp)) {
                    if (have > 0) {
                        Checkbox(checked = item.fromCollection, onCheckedChange = { vm.updatePending(item.copy(fromCollection = it)) })
                        Text(
                            stringResource(R.string.review_take, have) + if (item.fromCollection && item.quantity > have) " · " + stringResource(R.string.review_shortage, item.quantity - have) else "",
                            fontSize = 12.sp,
                        )
                    } else {
                        Text(stringResource(R.string.review_not_owned), fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 48.dp)) {
                FilterChip(selected = item.finish == Finish.FOIL, onClick = {
                    vm.updatePending(item.copy(finish = if (item.finish == Finish.FOIL) Finish.NONFOIL else Finish.FOIL))
                }, label = { Text(stringResource(R.string.foil)) })
                if (isCommanderDeck && showSection) {
                    FilterChip(selected = item.section == Section.COMMANDER, onClick = {
                        vm.updatePending(item.copy(section = if (item.section == Section.COMMANDER) Section.MAIN else Section.COMMANDER))
                    }, label = { Text(stringResource(R.string.section_commander)) })
                }
                if (!expanded) TextButton(onClick = onToggle) { Text(stringResource(R.string.more_options)) }
            }
            if (expanded) {
                Column(Modifier.padding(start = 48.dp, end = 8.dp)) {
                    ChoiceRow(stringResource(R.string.finish), listOf(Finish.NONFOIL, Finish.FOIL, Finish.ETCHED), item.finish, { finishLabelPlain(it) }) { vm.updatePending(item.copy(finish = it)) }
                    ChoiceRow(stringResource(R.string.language), LANGUAGES, item.language, { if (it.isBlank()) "EN" else it }) { vm.updatePending(item.copy(language = it)) }
                    ChoiceRow(stringResource(R.string.condition), CONDITIONS, item.condition) { vm.updatePending(item.copy(condition = it)) }
                    if (showSection) {
                        ChoiceRow(stringResource(R.string.section), listOf(Section.COMMANDER, Section.MAIN, Section.SIDEBOARD), item.section, { sectionLabelPlain(it) }) { vm.updatePending(item.copy(section = it)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.proxy, onCheckedChange = { vm.updatePending(item.copy(proxy = it)) })
                        Text(stringResource(R.string.proxy_explain), fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onSearch) { Text(stringResource(R.string.choose_other_print)) }
                }
            }
        }
    }
}

fun sectionLabelPlain(s: String) = when (s) { Section.COMMANDER -> "Commander"; Section.SIDEBOARD -> "Sideboard"; else -> "Deck" }
