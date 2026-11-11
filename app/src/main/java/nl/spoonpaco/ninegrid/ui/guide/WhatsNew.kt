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
        Item("Deck importeren met verschillende landjes: je krijgt nu precies de prints uit je lijst. Basic lands komen alleen nog uit je collectie als het dezelfde print is; bij andere kaarten zie je welke print er uit je collectie komt.", "decks"),
        Item("De bladweergave past altijd op één scherm, het app-icoon past in het rondje, en de melding met Ongedaan maken staat boven de navigatiebalk van Android.", "binders"),
        Item("Scanner: leest nummers beter (O/0 en I/1 hersteld), herkent de ster ★ van foils en zet die kaart alvast op foil (oranje: even controleren), en leest sneller.", "add"),
        Item("Grafiek: de as volgt de waarden, zodat je ook kleine veranderingen ziet. Het aantal kaarten loopt mee als tweede lijn, en Koers heet nu Prijsbeweging.", "overview"),
        Item("Back-up compleet: ook instellingen, deckregels, vrienden en het wijzigingslog. Je ziet wat erin zit en kiest bij terugzetten wat je terugzet.", "backup"),
        Item("Setscherm vernieuwd: kopkaart met voortgang, schakelaar Ontbreekt | Heb ik, en per kaart ♡ en + of waar hij ligt.", "collection"),
        Item("Manakosten staan nu rechts, vlak vóór de prijs, altijd even groot. Nieuw filter Game Changers. Verlanglijst: tik = kaartscherm, ✎ = bewerken.", "collection"),
        Item("Suggesties bij een deck: de blokken klappen in en uit en leggen uit wat ze zijn.", "decks"),
    )

    private val EN = listOf(
        Item("Importing a deck with different lands: you now get exactly the prints from your list. Basic lands only come out of your collection when it is the same print; for other cards you see which print comes out of your collection.", "decks"),
        Item("The page view always fits on one screen, the app icon fits in the circle, and the message with Undo sits above Android's navigation bar.", "binders"),
        Item("Scanner: reads numbers better (O/0 and I/1 repaired), recognises the foil star ★ and already sets that card to foil (orange: have a look), and reads faster.", "add"),
        Item("Chart: the axis follows the values, so small changes show too. The number of cards runs along as a second line, and Market is now called Price movement.", "overview"),
        Item("Complete backup: settings, deck rules, friends and the change log too. You see what is in it and choose what to restore.", "backup"),
        Item("New set screen: header card with progress, a Missing | I have switch, and per card ♡ and + or where it is.", "collection"),
        Item("Mana costs are now on the right, just before the price, always the same size. New Game Changers filter. Wishlist: tap = card screen, ✎ = edit.", "collection"),
        Item("Deck suggestions: the blocks fold and unfold and explain what they are.", "decks"),
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
