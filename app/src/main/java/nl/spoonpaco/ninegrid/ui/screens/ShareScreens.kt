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
                val ids = packs.map { it.id }.toSet()
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
    var choice by remember { mutableStateOf(ShareChoice(all = true)) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    val loose = entries.filter { it.entry.deckId == null && !it.entry.proxy && it.print != null }
    val binders = loose.mapNotNull { it.entry.binder?.takeIf { b -> b.isNotBlank() } }.distinct().sortedBy { it.lowercase() }
    val sets = loose.groupBy { it.print!!.setCode.uppercase() }.mapValues { (_, v) -> v.sumOf { it.entry.quantity } }.toSortedMap()
    val packs = remember(choice, entries, decks) { vm.sharePackages(choice) }
    val count = nl.spoonpaco.ninegrid.domain.Share.count(packs)
    val looseIn = choice.all || choice.loose
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = file; if (uri != null && f != null) { vm.saveShareTo(uri, f); onDismiss() }
    }
    fun build(then: (java.io.File) -> Unit) = scope.launch { val f = vm.buildShareFile(choice, name); file = f; then(f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.share_choose), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.share_name_label)) },
                        supportingText = { Text(stringResource(R.string.share_name_hint)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    CheckLine(stringResource(R.string.share_all), stringResource(R.string.share_all_sub), choice.all) { choice = choice.copy(all = it) }
                    CheckLine(stringResource(R.string.share_loose), null, looseIn, enabled = !choice.all) { choice = choice.copy(loose = it) }
                    if (binders.isNotEmpty()) {
                        Text(stringResource(R.string.share_binders), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        if (looseIn) Text(stringResource(R.string.share_in_loose), fontSize = 12.sp, color = extra.subtle)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            binders.forEach { b ->
                                FilterChip(selected = looseIn || b in choice.binders, enabled = !looseIn, onClick = {
                                    choice = choice.copy(binders = if (b in choice.binders) choice.binders - b else choice.binders + b)
                                }, label = { Text(b) })
                            }
                        }
                    }
                    if (sets.isNotEmpty()) {
                        Text(stringResource(R.string.share_sets), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        if (looseIn) Text(stringResource(R.string.share_in_loose), fontSize = 12.sp, color = extra.subtle)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sets.forEach { (code, n) ->
                                FilterChip(selected = looseIn || code in choice.sets, enabled = !looseIn, onClick = {
                                    choice = choice.copy(sets = if (code in choice.sets) choice.sets - code else choice.sets + code)
                                }, label = { Text("$code · $n") })
                            }
                        }
                    }
                    if (decks.isNotEmpty()) {
                        Text(stringResource(R.string.decks), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        Text(stringResource(R.string.share_decks_why), fontSize = 12.sp, color = extra.subtle)
                        decks.sortedBy { it.name.lowercase() }.forEach { d ->
                            CheckLine(d.name, formatLabelPlain(d.format), choice.all || d.id in choice.decks, enabled = !choice.all) {
                                choice = choice.copy(decks = if (it) choice.decks + d.id else choice.decks - d.id)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.share_privacy), fontSize = 12.sp, color = extra.subtle)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (packs.isEmpty()) stringResource(R.string.share_nothing) else stringResource(R.string.share_summary, count, packs.size),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ok = packs.isNotEmpty() && name.isNotBlank()
                        OutlinedButton(onClick = { build { save.launch(it.name) } }, enabled = ok) { Text(stringResource(R.string.share_save_file)) }
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
                        }, enabled = ok, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share_send)) }
                    }
                }
            }
        }
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
    var tab by rememberSaveable { mutableStateOf(0) }
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
                val ids = (if (only != null) setOf(only!!) else packs.map { it.id }.toSet())
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
        val get = r?.get.orEmpty().filter { it.entry.id in getSel }.sumOf { it.unit }
        val give = r?.give.orEmpty().filter { it.entry.id in giveSel }.sumOf { it.unit }
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
                Text(stringResource(R.string.trade_for_decks, item.decks.joinToString(", ")), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.reasons.firstOrNull()?.let { Text(reasonText(it), fontSize = 11.sp, color = LocalNgExtra.current.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Text(Money.format(item.unit, settings.currency), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
