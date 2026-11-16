package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.domain.PrintChoice
import nl.spoonpaco.ninegrid.domain.PrintOption
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.components.CardThumb
import nl.spoonpaco.ninegrid.ui.components.SmallTag
import nl.spoonpaco.ninegrid.ui.components.money
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors
import nl.spoonpaco.ninegrid.ui.theme.NgText

/** De regel "SLD 1798 · Secret Lair Drop ›" die de printlijst opent. */
@Composable
fun PrintLine(p: CardPrint?, label: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (label?.let { "$it: " } ?: "") + (p?.let { "${it.setCode} ${it.collectorNumber} · ${it.setName}" } ?: "–"),
            fontSize = NgText.body, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        Text(" ›", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun finishWord(f: String) = stringResource(when (f) { Finish.FOIL -> R.string.foil; Finish.ETCHED -> R.string.etched; else -> R.string.price_normal_label })

/**
 * Alle prints van een kaart (punt 8a–d, l): plaatje, set, nummer en jaar, per afwerking de prijs, "goedkoopst" bij de
 * goedkoopste en ✓ bij de huidige. Sorteren op nieuwste of goedkoopste. Tik = die print kiezen.
 */
@Composable
fun PrintPickerDialog(vm: AppViewModel, name: String, currentId: String?, onPick: (PrintOption) -> Unit, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    var options by remember(name) { mutableStateOf<List<PrintOption>?>(null) }
    var cheapFirst by remember { mutableStateOf(false) }
    LaunchedEffect(name) { options = runCatching { vm.printOptions(name) }.getOrDefault(emptyList()) }
    val cur = settings.currency
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.choose_print))
                Text(name, fontSize = NgText.body, color = extra.subtle, maxLines = 1)
            }
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !cheapFirst, onClick = { cheapFirst = false }, label = { Text(stringResource(R.string.print_sort_newest), fontSize = NgText.body) })
                    FilterChip(selected = cheapFirst, onClick = { cheapFirst = true }, label = { Text(stringResource(R.string.print_sort_cheapest), fontSize = NgText.body) })
                }
                val list = options
                when {
                    list == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                    list.isEmpty() -> Text(stringResource(R.string.msg_network_error), fontSize = NgText.body, color = extra.subtle)
                    else -> {
                        val cheapestId = PrintChoice.cheapestId(list, cur)
                        val sorted = PrintChoice.sort(list, cheapFirst, cur)
                        Text(stringResource(R.string.print_count, list.size), fontSize = NgText.caption, color = extra.subtle)
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(sorted, key = { it.print.id }) { o ->
                                val p = o.print
                                Row(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CardThumb(p, vm.images, settings, 36)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${p.setCode} ${p.collectorNumber}" + PrintChoice.year(p).let { if (it.isNotBlank()) " · $it" else "" },
                                                fontWeight = FontWeight.Bold, fontSize = NgText.emphasis)
                                            if (p.id == currentId) Text("  ✓", fontWeight = FontWeight.Bold, color = NgColors.Synced)
                                        }
                                        Text(p.setName, fontSize = NgText.body, color = extra.subtle, maxLines = 1)
                                        // Per afwerking die er van deze print bestaat de prijs.
                                        PriceLine(o, cur)
                                    }
                                    if (p.id == cheapestId) SmallTag(stringResource(R.string.print_cheapest), NgColors.Synced)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Normaal € 0,25 · Foil € 1,20 · Etched –" (alleen de afwerkingen die bestaan). */
@Composable
private fun PriceLine(o: PrintOption, cur: String) {
    val extra = LocalNgExtra.current
    val parts = o.finishes.map { f -> finishWord(f) + " " + (PrintChoice.price(o.print, f, cur, o.finishes)?.let { money(it, cur) } ?: "–") }
    Text(parts.joinToString(" · "), fontSize = NgText.caption, color = extra.price, maxLines = 2)
}

/**
 * ✎ bij een exemplaar (punt 8f–h, k): print, afwerking, taal, conditie en proxy. Bij een stapel kies je voor hoeveel
 * exemplaren het geldt; de rest blijft zoals hij was. De plek blijft altijd dezelfde.
 */
@Composable
fun EditCopyDialog(vm: AppViewModel, entry: Entry, print: CardPrint, onDismiss: () -> Unit, onPrintChanged: (String) -> Unit) {
    var e by remember { mutableStateOf(entry) }
    var newPrint by remember { mutableStateOf<CardPrint?>(null) }
    var finishes by remember { mutableStateOf(listOf(Finish.NONFOIL, Finish.FOIL, Finish.ETCHED)) }
    var note by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(entry.quantity.toString()) }
    LaunchedEffect(print.id) { finishes = runCatching { vm.finishesOf(print) }.getOrDefault(finishes) }
    val shown = newPrint ?: print
    val noFinish = stringResource(R.string.print_finish_missing)
    // Een afwerking die je al had, blijft kiesbaar (ook als Scryfall hem niet kent).
    val finishChoices = (finishes + (if (newPrint == null) listOf(entry.finish) else emptyList())).distinct()
        .sortedBy { listOf(Finish.NONFOIL, Finish.FOIL, Finish.ETCHED).indexOf(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PrintLine(shown, stringResource(R.string.print_label)) { picking = true }
                note?.let { Text(it, fontSize = NgText.body, color = NgColors.Amber) }
                ChoiceRow(stringResource(R.string.finish), finishChoices, e.finish, { finishLabelPlain(it) }) { e = e.copy(finish = it); note = null }
                ChoiceRow(stringResource(R.string.language), LANGUAGES, e.language, { if (it.isBlank()) "EN" else it }) { e = e.copy(language = it) }
                ChoiceRow(stringResource(R.string.condition), CONDITIONS, e.condition) { e = e.copy(condition = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.proxy, onCheckedChange = { e = e.copy(proxy = it) })
                    Text(stringResource(R.string.proxy_explain))
                }
                if (entry.quantity > 1) {
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(count, { count = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        label = { Text(stringResource(R.string.print_how_many, entry.quantity)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.print_how_many_note), fontSize = NgText.caption, color = LocalNgExtra.current.subtle)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = count.toIntOrNull()?.coerceIn(1, entry.quantity) ?: entry.quantity
                vm.changeCopy(entry, e, newPrint, n) { id -> if (id != null) onPrintChanged(id) }
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) PrintPickerDialog(vm, print.name, shown.id, onDismiss = { picking = false }, onPick = { o ->
        picking = false
        if (o.print.id == print.id) { newPrint = null; finishes = o.finishes; return@PrintPickerDialog }
        newPrint = o.print
        finishes = o.finishes
        // Bestaat de afwerking niet bij de nieuwe print, dan Normaal (of wat er wel is), met een melding.
        val fit = PrintChoice.fitFinish(e.finish, o.finishes)
        note = if (fit != e.finish) noFinish.format(finishLabelPlain(e.finish), finishLabelPlain(fit)) else null
        e = e.copy(finish = fit)
    })
}

/**
 * "+ Exemplaar toevoegen" (punt 8j): Normaal, Foil of Etched (alleen wat er van deze print bestaat, met prijs),
 * het aantal en meteen de plek: de collectie (eventueel binder, blad en vak) of een deck.
 */
@Composable
fun AddCopyDialog(vm: AppViewModel, print: CardPrint, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val decks by vm.decks.collectAsState()
    val binders by vm.binders.collectAsState()
    var finishes by remember { mutableStateOf<List<String>?>(null) }
    var finish by remember { mutableStateOf(Finish.NONFOIL) }
    var qty by remember { mutableStateOf("1") }
    var toDeck by remember { mutableStateOf<Long?>(null) }
    var section by remember { mutableStateOf(Section.MAIN) }
    var binder by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf("") }
    LaunchedEffect(print.id) {
        val f = runCatching { vm.finishesOf(print) }.getOrDefault(listOf(Finish.NONFOIL, Finish.FOIL))
        finishes = f
        finish = PrintChoice.fitFinish(Finish.NONFOIL, f)
    }
    val cur = settings.currency
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.add_copy))
                Text("${print.name} · ${print.setCode} ${print.collectorNumber}", fontSize = NgText.body, color = LocalNgExtra.current.subtle, maxLines = 1)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val f = finishes
                if (f == null) CircularProgressIndicator(Modifier.padding(8.dp))
                else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.forEach { x ->
                        FilterChip(selected = finish == x, onClick = { finish = x }, label = {
                            Column {
                                Text(finishWord(x), fontSize = NgText.body)
                                Text(PrintChoice.price(print, x, cur, f)?.let { money(it, cur) } ?: "–", fontSize = NgText.caption)
                            }
                        })
                    }
                }
                OutlinedTextField(qty, { qty = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.destination), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().clickable { toDeck = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = toDeck == null, onClick = { toDeck = null }); Text(stringResource(R.string.collection))
                }
                if (toDeck == null) {
                    OutlinedTextField(binder, { binder = it }, label = { Text(stringResource(R.string.binder_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (binders.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        binders.take(3).forEach { b -> FilterChip(selected = binder == b, onClick = { binder = b }, label = { Text(b, maxLines = 1) }) }
                    }
                    if (binder.isNotBlank()) Row {
                        OutlinedTextField(page, { page = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.page)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(slot, { slot = it.filter(Char::isDigit).take(1) }, label = { Text(stringResource(R.string.slot)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
                decks.forEach { d ->
                    Row(Modifier.fillMaxWidth().clickable { toDeck = d.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = toDeck == d.id, onClick = { toDeck = d.id }); Text(d.name)
                    }
                }
                if (toDeck != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Section.COMMANDER, Section.MAIN, Section.SIDEBOARD).forEach { s ->
                        FilterChip(selected = section == s, onClick = { section = s }, label = { Text(sectionLabel(s)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = finishes != null, onClick = {
                vm.addCopy(print, finish, qty.toIntOrNull() ?: 1, toDeck, section, binder.trim().ifBlank { null }, page.toIntOrNull(), slot.toIntOrNull()?.coerceIn(1, 9))
                onDismiss()
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
