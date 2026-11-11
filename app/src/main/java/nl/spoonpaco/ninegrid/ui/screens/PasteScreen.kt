package nl.spoonpaco.ninegrid.ui.screens

import nl.spoonpaco.ninegrid.ui.theme.NgText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteScreen(vm: AppViewModel, intoDeck: Long?, asNewDeck: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (asNewDeck) R.string.new_deck else R.string.paste_list)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("add") },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Text(stringResource(R.string.paste_explain), fontSize = NgText.body, color = LocalNgExtra.current.subtle)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("1 Edgar Markov (INR) 328\n4 Murder (M20) 109 *F*\n\nSIDEBOARD:\n2 Duress (STA) 29") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { clipboard.getText()?.let { text = it.text } }) { Text(stringResource(R.string.paste_from_clipboard)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.startPaste(text, intoDeck, asNewDeck); onNext() }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.next))
                }
            }
        }
    }
}
