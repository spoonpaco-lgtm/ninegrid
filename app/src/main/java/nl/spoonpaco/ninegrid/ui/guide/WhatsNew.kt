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
        Item("Delen en ruilen: stuur (een deel van) je collectie als bestand, bekijk collecties van vrienden en zie wat je kunt ruilen.", "share"),
        Item("Instellingen in acht groepen, elk met hoe het nu staat.", "settings"),
        Item("De handleiding staat bovenaan in Meer en heeft een zoekbalk.", "start"),
        Item("Scannen: knoppen gecentreerd, de uitslag in kleur en een knop Controleer met het aantal.", "add"),
        Item("AI-hulp: begrijpelijke foutmeldingen, stil opnieuw proberen bij drukte, en een AI-label op elk vak.", "settings"),
        Item("Combo's tellen weer mee in de bracket. Is het deck gewijzigd, dan zie je dat bij de combo's.", "decks"),
    )

    private val EN = listOf(
        Item("Share and trade: send (part of) your collection as a file, view friends' collections and see what you can trade.", "share"),
        Item("Settings in eight groups, each showing how it is set now.", "settings"),
        Item("The guide is at the top of More and has a search bar.", "start"),
        Item("Scanning: buttons centred, the result in colour and a Check button with the count.", "add"),
        Item("AI help: clear error messages, quiet retries when busy, and an AI label on every slot.", "settings"),
        Item("Combos count in the bracket again. If the deck changed, the combos tab tells you.", "decks"),
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
