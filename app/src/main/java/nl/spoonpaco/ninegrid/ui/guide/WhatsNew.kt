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
        Item("Herkennen op het plaatje: naast de tekst kijkt de scanner nu naar het plaatje, binnen de set die je invult. Splitkaarten, een verkeerd gelezen nummer of een kaart zonder leesbare naam worden zo vaker gevonden. Het plaatje maakt nooit groen: wat het vindt is oranje, en ziet het een andere kaart dan de tekst, dan wordt groen oranje.", "add"),
        Item("Werkt offline: per set maakt de app één keer vingerafdrukken uit de Scryfall-plaatjes (een paar honderd kB). Met Sets vooraf downloaden (Instellingen → Scannen) zet je ze klaar voor je hele collectie; een set die je bij het scannen invult, komt er vanzelf bij.", "settings"),
        Item("Kaart voor kaart: de app kijkt mee met de camera en vult wat de foto mist (set of nummer) aan met wat meerdere beelden van dezelfde kaart lazen.", "add"),
        Item("Controlescherm: is er iets te controleren, dan staan de zekere kaarten ingeklapt. Tik op Zeker om ze te zien; ze worden gewoon opgeslagen.", "add"),
    )

    private val EN = listOf(
        Item("Recognise by picture: besides the text, the scanner now looks at the picture, within the set you enter. Split cards, a misread number or a card without a readable name are found more often. The picture never makes a card green: what it finds is orange, and if it sees a different card than the text, green turns orange.", "add"),
        Item("Works offline: per set the app makes fingerprints once from the Scryfall images (a few hundred kB). Download sets in advance (Settings → Scanning) prepares them for your whole collection; a set you enter while scanning is added by itself.", "settings"),
        Item("Card by card: the app watches the camera and fills in what the photo missed (set or number) with what several frames of the same card read.", "add"),
        Item("Review screen: when there is something to check, the sure cards are folded away. Tap Sure to see them; they are saved as usual.", "add"),
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
