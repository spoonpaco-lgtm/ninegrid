package nl.spoonpaco.ninegrid.ui.screens

import nl.spoonpaco.ninegrid.ui.theme.NgText
import nl.spoonpaco.ninegrid.ui.theme.NgShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.Booster
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardThumb
import nl.spoonpaco.ninegrid.ui.components.money
import nl.spoonpaco.ninegrid.ui.components.privacyMask
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

private val types = listOf("", "play", "collector", "set")

@Composable
fun boosterTypeLabel(t: String) = when (t) {
    "play" -> "Play Booster"; "collector" -> "Collector Booster"; "set" -> "Set Booster"; else -> stringResource(R.string.booster)
}

/** Blok in het controlescherm: "☐ Dit was een booster", met wat je betaalde, het type en eventueel de box. */
@Composable
fun BoosterReviewBlock(vm: AppViewModel) {
    val extra = LocalNgExtra.current
    val boxes by vm.boosterBoxes.collectAsState()
    val boosters by vm.boosters.collectAsState()
    LaunchedEffect(Unit) { vm.prefillBooster() }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.boosterOn = !vm.boosterOn }) {
            Checkbox(vm.boosterOn, onCheckedChange = { vm.boosterOn = it })
            Text(stringResource(R.string.booster_this_was), fontWeight = FontWeight.SemiBold)
        }
        if (!vm.boosterOn) return@Column
        val box = vm.boosterBoxId?.let { id -> boxes.firstOrNull { it.id == id } }
        if (box == null) {
            OutlinedTextField(
                vm.boosterPaid, { vm.boosterPaid = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, singleLine = true,
                label = { Text(stringResource(R.string.booster_paid)) }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            types.forEach { t ->
                FilterChip(selected = vm.boosterType == t, onClick = { vm.boosterType = t },
                    label = { Text(if (t.isEmpty()) stringResource(R.string.booster) else t.replaceFirstChar { it.uppercase() }, fontSize = NgText.body) })
            }
        }
        // Box: prijs per booster = boxprijs gedeeld door het aantal.
        var startBox by remember { mutableStateOf(false) }
        if (box != null) {
            val opened = boosters.count { it.boxId == box.id }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.booster_box_line, opened + 1, box.size, money(box.price / box.size, box.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL)),
                    fontSize = NgText.body, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.endBox() }) { Text(stringResource(R.string.booster_box_end)) }
            }
        } else TextButton(onClick = { startBox = true }) { Text("+ " + stringResource(R.string.booster_box_start)) }
        if (startBox) {
            var size by remember { mutableStateOf("36") }
            var price by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { startBox = false },
                title = { Text(stringResource(R.string.booster_box_start)) },
                text = {
                    Column {
                        OutlinedTextField(size, { size = it.filter(Char::isDigit) }, singleLine = true, label = { Text(stringResource(R.string.booster_box_size)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, singleLine = true, label = { Text(stringResource(R.string.booster_box_price)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        val s = size.toIntOrNull() ?: 0; val p = vm.parseMoney(price) ?: 0.0
                        if (s > 0 && p > 0) Text(stringResource(R.string.booster_per, Money.format(p / s, vm.settings.value.currency)), fontSize = NgText.body, color = extra.subtle)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val s = size.toIntOrNull() ?: 0; val p = vm.parseMoney(price) ?: 0.0
                        if (s > 0 && p > 0) vm.startBox(s, p); startBox = false
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { startBox = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        Text(stringResource(R.string.booster_review_note), fontSize = NgText.caption, color = extra.subtle)
    }
}

/** Uitslag van een booster: betaald, eruit, winst of verlies (ook zonder bulk), de hit groot en de rest. */
@Composable
fun BoosterResultDialog(vm: AppViewModel) {
    val r = vm.boosterResult ?: return
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val cur = r.booster.currency
    val sorted = r.cards.sortedByDescending { (p, c) -> Money.unitPrice(Entry(printId = c.printId, finish = c.finish), p, cur) }
    val value = r.booster.valueAtOpen; val noBulk = r.booster.valueNoBulkAtOpen; val paid = r.booster.paid
    AlertDialog(
        onDismissRequest = { vm.closeBoosterResult() },
        title = { Text(boosterTypeLabel(r.booster.type) + " · " + r.booster.setCode) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                ResultLine(stringResource(R.string.booster_paid_short), money(paid, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), null)
                ResultLine(stringResource(R.string.booster_out), money(value, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), null)
                ResultLine(stringResource(R.string.booster_gain), signed(value - paid, cur), value >= paid)
                ResultLine(stringResource(R.string.booster_gain_nobulk, Money.format(settings.bulkThreshold, cur)), signed(noBulk - paid, cur), noBulk >= paid)
                sorted.firstOrNull()?.let { (p, c) ->
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.booster_hit), fontWeight = FontWeight.Bold, color = NgColors.Rare)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardThumb(p, vm.images, settings, 90, foil = c.finish != nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = NgText.heading)
                            Text("${p.setCode.uppercase()} ${p.collectorNumber}", fontSize = NgText.body, color = extra.subtle)
                            Text(money(Money.unitPrice(Entry(printId = c.printId, finish = c.finish), p, cur), cur), fontSize = NgText.large, fontWeight = FontWeight.Bold,
                                color = extra.price, modifier = privacyMask(value = Money.unitPrice(Entry(printId = c.printId, finish = c.finish), p, cur)))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                sorted.drop(1).forEach { (p, c) ->
                    Row {
                        Text((if (c.quantity > 1) "${c.quantity}× " else "") + p.name, fontSize = NgText.body, modifier = Modifier.weight(1f))
                        Text(money(Money.unitPrice(Entry(printId = c.printId, finish = c.finish), p, cur) * c.quantity, cur), fontSize = NgText.body, color = extra.subtle)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { vm.nextBooster() }) { Text(stringResource(R.string.booster_next)) } },
        dismissButton = { TextButton(onClick = { vm.closeBoosterResult() }) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun signed(v: Double, cur: String) = (if (v >= 0) "+" else "−") + money(kotlin.math.abs(v), cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL)

@Composable
private fun ResultLine(label: String, value: String, good: Boolean?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, color = when (good) { true -> NgColors.Synced; false -> NgColors.Error; null -> LocalNgExtra.current.price })
    }
}

/**
 * Overzicht → Boosters: per set en type wat je gemiddeld betaalde en eruit haalde; per booster waarde toen en nu.
 * Tik op een booster = wat erin zat. Lang indrukken = selecteren om te verwijderen.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BoostersScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit = {}) {
    val boosters by vm.boosters.collectAsState()
    val cards by vm.boosterCards.collectAsState()
    val prints by vm.boosterPrints.collectAsState()
    val boxes by vm.boosterBoxes.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val byId = remember(prints) { prints.associateBy { it.id } }
    val cardsOf = remember(cards) { cards.groupBy { it.boosterId } }
    fun now(b: Booster) = vm.boosterValue(cardsOf[b.id].orEmpty().map { byId[it.printId] to it }, b.currency)
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var deleting by remember { mutableStateOf<Set<Long>?>(null) }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    Scaffold(topBar = {
        if (selected.isNotEmpty()) TopAppBar(
            title = { Text(stringResource(R.string.boosters_selected, selected.size)) },
            navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(androidx.compose.material.icons.Icons.Filled.Close, stringResource(R.string.cancel)) } },
            actions = {
                TextButton(onClick = { selected = if (selected.size == boosters.size) emptySet() else boosters.map { it.id }.toSet() }) { Text(stringResource(R.string.select_all)) }
                IconButton(onClick = { deleting = selected }) { Icon(androidx.compose.material.icons.Icons.Filled.Delete, stringResource(R.string.delete), tint = NgColors.Error) }
            },
        ) else TopAppBar(
            title = { Text(stringResource(R.string.boosters_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.components.PrivacyButton(vm) },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (boosters.isEmpty()) item { Text(stringResource(R.string.boosters_none), modifier = Modifier.padding(16.dp), color = extra.subtle) }
            val groups = boosters.groupBy { it.setCode to it.type }
            groups.forEach { (k, list) ->
                item(key = "g_${k.first}_${k.second}") {
                    val cur = list.first().currency
                    val avgOut = list.map { now(it) }.average()
                    val avgPaid = list.map { it.paid }.average()
                    Card(colors = CardDefaults.cardColors(containerColor = extra.cardSurface), shape = NgShape.Normal,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${k.first} " + boosterTypeLabel(k.second) + " · " + androidx.compose.ui.res.pluralStringResource(R.plurals.n_opened, list.size, list.size), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.boosters_avg, money(avgOut, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), money(avgPaid, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL)), fontSize = NgText.body)
                            Text(stringResource(R.string.boosters_per_booster, signed(avgOut - avgPaid, cur)), fontSize = NgText.body, fontWeight = FontWeight.Bold,
                                color = if (avgOut >= avgPaid) NgColors.Synced else NgColors.Error)
                            list.mapNotNull { it.boxId }.distinct().forEach { bid ->
                                boxes.firstOrNull { it.id == bid }?.let { box ->
                                    val inBox = list.filter { it.boxId == bid }
                                    val out = inBox.sumOf { now(it) }
                                    // Tik op de box: de hele box verwijderen.
                                    Row(Modifier.fillMaxWidth().clickable { deleting = inBox.map { it.id }.toSet() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.boosters_box, inBox.size, box.size, money(out, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), signed(out - box.price, cur)), fontSize = NgText.body, color = extra.subtle, modifier = Modifier.weight(1f))
                                        Icon(androidx.compose.material.icons.Icons.Filled.Delete, stringResource(R.string.booster_box_delete), tint = extra.subtle, modifier = Modifier.height(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                items(list, key = { it.id }) { b ->
                    val ds = settings.dateStyle
                    val isSel = b.id in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isSel) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
                            .combinedClickable(
                                onClick = { if (selected.isNotEmpty()) selected = if (isSel) selected - b.id else selected + b.id else onOpen(b.id) },
                                onLongClick = { selected = selected + b.id },
                            )
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected.isNotEmpty()) Checkbox(checked = isSel, onCheckedChange = { selected = if (it) selected + b.id else selected - b.id })
                        Column(Modifier.weight(1f)) {
                            Text(nl.spoonpaco.ninegrid.domain.Dates.date(b.openedAt, ds) + " · " + androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards_short, cardsOf[b.id].orEmpty().sumOf { it.quantity }, cardsOf[b.id].orEmpty().sumOf { it.quantity }), fontSize = NgText.body)
                            Text(stringResource(R.string.boosters_then_now, money(b.valueAtOpen, b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), money(now(b), b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL)), fontSize = NgText.body, color = extra.subtle)
                        }
                        Text(money(b.paid, b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), color = extra.price, fontWeight = FontWeight.Bold)
                        if (selected.isEmpty()) Text("  ›", color = extra.subtle)
                    }
                }
            }
            item { Text(stringResource(R.string.boosters_note), fontSize = NgText.caption, color = extra.subtle, modifier = Modifier.padding(16.dp)) }
            if (boosters.isNotEmpty()) item {
                TextButton(onClick = { deleting = boosters.map { it.id }.toSet() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.boosters_delete_all), color = NgColors.Error)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    deleting?.let { ids -> DeleteBoostersDialog(vm, ids) { done -> deleting = null; if (done) selected = emptySet() } }
}

/**
 * Boosters verwijderen: alleen de booster (de kaarten blijven in je collectie) of ook de kaarten eruit halen.
 * Kaarten in een deck blijven altijd staan; dat staat er vooraf bij.
 */
@Composable
fun DeleteBoostersDialog(vm: AppViewModel, ids: Set<Long>, onClose: (deleted: Boolean) -> Unit) {
    var withCards by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(ids) { counts = vm.boosterCardsInCollection(ids) }
    AlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.boosters_delete_title, ids.size, ids.size)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable { withCards = false }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = !withCards, onClick = { withCards = false })
                    Text(stringResource(R.string.boosters_delete_only))
                }
                Row(Modifier.fillMaxWidth().clickable { withCards = true }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = withCards, onClick = { withCards = true })
                    Text(stringResource(R.string.boosters_delete_cards))
                }
                counts?.let { (inCol, notIn) ->
                    if (withCards) {
                        Text(stringResource(R.string.boosters_delete_cards_count, inCol), fontSize = NgText.body, modifier = Modifier.padding(top = 6.dp))
                        if (notIn > 0) Text(stringResource(R.string.boosters_delete_cards_skip, notIn), fontSize = NgText.body, color = LocalNgExtra.current.subtle)
                    }
                }
                Text(stringResource(R.string.boosters_delete_undo), fontSize = NgText.caption, color = LocalNgExtra.current.subtle, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { vm.deleteBoosters(ids, withCards); onClose(true) }) { Text(stringResource(R.string.delete), color = NgColors.Error) } },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Wat er in één booster zat: kop met betaald en winst, daaronder de kaarten (duurste eerst, de hit bovenaan). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoosterDetailScreen(vm: AppViewModel, boosterId: Long, onBack: () -> Unit, onCard: (String) -> Unit) {
    val boosters by vm.boosters.collectAsState()
    val cards by vm.boosterCards.collectAsState()
    val prints by vm.boosterPrints.collectAsState()
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    val b = boosters.firstOrNull { it.id == boosterId }
    if (b == null) { LaunchedEffect(Unit) { onBack() }; return }
    val byId = remember(prints) { prints.associateBy { it.id } }
    val mine = remember(cards, boosterId) { cards.filter { it.boosterId == boosterId } }
    fun value(c: nl.spoonpaco.ninegrid.data.db.BoosterCard) = vm.boosterValue(listOf(byId[c.printId] to c), b.currency)
    val sorted = remember(mine, prints, settings.currency) { mine.sortedByDescending { value(it) } }
    val now = sorted.sumOf { value(it) }
    // Wat je nog hebt (collectie of deck, geen proxies), per print en afwerking.
    val owned = remember(all) { all.filter { !it.entry.proxy }.groupBy { it.entry.printId to it.entry.finish }.mapValues { (_, l) -> l.sumOf { it.entry.quantity } } }
    var asCards by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("${b.setCode} " + boosterTypeLabel(b.type)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                nl.spoonpaco.ninegrid.ui.components.PrivacyButton(vm)
                IconButton(onClick = { editing = true }) { Icon(androidx.compose.material.icons.Icons.Filled.Edit, stringResource(R.string.booster_edit)) }
                IconButton(onClick = { deleting = true }) { Icon(androidx.compose.material.icons.Icons.Filled.Delete, stringResource(R.string.booster_delete), tint = NgColors.Error) }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = extra.cardSurface), shape = NgShape.Normal,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(nl.spoonpaco.ninegrid.domain.Dates.date(b.openedAt, settings.dateStyle) + " · " + androidx.compose.ui.res.pluralStringResource(R.plurals.n_cards_short, mine.sumOf { it.quantity }, mine.sumOf { it.quantity }), fontWeight = FontWeight.Bold)
                        ResultLine(stringResource(R.string.booster_paid), money(b.paid, b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), null)
                        ResultLine(stringResource(R.string.booster_value_then), money(b.valueAtOpen, b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), null)
                        ResultLine(stringResource(R.string.booster_value_now), money(now, b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), null)
                        ResultLine(stringResource(R.string.booster_result), signed(now - b.paid, b.currency), now >= b.paid)
                    }
                }
            }
            item { ThumbSwitch(asCards) { asCards = it } }
            if (mine.isEmpty()) item { Text(stringResource(R.string.booster_no_cards), modifier = Modifier.padding(16.dp), color = extra.subtle) }
            if (asCards) {
                items(sorted.chunked(3)) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            val p = byId[c.printId]
                            Column(Modifier.weight(1f).clickable { onCard(c.printId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                CardThumb(p, vm.images, settings, 100, foil = c.finish != nl.spoonpaco.ninegrid.data.db.Finish.NONFOIL)
                                Text((if (c == sorted.first()) "★ " else "") + (p?.name ?: "?") + (if (c.quantity > 1) " ×${c.quantity}" else ""), fontSize = NgText.caption, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = if (c == sorted.first()) FontWeight.Bold else FontWeight.Normal)
                                Text(money(value(c), b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.CARD), fontSize = NgText.caption, color = extra.price)
                                if ((owned[c.printId to c.finish] ?: 0) < c.quantity) Text(stringResource(R.string.booster_card_gone), fontSize = NgText.caption, color = extra.subtle)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                items(sorted, key = { it.id }) { c ->
                    val p = byId[c.printId]
                    val gone = (owned[c.printId to c.finish] ?: 0) < c.quantity
                    nl.spoonpaco.ninegrid.ui.components.CardRow(
                        Entry(printId = c.printId, finish = c.finish, quantity = c.quantity), p, settings, vm.images,
                        location = if (gone) stringResource(R.string.booster_card_gone) else null,
                        tag = if (c == sorted.first() && value(c) > 0) stringResource(R.string.booster_hit) else null,
                        priceText = money(value(c), b.currency, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.CARD),
                    ) { onCard(c.printId) }
                }
            }
        }
    }
    if (editing) {
        var price by remember(b.id) { mutableStateOf(String.format(java.util.Locale.getDefault(), "%.2f", b.paid)) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.booster_edit)) },
            text = {
                OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, singleLine = true,
                    label = { Text(stringResource(R.string.booster_paid)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = { TextButton(onClick = { vm.parseMoney(price)?.let { vm.updateBoosterPaid(b, it) }; editing = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleting) DeleteBoostersDialog(vm, setOf(b.id)) { done -> deleting = false; if (done) onBack() }
}

/** Thumbnails | Namen, zoals bij Combo's en Suggesties. */
@Composable
private fun ThumbSwitch(asCards: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegmentedButton(selected = asCards, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("▦ " + stringResource(R.string.combos_view_cards)) }
        SegmentedButton(selected = !asCards, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("☰ " + stringResource(R.string.combos_view_names)) }
    }
}

/** Blokje op Overzicht: hoeveel boosters, en wat ze gemiddeld opleverden. Tik = Boosters. */
@Composable
fun BoostersSummary(vm: AppViewModel, onOpen: () -> Unit) {
    val boosters by vm.boosters.collectAsState()
    if (boosters.isEmpty()) return
    val cards by vm.boosterCards.collectAsState()
    val prints by vm.boosterPrints.collectAsState()
    val extra = LocalNgExtra.current
    val byId = prints.associateBy { it.id }
    val cardsOf = cards.groupBy { it.boosterId }
    val cur = boosters.first().currency
    val out = boosters.sumOf { b -> vm.boosterValue(cardsOf[b.id].orEmpty().map { byId[it.printId] to it }, b.currency) }
    val paid = boosters.sumOf { it.paid }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.boosters_title), fontWeight = FontWeight.Bold, color = extra.price)
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.n_opened, boosters.size, boosters.size) + " · " + stringResource(R.string.boosters_total, money(paid, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL), money(out, cur, nl.spoonpaco.ninegrid.ui.components.PrivacyKind.TOTAL)), fontSize = NgText.body)
        }
        Text(signed(out - paid, cur), fontWeight = FontWeight.Bold, color = if (out >= paid) NgColors.Synced else NgColors.Error)
        Text("  ›", color = extra.subtle)
    }
}
