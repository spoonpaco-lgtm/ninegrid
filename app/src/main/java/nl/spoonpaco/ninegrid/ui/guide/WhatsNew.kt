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
        Item("Andere prints: op het kaartscherm opent Andere prints › de lijst met alle prints van een kaart, met plaatje, jaar, de prijs per afwerking, goedkoopst en ✓ bij de huidige. Sorteer op nieuwste of goedkoopste.", "collection"),
        Item("Print van een exemplaar wijzigen: ✎ → tik op de regel Print. Het exemplaar houdt zijn plek; bij een stapel kies je voor hoeveel het geldt. Zo zet je ook 1 van 3 op foil.", "collection"),
        Item("+ Exemplaar toevoegen staat nu altijd op het kaartscherm: Normaal, Foil of Etched (met prijs), het aantal en meteen de plek.", "collection"),
        Item("Verlanglijst: kies de precieze print uit de printlijst; prijs en prijsalarm rekenen met die print.", "allcards"),
        Item("Bans en reprints: een melding als een kaart die je hebt gebanned of weer toegestaan wordt, of opnieuw uitkomt in een nieuwe set. In te stellen bij Instellingen → Collectie.", "overview"),
        Item("Naar een deck: kaarten die banned zijn of buiten de kleuren van de commander vallen, staan meteen oranje bij Controleer.", "review"),
    )

    private val EN = listOf(
        Item("Other prints: on the card screen, Other prints › opens the list of all prints of a card, with image, year, the price per finish, cheapest and ✓ at the current one. Sort by newest or cheapest.", "collection"),
        Item("Change the print of a copy: ✎ → tap the Print line. The copy keeps its location; for a stack you choose how many it applies to. This is also how you make 1 of 3 foil.", "collection"),
        Item("+ Add copy is now always on the card screen: Normal, Foil or Etched (with price), the quantity and the location right away.", "collection"),
        Item("Wishlist: choose the exact print from the list of prints; price and price alert use that print.", "allcards"),
        Item("Bans and reprints: a message when a card you own is banned or allowed again, or comes out again in a new set. Set it in Settings → Collection.", "overview"),
        Item("To a deck: cards that are banned or outside the commander's colours are orange under Check right away.", "review"),
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
