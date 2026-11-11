package nl.spoonpaco.ninegrid.ui.components

import nl.spoonpaco.ninegrid.ui.theme.NgText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.AppSettings
import nl.spoonpaco.ninegrid.data.Images
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.scryfall.ScryfallClient
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

/** Zoek een kaart op naam en kies daarna de juiste print (set en nummer). */
@Composable
fun CardSearchDialog(
    scryfall: ScryfallClient,
    images: Images,
    settings: AppSettings,
    initial: String = "",
    onPick: (CardPrint) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initial) }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var prints by remember { mutableStateOf<List<CardPrint>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query, chosen) {
        if (chosen != null) return@LaunchedEffect
        delay(300)
        failed = false
        names = runCatching { scryfall.autocomplete(query.trim()) }.getOrElse { failed = true; emptyList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (chosen == null) stringResource(R.string.search_card) else stringResource(R.string.choose_print)) },
        text = {
            Column {
                if (chosen == null) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text(stringResource(R.string.card_name)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (failed) Text(stringResource(R.string.msg_network_error), color = LocalNgExtra.current.subtle, fontSize = NgText.body)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(names) { n ->
                            Text(
                                n,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    chosen = n
                                    loading = true
                                    scope.launch {
                                        prints = runCatching { scryfall.printsOf(n).map { it.toPrint() } }.getOrElse { emptyList() }
                                        loading = false
                                    }
                                }.padding(vertical = 10.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                } else {
                    Text(chosen ?: "", fontWeight = FontWeight.Bold)
                    if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(prints) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CardThumb(p, images, settings, 34)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${p.setCode} ${p.collectorNumber}", fontWeight = FontWeight.Bold)
                                    Text(p.setName, fontSize = NgText.body, color = LocalNgExtra.current.subtle)
                                }
                                Text(p.rarity, fontSize = NgText.body, color = LocalNgExtra.current.subtle)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (chosen != null) TextButton(onClick = { chosen = null; prints = emptyList() }) { Text(stringResource(R.string.back)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
