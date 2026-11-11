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
        Item("Op het kaartscherm veeg je naar links of rechts voor de volgende of vorige kaart uit de lijst waar je vandaan kwam.", "collection"),
        Item("Nieuw: label NIEUW op kaarten die je pas toevoegde. In de instellingen kies je hoe lang (standaard 7 dagen), met een knop Alles gezien; in de collectie kun je filteren en sorteren op nieuw.", "collection"),
        Item("Commander wijzigen: vervang je commander of voeg een tweede toe (Partner e.d.), vanuit het deckmenu of met Maak commander op het kaartscherm.", "decks"),
        Item("Combo's als kaartjes met + ertussen; tik opent de kaart, lang indrukken toont hem groot. Met Namen krijg je de oude lijst terug.", "decks"),
        Item("Ontwikkelaar-menu in de instellingen met een scanrapport: na een scan deel je met één tik de foto en wat er per vak gelezen is, zodat een slechte scan te onderzoeken is.", "settings"),
        Item("Alles wissen heeft nu ook Alles (fabrieksinstellingen): na een back-up begint de app helemaal opnieuw.", "backup"),
        Item("Gaat de lamp van de scanner niet aan, dan zegt de app waarom (bijvoorbeeld een lage batterij).", "add"),
    )

    private val EN = listOf(
        Item("On the card screen swipe left or right for the next or previous card of the list you came from.", "collection"),
        Item("New: a NEW label on cards you just added. Choose how long in the settings (7 days by default), with an All seen button; in the collection you can filter and sort on new.", "collection"),
        Item("Change commander: replace your commander or add a second one (Partner etc.), from the deck menu or with Make commander on the card screen.", "decks"),
        Item("Combos as small cards with a + between them; tap opens the card, press and hold shows it large. Names brings the old list back.", "decks"),
        Item("Developer menu in the settings with a scan report: after a scan you share the photo and what was read per slot with one tap, so a bad scan can be investigated.", "settings"),
        Item("Erase everything now also has Everything (factory reset): after a backup the app starts over completely.", "backup"),
        Item("If the scanner lamp does not turn on, the app tells you why (a low battery, for example).", "add"),
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
