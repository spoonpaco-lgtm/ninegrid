package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.offline.OwnedCombo
import nl.spoonpaco.ninegrid.domain.ComboFilter
import nl.spoonpaco.ninegrid.domain.ComboGroup
import nl.spoonpaco.ninegrid.domain.ComboView
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.money
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors
import nl.spoonpaco.ninegrid.ui.theme.NgShape
import nl.spoonpaco.ninegrid.ui.theme.NgText

/**
 * Combo's in je collectie (0.9.18). Uit de combodatabase van Commander Spellbook op de telefoon: wat je compleet hebt,
 * wat één kaart mist, en welke ontbrekende kaarten de meeste combo's afmaken. Altijd actueel, zonder knop.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CombosScreen(
    vm: AppViewModel, startCard: String?, onBack: () -> Unit, onCard: (String) -> Unit,
    onFriend: (String) -> Unit, onNewDeck: () -> Unit, onIntoDeck: (Long) -> Unit,
) {
    val extra = LocalNgExtra.current
    val settings by vm.settings.collectAsState()
    val decks by vm.decks.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var colors by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var free by rememberSaveable { mutableStateOf(false) }
    var twoCard by rememberSaveable { mutableStateOf(false) }
    var maxPrice by rememberSaveable { mutableStateOf<Double?>(null) }
    var card by rememberSaveable { mutableStateOf(startCard) }
    var asCards by remember { mutableStateOf(vm.comboCards()) }
    var big by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<OwnedCombo?>(null) }
    var closed by rememberSaveable { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val pricesVersion = vm.comboPricesVersion

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.combos_collection_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("decks") },
        )
    }) { pad ->
        if (!vm.comboDbReady) { ComboDbSetup(vm, Modifier.padding(pad)); return@Scaffold }
        val filter = ComboFilter(colors = colors.toSet(), format = format, free = free && tab == 0, twoCard = twoCard, maxPrice = if (tab == 0) null else maxPrice, card = card)
        val all = vm.collectionCombos
        val complete = all.filter { it.missing.isEmpty() && ComboView.matches(it, filter, vm.comboFreeNames, vm::comboPrice) }
        val almost = all.filter { it.missing.size == 1 && ComboView.matches(it, filter, vm.comboFreeNames, vm::comboPrice) }
        val buys = ComboView.bestBuys(almost, vm::comboPrice)
        Column(Modifier.padding(pad).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                listOf(stringResource(R.string.combos_tab_complete, complete.size), stringResource(R.string.combos_tab_almost, almost.size), stringResource(R.string.combos_tab_buy, buys.size))
                    .forEachIndexed { i, l -> SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l, maxLines = 1, fontSize = NgText.body) } }
            }
            // Filters in één rij die schuift: kleuren, format, vrij, 2 kaarten, prijs, kaart.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                card?.let { cName -> FilterChip(selected = true, onClick = { card = null }, label = { Text("$cName ✕", maxLines = 1) }) }
                "WUBRG".forEach { col ->
                    FilterChip(selected = col in colors, onClick = { colors = if (col in colors) colors.replace(col.toString(), "") else colors + col },
                        label = { nl.spoonpaco.ninegrid.ui.components.ManaPips(col.toString(), size = 16) })
                }
                var fmtMenu by remember { mutableStateOf(false) }
                Column {
                    FilterChip(selected = format != null, onClick = { fmtMenu = true }, label = { Text(format?.let { formatLabel(it) } ?: stringResource(R.string.combos_format_all)) })
                    DropdownMenu(fmtMenu, onDismissRequest = { fmtMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.combos_format_all)) }, onClick = { format = null; fmtMenu = false })
                        vm.comboFormats.sortedBy { formatOrder(it) }.forEach { f -> DropdownMenuItem(text = { Text(formatLabel(f)) }, onClick = { format = f; fmtMenu = false }) }
                    }
                }
                if (tab == 0) FilterChip(selected = free, onClick = { free = !free }, label = { Text(stringResource(R.string.combos_filter_free)) })
                FilterChip(selected = twoCard, onClick = { twoCard = !twoCard }, label = { Text(stringResource(R.string.combos_filter_two)) })
                if (tab != 0) {
                    var priceMenu by remember { mutableStateOf(false) }
                    Column {
                        FilterChip(selected = maxPrice != null, onClick = { priceMenu = true }, label = { Text(maxPrice?.let { "≤ " + money(it, settings.currency) } ?: stringResource(R.string.combos_filter_price)) })
                        DropdownMenu(priceMenu, onDismissRequest = { priceMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.combos_format_all)) }, onClick = { maxPrice = null; priceMenu = false })
                            listOf(1.0, 5.0, 10.0, 20.0, 50.0).forEach { p -> DropdownMenuItem(text = { Text("≤ " + money(p, settings.currency)) }, onClick = { maxPrice = p; priceMenu = false }) }
                        }
                    }
                }
            }
            if (tab != 2) ThumbnailSwitch(asCards) { asCards = it; vm.setComboCards(it) }
            if (vm.combosComputing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                when (tab) {
                    2 -> {
                        if (buys.isEmpty()) item { Text(stringResource(R.string.combos_none_filter), color = extra.subtle, modifier = Modifier.padding(16.dp)) }
                        items(buys.take(100), key = { "b_" + it.card }) { b ->
                            Row(Modifier.fillMaxWidth().clickable { card = b.card; tab = 1 }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (settings.imageMode != nl.spoonpaco.ninegrid.data.ImageMode.OFF) { CardThumbByName(vm, settings, b.card, onCard, { big = it }, width = 48); Spacer(Modifier.width(10.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(b.card, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.combos_buy_line, b.combos.size) + (b.price?.let { " · " + money(it, settings.currency) } ?: ""), fontSize = NgText.body, color = extra.subtle)
                                    val friend = vm.friendsHaving(b.card).firstOrNull()
                                    if (friend != null) Text(stringResource(R.string.combos_at_friend, friend.first.name, friend.second), fontSize = NgText.caption, color = Color(0xFFC7B3FF))
                                }
                                TextButton(onClick = { vm.addToWishlistByName(b.card) }) { Text(stringResource(R.string.wish_add_btn)) }
                            }
                            HorizontalDivider()
                        }
                    }
                    else -> {
                        val list = if (tab == 0) complete else almost
                        if (list.isEmpty()) item { Text(stringResource(if (all.isEmpty()) R.string.combos_none_collection else R.string.combos_none_filter), color = extra.subtle, modifier = Modifier.padding(16.dp)) }
                        ComboView.grouped(list).forEach { (g, l) ->
                            val k = g.name
                            item(key = "h_$k") {
                                Row(Modifier.fillMaxWidth().clickable { closed = if (k in closed.split(',')) closed.split(',').filter { it != k }.joinToString(",") else "$closed,$k" }
                                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text((if (k in closed.split(',')) "▸ " else "▾ ") + groupLabel(g), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("${l.size}", color = extra.subtle)
                                }
                            }
                            if (k !in closed.split(',')) items(l.take(200), key = { "c_" + k + it.id }) { c ->
                                Column(Modifier.fillMaxWidth().clickable { open = c }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    ComboRow(vm, settings, c.cards, missing = c.missing.firstOrNull(), twoCard = c.cards.size == 2 && c.requires.isEmpty(), asCards = asCards, onCard = onCard, onBig = { big = it })
                                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        c.produces.take(3).forEach { Pill(it, Color(0xFF1D2B38), Color(0xFF9FD3FF)) }
                                        c.bracketTag?.let { Pill(spellbookTagLabel(it), Color(0xFF3A2A1D), Color(0xFFFFC98F)) }
                                        if (c.requires.isNotEmpty()) Pill(stringResource(R.string.combos_requires_short), Color(0xFF2E2E2E), Color(0xFFCCCCCC))
                                        c.missing.firstOrNull()?.let { m -> vm.comboPrice(m)?.let { p -> Pill(m + " · " + money(p, settings.currency), NgColors.Amber.copy(alpha = 0.2f), NgColors.Amber) } }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
                item {
                    val uri = LocalUriHandler.current
                    Text(stringResource(R.string.combos_credit_db, vm.comboDbSize), fontSize = NgText.caption, color = extra.subtle,
                        modifier = Modifier.fillMaxWidth().clickable { uri.openUri("https://commanderspellbook.com") }.padding(16.dp))
                }
            }
        }
    }
    open?.let { c ->
        ComboDetail(vm, c, decks.filter { d -> ComboView.fitsColors(c.identity, vm.deckColors(d.id)) }, onClose = { open = null }, onCard = onCard, onFriend = onFriend,
            onNewDeck = { vm.pastePrefill = c.cards.joinToString("\n") { "1 $it" }; open = null; onNewDeck() },
            onIntoDeck = { id -> vm.pastePrefill = c.cards.joinToString("\n") { "1 $it" }; open = null; onIntoDeck(id) })
    }
    BigCardDialog(vm, settings, big) { big = null }
}

/** Nog geen combodatabase: uitleg en de knop om hem te downloaden. */
@Composable
private fun ComboDbSetup(vm: AppViewModel, modifier: Modifier) {
    val extra = LocalNgExtra.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.combos_db_explain), fontSize = NgText.body)
        Text(stringResource(R.string.combos_db_privacy), fontSize = NgText.body, color = extra.subtle)
        when {
            vm.comboDbRunning -> {
                Text(stringResource(R.string.combo_db_progress, vm.comboDbCount), fontSize = NgText.body)
                if (vm.comboDbPct >= 0) LinearProgressIndicator(progress = { vm.comboDbPct / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            vm.comboDbWaiting -> Text(stringResource(R.string.combos_db_waiting), fontSize = NgText.body, color = NgColors.Amber)
            else -> {
                if (vm.comboDbFailed) Text(stringResource(R.string.combos_db_failed), fontSize = NgText.body, color = NgColors.Amber)
                Button(onClick = { vm.startComboDownload(mobile = false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.combos_db_download)) }
                OutlinedButton(onClick = { vm.startComboDownload(mobile = true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.combos_db_download_mobile)) }
            }
        }
    }
}

/** Eén combo: kaarten, waar ze liggen, wat hij doet, de stappen, en wat je ermee kunt. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ComboDetail(
    vm: AppViewModel, c: OwnedCombo, fittingDecks: List<nl.spoonpaco.ninegrid.data.db.Deck>, onClose: () -> Unit, onCard: (String) -> Unit,
    onFriend: (String) -> Unit, onNewDeck: () -> Unit, onIntoDeck: (Long) -> Unit,
) {
    val extra = LocalNgExtra.current
    val settings by vm.settings.collectAsState()
    val decks by vm.decks.collectAsState()
    val deckNames = decks.associate { it.id to it.name }
    val uri = LocalUriHandler.current
    var pickDeck by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(c.cards.joinToString(" + "), fontSize = NgText.body, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ComboRow(vm, settings, c.cards, missing = c.missing.firstOrNull(), twoCard = c.cards.size == 2 && c.requires.isEmpty(), asCards = settings.imageMode != nl.spoonpaco.ninegrid.data.ImageMode.OFF, onCard = { onClose(); onCard(it) }, onBig = {})
                // Waar elke kaart ligt.
                c.cards.forEach { n ->
                    val missing = n in c.missing
                    val own = vm.entries.value.filter { !it.entry.proxy && it.print?.name?.let { p -> p.equals(n, true) || p.substringBefore(" // ").equals(n.substringBefore(" // "), true) } == true }
                        .sortedBy { if (it.entry.deckId == null) 0 else 1 }.firstOrNull()
                    val where = when {
                        own != null -> locationText(own.entry, deckNames) ?: stringResource(R.string.collection_loose)
                        else -> {
                            val friend = vm.friendsHaving(n).firstOrNull()
                            stringResource(R.string.combos_not_owned) + (vm.comboPrice(n)?.let { " · " + money(it, settings.currency) } ?: "") +
                                (friend?.let { " · " + stringResource(R.string.combos_at_friend, it.first.name, it.second) } ?: "")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (missing) "○ " else "● ") + n, fontWeight = FontWeight.SemiBold, fontSize = NgText.body, modifier = Modifier.weight(1f))
                        if (missing) TextButton(onClick = { vm.addToWishlistByName(n) }) { Text(stringResource(R.string.wish_add_btn)) }
                    }
                    Text(where, fontSize = NgText.caption, color = if (missing) NgColors.Amber else extra.subtle)
                }
                if (c.produces.isNotEmpty()) {
                    Text(stringResource(R.string.combos_produces), fontWeight = FontWeight.Bold, fontSize = NgText.body)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { c.produces.forEach { Pill(it, Color(0xFF1D2B38), Color(0xFF9FD3FF)) } }
                }
                if (c.requires.isNotEmpty()) {
                    Text(stringResource(R.string.combos_requires), fontWeight = FontWeight.Bold, fontSize = NgText.body)
                    c.requires.forEach { Text("• $it", fontSize = NgText.body) }
                }
                if (c.prerequisites.isNotBlank()) {
                    Text(stringResource(R.string.combos_prereq), fontWeight = FontWeight.Bold, fontSize = NgText.body)
                    Text(c.prerequisites, fontSize = NgText.body)
                }
                if (c.description.isNotBlank()) {
                    Text(stringResource(R.string.combos_steps), fontWeight = FontWeight.Bold, fontSize = NgText.body)
                    Text(c.description, fontSize = NgText.body)
                }
                if (c.legal.isNotEmpty()) Text(stringResource(R.string.combos_legal_in, c.legal.sortedBy { formatOrder(it) }.joinToString(", ") { formatLabel(it) }), fontSize = NgText.caption, color = extra.subtle)
                Text(stringResource(R.string.combos_open_spellbook), fontSize = NgText.body, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uri.openUri("https://commanderspellbook.com/combo/${c.id}/") })
                HorizontalDivider()
                OutlinedButton(onClick = onNewDeck, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.combos_new_deck)) }
                if (fittingDecks.isNotEmpty()) Column {
                    OutlinedButton(onClick = { pickDeck = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.combos_into_deck)) }
                    DropdownMenu(pickDeck, onDismissRequest = { pickDeck = false }) {
                        fittingDecks.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { pickDeck = false; onIntoDeck(d.id) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun groupLabel(g: ComboGroup) = stringResource(when (g) {
    ComboGroup.WIN -> R.string.combos_group_win
    ComboGroup.MANA -> R.string.combos_group_mana
    ComboGroup.TOKENS -> R.string.combos_group_tokens
    ComboGroup.LOCK -> R.string.combos_group_lock
    ComboGroup.OTHER -> R.string.combos_group_other
})

private val FORMAT_ORDER = listOf("commander", "oathbreaker", "brawl", "standardBrawl", "standard", "pioneer", "modern", "premodern", "legacy", "vintage", "pauper", "pauperCommander", "pauperCommanderMain", "predh", "alchemy")
private fun formatOrder(f: String) = FORMAT_ORDER.indexOf(f).let { if (it < 0) 99 else it }

/** "pauperCommanderMain" → "Pauper Commander Main". */
private fun formatLabel(f: String) = f.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
