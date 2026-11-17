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
        Item("Exporteren: een deck naar Moxfield, Archidekt of Cardmarket (⋮ → Exporteren naar een site…), de hele collectie als Moxfield-CSV (Meer → Back-up en export) en je verlanglijst als Cardmarket-lijst. De formaten zijn nagekeken bij de sites zelf.", "decks"),
        Item("Deck delen via QR-code: ⋮ → Delen via QR-code in een deck, en op Decks het scan-icoon om er een te ontvangen. Past een commanderdeck in één code.", "decks"),
        Item("Verlanglijst: eén minder met de −knop, een regel weghalen door naar links te vegen (beide met Terugzetten), en elke regel zegt welke print hij toont of dat het elke print is.", "allcards"),
        Item("Gecrasht? Bij de volgende start vraagt NineGrid of je het crashrapport wilt delen (versie, toestel en foutmelding, geen kaarten). Je kiest zelf of het verstuurd wordt.", "settings"),
        Item("Kleine verbeteringen: Lijn of Taart op het Overzicht blijft onthouden; bij kaart voor kaart scannen geldt de set van de vorige kaart als aanname; naar een deck scannen of plakken kan met Alle kaarten naar Deck of Sideboard.", "decks"),
        Item("Meldingen: toestemming wordt gevraagd zodra je prijsstijging-, ban- of reprintmeldingen aanzet.", "settings"),
        Item("Scanrapport (Ontwikkelaar) noemt nu ook wat de app eerst dacht, naast wat jij ervan maakte. Ook staan de instellingen van die scan erin (set, lamp, automatisch afdrukken, AI-hulp, offline database, taal). Instellingen → Ontwikkelaar → Testset draaien laat de scanner een map met testbladen op je telefoon lezen en toont wat goed en fout is.", "settings"),
    )

    private val EN = listOf(
        Item("Export: a deck to Moxfield, Archidekt or Cardmarket (⋮ → Export to a site…), the whole collection as a Moxfield CSV (More → Backup and export) and your wishlist as a Cardmarket list. The formats were checked at the sites themselves.", "decks"),
        Item("Share a deck by QR code: ⋮ → Share via QR code in a deck, and the scan icon on Decks to receive one. A commander deck fits in one code.", "decks"),
        Item("Wishlist: one less with the − button, remove a line by swiping left (both with Put back), and every line says which print it shows or that it is any print.", "allcards"),
        Item("Crashed? On the next start NineGrid asks whether you want to share the crash report (version, phone and error message, no cards). You decide whether it is sent.", "settings"),
        Item("Small improvements: Line or Pie on the Overview is remembered; when scanning card by card the set of the previous card is assumed; scanning or pasting into a deck has All cards to Deck or Sideboard.", "decks"),
        Item("Notifications: permission is asked as soon as you turn on price rise, ban or reprint alerts.", "settings"),
        Item("The scan report (Developer) now also shows what the app first thought, next to what you made of it. It also lists the settings of that scan (set, torch, auto capture, AI help, offline database, language). A Settings → Developer → Run test set makes the scanner read a folder of test sheets on your phone and shows what is right and wrong.", "settings"),
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
