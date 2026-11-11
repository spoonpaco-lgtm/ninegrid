package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.ImageMode
import nl.spoonpaco.ninegrid.data.SetCard
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/**
 * Set compleet: hoeveel van de set je hebt, en wat je nog mist. Bovenaan een kopkaart met de voortgang
 * (klapt in zodra je scrolt), dan zoeken en filteren, en één schakelaar Ontbreekt | Heb ik.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetScreen(vm: AppViewModel, code: String, onBack: () -> Unit, onReview: () -> Unit, onCard: (String) -> Unit = {}) {
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val decks by vm.decks.collectAsState()
    val wishes by vm.wishlist.collectAsState()
    val extra = LocalNgExtra.current
    var variants by remember { mutableStateOf(false) }
    var showMissing by remember { mutableStateOf(true) }
    LaunchedEffect(code) { vm.loadSet(code) }
    val list = vm.setList?.takeIf { it.code.equals(code, true) }
    // Wat je hebt: elk nummer in deze set, in je collectie of in een deck (geen proxies).
    // Completionist: alleen wat los in je binders ligt telt; wat alleen in een deck zit staat bij Ontbreekt, met de decknaam.
    val inSet = remember(all, code) { all.filter { !it.entry.proxy && it.print?.setCode.equals(code, true) } }
    val byNumber = remember(inSet) { inSet.groupBy { it.print!!.collectorNumber.lowercase() } }
    val owned = remember(inSet, settings.completionist) {
        inSet.filter { !settings.completionist || it.entry.deckId == null }.mapNotNull { it.print?.collectorNumber?.lowercase() }.toSet()
    }
    val deckNames = remember(decks) { decks.associate { it.id to it.name } }
    var query by remember { mutableStateOf("") }
    val filter = remember(settings.filters["set"]) { nl.spoonpaco.ninegrid.ui.components.CardFilter.fromJson(settings.filters["set"]) }
    val cards = remember(list, variants) { list?.cards.orEmpty().filter { variants || it.regular } }
    val have = remember(cards, owned) { cards.filter { it.number.lowercase() in owned } }
    val missing = remember(cards, owned) { cards.filter { it.number.lowercase() !in owned } }
    // Zoeken en filteren gelden voor de lijst, niet voor de telling van hoe compleet je bent.
    val shown = remember(have, missing, showMissing, query, filter) {
        (if (showMissing) missing else have).filter { c ->
            (query.isBlank() || c.name.contains(query.trim(), true) || c.number.equals(query.trim(), true)) && (filter.rarities.isEmpty() || c.rarity in filter.rarities)
        }
    }
    val wishIds = remember(wishes) { wishes.map { it.item.printId }.toSet() }
    val listState = rememberLazyListState()
    // De kopkaart klapt in zodra je scrolt; een tik op de kaart klapt hem weer uit (en terug).
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    var forceOpen by remember { mutableStateOf(false) }
    LaunchedEffect(scrolled) { if (!scrolled) forceOpen = false }
    val collapsed = scrolled && !forceOpen

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(code.uppercase(), fontWeight = FontWeight.Bold); list?.let { Text(it.name, fontSize = 12.sp, color = extra.subtle) } } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection")
                IconButton(onClick = { vm.loadSet(code, refresh = true) }, enabled = !vm.setLoading) { Icon(Icons.Filled.Refresh, null) }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.setLoading && list == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (list == null) return@Column

            // Kopkaart: voortgang, per rarity, wat ontbrekende kaarten samen kosten, Completionist.
            Card(
                colors = CardDefaults.cardColors(containerColor = extra.cardSurface), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).animateContentSize()
                    .clickable(enabled = scrolled) { forceOpen = !forceOpen },
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val pct = if (cards.isEmpty()) 0 else have.size * 100 / cards.size
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.set_complete, have.size, cards.size), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "  $pct%" + if (collapsed) " · ▾ " + stringResource(R.string.set_details) else if (scrolled) " · ▴" else "",
                            fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { if (cards.isEmpty()) 0f else have.size / cards.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)))
                    if (!collapsed) {
                        if (cards.isNotEmpty() && missing.isEmpty()) Row(Modifier.padding(top = 8.dp)) {
                            nl.spoonpaco.ninegrid.ui.components.Stamp(stringResource(R.string.stamp_complete), Color(0xFFD4AF37), "✓")
                        }
                        Spacer(Modifier.height(8.dp))
                        // Per rarity een balk: oranje mythic, goud rare, zilver uncommon, donker common.
                        listOf("mythic" to Color(0xFFE8742A), "rare" to Color(0xFFD4AF37), "uncommon" to Color(0xFFC0C8D2), "common" to Color(0xFF8A7F74)).forEach { (r, col) ->
                            val total = cards.count { it.rarity == r }
                            if (total > 0) {
                                val got = have.count { it.rarity == r }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(r.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, modifier = Modifier.width(84.dp))
                                    Box(
                                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                    ) { Box(Modifier.fillMaxWidth(got / total.toFloat()).height(8.dp).background(col)) }
                                    Text("$got/$total" + if (got == total) " ✓" else "", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(76.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val missingValue = missing.sumOf { (if (settings.currency == "USD") it.usd else it.eur) ?: 0.0 }
                        Row {
                            Text(stringResource(R.string.set_missing_total) + " ", fontSize = 13.sp, color = extra.subtle)
                            Text(nl.spoonpaco.ninegrid.ui.components.money(missingValue, settings.currency), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = extra.price,
                                modifier = nl.spoonpaco.ninegrid.ui.components.privacyMask())
                        }
                        CompletionistToggle(settings.completionist, onChange = { vm.setCompletionist(it) })
                    }
                }
            }

            // Zoeken en filteren, zoals in de collectie.
            nl.spoonpaco.ninegrid.ui.components.FilterSearchBar(
                query = query, onQuery = { query = it }, placeholder = stringResource(R.string.sets_search_card),
                filter = filter, onFilter = { vm.setFilter("set", it) },
                groups = setOf(nl.spoonpaco.ninegrid.ui.components.FilterGroup.RARITY),
            )
            // Eén schakelaar: wat ontbreekt of wat je hebt, met de aantallen.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                SegmentedButton(selected = showMissing, onClick = { showMissing = true }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                    Text(stringResource(R.string.set_tab_missing, missing.size), maxLines = 1)
                }
                SegmentedButton(selected = !showMissing, onClick = { showMissing = false }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                    Text(stringResource(R.string.set_tab_have, have.size), maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { variants = !variants }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = variants, onCheckedChange = { variants = it })
                Text(stringResource(R.string.set_variants), fontSize = 14.sp)
            }

            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
                items(shown, key = { it.id }) { c ->
                    val mine = byNumber[c.number.lowercase()].orEmpty()
                    SetCardRow(
                        vm, c, missing = showMissing, mine = mine, deckNames = deckNames, wished = c.id in wishIds,
                        onOpen = {
                            // Heb je hem: het kaartscherm van je eigen print. Anders eerst opzoeken.
                            val own = mine.firstOrNull()?.print?.id
                            if (own != null) onCard(own) else vm.openSetCard(c.id, onCard)
                        },
                        onWish = { vm.wishSetCard(c.id) },
                        onAdd = { vm.addMissing(c.id, onReview) },
                    )
                }
                if (shown.isEmpty()) item { Text(stringResource(R.string.guide_search_none), color = extra.subtle, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

/**
 * Eén kaart uit de set, in de stijl van de collectie: raritystreep, plaatje (gedimd als je hem niet hebt), naam,
 * nummer en rarity, manakosten en prijs rechts. Bij Ontbreekt: ♡ (verlanglijst) en + (toevoegen) klein onder elkaar.
 * Bij Heb ik: aantal, foil en waar hij ligt.
 */
@Composable
private fun SetCardRow(
    vm: AppViewModel, c: SetCard, missing: Boolean, mine: List<EntryWithPrint>, deckNames: Map<Long, String>, wished: Boolean,
    onOpen: () -> Unit, onWish: () -> Unit, onAdd: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val price = (if (settings.currency == "USD") c.usd else c.eur)
    // Waar je exemplaren liggen (Heb ik), of in welk deck hij zit (Ontbreekt met Completionist aan).
    val qty = mine.sumOf { it.entry.quantity }
    val foils = mine.filter { it.entry.finish != Finish.NONFOIL }.sumOf { it.entry.quantity }
    val places = mine.mapNotNull { locationText(it.entry, deckNames) }.distinct()
    val detail = listOfNotNull(
        "#${c.number}",
        c.rarity.replaceFirstChar { it.uppercase() },
        if (!c.regular) stringResource(R.string.set_variant) else null,
        if (!missing && qty > 0) "×$qty" + (if (foils > 0) " (" + stringResource(R.string.set_n_foil, foils) + ")" else "") else null,
        places.firstOrNull()?.let { it + if (places.size > 1) " +${places.size - 1}" else "" },
    ).joinToString(" · ")
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = extra.cardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(NgColors.rarity(c.rarity)))
            Spacer(Modifier.width(8.dp))
            if (settings.imageMode != ImageMode.OFF) {
                Box(Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    coil.compose.AsyncImage(
                        model = vm.images.request(c.image ?: "https://api.scryfall.com/cards/${c.id}?format=image&version=small", settings),
                        imageLoader = vm.images.loader, contentDescription = c.name, contentScale = ContentScale.Crop,
                        // Gedimd: deze kaart heb je (nog) niet.
                        modifier = Modifier.fillMaxSize().alpha(if (missing) 0.4f else 1f),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = extra.subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (c.mana.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                nl.spoonpaco.ninegrid.ui.components.ManaCost(c.mana, size = 15)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                price?.let { nl.spoonpaco.ninegrid.ui.components.money(it, settings.currency) } ?: "–", color = extra.price, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 52.dp),
            )
            if (missing) {
                Spacer(Modifier.width(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onWish), contentAlignment = Alignment.Center) {
                        Icon(if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, stringResource(R.string.set_to_wishlist), modifier = Modifier.size(15.dp),
                            tint = if (wished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, stringResource(R.string.add_cards), modifier = Modifier.size(17.dp), tint = Color.Black)
                    }
                }
            }
        }
    }
}
