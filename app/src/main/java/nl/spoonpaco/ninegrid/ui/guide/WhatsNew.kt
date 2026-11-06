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
        Item("Bijwerken vanuit de app: een melding als er een nieuwe versie is, en één tik op Bijwerken.", "settings"),
        Item("Zoeken in alle Magic-kaarten, met een label wat je al hebt, en een verlanglijst met prijsalarm.", "allcards"),
        Item("Binders: filteren, sorteren per binder en een bladweergave met lege vakken om te scannen.", "binders"),
        Item("Privacymodus: verberg bedragen met het oog bovenin; uitzetten met je vingerafdruk.", "privacy"),
        Item("Boosters bijhouden: wat je betaalde, wat eruit kwam, de hit en per set het gemiddelde.", "boosters"),
        Item("Decks: Statistieken, Combo's en Suggesties opnieuw ingedeeld, met landadvies, Trek 7, doelen per decktype en Bij vrienden.", "decks"),
        Item("Ruilen beter zichtbaar: op Overzicht, bij Suggesties en op het kaartscherm.", "share"),
        Item("Nieuw logo, gildekleuren, dag/nacht met zon en maan, en Magic-effecten zoals foil-glans en verbannen.", "settings"),
    )

    private val EN = listOf(
        Item("Update from the app: a notification when a new version is ready, and one tap on Update.", "settings"),
        Item("Search all Magic cards, with a label for what you already have, and a wishlist with price alerts.", "allcards"),
        Item("Binders: filter, sort by binder and a page view with empty slots to scan.", "binders"),
        Item("Privacy mode: hide amounts with the eye at the top; turn it off with your fingerprint.", "privacy"),
        Item("Track boosters: what you paid, what came out, the hit and the average per set.", "boosters"),
        Item("Decks: Statistics, Combos and Suggestions redesigned, with land advice, Draw 7, targets per deck type and At friends.", "decks"),
        Item("Trading easier to find: on Overview, in Suggestions and on the card screen.", "share"),
        Item("New logo, guild colours, day/night with sun and moon, and Magic effects such as foil shine and exile.", "settings"),
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
