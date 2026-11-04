package nl.spoonpaco.ninegrid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.AppViewModel.ImportStep
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/** Je bestaande collectie in één keer overzetten uit de MTG Collection Sheet (.xlsx) of een .csv. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val extra = LocalNgExtra.current
    val entries by vm.entries.collectAsState()
    val clipboard = LocalClipboardManager.current
    var replace by remember { mutableStateOf(false) }
    var asDeck by remember { mutableStateOf(false) }
    var deckName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readImport) }
    val mimeTypes = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values",
        "application/vnd.ms-excel", "text/plain", "application/octet-stream",
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.import_title)) },
            navigationIcon = { IconButton(onClick = { vm.cancelImport(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("import") },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (vm.importStep) {
                ImportStep.IDLE, ImportStep.FAILED -> {
                    Text(stringResource(R.string.import_explain))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.import_how), fontSize = 13.sp, color = extra.subtle)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { picker.launch(mimeTypes) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.import_choose))
                    }
                    if (vm.importStep == ImportStep.FAILED) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.import_unreadable), color = NgColors.Error)
                    }
                }
                ImportStep.READING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(); Spacer(Modifier.padding(8.dp)); Text(stringResource(R.string.import_reading))
                }
                ImportStep.MAPPING -> {
                    val d = vm.importData ?: return@Column
                    Text(stringResource(R.string.import_mapping_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.import_mapping_explain), fontSize = 13.sp, color = extra.subtle)
                    Spacer(Modifier.height(8.dp))
                    val roleLabels = nl.spoonpaco.ninegrid.domain.SheetImport.Role.entries.associate { it.name to roleLabel(it.name) }
                    d.headers.forEachIndexed { i, head ->
                        val example = d.sample.mapNotNull { it.getOrNull(i)?.takeIf { v -> v.isNotBlank() } }.take(2).joinToString(", ")
                        ChoiceRow(
                            "${head.ifBlank { "#${i + 1}" }}" + if (example.isNotBlank()) " ($example)" else "",
                            nl.spoonpaco.ninegrid.domain.SheetImport.Role.entries.map { it.name },
                            (vm.importMapping[i] ?: nl.spoonpaco.ninegrid.domain.SheetImport.Role.IGNORE).name,
                            { roleLabels[it] ?: it },
                        ) { r -> vm.importMapping = vm.importMapping + (i to nl.spoonpaco.ninegrid.domain.SheetImport.Role.valueOf(r)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    val ok = vm.importMapping.values.any { it == nl.spoonpaco.ninegrid.domain.SheetImport.Role.NAME || it == nl.spoonpaco.ninegrid.domain.SheetImport.Role.SCRYFALL_ID }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.cancelImport() }) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { vm.applyMapping() }, enabled = ok, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.next)) }
                    }
                    if (!ok) Text(stringResource(R.string.import_mapping_need_name), fontSize = 12.sp, color = extra.subtle)
                }
                ImportStep.READY -> {
                    val d = vm.importData ?: return@Column
                    Text(stringResource(R.string.import_found), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (d.format.isNotBlank()) Text(stringResource(R.string.import_format, d.format), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Line(stringResource(R.string.import_sheets), d.sheetsRead.joinToString(", "))
                    Line(stringResource(R.string.collection), pluralStringResource(R.plurals.n_cards, d.collectionCount, d.collectionCount))
                    Line(stringResource(R.string.decks), stringResource(R.string.import_decks_line, d.decks.size, d.deckCount))
                    d.decks.forEach { Text("· ${it.name} (${it.cards.sumOf { c -> c.quantity }})", fontSize = 13.sp, color = extra.subtle, modifier = Modifier.padding(start = 12.dp)) }
                    if (d.history.isNotEmpty()) Line(stringResource(R.string.import_history), stringResource(R.string.import_history_line, d.history.size))
                    val proxies = (d.collection + d.decks.flatMap { it.cards }).filter { it.proxy }.sumOf { it.quantity }
                    if (proxies > 0) Line(stringResource(R.string.proxy), proxies.toString())
                    if (d.skippedRows > 0) Text(stringResource(R.string.import_skipped, d.skippedRows), fontSize = 12.sp, color = extra.subtle)
                    Spacer(Modifier.height(12.dp))
                    if (entries.isNotEmpty()) {
                        Text(stringResource(R.string.import_existing), fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !replace, onClick = { replace = false }); Text(stringResource(R.string.import_add))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = replace, onClick = { replace = true }); Text(stringResource(R.string.import_replace))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Een bestand uit een andere app kan ook één deck zijn.
                    if (d.decks.isEmpty() && d.collection.isNotEmpty()) {
                        Text(stringResource(R.string.import_where), fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !asDeck, onClick = { asDeck = false }); Text(stringResource(R.string.import_to_collection_choice))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = asDeck, onClick = { asDeck = true }); Text(stringResource(R.string.import_as_deck))
                        }
                        if (asDeck) androidx.compose.material3.OutlinedTextField(deckName, { deckName = it }, singleLine = true, label = { Text(stringResource(R.string.deck_name)) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(stringResource(R.string.import_time_hint), fontSize = 12.sp, color = extra.subtle)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.cancelImport() }) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = { vm.runImport(replace, if (asDeck) deckName.trim().ifBlank { vm.importData?.format?.ifBlank { null } ?: "Import" } else null) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_start)) }
                    }
                }
                ImportStep.RESOLVING, ImportStep.SAVING -> {
                    Text(stringResource(if (vm.importStep == ImportStep.SAVING) R.string.import_saving else R.string.import_looking_up), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { vm.importProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("${(vm.importProgress * 100).toInt()}%", color = extra.subtle)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.import_keep_open), fontSize = 12.sp, color = extra.subtle)
                }
                ImportStep.DONE -> {
                    Text(vm.importResult, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.import_undo_hint), fontSize = 13.sp, color = extra.subtle)
                    if (vm.importNotFound.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.import_not_found_title), fontWeight = FontWeight.Bold, color = NgColors.Error)
                        vm.importNotFound.take(60).forEach {
                            Text("${it.quantity} ${it.name} (${it.setCode}) ${it.number}", fontSize = 13.sp)
                        }
                        if (vm.importNotFound.size > 60) Text("…", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.notFoundAsList())); vm.message = vm.getApplication<android.app.Application>().getString(R.string.copied) }) {
                            Text(stringResource(R.string.import_copy_not_found))
                        }
                        Text(stringResource(R.string.import_not_found_hint), fontSize = 12.sp, color = extra.subtle)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.cancelImport(); onDone() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_to_collection)) }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun roleLabel(r: String) = when (r) {
    "NAME" -> stringResource(R.string.role_name); "SET" -> stringResource(R.string.role_set); "SET_NAME" -> stringResource(R.string.role_set_name)
    "NUMBER" -> stringResource(R.string.role_number); "QUANTITY" -> stringResource(R.string.quantity); "FINISH" -> stringResource(R.string.finish)
    "LANGUAGE" -> stringResource(R.string.language); "CONDITION" -> stringResource(R.string.condition); "SCRYFALL_ID" -> "Scryfall ID"
    "PROXY" -> stringResource(R.string.proxy); else -> stringResource(R.string.role_ignore)
}
