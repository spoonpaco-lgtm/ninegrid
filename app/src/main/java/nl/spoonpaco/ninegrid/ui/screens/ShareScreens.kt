package nl.spoonpaco.ninegrid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.FriendPackage
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.domain.ShareChoice
import nl.spoonpaco.ninegrid.domain.ShareKind
import nl.spoonpaco.ninegrid.domain.TradeMatch
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardThumb
import nl.spoonpaco.ninegrid.ui.components.FoilBadge
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/** Delen en ruilen: je eigen collectie sturen, en de collecties die je van anderen kreeg. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(vm: AppViewModel, onBack: () -> Unit, onFriend: (String) -> Unit) {
    val friends by vm.friends.collectAsState()
    val packages by vm.friendPackages.collectAsState()
    val cards by vm.friendCards.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    var choosing by remember { mutableStateOf(false) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openShared) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.share_title), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("share") },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.share_mine_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(stringResource(R.string.share_mine_explain), fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { choosing = true }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share_choose)) }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_others_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, enabled = !vm.shareBusy) {
                        Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.share_open))
                    }
                }
                if (vm.shareBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(stringResource(R.string.share_open_explain), fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (friends.isEmpty()) item {
                Text(stringResource(R.string.share_none_yet), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
            }
            items(friends, key = { it.id }) { f ->
                val packs = packages.filter { it.friendId == f.id }
                // De verlanglijst van de ander is geen bezit: telt niet mee.
                val ids = packs.filter { it.kind != ShareKind.WISH }.map { it.id }.toSet()
                val list = cards.filter { it.card.packageId in ids }
                val count = list.sumOf { it.card.quantity }
                val value = vm.friendEntries(ids).sumOf { Money.total(it.entry, it.print, settings.currency) }
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(f.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            pluralStringPackages(packs.size) + " · " + stringResource(R.string.share_cards_value, count, Money.format(value, settings.currency)) +
                                " · " + nl.spoonpaco.ninegrid.domain.Dates.date(f.receivedAt, settings.dateStyle),
                            fontSize = 12.sp,
                        )
                    },
                    modifier = Modifier.clickable { onFriend(f.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (choosing) ShareChooser(vm, onDismiss = { choosing = false })

    vm.pendingOtherApp?.let { data ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.pendingOtherApp = null },
            title = { Text(stringResource(R.string.share_other_app_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.share_other_app_body, data.format.ifBlank { "?" }, data.collectionCount + data.deckCount), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.share_whose)) })
                }
            },
            confirmButton = { TextButton(onClick = { vm.importOtherAppAs(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.share_read_in)) } },
            dismissButton = { TextButton(onClick = { vm.pendingOtherApp = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun pluralStringPackages(n: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.share_packages, n, n)

/** Kiezen wat je stuurt: alles, de losse collectie, binders, sets en decks. Combineren mag. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareChooser(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val entries by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extra = LocalNgExtra.current
    var name by rememberSaveable { mutableStateOf(settings.shareName) }
    // Wat stuur je: alles, losse collectie (met decks naar keuze) of zelf kiezen.
    var mode by rememberSaveable { mutableStateOf("all") }
    var binders by remember { mutableStateOf(setOf<String>()) }
    var sets by remember { mutableStateOf(setOf<String>()) }
    var deckSel by remember { mutableStateOf(setOf<Long>()) }
    var wish by rememberSaveable { mutableStateOf(true) }
    var open by remember { mutableStateOf<String?>(null) }
    var setQuery by remember { mutableStateOf("") }
    var setSortName by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    val loose = entries.filter { it.entry.deckId == null && !it.entry.proxy && it.print != null }
    val binderList = loose.mapNotNull { it.entry.binder?.takeIf { b -> b.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
    data class SetRow(val code: String, val name: String, val count: Int)
    val setList = loose.groupBy { it.print!!.setCode.uppercase() }.map { (c, v) -> SetRow(c, v.first().print!!.setName, v.sumOf { it.entry.quantity }) }
    val choice = when (mode) {
        "all" -> ShareChoice(all = true, wishlist = wish)
        "loose" -> ShareChoice(loose = true, decks = deckSel, wishlist = wish)
        else -> ShareChoice(binders = binders, sets = sets, decks = deckSel, wishlist = wish)
    }
    val packs = remember(choice, entries, decks) { vm.sharePackages(choice) }
    val count = nl.spoonpaco.ninegrid.domain.Share.count(packs)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = file; if (uri != null && f != null) { vm.saveShareTo(uri, f); onDismiss() }
    }
    fun build(then: (java.io.File) -> Unit) = scope.launch { val f = vm.buildShareFile(choice, name); file = f; then(f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.share_choose), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    // Naam: één compacte regel, uitleg klein eronder.
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.share_name_label)) })
                    Text(stringResource(R.string.share_name_hint), fontSize = 11.sp, color = extra.subtle, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.share_what), fontWeight = FontWeight.Bold)
                    RadioLine(stringResource(R.string.share_all), stringResource(R.string.share_all_sub), mode == "all") { mode = "all" }
                    RadioLine(stringResource(R.string.share_loose), stringResource(R.string.share_loose_sub), mode == "loose") { mode = "loose" }
                    RadioLine(stringResource(R.string.share_custom), stringResource(R.string.share_custom_sub), mode == "custom") { mode = "custom" }
                    if (mode == "custom" && binderList.isNotEmpty()) Fold(stringResource(R.string.share_binders), binderList.size, binders.size, open == "b", { open = if (open == "b") null else "b" }) {
                        binderList.forEach { b -> CheckLine(b, null, b in binders) { binders = if (it) binders + b else binders - b } }
                    }
                    if (mode == "custom" && setList.isNotEmpty()) Fold(stringResource(R.string.share_sets), setList.size, sets.size, open == "s", { open = if (open == "s") null else "s" }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(setQuery, { setQuery = it }, singleLine = true, placeholder = { Text(stringResource(R.string.share_set_search)) }, modifier = Modifier.weight(1f))
                            TextButton(onClick = { setSortName = !setSortName }) { Text(stringResource(if (setSortName) R.string.sort_name else R.string.sort_count)) }
                        }
                        setList.filter { setQuery.isBlank() || it.code.contains(setQuery, true) || it.name.contains(setQuery, true) }
                            .let { l -> if (setSortName) l.sortedBy { it.name.lowercase() } else l.sortedByDescending { it.count } }
                            .forEach { r -> CheckLine("${r.code} · ${r.name}", "${r.count}", r.code in sets) { sets = if (it) sets + r.code else sets - r.code } }
                    }
                    if (mode != "all" && decks.isNotEmpty()) Fold(stringResource(R.string.decks), decks.size, deckSel.size, open == "d", { open = if (open == "d") null else "d" }) {
                        Text(stringResource(R.string.share_decks_why), fontSize = 12.sp, color = extra.subtle)
                        decks.sortedBy { it.name.lowercase() }.forEach { d ->
                            CheckLine(d.name, formatLabelPlain(d.format), d.id in deckSel) { deckSel = if (it) deckSel + d.id else deckSel - d.id }
                        }
                    }
                    if (vm.wishlistCount() > 0) CheckLine(stringResource(R.string.share_wishlist), stringResource(R.string.share_wishlist_sub, vm.wishlistCount()), wish) { wish = it }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.share_privacy), fontSize = 12.sp, color = extra.subtle)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    // Samenvatting: wat er meegaat.
                    if (packs.isEmpty()) Text(stringResource(R.string.share_nothing), fontWeight = FontWeight.Bold)
                    else {
                        Text(stringResource(R.string.share_total, count), fontWeight = FontWeight.Bold)
                        val looseN = packs.filter { it.kind != ShareKind.DECK }.sumOf { p -> p.cards.sumOf { it.qty } }
                        val deckPacks = packs.filter { it.kind == ShareKind.DECK }
                        val bytes = nl.spoonpaco.ninegrid.domain.Share.encode(nl.spoonpaco.ninegrid.domain.ShareFile(senderId = "x", senderName = name, createdAt = 0, packages = packs)).length
                        Text(
                            listOfNotNull(
                                if (looseN > 0) stringResource(R.string.share_sum_loose, looseN) else null,
                                if (deckPacks.isNotEmpty()) stringResource(R.string.share_sum_decks, deckPacks.size, deckPacks.sumOf { p -> p.cards.sumOf { it.qty } }) else null,
                                "± " + maxOf(1, bytes / 1024) + " KB",
                            ).joinToString(" · "),
                            fontSize = 12.sp, color = extra.subtle,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val ok = packs.isNotEmpty() && name.isNotBlank()
                    Button(onClick = {
                        build { f ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, f.nameWithoutExtension)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, null))
                            onDismiss()
                        }
                    }, enabled = ok, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share_send)) }
                    TextButton(onClick = { build { save.launch(it.name) } }, enabled = ok, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.share_save_file))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioLine(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title)
            sub?.let { Text(it, fontSize = 12.sp, color = LocalNgExtra.current.subtle) }
        }
    }
}

/** Dichtgeklapt blok met teller: "Sets (62)" en wat gekozen is ("· 4 gekozen"). */
@Composable
private fun Fold(title: String, total: Int, chosen: Int, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = LocalNgExtra.current.cardSurface), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$title ($total)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (chosen > 0) Text(stringResource(R.string.share_chosen, chosen), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Text(if (open) " ▴" else " ▾")
        }
        if (open) Column(Modifier.padding(start = 4.dp, end = 8.dp, bottom = 8.dp)) { content() }
    }
}

@Composable
private fun CheckLine(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title)
            sub?.let { Text(it, fontSize = 12.sp, color = LocalNgExtra.current.subtle) }
        }
    }
}

@Composable
fun packageLabel(p: FriendPackage) = when (p.kind) {
    ShareKind.LOOSE -> stringResource(R.string.share_loose)
    ShareKind.BINDER -> stringResource(R.string.share_pkg_binder, p.label)
    ShareKind.SET -> stringResource(R.string.share_pkg_set, p.label)
    ShareKind.WISH -> stringResource(R.string.mode_wish)
    else -> stringResource(R.string.share_pkg_deck, p.label)
}

/** De collectie van één persoon: alleen bekijken, en de ruilmatch. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendScreen(vm: AppViewModel, friendId: String, onBack: () -> Unit, onCard: (String) -> Unit) {
    val friends by vm.friends.collectAsState()
    val packages by vm.friendPackages.collectAsState()
    val allCards by vm.friendCards.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val friend = friends.firstOrNull { it.id == friendId }
    val packs = packages.filter { it.friendId == friendId }
    // Een persoon openen = meteen de ruilmatch; de kaarten staan in het tweede tabblad.
    var tab by rememberSaveable { mutableStateOf(1) }
    var only by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deletePack by remember { mutableStateOf<FriendPackage?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(friend?.name ?: "", fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(stringResource(if (friend?.fromOtherApp == true) R.string.share_view_only_app else R.string.share_view_only), fontSize = 12.sp, color = extra.subtle)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.rename)) }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.share_delete_person)) }
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("share")
            },
        )
    }) { pad ->
        if (friend == null) return@Scaffold
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.share_tab_cards)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.share_tab_trade)) })
            }
            if (tab == 0) {
                val ids = (if (only != null) setOf(only!!) else packs.filter { it.kind != ShareKind.WISH }.map { it.id }.toSet())
                val list = remember(ids, allCards) { vm.friendEntries(ids) }
                val shown = list.filter { query.isBlank() || it.print?.name?.contains(query, true) == true }
                    .sortedByDescending { Money.total(it.entry, it.print, settings.currency) }
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = only == null, onClick = { only = null }, label = { Text(stringResource(R.string.share_all_packages)) })
                            packs.forEach { p ->
                                FilterChip(selected = only == p.id, onClick = { only = if (only == p.id) null else p.id }, label = { Text(packageLabel(p)) },
                                    trailingIcon = if (only == p.id) ({ Icon(Icons.Filled.Delete, stringResource(R.string.share_delete_package), Modifier.size(16.dp).clickable { deletePack = p }) }) else null)
                            }
                        }
                        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search_card)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                        Text(
                            stringResource(R.string.share_cards_value, list.sumOf { it.entry.quantity }, Money.format(list.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency)),
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(shown, key = { it.entry.id }) { e ->
                        val p = e.print
                        Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { p?.let { onCard(it.id) } }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardThumb(p, vm.images, settings, 34)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p?.name ?: "?", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    FoilBadge(e.entry.finish)
                                }
                                Text(listOfNotNull(p?.let { "${it.setCode} ${it.collectorNumber}" }, e.entry.language.ifBlank { null }, e.entry.section?.let { sectionLabel(it) }).joinToString(" · "), fontSize = 12.sp, color = extra.subtle)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("×${e.entry.quantity}", fontWeight = FontWeight.Bold)
                                Text(Money.format(Money.unitPrice(e.entry, p, settings.currency), settings.currency), fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else TradePanel(vm, friend.name, friendId, packs.any { it.kind == ShareKind.DECK }, onCard)
        }
    }

    if (renaming && friend != null) {
        var n by remember { mutableStateOf(friend.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { Column { OutlinedTextField(n, { n = it }, singleLine = true); Text(stringResource(R.string.share_rename_local), fontSize = 12.sp) } },
            confirmButton = { TextButton(onClick = { vm.renameFriend(friendId, n); renaming = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleting && friend != null) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.share_delete_person)) },
        text = { Text(stringResource(R.string.share_delete_person_body, friend.name)) },
        confirmButton = { TextButton(onClick = { vm.deleteFriend(friendId); deleting = false; onBack() }) { Text(stringResource(R.string.delete), color = NgColors.Error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
    deletePack?.let { p ->
        AlertDialog(
            onDismissRequest = { deletePack = null },
            title = { Text(stringResource(R.string.share_delete_package)) },
            text = { Text(packageLabel(p)) },
            confirmButton = { TextButton(onClick = { vm.deleteFriendPackage(p.id); if (only == p.id) only = null; deletePack = null }) { Text(stringResource(R.string.delete), color = NgColors.Error) } },
            dismissButton = { TextButton(onClick = { deletePack = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Ruilmatch: wat jij van hem kunt gebruiken en wat hij van jou kan gebruiken, met prijzen. Vink aan om een ruil samen te stellen. */
@Composable
private fun TradePanel(vm: AppViewModel, name: String, friendId: String, theyHaveDecks: Boolean, onCard: (String) -> Unit) {
    val settings by vm.settings.collectAsState()
    val packages by vm.friendPackages.collectAsState()
    val cards by vm.friendCards.collectAsState()
    val extra = LocalNgExtra.current
    // Opnieuw rekenen als er een nieuwe versie binnenkomt (of de kaarten later laden dan de pakketten).
    LaunchedEffect(friendId, packages, cards.size) { vm.computeTrade(friendId) }
    val r = vm.tradeMatch?.takeIf { vm.tradeFriendId == friendId }
    var getSel by remember(friendId) { mutableStateOf(setOf<Long>()) }
    var giveSel by remember(friendId) { mutableStateOf(setOf<Long>()) }
    Column(Modifier.fillMaxSize()) {
        if (vm.tradeBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Zekere ruilen eerst: wat op een verlanglijst staat.
            if (r != null && (r.wishGet.isNotEmpty() || r.wishGive.isNotEmpty())) {
                if (r.wishGet.isNotEmpty()) {
                    item { Text(stringResource(R.string.trade_wish_get, name), fontWeight = FontWeight.Bold, color = NgColors.Synced, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
                    items(r.wishGet, key = { "wg" + it.entry.id }) { item ->
                        TradeRow(item, settings, vm, item.entry.id in getSel, onCard) { on -> getSel = if (on) getSel + item.entry.id else getSel - item.entry.id }
                    }
                }
                if (r.wishGive.isNotEmpty()) {
                    item { Text(stringResource(R.string.trade_wish_give, name), fontWeight = FontWeight.Bold, color = NgColors.Synced, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
                    items(r.wishGive, key = { "wv" + it.entry.id }) { item ->
                        TradeRow(item, settings, vm, item.entry.id in giveSel, onCard) { on -> giveSel = if (on) giveSel + item.entry.id else giveSel - item.entry.id }
                    }
                }
            }
            item {
                Text(stringResource(R.string.trade_explain), fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
                Text(stringResource(R.string.trade_get_title, name), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
                if (r != null && r.get.isEmpty()) Text(stringResource(R.string.trade_get_none), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
            }
            items(r?.get.orEmpty(), key = { "g" + it.entry.id }) { item ->
                TradeRow(item, settings, vm, item.entry.id in getSel, onCard) { on -> getSel = if (on) getSel + item.entry.id else getSel - item.entry.id }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.trade_give_title, name), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
                when {
                    !theyHaveDecks -> Text(stringResource(R.string.trade_give_no_decks, name), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
                    r != null && r.give.isEmpty() -> Text(stringResource(R.string.trade_give_none), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
                }
            }
            items(r?.give.orEmpty(), key = { "y" + it.entry.id }) { item ->
                TradeRow(item, settings, vm, item.entry.id in giveSel, onCard) { on -> giveSel = if (on) giveSel + item.entry.id else giveSel - item.entry.id }
            }
        }
        // De ruil die je aanvinkt, met wat hij waard is aan beide kanten.
        val get = (r?.get.orEmpty() + r?.wishGet.orEmpty()).filter { it.entry.id in getSel }.distinctBy { it.entry.id }.sumOf { it.unit }
        val give = (r?.give.orEmpty() + r?.wishGive.orEmpty()).filter { it.entry.id in giveSel }.distinctBy { it.entry.id }.sumOf { it.unit }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.trade_totals, Money.format(get, settings.currency), Money.format(give, settings.currency)), fontWeight = FontWeight.Bold)
            val diff = get - give
            Text(
                when {
                    getSel.isEmpty() && giveSel.isEmpty() -> stringResource(R.string.trade_tick)
                    kotlin.math.abs(diff) < 0.5 -> stringResource(R.string.trade_even)
                    diff > 0 -> stringResource(R.string.trade_you_more, Money.format(diff, settings.currency))
                    else -> stringResource(R.string.trade_they_more, name, Money.format(-diff, settings.currency))
                },
                fontSize = 13.sp,
                color = if (kotlin.math.abs(diff) < 0.5 || (getSel.isEmpty() && giveSel.isEmpty())) extra.subtle else NgColors.Amber,
            )
        }
    }
}

@Composable
private fun TradeRow(item: TradeMatch.Item, settings: nl.spoonpaco.ninegrid.data.AppSettings, vm: AppViewModel, checked: Boolean, onCard: (String) -> Unit, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Row(Modifier.weight(1f).clickable { onCard(item.print.id) }, verticalAlignment = Alignment.CenterVertically) {
            CardThumb(item.print, vm.images, settings, 30)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.print.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    FoilBadge(item.entry.finish)
                }
                if (item.decks.isNotEmpty()) Text(stringResource(R.string.trade_for_decks, item.decks.joinToString(", ")), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Text(stringResource(R.string.trade_on_wishlist), fontSize = 12.sp, color = NgColors.Synced)
                item.reasons.firstOrNull()?.takeIf { it != "wish" }?.let { Text(reasonText(it), fontSize = 11.sp, color = LocalNgExtra.current.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Text(Money.format(item.unit, settings.currency), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
