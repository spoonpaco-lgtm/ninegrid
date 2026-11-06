package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import nl.spoonpaco.ninegrid.ui.components.foilShine
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.ImageMode
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/**
 * Bladweergave: een binder zoals hij in de kast staat. Eén blad van 3×3 vult het scherm (vegen = volgend blad),
 * lege vakken zijn zichtbaar en scan je met één tik. Met ▦ zie je alle bladen tegelijk.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BinderScreen(
    vm: AppViewModel, binder: String, startPage: Int?, highlightSlot: Int?,
    onBack: () -> Unit, onCard: (String) -> Unit, onScanSlot: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    val extra = LocalNgExtra.current
    val inBinder = remember(all, binder) { all.filter { it.entry.deckId == null && it.entry.binder == binder } }
    val byPage = remember(inBinder) { inBinder.filter { it.entry.page != null }.groupBy { it.entry.page!! } }
    val noPage = remember(inBinder) { inBinder.filter { it.entry.page == null } }
    val lastPage = (byPage.keys.maxOrNull() ?: 1).coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = ((startPage ?: 1) - 1).coerceIn(0, lastPage - 1)) { lastPage }
    val scope = rememberCoroutineScope()
    var overview by rememberSaveable { mutableStateOf(false) }
    var withPrice by rememberSaveable { mutableStateOf(true) }
    var multi by remember { mutableStateOf<List<EntryWithPrint>?>(null) }
    var moving by remember { mutableStateOf<nl.spoonpaco.ninegrid.data.db.Entry?>(null) }
    var lit by remember { mutableStateOf(highlightSlot) }
    LaunchedEffect(highlightSlot) { if (highlightSlot != null) { kotlinx.coroutines.delay(2500); lit = null } }
    val total = inBinder.sumOf { it.entry.quantity }
    val value = inBinder.sumOf { Money.total(it.entry, it.print, settings.currency) }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.binder_title, binder) + if (overview) " · " + stringResource(R.string.binder_all_pages) else "", fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(stringResource(R.string.binder_sub, lastPage, total, nl.spoonpaco.ninegrid.ui.components.money(value, settings.currency)), fontSize = 12.sp, color = extra.subtle)
                }
            },
            navigationIcon = { IconButton(onClick = { if (overview) overview = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                IconButton(onClick = { overview = !overview }) { Icon(if (overview) Icons.Filled.ViewModule else Icons.Filled.GridView, stringResource(R.string.binder_all_pages)) }
                nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection")
            },
        )
    }) { pad ->
        if (overview) {
            // Alle bladen in één scherm: lege plekken springen eruit.
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(pad).fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items((1..lastPage).toList()) { pg ->
                    val cards = byPage[pg].orEmpty()
                    val filled = (1..9).count { s -> cards.any { it.entry.slot == s } }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { overview = false; scope.launch { pager.scrollToPage(pg - 1) } }) {
                        MiniPage(cards, Modifier.fillMaxWidth().aspectRatio(0.78f), current = pg - 1 == pager.currentPage)
                        Text(stringResource(R.string.binder_page_n, pg) + if (filled < 9) " · " + stringResource(R.string.binder_empty_n, 9 - filled) else "", fontSize = 11.sp, color = extra.subtle, textAlign = TextAlign.Center)
                    }
                }
                if (noPage.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Column {
                        Text(stringResource(R.string.binder_no_page, noPage.sumOf { it.entry.quantity }), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        noPage.forEach { e -> nl.spoonpaco.ninegrid.ui.components.CardRow(e.entry, e.print, settings, vm.images) { e.print?.let { onCard(it.id) } } }
                    }
                }
            }
            return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = withPrice, onClick = { withPrice = true }, label = { Text(stringResource(R.string.binder_with_price)) })
                FilterChip(selected = !withPrice, onClick = { withPrice = false }, label = { Text(stringResource(R.string.binder_images_only)) })
            }
            HorizontalPager(pager, Modifier.weight(1f)) { idx ->
                val pg = idx + 1
                val cards = byPage[pg].orEmpty()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(stringResource(R.string.binder_page_of, pg, lastPage), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.share_cards_value, cards.sumOf { it.entry.quantity }, nl.spoonpaco.ninegrid.ui.components.money(cards.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency)),
                            fontSize = 13.sp,
                        )
                    }
                    for (r in 0..2) Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (c in 0..2) {
                            val slot = r * 3 + c + 1
                            val here = cards.filter { it.entry.slot == slot }
                            val mod = Modifier.weight(1f).aspectRatio(0.716f)
                            if (here.isEmpty()) EmptySlot(slot, mod) { vm.scanForSlot(binder, pg, slot); onScanSlot() }
                            else SlotCard(here, settings, vm, withPrice, lit == slot && startPage == pg, mod,
                                onTap = { if (here.distinctBy { it.entry.printId }.size > 1) multi = here else here.first().print?.let { onCard(it.id) } },
                                onLong = { moving = here.first().entry })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) { Text("‹ " + stringResource(R.string.binder_page_n, pager.currentPage)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < lastPage - 1) { Text(stringResource(R.string.binder_page_n, pager.currentPage + 2) + " ›") }
            }
            // Strook mini-bladen: vol of met gaten, tik om te springen.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..lastPage).forEach { pg ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { scope.launch { pager.animateScrollToPage(pg - 1) } }) {
                        MiniPage(byPage[pg].orEmpty(), Modifier.width(34.dp).height(44.dp), current = pg - 1 == pager.currentPage, small = true)
                        Text("$pg", fontSize = 10.sp, color = extra.subtle)
                    }
                }
            }
        }
    }

    multi?.let { list ->
        AlertDialog(
            onDismissRequest = { multi = null },
            title = { Text(stringResource(R.string.binder_two_cards, list.distinctBy { it.entry.printId }.size)) },
            text = { Column { list.forEach { e -> nl.spoonpaco.ninegrid.ui.components.CardRow(e.entry, e.print, settings, vm.images) { multi = null; e.print?.let { onCard(it.id) } } } } },
            confirmButton = { TextButton(onClick = { multi = null }) { Text(stringResource(R.string.done)) } },
        )
    }
    moving?.let { e -> MoveDialog(e, decks, vm.binders.collectAsState().value, onDismiss = { moving = null }) { q, d, s, b, pg, sl -> vm.moveEntry(e, q, d, s, b, pg, sl); moving = null } }
}

/** Leeg vak: stippellijn met een +, tik = dit vak scannen. */
@Composable
private fun EmptySlot(slot: Int, modifier: Modifier, onClick: () -> Unit) {
    val color = LocalNgExtra.current.subtle
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .drawBehindDashed(color),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", fontSize = 24.sp, color = color)
            Text(stringResource(R.string.binder_empty_slot, slot), fontSize = 11.sp, color = color, textAlign = TextAlign.Center)
        }
    }
}

private fun Modifier.drawBehindDashed(color: Color) = this.then(Modifier.drawBehind {
    drawRoundRect(color, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
})

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotCard(
    here: List<EntryWithPrint>, settings: nl.spoonpaco.ninegrid.data.AppSettings, vm: AppViewModel, withPrice: Boolean, lit: Boolean,
    modifier: Modifier, onTap: () -> Unit, onLong: () -> Unit,
) {
    val e = here.first()
    val p = e.print
    val foil = here.any { it.entry.finish != Finish.NONFOIL }
    val qty = here.sumOf { it.entry.quantity }
    val prints = here.distinctBy { it.entry.printId }.size
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (lit) 3.dp else if (foil) 2.dp else 0.dp, if (lit) MaterialTheme.colorScheme.primary else if (foil) NgColors.Amber else Color.Transparent, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        if (settings.imageMode != ImageMode.OFF && p != null) {
            coil.compose.AsyncImage(
                model = vm.images.request(vm.images.urlFor(p, large = false), settings), imageLoader = vm.images.loader,
                contentDescription = p.name, modifier = Modifier.fillMaxSize().then(Modifier.foilShine(foil)), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else Column(Modifier.fillMaxSize().padding(6.dp)) {
            // Plaatjes uit: tegel met naam, set en nummer.
            Text(p?.name ?: "?", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${p?.setCode} ${p?.collectorNumber}", fontSize = 10.sp, color = LocalNgExtra.current.subtle)
        }
        if (foil) Text("✦", color = NgColors.Amber, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        if (qty > 1 && prints == 1) Text("×$qty", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(NgColors.Amber, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp))
        if (prints > 1) Text(stringResource(R.string.binder_n_cards, prints), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(NgColors.Error, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp))
        if (withPrice) Text(
            nl.spoonpaco.ninegrid.ui.components.money(here.sumOf { Money.total(it.entry, it.print, settings.currency) }, settings.currency), color = Color.White, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        )
    }
}

/** Een blad in het klein: gevulde vakken in kleur, lege als stippelrand. */
@Composable
private fun MiniPage(cards: List<EntryWithPrint>, modifier: Modifier, current: Boolean, small: Boolean = false) {
    val extra = LocalNgExtra.current
    Column(
        modifier.clip(RoundedCornerShape(5.dp)).background(extra.cardSurface)
            .border(if (current) 2.dp else 1.dp, if (current) NgColors.Amber else extra.subtle.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
            .padding(if (small) 3.dp else 5.dp),
        verticalArrangement = Arrangement.spacedBy(if (small) 1.5.dp else 3.dp),
    ) {
        for (r in 0..2) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (small) 1.5.dp else 3.dp)) {
            for (c in 0..2) {
                val slot = r * 3 + c + 1
                val e = cards.firstOrNull { it.entry.slot == slot }
                Box(
                    Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp))
                        .let { m -> if (e != null) m.background(NgColors.rarity(e.print?.rarity)) else m.border(BorderStroke(0.5.dp, extra.subtle.copy(alpha = 0.5f)), RoundedCornerShape(2.dp)) },
                )
            }
        }
    }
}
