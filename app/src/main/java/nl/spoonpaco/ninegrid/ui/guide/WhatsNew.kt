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
        Item("Scanner: groen is weer te vertrouwen. Een stukje naam (\"Keyrune\", \"Gloom\") is niet meer genoeg voor een kaart; de app kiest de naam die binnen de set het best past, en alleen als die duidelijk beter past dan de rest.", "review"),
        Item("Geen set ingevuld en geen nummer gelezen? Dan kiest de app niet meer op de gok een print, maar staat de kaart oranje. Bestaan alle kaarten van het blad samen in één set, dan vraagt het controlescherm: die set gebruiken? Pas na jouw tik telt hij.", "review"),
        Item("Splitkaarten (zoals Discovery // Dispersal) worden gedraaid gelezen. Een foto uit de galerij met een stuk van de buurpagina erop schuift de vakken niet meer op. Jaartallen als 2025W tellen niet meer als kaartnummer.", "add"),
        Item("Testset draaien (Ontwikkelaar) meet nu met het kader van de camera, en daarnaast als galerijfoto.", "settings"),

    )

    private val EN = listOf(
        Item("Scanner: green can be trusted again. Part of a name (\"Keyrune\", \"Gloom\") is no longer enough for a card; the app picks the name that fits best within the set, and only if it clearly fits better than the others.", "review"),
        Item("No set entered and no number read? The app no longer guesses a print; the card shows orange. If all cards on the page exist together in one set, the review screen asks: use that set? It only counts after you tap.", "review"),
        Item("Split cards (like Discovery // Dispersal) are read sideways. A gallery photo with part of the neighbouring page no longer shifts the slots. Years like 2025W no longer count as a card number.", "add"),
        Item("Run test set (Developer) now measures with the camera frame, and also as a gallery photo.", "settings"),

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
