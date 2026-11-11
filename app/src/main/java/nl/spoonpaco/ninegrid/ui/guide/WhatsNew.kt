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
        Item("De scanner is gerepareerd na vier scanrapporten: galerijfoto's met een lege rij of kolom komen niet meer in de verkeerde vakken, verzonnen setcodes en talen worden genegeerd, een jaartal is geen kaartnummer meer en de set van het blad wint van één verlezen set.", "add"),
        Item("Foil-twijfel is blauw met een ★. Oranje is nu alleen nog voor kaarten waarvan de naam, set of print niet zeker is. Op het controlescherm staan de kaarten in groepen.", "add"),
        Item("Kaart voor kaart scannen: de laatste drie kaarten onderin met +1 en een kruisje; is alles zeker, dan kun je meteen opslaan. Foil is een stand voor de hele sessie.", "add"),
        Item("Scannen naar een deck: in een deck onder Toevoegen kies je Scannen. Daarbij één keuze voor uit de collectie halen: alles wat je hebt, exact dezelfde print of niets.", "decks"),
        Item("Suggesties hebben thumbnails zoals Combo's: tik opent de kaart, vasthouden toont hem groot. De weergave kies je met één schakelaar Thumbnails | Namen; de blokken bij Combo's klappen in en uit.", "decks"),
        Item("Meer filter- en sorteerknoppen: kaarten van een vriend, het setscherm en het deck.", "collection"),
    )

    private val EN = listOf(
        Item("The scanner is repaired after four scan reports: gallery photos with an empty row or column no longer land in the wrong slots, made-up set codes and languages are ignored, a year is no longer a card number and the set of the page beats one misread set.", "add"),
        Item("Foil doubt is blue with a ★. Orange is now only for cards whose name, set or print is not sure. The review screen groups the cards.", "add"),
        Item("Scanning card by card: the last three cards at the bottom with +1 and a cross; if everything is sure you can save right away. Foil is a mode for the whole session.", "add"),
        Item("Scan into a deck: in a deck under Add choose Scan. With one choice for taking cards from your collection: everything you have, exact same print or nothing.", "decks"),
        Item("Suggestions have thumbnails like Combos: tap opens the card, press and hold shows it large. Choose the view with one switch, Thumbnails | Names; the blocks in Combos fold open and closed.", "decks"),
        Item("More filter and sort buttons: a friend's cards, the set screen and the deck.", "collection"),
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
