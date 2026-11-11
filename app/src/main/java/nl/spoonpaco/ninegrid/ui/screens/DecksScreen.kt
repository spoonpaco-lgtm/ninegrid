@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package nl.spoonpaco.ninegrid.ui.screens

import nl.spoonpaco.ninegrid.ui.theme.NgText
import nl.spoonpaco.ninegrid.ui.theme.NgShape
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.SegmentedButton
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.decks), fontWeight = FontWeight.Bold) }, actions = { nl.spoonpaco.ninegrid.ui.components.PrivacyButton(vm); nl.spoonpaco.ninegrid.ui.guide.HelpButton("decks") }) },
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
                                Text(pluralStringResource(R.plurals.n_decks, l.size, l.size) + " · " + nl.spoonpaco.ninegrid.ui.components.money(l.sumOf { it.value }, settings.currency), fontSize = NgText.body, color = extra.subtle)
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
        shape = NgShape.Normal,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(formatLabel(d.format), pluralStringResource(R.plurals.n_cards, count, count), check.bracketHint?.let { stringResource(R.string.bracket_short, it) }).joinToString(" · "),
                    fontSize = NgText.body, color = extra.subtle,
                )
            }
            if (check.issues.any { it.level == IssueLevel.ERROR }) Icon(Icons.Filled.Warning, null, tint = NgColors.Error)
            Spacer(Modifier.width(8.dp))
            ManaPips(check.colorIdentity, size = 16)
            Spacer(Modifier.width(10.dp))
            Text(nl.spoonpaco.ninegrid.ui.components.money(value, currency), color = extra.price, fontWeight = FontWeight.Bold, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeckDetailScreen(vm: AppViewModel, deckId: Long, onBack: () -> Unit, onCard: (String) -> Unit, onAddCards: (Long) -> Unit, onFriend: (String) -> Unit = {}, onScan: () -> Unit = {}) {
    val deck by vm.repo.deck(deckId).collectAsState(initial = null)
    val list by vm.repo.entriesForDeck(deckId).collectAsState(initial = emptyList())
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val extra = LocalNgExtra.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var formatPicker by remember { mutableStateOf(false) }
    var commanderDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var cardQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var cardSort by remember(deckId) { mutableStateOf(vm.deckSort(deckId)) }
    val deckFilter = remember(settings.filters["deck"]) { nl.spoonpaco.ninegrid.ui.components.CardFilter.fromJson(settings.filters["deck"]) }
    var collapsed by remember(deckId) { mutableStateOf(vm.deckCollapsed(deckId)) }
    val d = deck
    val text = Export.deckText(list, d?.format == DeckFormat.COMMANDER)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.writeText(uri, text)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasBanner = d?.format == DeckFormat.COMMANDER && list.any { it.entry.section == Section.COMMANDER && it.print != null }
    Scaffold(topBar = {
        TopAppBar(
            // Met een commander-banner staat de naam pas in de balk als de banner weggescrold is.
            title = { if (!hasBanner || listState.firstVisibleItemIndex > 0) Text(d?.name ?: "", maxLines = 1) },
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
                    if (d?.format == DeckFormat.COMMANDER) DropdownMenuItem(text = { Text(stringResource(R.string.commander_change)) }, onClick = { menu = false; commanderDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_text)) }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) }, onClick = { menu = false; saveLauncher.launch("${d?.name ?: "deck"}.txt") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_deck)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; deleting = true })
                }
            },
        )
    }) { pad ->
        if (d == null) return@Scaffold
        androidx.compose.runtime.LaunchedEffect(deckId, list.size) { vm.loadCachedCombos(deckId) }
        val report = vm.comboReport?.takeIf { vm.comboDeckId == deckId }
        // Is het deck veranderd, dan tellen alleen combo's waarvan beide kaarten er nog in zitten.
        val names = list.map { it.print?.name?.lowercase() }.toSet()
        val signals = report?.let { r ->
            Formats.ComboSignals(late = r.twoCardCombos.filter { c -> c.all { it.lowercase() in names } })
        }
        val check = Formats.check(d.format, Formats.toDeckCards(list.map { it.entry to it.print }), signals)
        val sections = listOf(Section.COMMANDER, Section.MAIN, Section.SIDEBOARD)
        LazyColumn(Modifier.padding(pad), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
            if (hasBanner) item(key = "banner") {
                CommanderBanner(vm, list.filter { it.entry.section == Section.COMMANDER && it.print != null }, check.colorIdentity, onCard)
            }
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
                Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = if (hasBanner) 6.dp else 16.dp)) {
                    if (!hasBanner) Row(verticalAlignment = Alignment.CenterVertically) {
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
                        nl.spoonpaco.ninegrid.ui.components.money(totalValue, settings.currency),
                        if (d.format == DeckFormat.COMMANDER) stringResource(R.string.bracket_short, check.bracketHint ?: "?") else null,
                        if (d.format == DeckFormat.CASUAL) null else if (check.issues.isEmpty()) "✓ " + stringResource(R.string.legal) else "⚠ " + pluralStringResource(R.plurals.n_issues, check.issues.size, check.issues.size),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bits.joinToString(" · "), fontSize = NgText.body, color = if (hasError) NgColors.Error else extra.subtle, modifier = Modifier.weight(1f))
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
                                Text(pluralStringResource(R.plurals.n_cards, n, n) + " · " + nl.spoonpaco.ninegrid.ui.components.money(part.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency), color = extra.subtle)
                            }
                        }
                        if (d.format == DeckFormat.COMMANDER) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.bracket_min, check.bracketHint ?: "?"), fontSize = NgText.body, fontWeight = FontWeight.Bold)
                            if (check.bracketReasons.isEmpty()) Text(stringResource(R.string.bracket_no_signals), fontSize = NgText.body, color = extra.subtle)
                            check.bracketReasons.forEach { r ->
                                Text(bracketReasonLabel(r.code) + ": " + r.cards.joinToString(", "), fontSize = NgText.body, color = extra.subtle)
                            }
                            Text(stringResource(R.string.bracket_note_short), fontSize = NgText.caption, color = extra.subtle)
                        }
                        if (d.format != DeckFormat.CASUAL) Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val banned = check.issues.any { it.code == "banned" || it.code == "not_legal" }
                            when {
                                banned -> nl.spoonpaco.ninegrid.ui.components.Stamp(stringResource(R.string.stamp_banned), NgColors.Error, "⚒")
                                check.issues.isEmpty() -> nl.spoonpaco.ninegrid.ui.components.Stamp(stringResource(R.string.legal), NgColors.Synced, "✓")
                                else -> {}
                            }
                            if (check.issues.isEmpty()) { Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.deck_ok), color = NgColors.Synced, fontSize = NgText.body) }
                        }
                        check.issues.forEach { i -> Text("• " + issueText(i), color = if (i.level == IssueLevel.ERROR) NgColors.Error else extra.subtle, fontSize = NgText.body) }
                    }
                }
            }
            // Tabbladen: Kaarten | Statistieken | Combo's. De controle hierboven blijft altijd staan.
            stickyHeader(key = "tabs") {
                androidx.compose.material3.ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    androidx.compose.material3.Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_cards)) })
                    androidx.compose.material3.Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.deck_stats)) })
                    androidx.compose.material3.Tab(selected = tab == 2, onClick = { tab = 2 }, text = {
                        Text(if (report == null || report.included.isEmpty()) stringResource(R.string.combos_title) else stringResource(R.string.combos_tab_count, report.included.size), maxLines = 1)
                    })
                    androidx.compose.material3.Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text(stringResource(R.string.tab_suggestions), maxLines = 1) })
                }
            }
            if (tab == 1) item { DeckStatsPanel(vm, d, list, onLands = { tab = 3 }) }
            if (tab == 2) item { CombosPanel(vm, d, report, onFriend, onCard) }
            if (tab == 3) item { SuggestionsPanel(vm, d, list, onFriend, onCard) }
            // Tabblad Kaarten: zoeken en toevoegen op één regel, dan de kaarten per type.
            if (tab == 0) item(key = "tools") {
                // Zoeken en filteren (kleur, type, rarity) zoals in de collectie; de filterkeuze wordt onthouden.
                Column(Modifier.padding(top = 8.dp)) {
                nl.spoonpaco.ninegrid.ui.components.FilterSearchBar(
                    query = cardQuery, onQuery = { cardQuery = it }, placeholder = stringResource(R.string.deck_search),
                    filter = deckFilter, onFilter = { vm.setFilter("deck", it) },
                    groups = setOf(nl.spoonpaco.ninegrid.ui.components.FilterGroup.COLOR, nl.spoonpaco.ninegrid.ui.components.FilterGroup.TYPE, nl.spoonpaco.ninegrid.ui.components.FilterGroup.RARITY),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Box {
                        var addMenu by remember { mutableStateOf(false) }
                        androidx.compose.material3.Button(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(2.dp)); Text(stringResource(R.string.deck_add) + " ▾") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.pick_from_collection)) }, onClick = { addMenu = false; picking = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.paste_list)) }, onClick = { addMenu = false; onAddCards(deckId) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.deck_add_scan)) }, onClick = { addMenu = false; vm.scanIntoDeck(deckId); onScan() })
                        }
                    }
                }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.deck_by_type), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.weight(1f))
                    Box {
                        var sortMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { sortMenu = true }) { Text(stringResource(R.string.deck_sort_label, deckSortName(cardSort)) + " ▾", fontSize = NgText.body) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("mana", "name", "price").forEach { k ->
                                DropdownMenuItem(text = { Text(deckSortName(k)) }, onClick = { sortMenu = false; cardSort = k; vm.setDeckSort(deckId, k) })
                            }
                        }
                    }
                }
            }
            if (tab == 0) {
                val q = cardQuery.trim()
                val visible = list.filter { e ->
                    val sec = e.entry.section ?: Section.MAIN
                    !(hasBanner && sec == Section.COMMANDER) && deckFilter.copy(decks = true).matches(e, "") &&
                        (q.isEmpty() || e.print?.name?.contains(q, true) == true || e.print?.typeLine?.contains(q, true) == true)
                }
                fun sorted(l: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>) = when (cardSort) {
                    "name" -> l.sortedBy { it.print?.name ?: "" }
                    "price" -> l.sortedByDescending { Money.total(it.entry, it.print, settings.currency) }
                    else -> l.sortedWith(compareBy<nl.spoonpaco.ninegrid.data.db.EntryWithPrint> { it.print?.cmc ?: 0.0 }.thenBy { it.print?.name ?: "" })
                }
                // Groepen: commander (zonder banner), de typen van het main deck, en het sideboard apart.
                val groups = buildList {
                    visible.filter { it.entry.section == Section.COMMANDER }.takeIf { it.isNotEmpty() }?.let { add("commander" to it) }
                    val main = visible.filter { (it.entry.section ?: Section.MAIN) == Section.MAIN }
                    nl.spoonpaco.ninegrid.ui.components.DECK_GROUP_ORDER.forEach { g ->
                        main.filter { nl.spoonpaco.ninegrid.ui.components.deckGroupOf(it.print?.typeLine ?: "") == g }.takeIf { it.isNotEmpty() }?.let { add(g to it) }
                    }
                    visible.filter { it.entry.section == Section.SIDEBOARD }.takeIf { it.isNotEmpty() }?.let { add("sideboard" to it) }
                }
                if (groups.isEmpty() && (q.isNotEmpty() || deckFilter.count > 0)) item { Text(stringResource(R.string.sugg_none), color = extra.subtle, modifier = Modifier.padding(16.dp)) }
                // Het kaartscherm veegt door de kaarten in de volgorde van dit scherm (gesloten groepen tellen niet mee).
                val deckPrintIds = groups.filter { q.isNotEmpty() || it.first !in collapsed }.flatMap { (_, part) -> sorted(part) }.mapNotNull { it.print?.id }
                groups.forEach { (g, part) ->
                    // Bij zoeken staan alle groepen open.
                    val closed = q.isEmpty() && g in collapsed
                    val n = part.filter { !it.entry.proxy }.sumOf { it.entry.quantity }
                    item(key = "g_$g") {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                collapsed = if (g in collapsed) collapsed - g else collapsed + g
                                vm.setDeckCollapsed(deckId, collapsed)
                            }.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text((if (closed) "▸ " else "▾ ") + deckGroupName(g), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text("$n · " + nl.spoonpaco.ninegrid.ui.components.money(part.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency), fontSize = NgText.body, color = extra.subtle)
                        }
                    }
                    if (!closed) items(sorted(part), key = { it.entry.id }) { e ->
                        nl.spoonpaco.ninegrid.ui.components.DeckCardRow(e.entry, e.print, settings, vm.images) { e.print?.let { vm.setCardList(deckPrintIds); onCard(it.id) } }
                    }
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
    if (commanderDialog && d != null) ChangeCommanderDialog(vm, d, onDismiss = { commanderDialog = false })
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
private fun deckSortName(k: String) = when (k) {
    "name" -> stringResource(R.string.deck_sort_name); "price" -> stringResource(R.string.deck_sort_price); else -> stringResource(R.string.deck_sort_mana)
}

@Composable
private fun deckGroupName(g: String) = when (g) {
    "commander" -> stringResource(R.string.section_commander)
    "sideboard" -> stringResource(R.string.section_sideboard)
    "Creature" -> stringResource(R.string.type_creatures); "Planeswalker" -> stringResource(R.string.type_planeswalkers)
    "Instant" -> stringResource(R.string.type_instants); "Sorcery" -> stringResource(R.string.type_sorceries)
    "Artifact" -> stringResource(R.string.type_artifacts); "Enchantment" -> stringResource(R.string.type_enchantments)
    "Battle" -> stringResource(R.string.type_battles); "Land" -> stringResource(R.string.type_lands)
    else -> stringResource(R.string.type_other)
}

/** Banner met de art van de commander (of beide partners): naam en kleuren erover. Tik op een naam = die kaart. */
@Composable
private fun CommanderBanner(vm: AppViewModel, commanders: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>, colors: String, onCard: (String) -> Unit) {
    val settings by vm.settings.collectAsState()
    val first = commanders.first().print!!
    // Scryfall heeft naast "normal" ook een "art_crop": alleen de tekening.
    val art = vm.images.urlFor(first, large = true)?.replace("/normal/", "/art_crop/")?.replace("/large/", "/art_crop/")?.replace("version=normal", "version=art_crop")
    Box(Modifier.fillMaxWidth().height(170.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (settings.imageMode != nl.spoonpaco.ninegrid.data.ImageMode.OFF && art != null) coil.compose.AsyncImage(
            model = vm.images.request(art, settings), imageLoader = vm.images.loader, contentDescription = first.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to androidx.compose.ui.graphics.Color.Transparent, 0.45f to androidx.compose.ui.graphics.Color.Transparent,
            1f to MaterialTheme.colorScheme.background,
        )))
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill = false)) {
                commanders.forEach { c ->
                    Text(c.print!!.name, fontWeight = FontWeight.Bold, fontSize = if (commanders.size > 1) 18.sp else 22.sp, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.clickable { onCard(c.print.id) })
                }
            }
            Spacer(Modifier.width(8.dp))
            ManaPips(colors)
        }
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

/** Een kaart in een decktabblad: titel met ⓘ (uitleg in een venster) en de inhoud. */
@Composable
private fun TabCard(
    title: String, info: String? = null, trailing: (@Composable () -> Unit)? = null, accent: Boolean = false,
    /** Inklapbaar blok: null = altijd open; anders dicht of open, met [onToggle] bij een tik op de kop. */
    closed: Boolean? = null, onToggle: () -> Unit = {},
    /** Korte uitleg onder de titel (alleen als het blok open is). */
    explain: String? = null,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val extra = LocalNgExtra.current
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (accent) Color(0xFF2A2236) else extra.cardSurface),
        border = if (accent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5B4A78)) else null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (closed != null) Modifier.fillMaxWidth().clip(NgShape.Normal).clickable(onClick = onToggle) else Modifier,
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = NgText.heading)
                if (info != null) androidx.compose.material3.IconButton(onClick = { open = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp), tint = extra.subtle)
                }
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
                if (closed != null) Text(if (closed) "▸" else "▾", fontSize = NgText.heading, color = extra.subtle, modifier = Modifier.padding(start = 8.dp))
            }
            if (closed != true) {
                if (explain != null) Text(explain, fontSize = NgText.body, lineHeight = 16.sp, color = extra.subtle, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
    if (open && info != null) AlertDialog(
        onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(info) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.done)) } },
    )
}

/** Een balkje: groen als het doel gehaald is, oranje als er te weinig is. */
@Composable
private fun RoleBar(label: String, have: Int, target: Int, onClick: (() -> Unit)? = null, open: Boolean = false) {
    val ok = have >= target
    Row(Modifier.fillMaxWidth().let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label + if (onClick != null) (if (open) " ▴" else " ▾") else "", fontSize = NgText.body, modifier = Modifier.width(110.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(NgShape.Small).background(LocalNgExtra.current.subtle.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (target <= 0) 1f else (have.toFloat() / target).coerceIn(0.03f, 1f))
                .clip(NgShape.Small).background(if (ok) NgColors.Synced else NgColors.Amber))
        }
        Text("$have / $target" + if (ok) " ✓" else "", fontSize = NgText.body, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun deckTypeName(t: String) = when (t) {
    "aggro" -> stringResource(R.string.decktype_aggro); "control" -> stringResource(R.string.decktype_control); else -> stringResource(R.string.decktype_midrange)
}

/** Statistieken: manacurve met landadvies, kleuren, openingshand en kaarttypen. */
@Composable
private fun DeckStatsPanel(vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, list: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>, onLands: () -> Unit) {
    val extra = LocalNgExtra.current
    val cards = remember(list) { Formats.toDeckCards(list.map { it.entry to it.print }) }
    val st = remember(cards) { nl.spoonpaco.ninegrid.domain.DeckStatsCalc.compute(cards) }
    val commander = deck.format == DeckFormat.COMMANDER
    val type = remember(cards, deck.id) { nl.spoonpaco.ninegrid.domain.Suggestions.deckTypeOf(cards, vm.deckRules(deck.id), commander) }
    var hand by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        TabCard(stringResource(R.string.mana_curve_title), info = stringResource(R.string.mana_curve_info),
            trailing = { Text(stringResource(R.string.nonlands_n, st.curve.sum()), fontSize = NgText.body, color = extra.subtle) }) {
            val max = (st.curve.maxOrNull() ?: 1).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                st.curve.forEach { n -> Text(if (n > 0) "$n" else "", fontSize = NgText.caption, color = extra.subtle, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                st.curve.forEach { n -> Box(Modifier.weight(1f).height((66f * n / max).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(MaterialTheme.colorScheme.primary)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                st.curve.indices.forEach { i -> Text(if (i == 7) "7+" else "$i", fontSize = NgText.caption, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            // Oordeel over de curve, volgens het soort deck.
            val limit = when (type) { "aggro" -> if (commander) 3.0 else 2.6; "control" -> if (commander) 4.0 else 3.4; else -> if (commander) 3.5 else 3.0 }
            val avg = String.format(java.util.Locale.getDefault(), "%.1f", st.averageCmc)
            Row(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.curve_avg, avg) + " · ", fontSize = NgText.body)
                Text(
                    if (st.averageCmc <= limit) stringResource(R.string.curve_fits, deckTypeName(type)) else stringResource(R.string.curve_high, deckTypeName(type)),
                    fontSize = NgText.body, fontWeight = FontWeight.Bold, color = if (st.averageCmc <= limit) NgColors.Synced else NgColors.Amber,
                )
            }
            val adv = st.landAdvice
            if (st.deckSize >= 40) Row {
                Text(stringResource(R.string.lands_advice, st.lands, adv.first, adv.last), fontSize = NgText.body)
                when {
                    st.lands > adv.last -> Text(" · " + stringResource(R.string.lands_fewer, st.lands - adv.last), fontSize = NgText.body, fontWeight = FontWeight.Bold, color = NgColors.Amber)
                    st.lands < adv.first -> Text(" · " + stringResource(R.string.lands_more, adv.first - st.lands), fontSize = NgText.body, fontWeight = FontWeight.Bold, color = NgColors.Amber)
                    else -> Text(" ✓", fontSize = NgText.body, color = NgColors.Synced)
                }
            }
        }
        TabCard(stringResource(R.string.colors_title), info = stringResource(R.string.colors_info)) {
            val totalSym = st.costSymbols.values.sum().coerceAtLeast(1)
            var behind: Pair<Char, Int>? = null
            "WUBRG".forEach { c ->
                val sym = st.costSymbols[c] ?: 0; val src = st.landSources[c] ?: 0
                if (sym > 0 || src > 0) {
                    val symPct = sym * 100.0 / totalSym
                    val landPct = if (st.lands > 0) src * 100.0 / st.lands else 0.0
                    val late = sym > 0 && landPct + 10 < symPct
                    if (late && behind == null) behind = c to Math.ceil(symPct / 100.0 * st.lands - src).toInt().coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        ManaPips(c.toString())
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            PairBar(stringResource(R.string.colors_costs), symPct, manaColor(c), false)
                            PairBar(stringResource(R.string.colors_lands), landPct, manaColor(c), late)
                        }
                    }
                }
            }
            behind?.let { (c, n) ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(NgShape.Normal).background(NgColors.Amber.copy(alpha = 0.14f)).clickable(onClick = onLands).padding(8.dp)) {
                    Text("⚠ " + stringResource(R.string.colors_behind, colorName(c), n) + "  ", fontSize = NgText.body, color = NgColors.Amber)
                    Text(stringResource(R.string.see_lands), fontSize = NgText.body, fontWeight = FontWeight.Bold, color = NgColors.Amber)
                }
            }
        }
        TabCard(stringResource(R.string.opening_hand_title), info = stringResource(R.string.opening_hand_info, st.lands, st.deckSize)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${Math.round(st.oddsTwoToFour * 100)}%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.odds_two_four), fontSize = NgText.body, modifier = Modifier.padding(bottom = 5.dp))
            }
            val mx = (0..7).maxOf { st.handOdds[it] ?: 0.0 }.coerceAtLeast(0.01)
            Row(Modifier.fillMaxWidth().height(60.dp).padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..7).forEach { i ->
                    val v = st.handOdds[i] ?: 0.0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${Math.round(v * 100)}%", fontSize = NgText.caption, color = extra.subtle)
                        Box(Modifier.fillMaxWidth().height((40 * v / mx).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (i in 2..4) MaterialTheme.colorScheme.primary else extra.subtle.copy(alpha = 0.4f)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (0..7).forEach { Text("$it", fontSize = NgText.caption, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) } }
            androidx.compose.material3.Button(onClick = { hand = true }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.draw_seven)) }
        }
        if (st.types.isNotEmpty()) TabCard(stringResource(R.string.card_types_title)) {
            val total = st.types.values.sum().coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(12.dp).clip(NgShape.Small)) {
                st.types.forEach { (t, n) -> Box(Modifier.weight(n.toFloat() / total).fillMaxHeight().background(typeColor(t))) }
            }
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                st.types.forEach { (t, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(NgShape.Small).background(typeColor(t))); Spacer(Modifier.width(4.dp))
                        Text("${typeName(t)} $n", fontSize = NgText.body)
                    }
                }
            }
        }
    }
    if (hand) SampleHandDialog(vm, list, onDismiss = { hand = false })
}

@Composable
private fun PairBar(label: String, pct: Double, color: Color, warn: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = NgText.caption, color = LocalNgExtra.current.subtle, modifier = Modifier.width(52.dp))
        Box(Modifier.weight(1f).height(7.dp).clip(NgShape.Small).background(LocalNgExtra.current.subtle.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((pct / 100).toFloat().coerceIn(0.02f, 1f)).clip(NgShape.Small).background(color))
        }
        Text("${Math.round(pct)}%" + if (warn) " ⚠" else "", fontSize = NgText.caption, color = if (warn) NgColors.Amber else LocalNgExtra.current.subtle,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(48.dp))
    }
}

private fun manaColor(c: Char) = when (c) {
    'W' -> Color(0xFFF3ECD2); 'U' -> Color(0xFF3D74B8); 'B' -> Color(0xFF8A7F90); 'R' -> Color(0xFFD8412F); 'G' -> Color(0xFF3F8A4F); else -> Color.Gray
}

@Composable
private fun colorName(c: Char) = when (c) {
    'W' -> stringResource(R.string.color_w); 'U' -> stringResource(R.string.color_u); 'B' -> stringResource(R.string.color_b)
    'R' -> stringResource(R.string.color_r); else -> stringResource(R.string.color_g)
}

private fun typeColor(t: String) = when (t) {
    "Creature" -> Color(0xFF5FAE6A); "Instant" -> Color(0xFF5B8BC6); "Sorcery" -> Color(0xFFC2543C); "Artifact" -> Color(0xFF8B6F4E)
    "Enchantment" -> Color(0xFFC9A24A); "Planeswalker" -> Color(0xFFB07CE0); "Battle" -> Color(0xFFD08A5A); else -> Color(0xFF6B5536)
}

@Composable
private fun typeName(t: String) = when (t) {
    "Creature" -> stringResource(R.string.type_creatures); "Instant" -> stringResource(R.string.type_instants); "Sorcery" -> stringResource(R.string.type_sorceries)
    "Artifact" -> stringResource(R.string.type_artifacts); "Enchantment" -> stringResource(R.string.type_enchantments); "Planeswalker" -> stringResource(R.string.type_planeswalkers)
    "Battle" -> stringResource(R.string.type_battles); else -> stringResource(R.string.type_lands)
}

/** Voorbeeldhand: 7 willekeurige kaarten uit de bibliotheek (zonder commander en sideboard), met Mulligan en Trek nog een. */
@Composable
private fun SampleHandDialog(vm: AppViewModel, list: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val library = remember(list) {
        list.filter { (it.entry.section ?: Section.MAIN) == Section.MAIN && it.print != null }.flatMap { e -> List(e.entry.quantity) { e.print!! } }
    }
    var deckOrder by remember { mutableStateOf(library.shuffled()) }
    var drawn by remember { mutableStateOf(7) }
    var mulligans by remember { mutableStateOf(0) }
    val hand = deckOrder.take(drawn)
    val lands = hand.count { nl.spoonpaco.ninegrid.domain.DeckStatsCalc.isLand(it.typeLine) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sample_hand_title) + if (mulligans > 0) " · " + stringResource(R.string.mulligan_n, mulligans) else "") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(stringResource(R.string.sample_hand_lands, lands, hand.size), fontSize = NgText.body, color = if (lands in 2..4) NgColors.Synced else NgColors.Amber)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hand.forEach { p -> nl.spoonpaco.ninegrid.ui.components.CardThumb(p, vm.images, settings, 70) }
                }
                if (settings.imageMode == nl.spoonpaco.ninegrid.data.ImageMode.OFF) hand.forEach { Text(it.name, fontSize = NgText.body) }
            }
        },
        confirmButton = { TextButton(onClick = { if (drawn < deckOrder.size) drawn++ }) { Text(stringResource(R.string.draw_one_more)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { deckOrder = library.shuffled(); drawn = 7; mulligans++ }) { Text(stringResource(R.string.mulligan)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
    )
}

/** Combo's in het deck en bijna-combo's (één kaart mist), via Commander Spellbook. */
@Composable
private fun CombosPanel(vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, report: nl.spoonpaco.ninegrid.data.ComboReport?, onFriend: (String) -> Unit, onCard: (String) -> Unit) {
    val extra = LocalNgExtra.current
    val settings by vm.settings.collectAsState()
    var asCards by remember { mutableStateOf(vm.comboCards()) }
    var big by remember { mutableStateOf<String?>(null) }
    // De twee blokken klappen in en uit; per deck onthouden, standaard open.
    var comboClosed by remember(deck.id) { mutableStateOf(vm.comboCollapsed(deck.id)) }
    fun toggleCombo(k: String) { comboClosed = if (k in comboClosed) comboClosed - k else comboClosed + k; vm.setComboCollapsed(deck.id, comboClosed) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (report == null) stringResource(R.string.combos_never) else "Commander Spellbook · " + stringResource(R.string.combos_searched, nl.spoonpaco.ninegrid.domain.Dates.dateTime(report.fetchedAt, settings.dateStyle)),
                fontSize = NgText.body, color = extra.subtle, modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.OutlinedButton(onClick = { vm.findCombos(deck.id) }, enabled = !vm.comboLoading) {
                Text(stringResource(if (report == null) R.string.combos_find else R.string.combos_refresh), maxLines = 1)
            }
        }
        if (vm.comboLoading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (report != null) ThumbnailSwitch(asCards) { asCards = it; vm.setComboCards(it) }
        if (report == null) {
            Text(stringResource(R.string.combos_explain), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(16.dp))
            return@Column
        }
        if (vm.comboStale) androidx.compose.material3.Surface(
            color = NgColors.Amber.copy(alpha = 0.15f), shape = NgShape.Normal,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(enabled = !vm.comboLoading) { vm.findCombos(deck.id) },
        ) {
            Text(stringResource(R.string.combos_stale), fontSize = NgText.body, color = NgColors.Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp))
        }
        if (deck.format == DeckFormat.COMMANDER) TabCard(stringResource(R.string.combos_bracket_title), info = stringResource(R.string.combos_bracket_info)) {
            val n = report.twoCardCombos.size
            Text(
                if (n > 0) stringResource(R.string.combos_bracket_line, n) else stringResource(R.string.combos_bracket_none),
                fontSize = NgText.body, fontWeight = if (n > 0) FontWeight.Bold else FontWeight.Normal, color = if (n > 0) MaterialTheme.colorScheme.primary else extra.subtle,
            )
            report.spellbookTag?.let { Text(stringResource(R.string.combos_spellbook_tag, spellbookTagLabel(it)), fontSize = NgText.body, color = extra.subtle) }
        }
        TabCard(stringResource(R.string.combos_in_deck), trailing = { Text("${report.included.size}", color = extra.subtle) }, closed = "in" in comboClosed, onToggle = { toggleCombo("in") }) {
            if (report.included.isEmpty()) Text(stringResource(R.string.combos_none), fontSize = NgText.body, color = extra.subtle)
            report.included.forEachIndexed { i, c ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 6.dp))
                var open by remember(c.id) { mutableStateOf(false) }
                val two = if (report.spellbookTag != null) report.twoCardCombos.any { it.toSet() == c.cards.toSet() } else c.twoCard
                ComboRow(vm, settings, c.cards, missing = null, twoCard = two, asCards = asCards, onCard = onCard, onBig = { big = it })
                if (c.produces.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.produces.take(4).forEach { Pill(it, Color(0xFF1D2B38), Color(0xFF9FD3FF)) }
                }
                if (c.prerequisites.isNotBlank()) {
                    Text((if (open) "▴ " else "▾ ") + stringResource(R.string.combos_prereq), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(top = 4.dp).clickable { open = !open })
                    if (open) Text(c.prerequisites, fontSize = NgText.body)
                }
            }
        }
        if (report.almost.isNotEmpty()) TabCard(stringResource(R.string.combos_almost_title), trailing = { Text("${report.almost.size}", color = extra.subtle) }, closed = "almost" in comboClosed, onToggle = { toggleCombo("almost") }) {
            report.almost.forEachIndexed { i, c ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val missing = c.missing.first()
                ComboRow(vm, settings, c.cards, missing = missing, twoCard = false, asCards = asCards, onCard = onCard, onBig = { big = it })
                val own = vm.inCollectionByName(missing)
                val friend = if (own == null) vm.friendsHaving(missing).firstOrNull() else null
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        own != null -> {
                            Pill(stringResource(R.string.combos_in_collection) + " · " + (locationText(own.entry, emptyMap()) ?: stringResource(R.string.collection_loose)), Color(0xFF1F3A24), Color(0xFF8FDC9A))
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.moveEntry(own.entry, 1, deck.id, Section.MAIN, null, null, null) }) { Text(stringResource(R.string.pick_put_in_deck) + " ›") }
                        }
                        friend != null -> {
                            Pill(stringResource(R.string.combos_at_friend, friend.first.name, friend.second), Color(0xFF2A2442), Color(0xFFC7B3FF))
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onFriend(friend.first.id) }) { Text(stringResource(R.string.share_tab_trade) + " ›") }
                        }
                        else -> {
                            Pill(stringResource(R.string.combos_not_owned), extra.subtle.copy(alpha = 0.2f), extra.subtle)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.addToWishlistByName(missing) }) { Text(stringResource(R.string.wish_add_btn) + " ›") }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.combos_credit), fontSize = NgText.caption, color = extra.subtle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
    BigCardDialog(vm, settings, big) { big = null }
}

/** Eén segmentbalk Thumbnails | Namen, op Combo's en Suggesties op dezelfde plek. */
@Composable
private fun ThumbnailSwitch(asCards: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        androidx.compose.material3.SegmentedButton(
            selected = asCards, onClick = { onChange(true) },
            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("▦ " + stringResource(R.string.combos_view_cards)) }
        androidx.compose.material3.SegmentedButton(
            selected = !asCards, onClick = { onChange(false) },
            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("☰ " + stringResource(R.string.combos_view_names)) }
    }
}

/** Plaatje van een kaart op naam: uit het deck of de collectie als je hem hebt, anders via Scryfall. */
private fun comboImageUrl(vm: AppViewModel, name: String, large: Boolean): String {
    val own = vm.entries.value.firstOrNull { it.print?.name?.substringBefore(" // ").equals(name, true) }?.print
    return vm.images.urlFor(own, large) ?: ("https://api.scryfall.com/cards/named?format=image&version=" + (if (large) "normal" else "small") + "&exact=" + java.net.URLEncoder.encode(name, "UTF-8"))
}

/** Een kleine kaartafbeelding op naam. Tik opent het kaartscherm (ook van een kaart die je niet hebt), vasthouden toont hem groot. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CardThumbByName(
    vm: AppViewModel, settings: nl.spoonpaco.ninegrid.data.AppSettings, name: String, onCard: (String) -> Unit, onBig: (String) -> Unit,
    width: Int = 72, dimmed: Boolean = false,
) {
    val extra = LocalNgExtra.current
    val shape = NgShape.Small
    val ownedId = vm.entries.value.firstOrNull { it.print?.name?.substringBefore(" // ").equals(name, true) }?.print?.id
    Box(
        Modifier.width(width.dp).height((width * 1.4f).dp).clip(shape)
            .let { m -> if (dimmed) m.border(1.5.dp, NgColors.Amber, shape) else m }
            .background(extra.subtle.copy(alpha = 0.15f))
            .combinedClickable(onClick = { if (ownedId != null) onCard(ownedId) else vm.openCardByName(name, onCard) }, onLongClick = { onBig(comboImageUrl(vm, name, true)) }),
    ) {
        coil.compose.AsyncImage(
            model = vm.images.request(comboImageUrl(vm, name, false), settings), imageLoader = vm.images.loader, contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = if (dimmed) 0.45f else 1f, modifier = Modifier.matchParentSize(),
        )
    }
}

/** De grote kaart na lang indrukken. */
@Composable
private fun BigCardDialog(vm: AppViewModel, settings: nl.spoonpaco.ninegrid.data.AppSettings, url: String?, onClose: () -> Unit) {
    if (url == null) return
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        coil.compose.AsyncImage(
            model = vm.images.request(url, settings), imageLoader = vm.images.loader, contentDescription = null,
            modifier = Modifier.fillMaxWidth().clip(NgShape.Large).clickable(onClick = onClose),
        )
    }
}

/** Een combo als kaartjes met + ertussen (of als namen). Tik opent de kaart, lang indrukken toont hem groot. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ComboRow(
    vm: AppViewModel, settings: nl.spoonpaco.ninegrid.data.AppSettings, cards: List<String>, missing: String?, twoCard: Boolean,
    asCards: Boolean, onCard: (String) -> Unit, onBig: (String) -> Unit,
) {
    if (!asCards || settings.imageMode == nl.spoonpaco.ninegrid.data.ImageMode.OFF) { ComboPills(cards, missing, twoCard); return }
    val extra = LocalNgExtra.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cards.forEachIndexed { i, n ->
            if (i > 0) Text("+", color = extra.subtle, fontWeight = FontWeight.Bold)
            CardThumbByName(vm, settings, n, onCard, onBig, dimmed = n.equals(missing, true))
        }
        if (twoCard) Pill(stringResource(R.string.combos_two_card), Color(0xFFB7412F), Color.White)
    }
}

/** Kaarten van een combo als labels met +; de ontbrekende kaart gestippeld. */
@Composable
private fun ComboPills(cards: List<String>, missing: String?, twoCard: Boolean) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cards.forEachIndexed { i, n ->
            if (i > 0) Text("+", color = LocalNgExtra.current.subtle, modifier = Modifier.align(Alignment.CenterVertically))
            val miss = n.equals(missing, true)
            Text(
                n, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(NgShape.Normal)
                    .let { m -> if (miss) m.border(1.5.dp, NgColors.Amber, NgShape.Normal) else m.background(LocalNgExtra.current.subtle.copy(alpha = 0.2f)) }
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        if (twoCard) Pill(stringResource(R.string.combos_two_card), Color(0xFFB7412F), Color.White)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, fontSize = NgText.caption, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.clip(NgShape.Normal).background(bg).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
private fun spellbookTagLabel(t: String) = when (t) {
    "R" -> "Ruthless"; "S" -> "Spicy"; "P" -> "Powerful"; "O" -> "Oddball"; "PA" -> "Precon Appropriate"; "C" -> "Casual"; else -> t
}

/** Suggesties: regels, rollen tegenover doelen, wat erin of eruit kan, wat vrienden hebben, en AI onderaan. */
@Composable
private fun SuggestionsPanel(vm: AppViewModel, deck: nl.spoonpaco.ninegrid.data.db.Deck, list: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>, onFriend: (String) -> Unit, onCard: (String) -> Unit) {
    val extra = LocalNgExtra.current
    val settings by vm.settings.collectAsState()
    var rules by remember(deck.id) { mutableStateOf(vm.deckRules(deck.id)) }
    var editRules by remember { mutableStateOf(false) }
    var swap by remember { mutableStateOf<SwapRequest?>(null) }
    var openRole by remember { mutableStateOf<nl.spoonpaco.ninegrid.domain.Role?>(null) }
    var tickRole by remember { mutableStateOf<nl.spoonpaco.ninegrid.domain.Role?>(null) }
    // Thumbnails of namen: dezelfde keuze (en dezelfde segmentbalk) als bij Combo's.
    var asCards by remember { mutableStateOf(vm.comboCards()) }
    var big by remember { mutableStateOf<String?>(null) }
    val thumbsOn = asCards && settings.imageMode != nl.spoonpaco.ninegrid.data.ImageMode.OFF
    fun thumbFor(name: String): (@Composable () -> Unit)? = if (thumbsOn) ({ CardThumbByName(vm, settings, name, onCard, { big = it }, width = 54) }) else null
    androidx.compose.runtime.LaunchedEffect(deck.id, list.size, rules) { vm.computeSuggestions(deck.id); vm.computeFriendOffers(deck.id) }
    val r = vm.suggestions?.takeIf { vm.suggestionsDeckId == deck.id }
    val commander = deck.format == DeckFormat.COMMANDER
    fun save(nr: nl.spoonpaco.ninegrid.domain.DeckRules) { rules = nr; vm.setDeckRules(deck.id, nr) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // Regels in één regel, met de kleuren waarmee gerekend wordt.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (r?.let { colorDots(it.colors).ifEmpty { stringResource(R.string.sugg_colorless) } + (if (it.splashColors.isNotEmpty()) " +" + colorDots(it.splashColors) else "") + "  ·  " } ?: "") +
                        rulesSummary(rules, commander),
                    fontSize = NgText.body,
                )
            }
            androidx.compose.material3.OutlinedButton(onClick = { editRules = true }) { Text(stringResource(R.string.sugg_rules_button)) }
        }
        if (vm.suggestionsFailed && vm.suggestionsDeckId == deck.id) {
            Text(stringResource(R.string.sugg_failed), fontSize = NgText.body, color = NgColors.Error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            TextButton(onClick = { vm.computeSuggestions(deck.id) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.sugg_retry)) }
            return@Column
        }
        if (r == null) { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)); return@Column }
        ThumbnailSwitch(asCards) { asCards = it; vm.setComboCards(it) }

        TabCard(stringResource(R.string.sugg_roles), info = stringResource(R.string.sugg_roles_info)) {
            Row(Modifier.fillMaxWidth().clip(NgShape.Normal).background(extra.subtle.copy(alpha = 0.12f)).clickable { editRules = true }.padding(8.dp)) {
                Text(
                    stringResource(if (r.deckTypeAuto) R.string.sugg_targets_auto else R.string.sugg_targets_set, deckTypeName(r.deckType)) + "  ",
                    fontSize = NgText.body, modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.sugg_targets_change), fontSize = NgText.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            r.roles.filter { it.value.second > 0 }.forEach { (role, v) ->
                RoleBar(roleName(role), v.first, v.second, onClick = { openRole = if (openRole == role) null else role }, open = openRole == role)
                if (openRole == role) Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                    val names = r.roleCards[role].orEmpty()
                    if (names.isEmpty()) Text(stringResource(R.string.sugg_role_none), fontSize = NgText.body, color = extra.subtle)
                    else Text(names.joinToString(" · "), fontSize = NgText.body, color = extra.subtle)
                    TextButton(onClick = { tickRole = role }) { Text("+ " + stringResource(R.string.sugg_role_tick)) }
                }
            }
        }

        // De drie blokken klappen in en uit; per deck onthouden.
        var suggClosed by remember(deck.id) { mutableStateOf(vm.suggCollapsed(deck.id)) }
        fun toggleSugg(k: String) { suggClosed = if (k in suggClosed) suggClosed - k else suggClosed + k; vm.setSuggCollapsed(deck.id, suggClosed) }
        TabCard(stringResource(R.string.sugg_adds) + " (${r.adds.size})", closed = "adds" in suggClosed, onToggle = { toggleSugg("adds") }, explain = stringResource(R.string.sugg_adds_explain)) {
            if (r.adds.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = NgText.body, color = extra.subtle)
            r.adds.forEach { s ->
                SuggestionRow(s.card.name + (s.card.where?.let { " · $it" } ?: ""), s.reasons.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.swap_action), {
                    swap = SwapRequest(inName = s.card.name, outEntryId = r.cuts.firstOrNull()?.entryKey, land = false)
                }, thumbFor(s.card.name))
            }
        }
        if (r.lands.isNotEmpty()) TabCard(stringResource(R.string.sugg_lands) + " (${r.lands.size})", closed = "lands" in suggClosed, onToggle = { toggleSugg("lands") }, explain = stringResource(R.string.sugg_lands_explain)) {
            r.lands.forEach { s ->
                SuggestionRow(s.card.name + (s.card.where?.let { " · $it" } ?: ""), "", stringResource(R.string.swap_action), {
                    swap = SwapRequest(inName = s.card.name, outEntryId = null, land = true)
                }, thumbFor(s.card.name))
            }
        }
        TabCard(stringResource(R.string.sugg_cuts) + " (${r.cuts.size})", closed = "cuts" in suggClosed, onToggle = { toggleSugg("cuts") }, explain = stringResource(R.string.sugg_cuts_explain)) {
            if (r.cuts.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = NgText.body, color = extra.subtle)
            r.cuts.forEach { c ->
                SuggestionRow(c.name, c.reasons.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.swap_action), {
                    swap = SwapRequest(inName = r.adds.firstOrNull()?.card?.name, outEntryId = c.entryKey, land = false, fromCut = true)
                }, thumbFor(c.name))
            }
        }
        // Bij vrienden: losse kaarten uit gedeelde collecties die dit deck beter maken.
        val offers = vm.friendOffers.takeIf { vm.friendOffersDeck == deck.id }.orEmpty()
        if (offers.isNotEmpty()) TabCard(stringResource(R.string.sugg_friends_title), info = stringResource(R.string.sugg_friends_info)) {
            offers.take(8).forEach { o ->
                SuggestionRow(
                    o.item.print.name + " · " + o.friend.name + "  ·  " + Money.format(o.item.unit, settings.currency),
                    o.item.reasons.filter { it != "wish" }.map { reasonText(it) }.joinToString(" · "), stringResource(R.string.see_trade),
                    { onFriend(o.friend.id) }, thumbFor(o.item.print.name),
                )
            }
        }
        AiDeckBlock(vm, deck, onSwap = { inName, outId -> swap = SwapRequest(inName = inName, outEntryId = outId, land = false) })
        Text(stringResource(R.string.sugg_note), fontSize = NgText.caption, color = extra.subtle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }

    BigCardDialog(vm, settings, big) { big = null }
    if (editRules) RulesDialog(rules, commander, r, onDismiss = { editRules = false }) { nr -> editRules = false; save(nr) }
    tickRole?.let { role ->
        val counted = r?.roleCards?.get(role).orEmpty().toSet()
        val manual = rules.roleCards[role.name].orEmpty().toSet()
        val names = list.filter { (it.entry.section ?: Section.MAIN) != Section.SIDEBOARD && it.print != null && !nl.spoonpaco.ninegrid.domain.DeckStatsCalc.isLand(it.print.typeLine) }
            .map { it.print!!.name }.distinct().sorted()
        var picked by remember(role) { mutableStateOf(manual) }
        AlertDialog(
            onDismissRequest = { tickRole = null },
            title = { Text(stringResource(R.string.sugg_role_tick_title, roleName(role))) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.sugg_role_tick_body), fontSize = NgText.body, color = extra.subtle)
                    names.forEach { n ->
                        val auto = n in counted && n !in manual
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = !auto) { picked = if (n in picked) picked - n else picked + n }) {
                            androidx.compose.material3.Checkbox(checked = auto || n in picked, onCheckedChange = null, enabled = !auto)
                            Text(n + if (auto) "  · " + stringResource(R.string.sugg_role_auto) else "", fontSize = NgText.body, color = if (auto) extra.subtle else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val m = rules.roleCards.toMutableMap(); if (picked.isEmpty()) m.remove(role.name) else m[role.name] = picked.sorted()
                save(rules.copy(roleCards = m)); tickRole = null
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { tickRole = null }) { Text(stringResource(R.string.cancel)) } },
        )
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
    if (!settings.aiEnabled) {
        Text(stringResource(R.string.ai_deck_off), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        return
    }
    TabCard(stringResource(R.string.ai_deck_title), info = stringResource(R.string.ai_deck_privacy), accent = true) {
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !outside, onClick = { outside = false }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.ai_scope_mine), maxLines = 1) }
            SegmentedButton(selected = outside, onClick = { outside = true }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.ai_scope_all), maxLines = 1) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            androidx.compose.material3.Button(onClick = { vm.askAiDeck(deck.id, outside) }, enabled = !vm.aiDeckBusy) { Text(stringResource(R.string.ai_ask)) }
            if (vm.aiDeckBusy) { Spacer(Modifier.width(8.dp)); androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        }
        vm.aiDeckError?.let { Text(it, fontSize = NgText.body, color = NgColors.Error) }
        val r = vm.aiDeck?.takeIf { it.deckId == deck.id } ?: return@TabCard
        val deckEntries = all.filter { it.entry.deckId == deck.id }
        fun outId(name: String?) = name?.let { n -> deckEntries.firstOrNull { it.print?.name == n }?.entry?.id }
        if (r.owned.isEmpty() && r.buy.isEmpty()) Text(stringResource(R.string.sugg_none), fontSize = NgText.body, color = extra.subtle)
        r.owned.forEach { s ->
            SuggestionRow(s.inName + (s.outName?.let { "  ⇄  $it" } ?: ""), s.reason, stringResource(R.string.swap_action), onClick = { onSwap(s.inName, outId(s.outName)) })
        }
        if (r.buy.isNotEmpty()) {
            Text(stringResource(R.string.ai_buy_title), fontWeight = FontWeight.Bold, fontSize = NgText.body, modifier = Modifier.padding(top = 6.dp))
            r.buy.forEach { b ->
                val price = Money.unitPrice(nl.spoonpaco.ninegrid.data.db.Entry(printId = b.print.id), b.print, settings.currency)
                Column(Modifier.padding(vertical = 2.dp)) {
                    SuggestionRow(b.print.name + (b.outName?.let { "  ⇄  $it" } ?: "") + "  ·  " + nl.spoonpaco.ninegrid.ui.components.money(price, settings.currency), b.reason,
                        if (settings.currency == "USD") "TCGplayer" else "Cardmarket",
                        onClick = {
                            ((if (settings.currency == "USD") b.print.tcgplayerUri else b.print.cardmarketUri) ?: b.print.scryfallUri)?.let { runCatching { uri.openUri(it) } }
                        })
                    TextButton(onClick = { vm.addToWishlistByName(b.print.name) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("+ " + stringResource(R.string.wish_add_btn), fontSize = NgText.body) }
                }
            }
        }
        if (r.dropped > 0) Text(stringResource(R.string.ai_dropped, r.dropped), fontSize = NgText.caption, color = extra.subtle)
    }
}

private data class SwapRequest(val inName: String?, val outEntryId: Long?, val land: Boolean, val fromCut: Boolean = false)

private fun colorDots(c: String) = c.map { when (it) { 'W' -> "⚪"; 'U' -> "🔵"; 'B' -> "⚫"; 'R' -> "🔴"; 'G' -> "🟢"; else -> "" } }.joinToString("")

@Composable
private fun rulesSummary(r: nl.spoonpaco.ninegrid.domain.DeckRules, commander: Boolean): String {
    val parts = listOfNotNull(
        r.tribe.takeIf { it.isNotBlank() },
        if (r.deckType != "auto") deckTypeName(r.deckType) else null,
        if (commander && r.maxBracket > 0) stringResource(R.string.sugg_bracket_short, r.maxBracket) else null,
        if (!commander && r.splash) stringResource(R.string.sugg_splash_short) else null,
        if (r.includeOtherDecks) stringResource(R.string.sugg_other_decks_short) else null,
    )
    return if (parts.isEmpty()) stringResource(R.string.sugg_rules_none) else parts.joinToString(" · ")
}

@Composable
private fun RulesDialog(
    start: nl.spoonpaco.ninegrid.domain.DeckRules, commander: Boolean, report: nl.spoonpaco.ninegrid.domain.SuggestionReport?,
    onDismiss: () -> Unit, onApply: (nl.spoonpaco.ninegrid.domain.DeckRules) -> Unit,
) {
    var rules by remember { mutableStateOf(start) }
    val extra = LocalNgExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sugg_rules)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    rules.tribe, { rules = rules.copy(tribe = it.trim().replaceFirstChar { c -> c.uppercase() }) }, singleLine = true,
                    label = { Text(stringResource(R.string.sugg_tribe)) }, placeholder = { Text("Vampire") }, modifier = Modifier.fillMaxWidth(),
                )
                if (commander) {
                    Text(stringResource(R.string.sugg_max_bracket), fontSize = NgText.body, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..4).forEach { b ->
                            androidx.compose.material3.FilterChip(selected = rules.maxBracket == b, onClick = { rules = rules.copy(maxBracket = b) },
                                label = { Text(if (b == 0) stringResource(R.string.sugg_no_limit) else "$b") })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = rules.splash, onCheckedChange = { rules = rules.copy(splash = it) })
                        Text(stringResource(R.string.sugg_splash), fontSize = NgText.body)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = rules.includeOtherDecks, onCheckedChange = { rules = rules.copy(includeOtherDecks = it) })
                    Text(stringResource(R.string.sugg_other_decks), fontSize = NgText.body)
                }
                // Soort deck: bepaalt de streefgetallen. Automatisch tenzij je zelf kiest.
                Text(stringResource(R.string.sugg_deck_type), fontSize = NgText.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                val types = listOf("auto", "aggro", "midrange", "control")
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = rules.deckType == t, onClick = { rules = rules.copy(deckType = t, targets = emptyMap()) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, types.size),
                        ) { Text(if (t == "auto") stringResource(R.string.decktype_auto) else deckTypeName(t), fontSize = NgText.caption, maxLines = 1) }
                    }
                }
                // Doelen per rol: de voorgestelde waarde, met − en + aan te passen.
                val type = if (rules.deckType == "auto") report?.deckType ?: "midrange" else rules.deckType
                val base = nl.spoonpaco.ninegrid.domain.Suggestions.targets(commander, type)
                Text(stringResource(R.string.sugg_targets_title), fontSize = NgText.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                nl.spoonpaco.ninegrid.domain.Role.entries.forEach { role ->
                    val v = rules.targets[role.name] ?: base[role] ?: 0
                    val changed = rules.targets.containsKey(role.name)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(roleName(role), fontSize = NgText.body, modifier = Modifier.weight(1f))
                        IconButton(onClick = { rules = rules.copy(targets = rules.targets + (role.name to (v - 1).coerceAtLeast(0))) }, modifier = Modifier.size(32.dp)) { Text("−") }
                        Text("$v", fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal, color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(28.dp))
                        IconButton(onClick = { rules = rules.copy(targets = rules.targets + (role.name to v + 1)) }, modifier = Modifier.size(32.dp)) { Text("+") }
                    }
                }
                if (rules.targets.isNotEmpty()) TextButton(onClick = { rules = rules.copy(targets = emptyMap()) }) { Text(stringResource(R.string.sugg_targets_reset)) }
                Text(stringResource(R.string.sugg_targets_note), fontSize = NgText.caption, color = extra.subtle)
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
                if (inName != null && available == 0) Text(stringResource(R.string.swap_none_available), fontSize = NgText.body, color = NgColors.Error)

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
                Text(stringResource(if (req.land) R.string.swap_choose_basic else R.string.swap_choose_out), fontWeight = FontWeight.Bold, fontSize = NgText.body)
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
                Text(stringResource(R.string.swap_note), fontSize = NgText.caption, color = extra.subtle, modifier = Modifier.padding(top = 4.dp))
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
        Text(title, fontSize = NgText.body, color = LocalNgExtra.current.subtle)
        if (print != null) nl.spoonpaco.ninegrid.ui.components.CardThumb(print, vm.images, settings, width = 110)
        else Box(Modifier.width(110.dp).height(154.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, NgShape.Small))
        Text(name, fontSize = NgText.body, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun SwapOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = NgText.body)
    }
}

@Composable
private fun SuggestionRow(title: String, why: String, action: String, onClick: () -> Unit, thumb: (@Composable () -> Unit)? = null) {
    // De hele regel is tikbaar, niet alleen de knop. Het plaatje opent de kaart (tik) of toont hem groot (vasthouden).
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp)) {
        if (thumb != null) { thumb(); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = NgText.body, fontWeight = FontWeight.SemiBold)
            if (why.isNotBlank()) Text(why, fontSize = NgText.caption, color = LocalNgExtra.current.subtle)
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
fun reasonText(code: String): String {
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
