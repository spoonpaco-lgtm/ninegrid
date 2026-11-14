package nl.spoonpaco.ninegrid.ui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.R

/** De nieuwe punten van deze versie, elk met het hoofdstuk van de handleiding waar het staat. */
object WhatsNew {
    data class Item(val text: String, val chapter: String?)

    fun items(dutch: Boolean): List<Item> = if (dutch) NL else EN

    private val NL = listOf(
        Item("Overzicht: Aantal kaarten is nu een vakje in Lijnen kiezen. De lijst heeft groepen (gekozen, totaal, decks, sets), alleen sets waar je kaarten van hebt, de duurste eerst en met de naam erbij.", "overview"),
        Item("Overzicht: een schakelaar Lijn | Taart bij de grafiek. De taart toont de verdeling van nu per set, rarity of deck, naar waarde of aantal kaarten.", "overview"),
        Item("Kaartscherm: een inklapbaar blok Prijsverloop met het verloop van de prijs van die kaart en, dichtgeklapt, de verandering van de afgelopen week.", "collection"),
        Item("Fix: tikken op een ontbrekende kaart bij Combo's opende een leeg venster.", "decks"),
    )

    private val EN = listOf(
        Item("Overview: Number of cards is now a box in Choose lines. The list has groups (chosen, totals, decks, sets), only sets you have cards of, most valuable first and with the name added.", "overview"),
        Item("Overview: a Line | Pie switch at the chart. The pie shows how things are divided right now by set, rarity or deck, by value or number of cards.", "overview"),
        Item("Card screen: a collapsible Price history block with the price of that card over time and, collapsed, the change over the past week.", "collection"),
        Item("Fix: tapping a missing card in Combos opened an empty window.", "decks"),
    )
}

@Composable
fun WhatsNewDialog(onChapter: (String) -> Unit, onDismiss: () -> Unit) {
    val dutch = LocalConfiguration.current.locales[0].language == "nl"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new) + " · " + BuildConfig.VERSION_NAME + " – " + BuildConfig.CODENAME) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WhatsNew.items(dutch).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text("•  ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(item.text, fontSize = 14.sp)
                            item.chapter?.let { ch ->
                                Text(
                                    stringResource(R.string.whats_new_more), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onDismiss(); onChapter(ch) }.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.whats_new_later), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
