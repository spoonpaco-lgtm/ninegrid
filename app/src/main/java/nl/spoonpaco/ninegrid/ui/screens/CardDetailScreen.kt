package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.spoonpaco.ninegrid.ui.components.foilShine
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.ImageMode
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.domain.Formats
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.FoilBadge
import nl.spoonpaco.ninegrid.ui.components.isNew
import nl.spoonpaco.ninegrid.ui.components.ManaPips
import nl.spoonpaco.ninegrid.ui.components.SmallTag
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")
val LANGUAGES = listOf("", "DE", "FR", "IT", "ES", "PT", "JP", "KO", "RU", "CN", "TW")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(vm: AppViewModel, printId: String, onBack: () -> Unit, onFriend: (String) -> Unit = {}, onBinder: (String, Int?, Int?) -> Unit = { _, _, _ -> }, onSwipe: (String) -> Unit = {}) {
    val settings by vm.settings.collectAsState()
    val print by vm.repo.print(printId).collectAsState(initial = null)
    val copies by vm.repo.entriesForPrint(printId).collectAsState(initial = emptyList())
    val decks by vm.decks.collectAsState()
    var makeCommander by remember { mutableStateOf<Pair<nl.spoonpaco.ninegrid.data.db.Deck, nl.spoonpaco.ninegrid.data.db.EntryWithPrint>?>(null) }
    val uri = LocalUriHandler.current
    var editing by remember { mutableStateOf<Entry?>(null) }
    var moving by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var exiling by remember { mutableStateOf<Entry?>(null) }
    val extra = LocalNgExtra.current

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(print?.name ?: "", maxLines = 1)
                    // Komt het kaartscherm uit een lijst, dan zie je waar je bent; vegen gaat naar de volgende of vorige kaart.
                    vm.cardPosition(printId)?.let { (i, n) -> Text(stringResource(R.string.card_position, i, n), fontSize = 12.sp, color = LocalNgExtra.current.subtle) }
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("collection") },
        )
    }) { pad ->
        val p = print
        val swipePx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
        Column(
            Modifier.padding(pad).fillMaxSize()
                .pointerInput(printId) {
                    var dx = 0f
                    androidx.compose.foundation.gestures.detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onHorizontalDrag = { _, d -> dx += d },
                        onDragEnd = {
                            // Naar links = volgende kaart, naar rechts = vorige. Aan het begin of einde van de lijst gebeurt niets.
                            val target = when { dx <= -swipePx -> vm.cardNeighbour(printId, +1); dx >= swipePx -> vm.cardNeighbour(printId, -1); else -> null }
                            target?.let(onSwipe)
                        },
                    )
                }
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (p == null) return@Column
            // De kaart groot bovenaan; foils met een duidelijke glans. Tik = volledig scherm.
            val shiny = copies.any { it.entry.finish != Finish.NONFOIL }
            var imageIssue by remember(p.id) { mutableStateOf<nl.spoonpaco.ninegrid.data.Images.Issue?>(null) }
            var anyway by remember(p.id) { mutableStateOf(false) }
            var full by remember { mutableStateOf(false) }
            val model = if (anyway) vm.images.requestAnyway(vm.images.urlFor(p, large = true)) else vm.images.request(vm.images.urlFor(p, large = true), settings)
            Box(
                Modifier.fillMaxWidth(0.75f).aspectRatio(0.716f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .foilShine(shiny, strength = 1.36f).clickable(enabled = imageIssue == null) { full = true },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = model, imageLoader = vm.images.loader, contentDescription = p.name,
                    onError = { imageIssue = if (anyway) nl.spoonpaco.ninegrid.data.Images.Issue.FAILED else vm.images.issueFor(it.result.throwable, settings) },
                    onSuccess = { imageIssue = null },
                    modifier = Modifier.fillMaxSize(),
                )
                // Een gewone reden in plaats van een technische fout; bij "alleen via wifi" toch één keer laden.
                imageIssue?.let { iss ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        nl.spoonpaco.ninegrid.ui.components.ImageIssueMark(iss, small = false)
                        if (iss == nl.spoonpaco.ninegrid.data.Images.Issue.WIFI_ONLY) TextButton(onClick = { anyway = true; imageIssue = null }) { Text(stringResource(R.string.img_load_anyway), fontSize = 12.sp) }
                        if (iss == nl.spoonpaco.ninegrid.data.Images.Issue.FAILED) TextButton(onClick = { anyway = !anyway; imageIssue = null }) { Text(stringResource(R.string.img_retry), fontSize = 12.sp) }
                    }
                }
            }
            if (full) androidx.compose.ui.window.Dialog(onDismissRequest = { full = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().clickable { full = false }, contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = model, imageLoader = vm.images.loader, contentDescription = p.name,
                        modifier = Modifier.fillMaxWidth(0.95f).aspectRatio(0.716f).clip(RoundedCornerShape(16.dp)).foilShine(shiny, strength = 1.36f),
                    )
                }
            }

            // Naam één keer, met type, kleuren, set en rarity.
            Spacer(Modifier.height(12.dp))
            Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(p.typeLine, color = extra.subtle, fontSize = 13.sp)
                if (p.colors.isNotEmpty()) { Spacer(Modifier.width(6.dp)); ManaPips(p.colors, size = 16) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.setName} (${p.setCode}) #${p.collectorNumber} · ", fontSize = 13.sp, color = extra.subtle)
                Text(p.rarity.replaceFirstChar { it.uppercase() }, color = NgColors.rarity(p.rarity), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (p.gameChanger) SmallTag(stringResource(R.string.game_changer), MaterialTheme.colorScheme.primary)
            }
            // Legaliteit klein, in twee vaste regels van drie.
            val legal = listOf("commander", "modern", "pioneer", "standard", "legacy", "pauper").map { f -> f to Formats.legalityOf(p.legalities, f) }
            if (legal.any { it.second != null }) {
                Spacer(Modifier.height(8.dp))
                legal.chunked(3).forEach { line ->
                    Text(
                        line.joinToString(" · ") { (f, l) -> "${f.replaceFirstChar { it.uppercase() }} ${if (l == "legal") "✓" else if (l == "restricted") "1" else "✗"}" },
                        fontSize = 12.sp, color = extra.subtle,
                    )
                }
            }
            // Toevoegen of op de verlanglijst, gevuld in de accentkleur.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                if (copies.isEmpty()) androidx.compose.material3.Button(onClick = { vm.addOneToCollection(p) }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_to_collection)) }
                androidx.compose.material3.Button(onClick = { vm.addToWishlist(p) }) { Text("♡ " + stringResource(R.string.wish_add_btn)) }
            }

            // Prijs: normaal en foil groot naast elkaar; vrienden die hem hebben; de links klein.
            val market = if (settings.currency == "USD") "TCGplayer" else "Cardmarket"
            nl.spoonpaco.ninegrid.ui.components.SectionCard(
                stringResource(R.string.price_title), info = stringResource(R.string.price_note_card),
                trailing = { Text(market, fontSize = 12.sp, color = extra.subtle) },
            ) {
                val normal = if (settings.currency == "USD") p.usd else p.eur
                val foil = if (settings.currency == "USD") p.usdFoil else p.eurFoil
                Row {
                    listOf(stringResource(R.string.price_normal_label) to normal, stringResource(R.string.foil) to foil).forEach { (label, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 12.sp, color = extra.subtle)
                            Text(v?.let { nl.spoonpaco.ninegrid.ui.components.money(it, settings.currency) } ?: "–", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = extra.price, modifier = nl.spoonpaco.ninegrid.ui.components.privacyMask())
                        }
                    }
                }
                val friendsHave = vm.friendsHaving(p.name)
                if (friendsHave.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.also_at) + " ", fontSize = 13.sp, color = NgColors.Synced)
                    friendsHave.forEachIndexed { i, (f, n) ->
                        Text((if (i > 0) " · " else "") + "${f.name} ×$n", fontSize = 13.sp, color = NgColors.Synced, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onFriend(f.id) })
                    }
                    Text(" ›", fontSize = 13.sp, color = NgColors.Synced)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    p.scryfallUri?.let { u -> TextButton(onClick = { uri.openUri(u) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text("↗ Scryfall") } }
                    // De doorverwijslink van Scryfall wordt door Cardmarket soms geblokkeerd; zoeken op naam werkt altijd.
                    val marketUri = if (settings.currency == "USD") p.tcgplayerUri
                    else "https://www.cardmarket.com/en/Magic/Products/Search?searchString=" + java.net.URLEncoder.encode(p.name.substringBefore(" // "), "UTF-8")
                    marketUri?.let { u -> TextButton(onClick = { uri.openUri(u) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("↗ $market") } }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.your_copies, copies.sumOf { it.entry.quantity }), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            copies.forEach { cp ->
                val e = cp.entry
                Card(colors = CardDefaults.cardColors(containerColor = extra.cardSurface), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Tik op de plek in een binder: dat blad in de bladweergave.
                            Text(
                                locationText(e, decks.associate { it.id to it.name }) ?: stringResource(R.string.collection_loose), fontWeight = FontWeight.Bold,
                                color = if (e.deckId == null && !e.binder.isNullOrBlank()) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = Modifier.weight(1f).let { m -> if (e.deckId == null && !e.binder.isNullOrBlank()) m.clickable { onBinder(e.binder!!, e.page, e.slot) } else m },
                            )
                            FoilBadge(e.finish)
                            if (e.isNew(settings)) nl.spoonpaco.ninegrid.ui.components.NewBadge()
                            if (e.language.isNotBlank()) SmallTag(e.language)
                            SmallTag(e.condition)
                            if (e.proxy) SmallTag(stringResource(R.string.proxy), NgColors.Proxy)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.updateEntry(e.copy(quantity = e.quantity - 1)) }) { Icon(Icons.Filled.Remove, stringResource(R.string.less)) }
                            Text("${e.quantity}", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.updateEntry(e.copy(quantity = e.quantity + 1)) }) { Icon(Icons.Filled.Add, stringResource(R.string.more_one)) }
                            Spacer(Modifier.weight(1f))
                            val cmdDeck = decks.firstOrNull { it.id == e.deckId && it.format == nl.spoonpaco.ninegrid.data.db.DeckFormat.COMMANDER }
                            if (cmdDeck != null && e.section != nl.spoonpaco.ninegrid.data.db.Section.COMMANDER && nl.spoonpaco.ninegrid.domain.CommanderRules.canBeCommander(p.typeLine, p.oracleText)) {
                                TextButton(onClick = { makeCommander = cmdDeck to cp }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.make_commander), fontSize = 12.sp) }
                            }
                            IconButton(onClick = { moving = e }) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.move)) }
                            IconButton(onClick = { editing = e }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                            IconButton(onClick = { deleting = e }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.scryfall_credit), fontSize = 11.sp, color = extra.subtle)
        }
    }

    editing?.let { e -> EditEntryDialog(e, onDismiss = { editing = null }) { vm.updateEntry(it); editing = null } }
    makeCommander?.let { (dk, cp) -> ChangeCommanderDialog(vm, dk, start = PickedCommander(cp.entry, cp.print!!), onDismiss = { makeCommander = null }) }
    moving?.let { e -> MoveDialog(e, decks, vm.binders.collectAsState().value, onDismiss = { moving = null }) { q, d, s, b, pg, sl -> vm.moveEntry(e, q, d, s, b, pg, sl); moving = null } }
    exiling?.let { e -> nl.spoonpaco.ninegrid.ui.components.ExileSwirl { vm.deleteEntry(e); exiling = null } }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_copies_title)) },
            text = { Text(stringResource(R.string.delete_copies_body, e.quantity)) },
            confirmButton = { TextButton(onClick = { exiling = e; deleting = null }) { Text(stringResource(R.string.exile)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun ChoiceRow(label: String, options: List<String>, selected: String, display: (String) -> String = { it }, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Column {
            OutlinedButton(onClick = { open = true }) { Text(display(selected)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(display(o)) }, onClick = { open = false; onSelect(o) }) }
            }
        }
    }
}

@Composable
fun langLabel(code: String): String = if (code.isBlank()) stringResource(R.string.english) else code

@Composable
fun finishLabel(f: String): String = when (f) {
    Finish.FOIL -> stringResource(R.string.foil)
    Finish.ETCHED -> stringResource(R.string.etched)
    else -> stringResource(R.string.nonfoil)
}

@Composable
fun EditEntryDialog(entry: Entry, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    var e by remember { mutableStateOf(entry) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column {
                ChoiceRow(stringResource(R.string.finish), listOf(Finish.NONFOIL, Finish.FOIL, Finish.ETCHED), e.finish, { finishLabelPlain(it) }) { e = e.copy(finish = it) }
                ChoiceRow(stringResource(R.string.language), LANGUAGES, e.language, { if (it.isBlank()) "EN" else it }) { e = e.copy(language = it) }
                ChoiceRow(stringResource(R.string.condition), CONDITIONS, e.condition) { e = e.copy(condition = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.proxy, onCheckedChange = { e = e.copy(proxy = it) })
                    Text(stringResource(R.string.proxy_explain))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(e) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

fun finishLabelPlain(f: String) = when (f) { Finish.FOIL -> "Foil"; Finish.ETCHED -> "Etched"; else -> "Non-foil" }

@Composable
fun MoveDialog(
    entry: Entry,
    decks: List<Deck>,
    binders: List<String>,
    onDismiss: () -> Unit,
    onMove: (Int, Long?, String?, String?, Int?, Int?) -> Unit,
) {
    var qty by remember { mutableStateOf(entry.quantity.toString()) }
    var toDeck by remember { mutableStateOf<Long?>(if (entry.deckId == null) decks.firstOrNull()?.id else null) }
    var section by remember { mutableStateOf(Section.MAIN) }
    var binder by remember { mutableStateOf(binders.firstOrNull() ?: "") }
    var page by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = toDeck == null, onClick = { toDeck = null })
                    Text(stringResource(R.string.collection))
                }
                if (toDeck == null) {
                    OutlinedTextField(binder, { binder = it }, label = { Text(stringResource(R.string.binder_optional)) }, singleLine = true)
                    Row {
                        OutlinedTextField(page, { page = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.page)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(slot, { slot = it.filter(Char::isDigit).take(1) }, label = { Text(stringResource(R.string.slot)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
                decks.filter { it.id != entry.deckId }.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = toDeck == d.id, onClick = { toDeck = d.id })
                        Text(d.name)
                    }
                }
                if (toDeck != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Section.COMMANDER, Section.MAIN, Section.SIDEBOARD).forEach { s ->
                            FilterChip(selected = section == s, onClick = { section = s }, label = { Text(sectionLabel(s)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull()?.coerceIn(1, entry.quantity) ?: entry.quantity
                onMove(q, toDeck, section, binder.trim().ifBlank { null }, page.toIntOrNull(), slot.toIntOrNull()?.coerceIn(1, 9))
            }) { Text(stringResource(R.string.move)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun sectionLabel(s: String) = when (s) {
    Section.COMMANDER -> stringResource(R.string.section_commander)
    Section.SIDEBOARD -> stringResource(R.string.section_sideboard)
    else -> stringResource(R.string.section_main)
}
