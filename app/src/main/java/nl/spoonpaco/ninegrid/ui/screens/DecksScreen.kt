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
                                    list.sumOf { it.entry.quantity }.let { n -> pluralStringResource(R.plurals.n_cards, n, n) },
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
                        Text(stringResource(R.string.bracket_min, check.bracketHint ?: "?"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (check.bracketReasons.isEmpty()) Text(stringResource(R.string.bracket_no_signals), fontSize = 12.sp, color = extra.subtle)
                        check.bracketReasons.forEach { r ->
                            Text(bracketReasonLabel(r.code) + ": " + r.cards.joinToString(", "), fontSize = 12.sp, color = extra.subtle)
                        }
                        Text(stringResource(R.string.bracket_note), fontSize = 11.sp, color = extra.subtle)
                    }
                    if (check.issues.isEmpty() && d.format != DeckFormat.CASUAL) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.deck_ok), color = NgColors.Synced, fontSize = 13.sp)
                    }
                    check.issues.forEach { i -> Text("• " + issueText(i), color = if (i.level == IssueLevel.ERROR) NgColors.Error else extra.subtle, fontSize = 13.sp) }
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
        // Manacurve als staafjes.
        Text(stringResource(R.string.mana_curve, String.format(java.util.Locale.getDefault(), "%.2f", st.averageCmc)), fontSize = 13.sp)
        val max = (st.curve.maxOrNull() ?: 1).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(90.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.curve.forEachIndexed { i, n ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontSize = 11.sp, color = extra.subtle)
                    Box(Modifier.fillMaxWidth().height((60f * n / max).dp.coerceAtLeast(2.dp)).background(MaterialTheme.colorScheme.primary))
                    Text(if (i == 7) "7+" else "$i", fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.colors_cost_vs_lands), fontSize = 13.sp)
        val totalSym = st.costSymbols.values.sum().coerceAtLeast(1)
        "WUBRG".forEach { c ->
            val sym = st.costSymbols[c] ?: 0; val src = st.landSources[c] ?: 0
            if (sym > 0 || src > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(nl.spoonpaco.ninegrid.ui.theme.NgColors.mana(c)))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.color_line, sym, Math.round(sym * 100.0 / totalSym).toInt(), src, st.lands), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.opening_hand, st.lands, st.deckSize), fontSize = 13.sp)
        listOf(2, 3, 4).forEach { k ->
            Text(stringResource(R.string.odds_line, k, Math.round((st.handOdds[k] ?: 0.0) * 1000) / 10.0), fontSize = 12.sp)
        }
        Text(stringResource(R.string.odds_total, Math.round(st.oddsTwoToFour * 1000) / 10.0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    androidx.compose.runtime.LaunchedEffect(deck.id, deckSize) { vm.computeSuggestions(deck.id) }
    val r = vm.suggestions?.takeIf { vm.suggestionsDeckId == deck.id }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.sugg_rules), fontWeight = FontWeight.Bold)
        androidx.compose.material3.OutlinedTextField(
            rules.tribe, { rules = rules.copy(tribe = it.trim().replaceFirstChar { c -> c.uppercase() }) }, singleLine = true,
            label = { Text(stringResource(R.string.sugg_tribe)) }, placeholder = { Text("Vampire") }, modifier = Modifier.fillMaxWidth(),
        )
        if (deck.format == DeckFormat.COMMANDER) {
            Text(stringResource(R.string.sugg_max_bracket), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..4).forEach { b ->
                    androidx.compose.material3.FilterChip(selected = rules.maxBracket == b, onClick = { rules = rules.copy(maxBracket = b) },
                        label = { Text(if (b == 0) stringResource(R.string.sugg_no_limit) else "$b") })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = rules.includeOtherDecks, onCheckedChange = { rules = rules.copy(includeOtherDecks = it) })
            Text(stringResource(R.string.sugg_other_decks), fontSize = 13.sp)
        }
        androidx.compose.material3.OutlinedButton(onClick = { vm.setDeckRules(deck.id, rules) }) { Text(stringResource(R.string.sugg_apply)) }
        if (r == null) { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)); return@Column }

        // Wat het deck per rol heeft tegenover een streefgetal.
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.sugg_roles), fontWeight = FontWeight.Bold)
        r.roles.filter { it.value.second > 0 }.forEach { (role, v) ->
            Text(roleName(role) + ": ${v.first} / ${v.second}", fontSize = 12.sp, color = if (v.first < v.second) nl.spoonpaco.ninegrid.ui.theme.NgColors.Error else extra.subtle)
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.sugg_adds), fontWeight = FontWeight.Bold)
        if (r.adds.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = 12.sp, color = extra.subtle)
        r.adds.forEach { s ->
            SuggestionRow(s.card.name + (s.card.where?.let { " · $it" } ?: ""), s.reasons.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.pick_put_in_deck)) {
                vm.addSuggestion(s.card.key, deck.id)
            }
        }
        if (r.lands.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.sugg_lands), fontWeight = FontWeight.Bold)
            r.lands.forEach { s ->
                SuggestionRow(s.card.name + (s.card.where?.let { " · $it" } ?: ""), stringResource(R.string.sugg_land_swap), stringResource(R.string.sugg_swap)) {
                    vm.swapLand(s.card.key, deck.id)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.sugg_cuts), fontWeight = FontWeight.Bold)
        if (r.cuts.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = 12.sp, color = extra.subtle)
        r.cuts.forEach { c ->
            SuggestionRow(c.name, c.reasons.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.sugg_to_collection)) {
                vm.cutSuggestion(c.entryKey, deck.id)
            }
        }
        Text(stringResource(R.string.sugg_note), fontSize = 11.sp, color = extra.subtle, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SuggestionRow(title: String, why: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (why.isNotBlank()) Text(why, fontSize = 11.sp, color = LocalNgExtra.current.subtle)
        }
        androidx.compose.material3.TextButton(onClick = onClick) { Text(action) }
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
