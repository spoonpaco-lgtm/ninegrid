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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.decks), fontWeight = FontWeight.Bold) }, actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("decks") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewDeck, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, stringResource(R.string.new_deck)) }
        },
    ) { pad ->
        if (decks.isEmpty()) {
            EmptyState(stringResource(R.string.empty_decks_title), stringResource(R.string.empty_decks_body), Modifier.padding(pad))
            return@Scaffold
        }
        var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
        val sort = settings.decksSort
        // Per deck één keer uitrekenen: kaarten, controle en waarde.
        data class DeckLine(val d: nl.spoonpaco.ninegrid.data.db.Deck, val count: Int, val value: Double, val check: nl.spoonpaco.ninegrid.domain.DeckCheck)
        val rows = remember(decks, all, settings.currency) {
            decks.map { d ->
                val list = all.filter { it.entry.deckId == d.id }
                DeckLine(d, list.filter { !it.entry.proxy }.sumOf { it.entry.quantity }, list.sumOf { Money.total(it.entry, it.print, settings.currency) },
                    Formats.check(d.format, Formats.toDeckCards(list.map { it.entry to it.print })))
            }
        }
        val shown = rows.filter { query.isBlank() || it.d.name.contains(query.trim(), true) }.let { l ->
            when (sort) {
                "name" -> l.sortedBy { it.d.name.lowercase() }
                "count" -> l.sortedByDescending { it.count }
                "updated" -> l.sortedByDescending { it.d.updatedAt }
                else -> l.sortedByDescending { it.value }
            }
        }
        // Standaard (sortering "Per format", geen zoekterm): per format met een kopje.
        val grouped = sort == "format" && query.isBlank()
        Column(Modifier.padding(pad)) {
            Spacer(Modifier.height(6.dp))
            nl.spoonpaco.ninegrid.ui.components.FilterSearchBar(
                query = query, onQuery = { query = it }, placeholder = stringResource(R.string.decks_search),
                filter = nl.spoonpaco.ninegrid.ui.components.CardFilter(), onFilter = {}, groups = emptySet(),
                sortOptions = listOf("format" to stringResource(R.string.sort_per_format), "value" to stringResource(R.string.sort_value),
                    "name" to stringResource(R.string.sort_name), "count" to stringResource(R.string.sort_count), "updated" to stringResource(R.string.sort_updated)),
                sort = sort, onSort = { vm.setDecksSort(it) },
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (grouped) {
                    shown.groupBy { it.d.format }.toList().sortedByDescending { (_, l) -> l.sumOf { it.value } }.forEach { (f, l) ->
                        item(key = "h_$f") {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                                Text(formatLabel(f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Text(pluralStringResource(R.plurals.n_decks, l.size, l.size) + " · " + Money.format(l.sumOf { it.value }, settings.currency), fontSize = 12.sp, color = extra.subtle)
                            }
                        }
                        items(l, key = { it.d.id }) { r -> DeckRowCard(r.d, r.count, r.value, r.check, settings.currency) { onDeck(r.d.id) } }
                    }
                } else {
                    items(shown, key = { it.d.id }) { r -> DeckRowCard(r.d, r.count, r.value, r.check, settings.currency) { onDeck(r.d.id) } }
                }
            }
        }
    }
}

@Composable
private fun DeckRowCard(d: nl.spoonpaco.ninegrid.data.db.Deck, count: Int, value: Double, check: nl.spoonpaco.ninegrid.domain.DeckCheck, currency: String, onClick: () -> Unit) {
    val extra = LocalNgExtra.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = extra.cardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(formatLabel(d.format), pluralStringResource(R.plurals.n_cards, count, count), check.bracketHint?.let { stringResource(R.string.bracket_short, it) }).joinToString(" · "),
                    fontSize = 12.sp, color = extra.subtle,
                )
            }
            if (check.issues.any { it.level == IssueLevel.ERROR }) Icon(Icons.Filled.Warning, null, tint = NgColors.Error)
            Spacer(Modifier.width(8.dp))
            ManaPips(check.colorIdentity, size = 16)
            Spacer(Modifier.width(10.dp))
            Text(Money.format(value, currency), color = extra.price, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
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
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("decks")
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
        androidx.compose.runtime.LaunchedEffect(deckId, list.size) { vm.loadCachedCombos(deckId) }
        val report = vm.comboReport?.takeIf { vm.comboDeckId == deckId }
        val signals = report?.let { r ->
            fun cardsOf(ids: List<String>) = ids.mapNotNull { id -> r.included.firstOrNull { it.id == id }?.cards }
            Formats.ComboSignals(cardsOf(r.earlyTwoCard), cardsOf(r.lateTwoCard))
        }
        val check = Formats.check(d.format, Formats.toDeckCards(list.map { it.entry to it.print }), signals)
        val sections = listOf(Section.COMMANDER, Section.MAIN, Section.SIDEBOARD)
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                // Kop: bij Commander de commander zelf, anders het format. Eén samenvattende regel; tik voor de details.
                // Is er een probleem met het deck, dan staat hij open.
                val commanderCard = list.firstOrNull { it.entry.section == Section.COMMANDER }
                val real = list.filter { !it.entry.proxy }
                val cardCount = real.sumOf { it.entry.quantity }
                val totalValue = list.sumOf { Money.total(it.entry, it.print, settings.currency) }
                val hasError = check.issues.any { it.level == IssueLevel.ERROR }
                var open by androidx.compose.runtime.saveable.rememberSaveable(deckId) { mutableStateOf(false) }
                val expanded = open || hasError
                Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (d.format == DeckFormat.COMMANDER && commanderCard?.print != null) {
                            nl.spoonpaco.ninegrid.ui.components.CardThumb(commanderCard.print, vm.images, settings, width = 40)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (d.format == DeckFormat.COMMANDER && commanderCard?.print != null) commanderCard.print.name else formatLabel(d.format),
                            fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f),
                        )
                        ManaPips(check.colorIdentity)
                    }
                    val bits = listOfNotNull(
                        formatLabel(d.format),
                        pluralStringResource(R.plurals.n_cards, cardCount, cardCount),
                        Money.format(totalValue, settings.currency),
                        if (d.format == DeckFormat.COMMANDER) stringResource(R.string.bracket_short, check.bracketHint ?: "?") else null,
                        if (d.format == DeckFormat.CASUAL) null else if (check.issues.isEmpty()) "✓ " + stringResource(R.string.legal) else "⚠ " + pluralStringResource(R.plurals.n_issues, check.issues.size, check.issues.size),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bits.joinToString(" · "), fontSize = 13.sp, color = if (hasError) NgColors.Error else extra.subtle, modifier = Modifier.weight(1f))
                        Text(if (expanded) "▴" else "▾", color = extra.subtle)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        // Format wijzigen: tik op het format (ook via ⋮ → Format wijzigen).
                        Row(Modifier.clickable { formatPicker = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.format) + ": ", color = extra.subtle)
                            Text(formatLabel(d.format), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Edit, stringResource(R.string.change_format), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        sections.forEach { s ->
                            val part = list.filter { (it.entry.section ?: Section.MAIN) == s }
                            if (part.isNotEmpty()) Row {
                                // Bij de commander-regel de naam, niet nog een keer "Commander".
                                val label = if (s == Section.COMMANDER && part.size == 1) part.first().print?.name ?: sectionLabel(s) else sectionLabel(s)
                                val n = part.filter { !it.entry.proxy }.sumOf { it.entry.quantity }
                                Text(label, modifier = Modifier.weight(1f), color = extra.subtle, maxLines = 1)
                                Text(pluralStringResource(R.plurals.n_cards, n, n) + " · " + Money.format(part.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency), color = extra.subtle)
                            }
                        }
                        if (d.format == DeckFormat.COMMANDER) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.bracket_min, check.bracketHint ?: "?"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (check.bracketReasons.isEmpty()) Text(stringResource(R.string.bracket_no_signals), fontSize = 12.sp, color = extra.subtle)
                            check.bracketReasons.forEach { r ->
                                Text(bracketReasonLabel(r.code) + ": " + r.cards.joinToString(", "), fontSize = 12.sp, color = extra.subtle)
                            }
                            Text(stringResource(R.string.bracket_note_short), fontSize = 11.sp, color = extra.subtle)
                        }
                        if (check.issues.isEmpty() && d.format != DeckFormat.CASUAL) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.deck_ok), color = NgColors.Synced, fontSize = 13.sp)
                        }
                        check.issues.forEach { i -> Text("• " + issueText(i), color = if (i.level == IssueLevel.ERROR) NgColors.Error else extra.subtle, fontSize = 13.sp) }
                    }
                }
            }
            // Tabbladen: Kaarten | Statistieken | Combo's. De controle hierboven blijft altijd staan.
            stickyHeader(key = "tabs") {
                androidx.compose.material3.ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_cards)) })
                    androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.deck_stats)) })
                    androidx.compose.material3.Tab(selected = tab == 2, onClick = { tab = 2 }, text = {
                        Text(if (report == null) stringResource(R.string.combos_title) else stringResource(R.string.combos_tab_count, report.included.size), maxLines = 1)
                    })
                    androidx.compose.material3.Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text(stringResource(R.string.tab_suggestions), maxLines = 1) })
                }
            }
            if (tab == 1) item { DeckStatsPanel(nl.spoonpaco.ninegrid.domain.DeckStatsCalc.compute(Formats.toDeckCards(list.map { it.entry to it.print }))) }
            if (tab == 2) item { CombosPanel(vm, d, report) }
            if (tab == 3) item { SuggestionsPanel(vm, d, list.size) }
            // Kaarten toevoegen hoort bij het tabblad Kaarten.
            if (tab == 0) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = { picking = true }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.pick_from_collection))
                    }
                    androidx.compose.material3.OutlinedButton(onClick = { onAddCards(deckId) }) { Text(stringResource(R.string.paste_list)) }
                }
            }
            if (tab == 0) sections.forEach { s ->
                val part = list.filter { (it.entry.section ?: Section.MAIN) == s }.sortedBy { it.print?.name ?: "" }
                if (part.isNotEmpty()) {
                    item(key = "h_$s") { SectionHeader(sectionLabel(s), part.sumOf { it.entry.quantity }.let { n -> pluralStringResource(R.plurals.n_cards, n, n) }) }
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

@Composable
private fun bracketReasonLabel(code: String) = when (code) {
    "game_changers" -> stringResource(R.string.bracket_gc)
    "mass_land_denial" -> stringResource(R.string.bracket_mld)
    "extra_turns" -> stringResource(R.string.bracket_turns)
    "combos_early" -> stringResource(R.string.bracket_combo_early)
    "combos_late" -> stringResource(R.string.bracket_combo_late)
    else -> stringResource(R.string.bracket_tutors)
}

/** Manacurve, kleuren in kosten tegenover kleuren in lands, en de kans op 2 tot 4 lands in je openingshand. */
@Composable
private fun DeckStatsPanel(st: nl.spoonpaco.ninegrid.domain.DeckStats) {
    val extra = LocalNgExtra.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Manacurve: getallen, staven met een vaste bodem en de labels los, zodat het bij elke lettergrootte klopt.
        Text(stringResource(R.string.mana_curve, String.format(java.util.Locale.getDefault(), "%.2f", st.averageCmc)), fontSize = 13.sp)
        val max = (st.curve.maxOrNull() ?: 1).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.curve.forEach { n -> Text(if (n > 0) "$n" else "", fontSize = 11.sp, color = extra.subtle, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.curve.forEach { n ->
                Box(Modifier.weight(1f).height((60f * n / max).dp.coerceAtLeast(2.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.curve.indices.forEach { i -> Text(if (i == 7) "7+" else "$i", fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
        // Kleuren: manabolletjes (zwart is anders onzichtbaar), en ⚠ als de lands achterlopen op het aandeel in de kosten.
        Text(stringResource(R.string.colors_cost_vs_lands), fontSize = 13.sp)
        val totalSym = st.costSymbols.values.sum().coerceAtLeast(1)
        "WUBRG".forEach { c ->
            val sym = st.costSymbols[c] ?: 0; val src = st.landSources[c] ?: 0
            if (sym > 0 || src > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                ManaPips(c.toString())
                Spacer(Modifier.width(6.dp))
                val symPct = sym * 100.0 / totalSym
                val landPct = if (st.lands > 0) src * 100.0 / st.lands else 0.0
                val behind = sym > 0 && landPct + 10 < symPct
                Text(
                    stringResource(R.string.color_line, sym, Math.round(symPct).toInt(), src, st.lands) + if (behind) "  ⚠" else "",
                    fontSize = 12.sp, color = if (behind) NgColors.Amber else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if ("WUBRG".any { c -> val sym = st.costSymbols[c] ?: 0; sym > 0 && (if (st.lands > 0) (st.landSources[c] ?: 0) * 100.0 / st.lands else 0.0) + 10 < sym * 100.0 / totalSym }) {
            Text(stringResource(R.string.colors_behind_note), fontSize = 11.sp, color = extra.subtle)
        }
        Spacer(Modifier.height(10.dp))
        // Openingshand in één regel.
        Text(stringResource(R.string.opening_hand, st.lands, st.deckSize), fontSize = 13.sp)
        fun pct(x: Double) = Math.round(x * 100).toInt()
        Text(
            stringResource(R.string.odds_one_line, pct(st.oddsTwoToFour), pct(st.handOdds[2] ?: 0.0), pct(st.handOdds[3] ?: 0.0), pct(st.handOdds[4] ?: 0.0)),
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Combo's in het deck en bijna-combo's (één kaart mist), via Commander Spellbook. */
@Composable
private fun CombosPanel(vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, report: nl.spoonpaco.ninegrid.data.ComboReport?) {
    val extra = LocalNgExtra.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.combos_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            androidx.compose.material3.OutlinedButton(onClick = { vm.findCombos(deck.id) }, enabled = !vm.comboLoading) {
                Text(stringResource(if (report == null) R.string.combos_find else R.string.combos_refresh))
            }
        }
        if (vm.comboLoading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        if (report == null) {
            Text(stringResource(R.string.combos_explain), fontSize = 12.sp, color = extra.subtle)
            return@Column
        }
        report.spellbookTag?.let { Text(stringResource(R.string.combos_spellbook_tag, spellbookTagLabel(it)), fontSize = 12.sp) }
        if (report.included.isEmpty()) Text(stringResource(R.string.combos_none), fontSize = 13.sp, color = extra.subtle)
        report.included.forEach { c ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(c.cards.joinToString(" + "), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (c.produces.isNotEmpty()) Text("→ " + c.produces.joinToString(", "), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                if (c.prerequisites.isNotBlank()) Text(c.prerequisites, fontSize = 11.sp, color = extra.subtle)
                if (c.twoCard) Text(stringResource(R.string.combos_two_card), fontSize = 11.sp, color = nl.spoonpaco.ninegrid.ui.theme.NgColors.Error)
            }
        }
        if (report.almost.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.combos_almost), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            report.almost.forEach { c ->
                val missing = c.missing.first()
                val own = vm.inCollectionByName(missing)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(c.cards.joinToString(" + "), fontSize = 12.sp)
                        Text(
                            stringResource(R.string.combos_missing, missing) + if (own != null) " · " + (locationText(own.entry, emptyMap()) ?: stringResource(R.string.collection_loose)) else " · " + stringResource(R.string.combos_not_owned),
                            fontSize = 11.sp, color = if (own != null) nl.spoonpaco.ninegrid.ui.theme.NgColors.Synced else extra.subtle,
                        )
                    }
                    if (own != null) androidx.compose.material3.TextButton(onClick = { vm.moveEntry(own.entry, 1, deck.id, Section.MAIN, null, null, null) }) {
                        Text(stringResource(R.string.pick_put_in_deck))
                    }
                }
            }
        }
        Text(stringResource(R.string.combos_credit), fontSize = 10.sp, color = extra.subtle, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun spellbookTagLabel(t: String) = when (t) {
    "R" -> "Ruthless"; "S" -> "Spicy"; "P" -> "Powerful"; "O" -> "Oddball"; "PA" -> "Precon Appropriate"; "C" -> "Casual"; else -> t
}

/** Suggesties uit je eigen collectie: erin, betere lands, en wat eruit kan. Met eigen regels per deck. */
@Composable
private fun SuggestionsPanel(vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, deckSize: Int) {
    val extra = LocalNgExtra.current
    var rules by remember(deck.id) { mutableStateOf(vm.deckRules(deck.id)) }
    var editRules by remember { mutableStateOf(false) }
    var swap by remember { mutableStateOf<SwapRequest?>(null) }
    androidx.compose.runtime.LaunchedEffect(deck.id, deckSize) { vm.computeSuggestions(deck.id) }
    val r = vm.suggestions?.takeIf { vm.suggestionsDeckId == deck.id }
    val commander = deck.format == DeckFormat.COMMANDER
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Regels als één samenvattende regel; aanpassen in een eigen venster.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sugg_rules), fontWeight = FontWeight.Bold)
                Text(rulesSummary(rules, commander), fontSize = 12.sp, color = extra.subtle)
            }
            androidx.compose.material3.OutlinedButton(onClick = { editRules = true }) { Text(stringResource(R.string.sugg_rules_button)) }
        }
        if (vm.suggestionsFailed && vm.suggestionsDeckId == deck.id) {
            Text(stringResource(R.string.sugg_failed), fontSize = 13.sp, color = NgColors.Error, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { vm.computeSuggestions(deck.id) }) { Text(stringResource(R.string.sugg_retry)) }
            return@Column
        }
        if (r == null) { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)); return@Column }

        // AI: alleen op verzoek; uit je collectie of ook kaarten die je nog niet hebt.
        AiDeckBlock(vm, deck, onSwap = { inName, outId -> swap = SwapRequest(inName = inName, outEntryId = outId, land = false) })

        // Met welke kleuren de app rekent, zodat je ziet waarom iets wel of niet wordt voorgesteld.
        Text(
            stringResource(R.string.sugg_colors, colorDots(r.colors).ifEmpty { stringResource(R.string.sugg_colorless) }) +
                (if (r.splashColors.isNotEmpty()) "  " + stringResource(R.string.sugg_splash_on, colorDots(r.splashColors)) else ""),
            fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
        )

        // Wat het deck per rol heeft tegenover een streefgetal.
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.sugg_roles), fontWeight = FontWeight.Bold)
        r.roles.filter { it.value.second > 0 }.forEach { (role, v) ->
            Text(roleName(role) + ": ${v.first} / ${v.second}", fontSize = 12.sp, color = if (v.first < v.second) NgColors.Error else extra.subtle)
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.sugg_adds), fontWeight = FontWeight.Bold)
        if (r.adds.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = 12.sp, color = extra.subtle)
        r.adds.forEach { s ->
            SuggestionRow(s.card.name + (s.card.where?.let { " · $it" } ?: ""), s.reasons.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.swap_action)) {
                swap = SwapRequest(inName = s.card.name, outEntryId = r.cuts.firstOrNull()?.entryKey, land = false)
            }
        }
        if (r.lands.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.sugg_lands), fontWeight = FontWeight.Bold)
            r.lands.forEach { s ->
                SuggestionRow(s.card.name + (s.card.where?.let { " · $it" } ?: ""), stringResource(R.string.sugg_land_swap), stringResource(R.string.swap_action)) {
                    swap = SwapRequest(inName = s.card.name, outEntryId = null, land = true)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.sugg_cuts), fontWeight = FontWeight.Bold)
        if (r.cuts.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = 12.sp, color = extra.subtle)
        r.cuts.forEach { c ->
            SuggestionRow(c.name, c.reasons.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.swap_action)) {
                swap = SwapRequest(inName = r.adds.firstOrNull()?.card?.name, outEntryId = c.entryKey, land = false, fromCut = true)
            }
        }
        Text(stringResource(R.string.sugg_note), fontSize = 11.sp, color = extra.subtle, modifier = Modifier.padding(top = 8.dp))
    }

    if (editRules) RulesDialog(rules, commander, onDismiss = { editRules = false }) { nr ->
        rules = nr; editRules = false; vm.setDeckRules(deck.id, nr)
    }
    swap?.let { req -> SwapDialog(vm, deck, req, r, rules) { swap = null } }
}

@Composable
private fun AiDeckBlock(vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, onSwap: (String, Long?) -> Unit) {
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val all by vm.entries.collectAsState()
    var outside by remember { mutableStateOf(false) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.ai_deck_title), fontWeight = FontWeight.Bold)
    if (!settings.aiEnabled) { Text(stringResource(R.string.ai_deck_off), fontSize = 12.sp, color = extra.subtle); return }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.FilterChip(selected = !outside, onClick = { outside = false }, label = { Text(stringResource(R.string.ai_scope_mine)) })
        androidx.compose.material3.FilterChip(selected = outside, onClick = { outside = true }, label = { Text(stringResource(R.string.ai_scope_all)) })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedButton(onClick = { vm.askAiDeck(deck.id, outside) }, enabled = !vm.aiDeckBusy) { Text(stringResource(R.string.ai_ask)) }
        if (vm.aiDeckBusy) { Spacer(Modifier.width(8.dp)); androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
    }
    Text(stringResource(R.string.ai_deck_privacy), fontSize = 11.sp, color = extra.subtle)
    vm.aiDeckError?.let { Text(it, fontSize = 12.sp, color = NgColors.Error) }
    val r = vm.aiDeck?.takeIf { it.deckId == deck.id } ?: return
    val deckEntries = all.filter { it.entry.deckId == deck.id }
    fun outId(name: String?) = name?.let { n -> deckEntries.firstOrNull { it.print?.name == n }?.entry?.id }
    if (r.owned.isEmpty() && r.buy.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = 12.sp, color = extra.subtle)
    r.owned.forEach { s ->
        SuggestionRow(s.inName + (s.outName?.let { "  ⇄  $it" } ?: ""), s.reason, stringResource(R.string.swap_action)) { onSwap(s.inName, outId(s.outName)) }
    }
    if (r.buy.isNotEmpty()) {
        Text(stringResource(R.string.ai_buy_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        r.buy.forEach { b ->
            val price = Money.unitPrice(nl.spoonpaco.ninegrid.data.db.Entry(printId = b.print.id), b.print, settings.currency)
            SuggestionRow(b.print.name + (b.outName?.let { "  ⇄  $it" } ?: "") + "  ·  " + Money.format(price, settings.currency), b.reason,
                if (settings.currency == "USD") "TCGplayer" else "Cardmarket") {
                ((if (settings.currency == "USD") b.print.tcgplayerUri else b.print.cardmarketUri) ?: b.print.scryfallUri)?.let { runCatching { uri.openUri(it) } }
            }
        }
    }
    if (r.dropped > 0) Text(stringResource(R.string.ai_dropped, r.dropped), fontSize = 11.sp, color = extra.subtle)
}

private data class SwapRequest(val inName: String?, val outEntryId: Long?, val land: Boolean, val fromCut: Boolean = false)

private fun colorDots(c: String) = c.map { when (it) { 'W' -> "⚪"; 'U' -> "🔵"; 'B' -> "⚫"; 'R' -> "🔴"; 'G' -> "🟢"; else -> "" } }.joinToString("")

@Composable
private fun rulesSummary(r: nl.spoonpaco.ninegrid.domain.DeckRules, commander: Boolean): String {
    val parts = listOfNotNull(
        r.tribe.takeIf { it.isNotBlank() },
        if (commander && r.maxBracket > 0) stringResource(R.string.sugg_bracket_short, r.maxBracket) else null,
        if (!commander && r.splash) stringResource(R.string.sugg_splash_short) else null,
        if (r.includeOtherDecks) stringResource(R.string.sugg_other_decks_short) else null,
    )
    return if (parts.isEmpty()) stringResource(R.string.sugg_rules_none) else parts.joinToString(" · ")
}

@Composable
private fun RulesDialog(start: nl.spoonpaco.ninegrid.domain.DeckRules, commander: Boolean, onDismiss: () -> Unit, onApply: (nl.spoonpaco.ninegrid.domain.DeckRules) -> Unit) {
    var rules by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sugg_rules)) },
        text = {
            Column {
                OutlinedTextField(
                    rules.tribe, { rules = rules.copy(tribe = it.trim().replaceFirstChar { c -> c.uppercase() }) }, singleLine = true,
                    label = { Text(stringResource(R.string.sugg_tribe)) }, placeholder = { Text("Vampire") }, modifier = Modifier.fillMaxWidth(),
                )
                if (commander) {
                    Text(stringResource(R.string.sugg_max_bracket), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..4).forEach { b ->
                            androidx.compose.material3.FilterChip(selected = rules.maxBracket == b, onClick = { rules = rules.copy(maxBracket = b) },
                                label = { Text(if (b == 0) stringResource(R.string.sugg_no_limit) else "$b") })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = rules.splash, onCheckedChange = { rules = rules.copy(splash = it) })
                        Text(stringResource(R.string.sugg_splash), fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = rules.includeOtherDecks, onCheckedChange = { rules = rules.copy(includeOtherDecks = it) })
                    Text(stringResource(R.string.sugg_other_decks), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(rules) }) { Text(stringResource(R.string.sugg_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Ruilvenster: links wat erin gaat, rechts wat eruit gaat, met een aantal. Bij een land kies je welk basic land
 * (en welke print) eruit gaat. De ruil is één wijziging, dus in één keer ongedaan te maken.
 */
@Composable
private fun SwapDialog(
    vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, req: SwapRequest,
    report: nl.spoonpaco.ninegrid.domain.SuggestionReport?, rules: nl.spoonpaco.ninegrid.domain.DeckRules, onDismiss: () -> Unit,
) {
    val extra = LocalNgExtra.current
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val commander = deck.format == DeckFormat.COMMANDER
    var inName by remember { mutableStateOf(req.inName) }
    var outId by remember { mutableStateOf(if (req.land) null else req.outEntryId) }
    var pickedOut by remember { mutableStateOf(!req.land) }
    var qty by remember { mutableStateOf(1) }

    // Waar de kaart die erin gaat vandaan komt: eerst losse kaarten, dan (als dat mag) andere decks.
    val sources = all.filter { e ->
        e.print?.name == inName && !e.entry.proxy && e.entry.deckId != deck.id && (e.entry.deckId == null || rules.includeOtherDecks)
    }.sortedBy { if (it.entry.deckId == null) 0 else 1 }
    val available = sources.sumOf { it.entry.quantity }
    val inPrint = sources.firstOrNull()?.print
    val inDeck = all.filter { it.entry.deckId == deck.id && it.entry.section != Section.COMMANDER }

    // Wat eruit kan: bij een land de basics (kleuren van het nieuwe land bovenaan), anders de schrappen-lijst en de rest.
    val outOptions = if (req.land) {
        val colors = inPrint?.let { nl.spoonpaco.ninegrid.domain.Suggestions.landColors(it.typeLine, it.oracleText, it.producedMana, it.colorIdentity) }.orEmpty()
        val type = mapOf('W' to "Plains", 'U' to "Island", 'B' to "Swamp", 'R' to "Mountain", 'G' to "Forest")
        inDeck.filter { Formats.isBasicLand(it.print?.typeLine.orEmpty()) }
            .sortedWith(compareBy({ b -> if (colors.any { c -> b.print?.typeLine.orEmpty().contains(type[c] ?: "-") }) 0 else 1 }, { it.print?.name }, { -it.entry.quantity }))
    } else {
        val cutOrder = report?.cuts?.map { it.name }.orEmpty()
        inDeck.filter { !it.print?.typeLine.orEmpty().contains("Land") }
            .sortedWith(compareBy({ e -> cutOrder.indexOf(e.print?.name).let { if (it < 0) Int.MAX_VALUE else it } }, { it.print?.name }))
    }
    if (req.land && !pickedOut && outOptions.isNotEmpty()) { outId = outOptions.first().entry.id; pickedOut = true }
    val out = outOptions.firstOrNull { it.entry.id == outId } ?: inDeck.firstOrNull { it.entry.id == outId }

    // Hoeveel: niet meer dan je hebt, niet meer dan 4 (Commander: 1), en niet meer dan er van de uitgaande kaart in het deck zit.
    val limit = if (commander) 1 else 4
    val maxQty = listOfNotNull(
        if (inName != null) available else null,
        out?.entry?.quantity,
        if (inName != null || !req.land) limit else null,
    ).minOrNull()?.coerceAtLeast(1) ?: 1
    if (qty > maxQty) qty = maxQty
    var showAllOut by remember { mutableStateOf(false) }
    var chooseIn by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swap_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwapSide(stringResource(R.string.swap_in), inName?.let { inPrint } , inName ?: stringResource(R.string.swap_nothing_in), vm, settings, Modifier.weight(1f))
                    SwapSide(stringResource(R.string.swap_out), out?.print, out?.print?.let { p -> p.name + if (req.land) " · ${p.setCode}" else "" } ?: stringResource(R.string.swap_nothing_out), vm, settings, Modifier.weight(1f))
                }
                if (inName != null && available == 0) Text(stringResource(R.string.swap_none_available), fontSize = 12.sp, color = NgColors.Error)

                // Erin kiezen, als je vanuit "Eruit" begon.
                if (req.fromCut) {
                    TextButton(onClick = { chooseIn = !chooseIn }) { Text(stringResource(R.string.swap_choose_in)) }
                    if (chooseIn) {
                        SwapOption(stringResource(R.string.swap_nothing_in), inName == null) { inName = null; chooseIn = false }
                        report?.adds.orEmpty().forEach { s -> SwapOption(s.card.name, inName == s.card.name) { inName = s.card.name; chooseIn = false } }
                    }
                }

                // Eruit kiezen.
                Spacer(Modifier.height(6.dp))
                Text(stringResource(if (req.land) R.string.swap_choose_basic else R.string.swap_choose_out), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                SwapOption(stringResource(R.string.swap_nothing_out), outId == null) { outId = null }
                val shown = if (showAllOut || req.land) outOptions else outOptions.take(6)
                shown.forEach { e ->
                    val p = e.print
                    val label = if (req.land) "${p?.name} · ${p?.setCode} ${p?.collectorNumber} · ${e.entry.quantity}×" else "${p?.name} · ${e.entry.quantity}×"
                    SwapOption(label, outId == e.entry.id) { outId = e.entry.id }
                }
                if (!req.land && !showAllOut && outOptions.size > 6) TextButton(onClick = { showAllOut = true }) { Text(stringResource(R.string.swap_more)) }

                // Aantal.
                if (maxQty > 1) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.swap_qty), modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (qty > 1) qty-- }) { Text("−") }
                    Text("$qty", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { if (qty < maxQty) qty++ }) { Text("+") }
                }
                Text(stringResource(R.string.swap_note), fontSize = 11.sp, color = extra.subtle, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = (inName != null && available > 0) || out != null,
                onClick = {
                    // Erin: uit één of meer stapels tot het aantal bereikt is.
                    var left = if (inName != null) qty else 0
                    val ins = sources.mapNotNull { e -> if (left <= 0) null else minOf(left, e.entry.quantity).also { left -= it }.let { e.entry.id to it } }
                    val outs = out?.let { listOf(it.entry.id to qty.coerceAtMost(it.entry.quantity)) }.orEmpty()
                    vm.swapCards(deck.id, ins, outs)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.swap_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SwapSide(title: String, print: nl.spoonpaco.ninegrid.data.db.CardPrint?, name: String, vm: AppViewModel, settings: nl.spoonpaco.ninegrid.data.AppSettings, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 12.sp, color = LocalNgExtra.current.subtle)
        if (print != null) nl.spoonpaco.ninegrid.ui.components.CardThumb(print, vm.images, settings, width = 110)
        else Box(Modifier.width(110.dp).height(154.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun SwapOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun SuggestionRow(title: String, why: String, action: String, onClick: () -> Unit) {
    // De hele regel is tikbaar, niet alleen de knop.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (why.isNotBlank()) Text(why, fontSize = 11.sp, color = LocalNgExtra.current.subtle)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun roleName(r: nl.spoonpaco.ninegrid.domain.Role) = when (r) {
    nl.spoonpaco.ninegrid.domain.Role.RAMP -> stringResource(R.string.role_ramp)
    nl.spoonpaco.ninegrid.domain.Role.DRAW -> stringResource(R.string.role_draw)
    nl.spoonpaco.ninegrid.domain.Role.REMOVAL -> stringResource(R.string.role_removal)
    nl.spoonpaco.ninegrid.domain.Role.WIPE -> stringResource(R.string.role_wipe)
    nl.spoonpaco.ninegrid.domain.Role.COUNTER -> stringResource(R.string.role_counter)
    nl.spoonpaco.ninegrid.domain.Role.PROTECTION -> stringResource(R.string.role_protection)
    nl.spoonpaco.ninegrid.domain.Role.TOKENS -> stringResource(R.string.role_tokens)
    nl.spoonpaco.ninegrid.domain.Role.RECURSION -> stringResource(R.string.role_recursion)
}

@Composable
private fun reasonText(code: String): String {
    val arg = code.substringAfter(':', "")
    return when (code.substringBefore(':')) {
        "tribe" -> stringResource(R.string.why_tribe, arg)
        "tribe_text" -> stringResource(R.string.why_tribe_text, arg)
        "not_tribe" -> stringResource(R.string.why_not_tribe, arg)
        "role" -> stringResource(R.string.why_role, roleName(nl.spoonpaco.ninegrid.domain.Role.valueOf(arg)))
        "theme" -> stringResource(R.string.why_theme, arg)
        "cheap" -> stringResource(R.string.why_cheap)
        "expensive" -> stringResource(R.string.why_expensive)
        "no_role" -> stringResource(R.string.why_no_role)
        "over_bracket" -> stringResource(R.string.why_over_bracket)
        else -> ""
    }
}
