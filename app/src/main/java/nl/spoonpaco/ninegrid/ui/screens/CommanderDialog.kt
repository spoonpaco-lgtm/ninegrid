package nl.spoonpaco.ninegrid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.Repository
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Deck
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Section
import nl.spoonpaco.ninegrid.domain.CommanderRules
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

/** De kaart die de nieuwe commander wordt: een bestaand exemplaar (deck of collectie), of een nieuwe kaart uit het zoeken. */
data class PickedCommander(val entry: Entry?, val print: CardPrint)

/**
 * Commander wijzigen: eerst kiezen wie (uit het deck, uit je collectie of via zoeken), dan wat er met de oude
 * commander gebeurt, of de nieuwe als tweede commander erbij (Partner, Friends forever, Background, Doctor's companion).
 * Met [start] ingevuld (vanaf het kaartscherm) begint het meteen bij de tweede stap.
 */
@Composable
fun ChangeCommanderDialog(vm: AppViewModel, deck: Deck, start: PickedCommander? = null, onDismiss: () -> Unit) {
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val extra = LocalNgExtra.current
    var picked by remember { mutableStateOf(start) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val commanders = remember(all, deck.id) { all.filter { it.entry.deckId == deck.id && it.entry.section == Section.COMMANDER && it.print != null } }

    if (searching) {
        nl.spoonpaco.ninegrid.ui.components.CardSearchDialog(
            scryfall = vm.repo.scryfall, images = vm.images, settings = settings,
            onPick = { p -> searching = false; picked = PickedCommander(null, p) },
            onDismiss = { searching = false },
        )
        return
    }

    val p = picked
    if (p == null) {
        // Stap 1: wie wordt de commander?
        val candidates = remember(all, deck.id, query) {
            all.filter { e ->
                val pr = e.print ?: return@filter false
                (e.entry.deckId == deck.id && e.entry.section != Section.COMMANDER || e.entry.deckId == null) &&
                    CommanderRules.canBeCommander(pr.typeLine, pr.oracleText) &&
                    (query.isBlank() || pr.name.contains(query.trim(), true))
            }.distinctBy { it.print!!.id }.sortedBy { it.print!!.name }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.commander_change)) },
            text = {
                Column {
                    OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_card)) })
                    Spacer(Modifier.height(6.dp))
                    if (candidates.isEmpty()) Text(stringResource(R.string.commander_none_candidates), fontSize = 13.sp, color = extra.subtle)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.print!!.id }) { c ->
                            Column(Modifier.fillMaxWidth().clickable { picked = PickedCommander(c.entry, c.print!!) }.padding(vertical = 8.dp)) {
                                Text(c.print!!.name, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(if (c.entry.deckId == deck.id) R.string.commander_in_deck else R.string.commander_in_collection) + " · " + c.print.typeLine,
                                    fontSize = 12.sp, color = extra.subtle, maxLines = 1,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { searching = true }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.commander_search_new)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    // Stap 2: vervangen of tweede commander, en wat er met de oude gebeurt.
    val canSecond = commanders.size == 1 && commanders.all { c ->
        val o = c.print!!
        CommanderRules.canPair(o.name, o.typeLine, o.oracleText, p.print.name, p.print.typeLine, p.print.oracleText)
    }
    var replace by remember { mutableStateOf(true) }
    var old by remember { mutableStateOf(Repository.OldCommander.TO_DECK) }
    val allowed = CommanderRules.canBeCommander(p.print.typeLine, p.print.oracleText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.print.name) },
        text = {
            Column {
                if (!allowed) Text(stringResource(R.string.commander_not_allowed), color = nl.spoonpaco.ninegrid.ui.theme.NgColors.Amber, fontSize = 13.sp)
                if (commanders.isNotEmpty()) {
                    val names = commanders.joinToString(" + ") { it.print!!.name }
                    Row(Modifier.fillMaxWidth().clickable { replace = true }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = replace, onClick = { replace = true }); Text(stringResource(R.string.commander_replace, names), fontSize = 14.sp)
                    }
                    if (canSecond) Row(Modifier.fillMaxWidth().clickable { replace = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !replace, onClick = { replace = false }); Text(stringResource(R.string.commander_add_second), fontSize = 14.sp)
                    } else Text(stringResource(R.string.commander_no_pair), fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(start = 12.dp))
                    if (replace) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.commander_old_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth().clickable { old = Repository.OldCommander.TO_DECK }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = old == Repository.OldCommander.TO_DECK, onClick = { old = Repository.OldCommander.TO_DECK }); Text(stringResource(R.string.commander_old_to_deck), fontSize = 14.sp)
                        }
                        Row(Modifier.fillMaxWidth().clickable { old = Repository.OldCommander.TO_COLLECTION }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = old == Repository.OldCommander.TO_COLLECTION, onClick = { old = Repository.OldCommander.TO_COLLECTION }); Text(stringResource(R.string.commander_old_to_collection), fontSize = 14.sp)
                        }
                    }
                }
                if (p.entry == null) Text(stringResource(R.string.commander_new_card), fontSize = 12.sp, color = extra.subtle, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.changeCommander(deck, p.entry, if (p.entry == null) p.print else null, replace || commanders.isEmpty(), old); onDismiss() }) {
                Text(stringResource(R.string.commander_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
