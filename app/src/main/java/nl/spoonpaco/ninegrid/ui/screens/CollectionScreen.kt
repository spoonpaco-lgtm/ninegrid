package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardFilter
import nl.spoonpaco.ninegrid.ui.components.CardRow
import nl.spoonpaco.ninegrid.ui.components.CardSearchDialog
import nl.spoonpaco.ninegrid.ui.components.EmptyState
import nl.spoonpaco.ninegrid.ui.components.FilterGroup
import nl.spoonpaco.ninegrid.ui.components.FilterSearchBar
import nl.spoonpaco.ninegrid.ui.components.SectionHeader
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

/**
 * Collectie in drie standen: je eigen kaarten, alle Magic-kaarten (zoeken), en je verlanglijst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    vm: AppViewModel,
    onCard: (String) -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onReview: () -> Unit,
    onImport: () -> Unit = {},
    onShare: () -> Unit = {},
    onBinder: (String) -> Unit = {},
    onFriend: (String) -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val wishes by vm.wishlist.collectAsState()
    var mode by rememberSaveable { mutableStateOf("mine") }
    LaunchedEffect(vm.collectionModeRequest) { vm.collectionModeRequest?.let { mode = it; vm.collectionModeRequest = null } }
    var query by rememberSaveable { mutableStateOf("") }
    var allQuery by rememberSaveable { mutableStateOf("") }
    // Filters en sortering worden onthouden, ook na het afsluiten.
    val filter = remember(settings.filters["collection"]) { CardFilter.fromJson(settings.filters["collection"]) }
    val allFilter = remember(settings.filters["allcards"]) { CardFilter.fromJson(settings.filters["allcards"]) }
    val sort = settings.collectionSort
    var menu by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    var pasteWish by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var fabVisible by remember { mutableStateOf(true) }
    var lastIndex by remember { mutableStateOf(0) }
    var lastOffset by remember { mutableStateOf(0) }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) ->
            val down = i > lastIndex || (i == lastIndex && o > lastOffset + 8)
            val up = i < lastIndex || (i == lastIndex && o < lastOffset - 8)
            if (down) fabVisible = false else if (up || i == 0) fabVisible = true
            lastIndex = i; lastOffset = o
        }
    }
    var search by remember { mutableStateOf(false) }
    val deckNames = remember(decks) { decks.associate { it.id to it.name } }
    val binderList = remember(all) { all.filter { it.entry.deckId == null }.mapNotNull { it.entry.binder?.takeIf { b -> b.isNotBlank() } }.distinct().sortedBy { it.lowercase() } }

    // Alleen opnieuw uitrekenen als de kaarten of de filters echt veranderen (scheelt veel bij duizenden kaarten).
    val filtered = remember(all, query, filter) { all.filter { filter.matches(it, query) } }
    val count = remember(filtered) { filtered.sumOf { it.entry.quantity } }
    val value = remember(filtered, settings.currency) { filtered.sumOf { Money.total(it.entry, it.print, settings.currency) } }
    data class Group(val key: String, val title: String, val count: Int, val value: Double, val cards: List<nl.spoonpaco.ninegrid.data.db.EntryWithPrint>, val binder: String? = null)
    val groups = remember(filtered, sort, settings.currency) {
        when (sort) {
            "set" -> filtered.groupBy { it.print?.setCode ?: "?" }.toSortedMap().map { (set, list) ->
                Group(
                    "s_$set", "$set · " + (list.firstOrNull()?.print?.setName ?: ""), list.sumOf { it.entry.quantity }, list.sumOf { Money.total(it.entry, it.print, settings.currency) },
                    list.sortedWith(compareBy({ it.print?.collectorNumber?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 0 }, { it.print?.collectorNumber })),
                )
            }
            // Per binder, binnen de binder op blad en vak; zonder binder onderaan.
            "binder" -> filtered.groupBy { if (it.entry.deckId != null) "\u0000deck" else it.entry.binder?.takeIf { b -> b.isNotBlank() } ?: "" }
                .toList().sortedWith(compareBy({ it.first.isEmpty() || it.first.startsWith("\u0000") }, { it.first.lowercase() }))
                .map { (b, list) ->
                    Group(
                        "b_$b", b, list.sumOf { it.entry.quantity }, list.sumOf { Money.total(it.entry, it.print, settings.currency) },
                        list.sortedWith(compareBy({ it.entry.page ?: Int.MAX_VALUE }, { it.entry.slot ?: Int.MAX_VALUE }, { it.print?.name ?: "" })),
                        binder = b,
                    )
                }
            else -> emptyList()
        }
    }
    val sortedFlat = remember(filtered, sort, settings.currency) {
        when (sort) {
            "price" -> filtered.sortedByDescending { Money.total(it.entry, it.print, settings.currency) }
            "name" -> filtered.sortedBy { it.print?.name ?: "" }
            else -> emptyList()
        }
    }
    // Samen: bij "elke print" de goedkoopste print, kaarten zonder prijs tellen niet mee.
    val wishValue = wishes.sumOf { w -> (w.item.wanted - w.item.got).coerceAtLeast(0) * (vm.wishUnitPrice(w, settings.currency) ?: 0.0) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.collection), fontWeight = FontWeight.Bold)
                    Text(
                        when (mode) {
                            "all" -> stringResource(R.string.all_cards_sub)
                            "wish" -> listOfNotNull(
                                stringResource(R.string.wish_total_from, vm.wishlistCount(), nl.spoonpaco.ninegrid.ui.components.money(wishValue, settings.currency)),
                                wishes.count { it.item.alarmPrice != null }.takeIf { it > 0 }?.let { "🔔 " + pluralStringResource(R.plurals.wish_n_alarms, it, it) },
                                wishes.count { vm.friendsHaving(it.item.name).isNotEmpty() }.takeIf { it > 0 }?.let { stringResource(R.string.wish_n_at_friends, it) + " ›" },
                            ).joinToString(" · ")
                            else -> pluralStringResource(R.plurals.n_cards, count, count) + " · " + nl.spoonpaco.ninegrid.ui.components.money(value, settings.currency)
                        },
                        style = MaterialTheme.typography.bodySmall, color = LocalNgExtra.current.subtle,
                    )
                }
            }, actions = {
                nl.spoonpaco.ninegrid.ui.components.PrivacyButton(vm)
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection")
                Box0 {
                    IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_title)) }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { topMenu = false; onShare() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.wish_paste)) }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null) }, onClick = { topMenu = false; pasteWish = true })
                    }
                }
            })
        },
        floatingActionButton = {
            // Verdwijnt bij naar beneden scrollen, komt terug bij een stukje omhoog.
            if (mode == "mine") androidx.compose.animation.AnimatedVisibility(fabVisible, enter = androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.scaleOut()) {
                FloatingActionButton(onClick = { menu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_cards))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.scan_page)) }, leadingIcon = { Icon(Icons.Filled.CameraAlt, null) }, onClick = { menu = false; onScan() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.paste_list)) }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null) }, onClick = { menu = false; onPaste() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.search_card)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, onClick = { menu = false; search = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.import_title)) }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) }, onClick = { menu = false; onImport() })
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Drie standen, als één schakelaar.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                listOf("mine" to R.string.mode_mine, "all" to R.string.mode_all, "wish" to R.string.mode_wish).forEachIndexed { i, (k, l) ->
                    SegmentedButton(selected = mode == k, onClick = { mode = k }, shape = SegmentedButtonDefaults.itemShape(i, 3)) {
                        Text(stringResource(l), maxLines = 1, fontSize = 13.sp)
                    }
                }
            }
            when (mode) {
                "all" -> AllCardsPanel(vm, allQuery, { allQuery = it }, allFilter, onCard)
                "wish" -> WishlistPanel(vm, onCard, onPaste = { pasteWish = true }, onAll = { mode = "all" }, onFriend = onFriend)
                else -> {
                    FilterSearchBar(
                        query = query, onQuery = { query = it }, placeholder = stringResource(R.string.search_hint),
                        filter = filter, onFilter = { vm.setFilter("collection", it) },
                        groups = setOf(FilterGroup.RARITY, FilterGroup.COLOR, FilterGroup.TYPE, FilterGroup.FOIL, FilterGroup.DECKS, FilterGroup.TEXT, FilterGroup.BINDER),
                        sortOptions = listOf("set" to stringResource(R.string.sort_set), "binder" to stringResource(R.string.sort_binder), "price" to stringResource(R.string.sort_price), "name" to stringResource(R.string.sort_name)),
                        sort = sort, onSort = { vm.setCollectionSort(it) },
                        binderOptions = binderList,
                    )
                    if (all.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(stringResource(R.string.empty_collection_title), stringResource(R.string.empty_collection_body))
                            // Je hebt al een collectie in de Sheet? Dan in één keer importeren.
                            androidx.compose.material3.Button(onClick = onImport) {
                                Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.import_title))
                            }
                        }
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                            if (sort == "set" || sort == "binder") {
                                groups.forEach { g ->
                                    item(key = "h_${g.key}") {
                                        val title = when {
                                            g.binder == null -> g.title
                                            g.binder.startsWith("\u0000") -> stringResource(R.string.in_decks)
                                            g.binder.isEmpty() -> stringResource(R.string.no_binder)
                                            else -> g.binder
                                        }
                                        val detail = pluralStringResource(R.plurals.n_cards, g.count, g.count) + " · " + nl.spoonpaco.ninegrid.ui.components.money(g.value, settings.currency)
                                        if (g.binder != null && g.binder.isNotEmpty() && !g.binder.startsWith("\u0000")) {
                                            // Tik op een binder: de bladweergave.
                                            Row(Modifier.fillMaxWidth().clickable { onBinder(g.binder) }) { SectionHeader("$title  ▦ ›", detail) }
                                        } else SectionHeader(title, detail)
                                    }
                                    items(g.cards, key = { it.entry.id }) { e ->
                                        CardRow(e.entry, e.print, settings, vm.images, location = locationText(e.entry, deckNames)) { e.print?.let { onCard(it.id) } }
                                    }
                                }
                            } else {
                                items(sortedFlat, key = { it.entry.id }) { e ->
                                    CardRow(e.entry, e.print, settings, vm.images, location = locationText(e.entry, deckNames)) { e.print?.let { onCard(it.id) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (search) {
        CardSearchDialog(vm.repo.scryfall, vm.images, settings, onPick = { p -> search = false; vm.startSingle(p); onReview() }, onDismiss = { search = false })
    }
    if (pasteWish) WishPasteDialog(vm, onDismiss = { pasteWish = false })
}

@Composable
private fun Box0(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }

/** Zoeken in alle Magic-kaarten: offline als het kan, anders bij Scryfall. */
@Composable
private fun AllCardsPanel(vm: AppViewModel, query: String, onQuery: (String) -> Unit, filter: CardFilter, onCard: (String) -> Unit) {
    val settings by vm.settings.collectAsState()
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val extra = LocalNgExtra.current
    LaunchedEffect(query, filter) { vm.searchAll(query, filter) }
    // Wat je al hebt: per naam, los en in welke decks.
    val owned = remember(all) { all.filter { !it.entry.proxy && it.print != null }.groupBy { it.print!!.name.lowercase() } }
    val deckNames = remember(decks) { decks.associate { it.id to it.name } }
    val shown = remember(vm.allResults, filter.notOwned, owned) {
        if (filter.notOwned) vm.allResults.filter { it.name.lowercase() !in owned } else vm.allResults
    }
    Column(Modifier.fillMaxSize()) {
        FilterSearchBar(
            query = query, onQuery = onQuery, placeholder = stringResource(R.string.all_cards_hint),
            filter = filter, onFilter = { vm.setFilter("allcards", it) },
            groups = setOf(FilterGroup.RARITY, FilterGroup.COLOR, FilterGroup.TYPE, FilterGroup.TEXT, FilterGroup.NOT_OWNED),
            accent = true,
        )
        Text(
            stringResource(if (vm.allCardsOffline(filter)) R.string.all_cards_src_offline else R.string.all_cards_src_online),
            fontSize = 11.sp, color = extra.subtle, modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (vm.allLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        val hasFilter = filter.rarities.isNotEmpty() || filter.colors.isNotEmpty() || filter.types.isNotEmpty() || filter.notOwned
        if (query.trim().length < 2 && !hasFilter) {
            Text(stringResource(R.string.all_cards_min), fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(16.dp))
            // Recente zoekopdrachten.
            if (settings.recentSearches.isNotEmpty()) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                settings.recentSearches.forEach { r -> AssistChip(onClick = { onQuery(r) }, label = { Text(r) }) }
            }
            return@Column
        }
        if (vm.allFailed) { Text(stringResource(R.string.msg_network_error), modifier = Modifier.padding(16.dp)); return@Column }
        if (!vm.allLoading && shown.isEmpty()) { Text(stringResource(R.string.guide_search_none), modifier = Modifier.padding(16.dp), color = extra.subtle); return@Column }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(shown, key = { it.id }) { p ->
                val mine = owned[p.name.lowercase()].orEmpty()
                val loose = mine.filter { it.entry.deckId == null }.sumOf { it.entry.quantity }
                val inDecks = mine.mapNotNull { it.entry.deckId }.distinct().mapNotNull { deckNames[it] }
                val tag = listOfNotNull(
                    if (loose > 0) stringResource(R.string.tag_in_collection, loose) else null,
                    inDecks.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.in_deck, it.joinToString(", ")) },
                ).joinToString(" · ").ifBlank { null }
                CardRow(nl.spoonpaco.ninegrid.data.db.Entry(printId = p.id), p, settings, vm.images, tag = tag) { vm.openPrint(p, query, onCard) }
            }
            if (vm.allHasMore) item {
                TextButton(onClick = { vm.moreAll(query, filter) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
            }
        }
    }
}

/** De verlanglijst: wat je zoekt, met "Gekocht", aanpassen en prijsalarm. */
/**
 * Eén regel op de verlanglijst: tik = bewerken, ✓ = gekocht. De tweede regel zegt wat er speelt:
 * het prijsalarm, hoeveel je er nog zoekt, welke vriend hem heeft, en of het om een precieze print gaat.
 */
@Composable
private fun WishRow(
    vm: AppViewModel, w: nl.spoonpaco.ninegrid.data.db.WishWithPrint, unit: Double?, priceText: String,
    onEdit: () -> Unit, onBought: () -> Unit, onFriend: (String) -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val p = w.print
    val i = w.item
    androidx.compose.material3.Card(
        onClick = onEdit,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = extra.cardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(NgColors.rarity(p?.rarity)))
            Spacer(Modifier.width(8.dp))
            nl.spoonpaco.ninegrid.ui.components.CardThumb(p, vm.images, settings, 30, foil = i.foilOnly)
            if (settings.imageMode != nl.spoonpaco.ninegrid.data.ImageMode.OFF) Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(i.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    val cost = p?.manaCost.orEmpty()
                    if (cost.isNotBlank()) {
                        val symbols = Regex("""\{[^}]+\}""").findAll(cost.substringBefore(" // ")).count()
                        Spacer(Modifier.width(5.dp))
                        nl.spoonpaco.ninegrid.ui.components.ManaCost(cost, size = if (i.name.length + symbols * 2 > 22) 12 else 15)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val parts = mutableListOf<@Composable () -> Unit>()
                    i.alarmPrice?.let { target ->
                        val below = unit != null && unit <= target
                        parts += {
                            Text(
                                if (below) "🔔 " + stringResource(R.string.wish_below_target, nl.spoonpaco.ninegrid.ui.components.money(target, i.alarmCurrency))
                                else "🔔 " + nl.spoonpaco.ninegrid.ui.components.money(target, i.alarmCurrency) + (unit?.let { " · " + stringResource(R.string.wish_now, nl.spoonpaco.ninegrid.ui.components.money(it, settings.currency)) } ?: ""),
                                fontSize = 12.sp, color = if (below) NgColors.Synced else extra.subtle, fontWeight = if (below) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                            )
                        }
                    }
                    if (i.wanted > 1) parts += { Text(stringResource(R.string.wish_progress, i.got, i.wanted), fontSize = 12.sp, color = extra.subtle, maxLines = 1) }
                    vm.friendsHaving(i.name).firstOrNull()?.let { (f, _) ->
                        parts += {
                            Text(stringResource(R.string.wish_friend_has, f.name) + " ›", fontSize = 12.sp, color = NgColors.Synced, fontWeight = FontWeight.Bold, maxLines = 1,
                                modifier = Modifier.clickable { onFriend(f.id) })
                        }
                    }
                    if (!i.anyPrint && p != null) parts += { Text(stringResource(R.string.wish_only_print, "${p.setCode} ${p.collectorNumber}") + if (i.foilOnly) " · foil" else "", fontSize = 12.sp, color = extra.subtle, maxLines = 1) }
                    else if (i.foilOnly) parts += { Text(stringResource(R.string.wish_only_foil), fontSize = 12.sp, color = extra.subtle, maxLines = 1) }
                    parts.forEachIndexed { k, part -> if (k > 0) Text(" · ", fontSize = 12.sp, color = extra.subtle); part() }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (i.anyPrint && unit != null) Text(stringResource(R.string.wish_from_label), fontSize = 10.sp, color = extra.subtle)
                Text(if (i.anyPrint && unit != null) nl.spoonpaco.ninegrid.ui.components.money(unit, settings.currency) else priceText,
                    color = extra.price, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = nl.spoonpaco.ninegrid.ui.components.privacyMask())
            }
            Spacer(Modifier.width(6.dp))
            // Gekocht: klein, zodat de naam ruimte houdt.
            androidx.compose.material3.FilledTonalIconButton(onClick = onBought, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Check, stringResource(R.string.wish_bought_btn), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WishlistPanel(vm: AppViewModel, onCard: (String) -> Unit, onPaste: () -> Unit, onAll: () -> Unit, onFriend: (String) -> Unit = {}) {
    val settings by vm.settings.collectAsState()
    val wishes by vm.wishlist.collectAsState()
    val extra = LocalNgExtra.current
    var edit by remember { mutableStateOf<nl.spoonpaco.ninegrid.data.db.WishWithPrint?>(null) }
    if (wishes.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(stringResource(R.string.wish_empty_title), stringResource(R.string.wish_empty_body))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onAll) { Text(stringResource(R.string.mode_all)) }
                androidx.compose.material3.OutlinedButton(onClick = onPaste) { Text(stringResource(R.string.wish_paste)) }
            }
        }
        return
    }
    LaunchedEffect(wishes.map { it.item.id to it.item.anyPrint }, settings.currency) { vm.loadCheapest() }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(wishes, key = { it.item.id }) { w ->
            val unitPrice = vm.wishUnitPrice(w, settings.currency)
            val priceText = when {
                unitPrice == null -> "–"
                w.item.anyPrint -> stringResource(R.string.wish_from, nl.spoonpaco.ninegrid.ui.components.money(unitPrice, settings.currency))
                else -> nl.spoonpaco.ninegrid.ui.components.money(unitPrice, settings.currency)
            }
            val p = w.print
            WishRow(vm, w, unitPrice, priceText, onEdit = { edit = w }, onBought = { vm.boughtWish(w) }, onFriend = onFriend)
        }
        item { Text(stringResource(R.string.wish_alarm_note), fontSize = 11.sp, color = extra.subtle, modifier = Modifier.padding(16.dp)) }
    }
    edit?.let { w -> WishEditDialog(vm, w, onDismiss = { edit = null }) }
}

@Composable
private fun WishEditDialog(vm: AppViewModel, w: nl.spoonpaco.ninegrid.data.db.WishWithPrint, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var wanted by remember { mutableStateOf(w.item.wanted.toString()) }
    var anyPrint by remember { mutableStateOf(w.item.anyPrint) }
    var foilOnly by remember { mutableStateOf(w.item.foilOnly) }
    var alarmOn by remember { mutableStateOf(w.item.alarmPrice != null) }
    val unit = w.print?.let { p -> Money.unitPrice(nl.spoonpaco.ninegrid.data.db.Entry(printId = p.id, finish = if (foilOnly) nl.spoonpaco.ninegrid.data.db.Finish.FOIL else nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL), p, settings.currency) } ?: 0.0
    // Doelprijs staat al klaar: huidige prijs min 10%.
    var target by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", w.item.alarmPrice ?: (unit * 0.9))) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(w.item.name) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(wanted, { v -> wanted = v.filter { it.isDigit() }.take(3) }, singleLine = true, label = { Text(stringResource(R.string.wish_wanted)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                CheckRowSimple(stringResource(R.string.wish_any_print_long), anyPrint) { anyPrint = it }
                if (!anyPrint && w.print != null) Text("${w.print.setCode} ${w.print.collectorNumber} · ${w.print.setName}", fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp))
                CheckRowSimple(stringResource(R.string.wish_foil_only), foilOnly) { foilOnly = it }
                Spacer(Modifier.padding(4.dp))
                CheckRowSimple(stringResource(R.string.wish_alarm), alarmOn) { alarmOn = it }
                if (alarmOn) {
                    androidx.compose.material3.OutlinedTextField(target, { v -> target = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(8) }, singleLine = true,
                        label = { Text(stringResource(R.string.wish_alarm_target, settings.currency)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    Text(stringResource(R.string.wish_alarm_note), fontSize = 11.sp, color = LocalNgExtra.current.subtle)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = wanted.toIntOrNull()?.coerceAtLeast(1) ?: w.item.wanted
                vm.updateWish(w.item.copy(
                    wanted = n, got = w.item.got.coerceAtMost(n - 1).coerceAtLeast(0), anyPrint = anyPrint, foilOnly = foilOnly,
                    alarmPrice = if (alarmOn) target.toDoubleOrNull() else null, alarmCurrency = settings.currency, alarmArmed = true,
                ))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.deleteWish(w.item.id); onDismiss() }) { Text(stringResource(R.string.delete), color = nl.spoonpaco.ninegrid.ui.theme.NgColors.Error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun CheckRowSimple(label: String, v: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!v) }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = v, onCheckedChange = set); Text(label, fontSize = 14.sp)
    }
}

/** Een lijst plakken naar de verlanglijst. */
@Composable
private fun WishPasteDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.wish_paste)) },
        text = {
            Column {
                Text(stringResource(R.string.wish_paste_hint), fontSize = 12.sp)
                androidx.compose.material3.OutlinedTextField(text, { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                result?.let { (ok, bad) -> Text(stringResource(R.string.wish_paste_done, ok) + if (bad > 0) " · " + stringResource(R.string.share_not_found, bad) else "", fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (result == null) TextButton(onClick = { busy = true; vm.pasteToWishlist(text) { ok, bad -> busy = false; result = ok to bad } }, enabled = !busy && text.isNotBlank()) { Text(stringResource(R.string.add)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun locationText(e: nl.spoonpaco.ninegrid.data.db.Entry, deckNames: Map<Long, String>): String? = when {
    e.deckId != null -> stringResource(R.string.in_deck, deckNames[e.deckId] ?: "")
    e.binder != null && e.page != null && e.slot != null -> stringResource(R.string.binder_page_slot, e.binder, e.page, e.slot)
    e.binder != null && e.page != null -> stringResource(R.string.binder_page, e.binder, e.page)
    e.binder != null -> e.binder
    else -> null
}
