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
        Item("Top movers op het Overzicht: welke kaarten van jou het meest stegen of daalden, per dag of per week, op geld (prijsverandering maal je aantal). Winner of the Week staat bovenaan, en nu is er ook Loser of the Week. Ingeklapt zie je alleen die; tik op de titel voor de hele lijst.", "overview"),
        Item("Effecten op Winner en Loser: een mythic-gloed en bloed dat van de verliezer afdruipt, of een stempel WINNER en DESTROYED. Kies onder Instellingen → Weergave.", "overview"),
        Item("Magic-effecten per effect: naast de hoofdschakelaar zet je nu elk effect los aan of uit (foil-glans, verbannen, stempels, schildjes, Winner en Loser, bewegende privacystijlen).", "settings"),
        Item("Toxic heeft diepte gekregen: schaduw, glanzende ooze en druppels met een glimlicht, zoals het bloed bij Loser of the Week.", "privacy"),
        Item("Scanner: de setlijst komt eerst uit de offline kaartdatabase. Kiezen binnen de set en het raster op de namen werken zo ook zonder internet.", "add"),

    )

    private val EN = listOf(
        Item("Top movers on the Overview: which of your cards rose or fell the most, per day or per week, by money (price change times your copies). Winner of the Week sits on top, and there is now a Loser of the Week too. Collapsed you only see that one; tap the title for the full list.", "overview"),
        Item("Effects on Winner and Loser: a mythic glow and blood dripping off the loser, or a WINNER and DESTROYED stamp. Choose under Settings → View.", "overview"),
        Item("Magic effects per effect: next to the main switch you can now turn each effect on or off (foil shine, exile, stamps, shields, Winner and Loser, animated privacy styles).", "settings"),
        Item("Toxic got depth: a shadow, glossy ooze and drops with a highlight, like the blood on Loser of the Week.", "privacy"),
        Item("Scanner: the set list now comes from the offline card database first, so picking within the set and the grid on the names also work without internet.", "add"),

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
