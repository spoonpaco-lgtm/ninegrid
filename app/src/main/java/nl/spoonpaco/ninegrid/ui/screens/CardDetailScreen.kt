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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import nl.spoonpaco.ninegrid.ui.components.ManaPips
import nl.spoonpaco.ninegrid.ui.components.SmallTag
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")
val LANGUAGES = listOf("", "DE", "FR", "IT", "ES", "PT", "JP", "KO", "RU", "CN", "TW")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(vm: AppViewModel, printId: String, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val print by vm.repo.print(printId).collectAsState(initial = null)
    val copies by vm.repo.entriesForPrint(printId).collectAsState(initial = emptyList())
    val decks by vm.decks.collectAsState()
    val uri = LocalUriHandler.current
    var editing by remember { mutableStateOf<Entry?>(null) }
    var moving by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val extra = LocalNgExtra.current

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(print?.name ?: "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        val p = print
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (p == null) return@Column
            Row {
                AsyncImage(
                    model = vm.images.request(p.imageNormal ?: p.imageSmall, settings.copy(imageMode = if (settings.imageMode == ImageMode.OFF) ImageMode.OFF else settings.imageMode)),
                    imageLoader = vm.images.loader,
                    contentDescription = p.name,
                    modifier = Modifier.width(150.dp).aspectRatio(0.716f).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(p.typeLine, color = extra.subtle, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    ManaPips(p.colors)
                    Spacer(Modifier.height(8.dp))
                    Text("${p.setName} (${p.setCode}) #${p.collectorNumber}", fontSize = 13.sp)
                    Text(p.rarity.replaceFirstChar { it.uppercase() }, color = NgColors.rarity(p.rarity), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    val normal = if (settings.currency == "USD") p.usd else p.eur
                    val foil = if (settings.currency == "USD") p.usdFoil else p.eurFoil
                    Text(stringResource(R.string.price_normal, normal?.let { Money.format(it, settings.currency) } ?: "–"), color = extra.price)
                    Text(stringResource(R.string.price_foil, foil?.let { Money.format(it, settings.currency) } ?: "–"), color = extra.price)
                    if (p.gameChanger) SmallTag(stringResource(R.string.game_changer), MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.scryfallUri?.let { u ->
                    OutlinedButton(onClick = { uri.openUri(u) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(4.dp)); Text("Scryfall") }
                }
                val market = if (settings.currency == "USD") p.tcgplayerUri else p.cardmarketUri
                market?.let { u ->
                    OutlinedButton(onClick = { uri.openUri(u) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(4.dp))
                        Text(if (settings.currency == "USD") "TCGplayer" else "Cardmarket")
                    }
                }
            }
            val legal = listOf("commander", "modern", "pioneer", "standard", "legacy", "pauper").map { f -> f to Formats.legalityOf(p.legalities, f) }
            if (legal.any { it.second != null }) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.legality), fontWeight = FontWeight.Bold)
                Text(
                    legal.joinToString("  ") { (f, l) -> "${f.replaceFirstChar { it.uppercase() }} ${if (l == "legal") "✓" else if (l == "restricted") "1" else "✗"}" },
                    fontSize = 13.sp, color = extra.subtle,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.your_copies, copies.sumOf { it.entry.quantity }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            copies.forEach { cp ->
                val e = cp.entry
                Card(colors = CardDefaults.cardColors(containerColor = extra.cardSurface), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(locationText(e, decks.associate { it.id to it.name }) ?: stringResource(R.string.collection_loose), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            FoilBadge(e.finish)
                            if (e.language.isNotBlank()) SmallTag(e.language)
                            SmallTag(e.condition)
                            if (e.proxy) SmallTag(stringResource(R.string.proxy), NgColors.Proxy)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.updateEntry(e.copy(quantity = e.quantity - 1)) }) { Icon(Icons.Filled.Remove, stringResource(R.string.less)) }
                            Text("${e.quantity}", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.updateEntry(e.copy(quantity = e.quantity + 1)) }) { Icon(Icons.Filled.Add, stringResource(R.string.more_one)) }
                            Spacer(Modifier.weight(1f))
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
    moving?.let { e -> MoveDialog(e, decks, vm.binders.collectAsState().value, onDismiss = { moving = null }) { q, d, s, b, pg, sl -> vm.moveEntry(e, q, d, s, b, pg, sl); moving = null } }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_copies_title)) },
            text = { Text(stringResource(R.string.delete_copies_body, e.quantity)) },
            confirmButton = { TextButton(onClick = { vm.deleteEntry(e); deleting = null }) { Text(stringResource(R.string.delete)) } },
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
