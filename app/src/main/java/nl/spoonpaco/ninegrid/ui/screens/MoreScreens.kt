package nl.spoonpaco.ninegrid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import nl.spoonpaco.ninegrid.BuildConfig
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.ImageMode
import nl.spoonpaco.ninegrid.data.UpdateChecker
import nl.spoonpaco.ninegrid.domain.Export
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onPaste: () -> Unit, onChanges: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit, onGuide: () -> Unit, onImport: () -> Unit = {}) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.more), fontWeight = FontWeight.Bold) }) }) { pad ->
        Column(Modifier.padding(pad)) {
            ListItem(headlineContent = { Text(stringResource(R.string.import_title)) }, supportingContent = { Text(stringResource(R.string.import_sub)) },
                leadingContent = { Icon(Icons.Filled.FileOpen, null) }, modifier = Modifier.clickableRow(onImport))
            ListItem(headlineContent = { Text(stringResource(R.string.paste_list)) }, leadingContent = { Icon(Icons.Filled.ContentPaste, null) },
                modifier = Modifier.clickableRow(onPaste))
            ListItem(headlineContent = { Text(stringResource(R.string.change_log)) }, leadingContent = { Icon(Icons.Filled.History, null) },
                modifier = Modifier.clickableRow(onChanges))
            ListItem(headlineContent = { Text(stringResource(R.string.settings_backup)) }, leadingContent = { Icon(Icons.Filled.Settings, null) },
                modifier = Modifier.clickableRow(onSettings))
            ListItem(headlineContent = { Text(stringResource(R.string.guide)) }, leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                modifier = Modifier.clickableRow(onGuide))
            ListItem(headlineContent = { Text(stringResource(R.string.about)) }, leadingContent = { Icon(Icons.Filled.Info, null) },
                modifier = Modifier.clickableRow(onAbout))
        }
    }
}

fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeLogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val changes by vm.changes.collectAsState()
    val st by vm.settings.collectAsState()
    val latest = changes.firstOrNull { !it.reverted }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.change_log)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("undo") },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Button(onClick = { vm.undo() }, enabled = latest != null, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, null); Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.undo_latest))
            }
            Text(stringResource(R.string.undo_explain), fontSize = 12.sp, color = LocalNgExtra.current.subtle, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn {
                items(changes, key = { it.id }) { ch ->
                    ListItem(
                        headlineContent = { Text(ch.summary, textDecoration = if (ch.reverted) TextDecoration.LineThrough else null) },
                        supportingContent = {
                            Text(nl.spoonpaco.ninegrid.domain.Dates.dateTime(ch.timestamp, st.dateStyle) + if (ch.reverted) " · " + stringResource(R.string.undone) else "")
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val all by vm.entries.collectAsState()
    val decks by vm.decks.collectAsState()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var wiping by remember { mutableStateOf(false) }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backupTo) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.writeText(it, Export.collectionCsv(all, decks.associate { d -> d.id to d.name })) }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setBackupFolder) }
    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "system" }
    val themeLabels = mapOf("system" to themeLabelPlain("system"), "day" to themeLabelPlain("day"), "night" to themeLabelPlain("night"))
    val langLabels = mapOf("system" to langLabelPlain("system"), "nl" to "Nederlands", "en" to "English")
    val imageLabels = listOf(ImageMode.OFF, ImageMode.SMALL, ImageMode.FULL).associateWith { imageModeLabelPlain(it) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_backup)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("backup") },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.appearance), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ChoiceRow(stringResource(R.string.theme), listOf("system", "day", "night"), settings.theme, { themeLabels[it] ?: it }) { vm.setTheme(it) }
            ChoiceRow(stringResource(R.string.app_language), listOf("system", "nl", "en"), currentLang.substringBefore('-'), { langLabels[it] ?: it }) {
                AppCompatDelegate.setApplicationLocales(if (it == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(it))
            }
            ChoiceRow(stringResource(R.string.currency), listOf("EUR", "USD"), settings.currency, { if (it == "USD") "USD (TCGplayer)" else "EUR (Cardmarket)" }) { vm.setCurrency(it) }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.images), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ChoiceRow(stringResource(R.string.images_store), listOf(ImageMode.OFF, ImageMode.SMALL, ImageMode.FULL), settings.imageMode, { imageLabels[it] ?: it }) { vm.setImageMode(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wifi_only), modifier = Modifier.weight(1f))
                Switch(checked = settings.wifiOnly, onCheckedChange = { vm.setWifiOnly(it) })
            }
            Text(stringResource(R.string.images_used, vm.images.diskUsageBytes() / (1024 * 1024)), fontSize = 12.sp, color = LocalNgExtra.current.subtle)

            // Datum en tijd: alleen hoe het op het scherm staat; opslaan en delen gebeurt altijd in één vast formaat.
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.date_time), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            val sys = stringResource(R.string.follow_phone)
            val mon = stringResource(R.string.monday); val sun = stringResource(R.string.sunday)
            ChoiceRow(stringResource(R.string.time_format), listOf("system", "24", "12"), settings.timeFormat,
                { mapOf("system" to sys, "24" to "24h (14:30)", "12" to "12h (2:30 PM)")[it] ?: it }) { vm.setTimeFormat(it) }
            ChoiceRow(stringResource(R.string.date_format), listOf("system", "dmy", "mdy", "ymd"), settings.dateFormat,
                { mapOf("system" to sys, "dmy" to "05-11-2026", "mdy" to "11/05/2026", "ymd" to "2026-11-05")[it] ?: it }) { vm.setDateFormat(it) }
            ChoiceRow(stringResource(R.string.week_start), listOf("system", "mon", "sun"), settings.weekStart,
                { mapOf("system" to sys, "mon" to mon, "sun" to sun)[it] ?: it }) { vm.setWeekStart(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_weekday), modifier = Modifier.weight(1f))
                Switch(checked = settings.showWeekday, onCheckedChange = { vm.setShowWeekday(it) })
            }
            Text(stringResource(R.string.date_example, nl.spoonpaco.ninegrid.domain.Dates.dateTime(System.currentTimeMillis(), settings.dateStyle)), fontSize = 12.sp, color = LocalNgExtra.current.subtle)

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.completionist), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.completionist_explain), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.completionist, onCheckedChange = { vm.setCompletionist(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.breakout_alerts), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.breakout_alerts_explain), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.breakoutAlerts, onCheckedChange = { vm.setBreakoutAlerts(it) })
            }

            // Offline kaartdatabase: optie, standaard uit.
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.offline_db), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadOfflineDbInfo() }
            Text(stringResource(R.string.offline_pros), fontSize = 12.sp)
            Text(stringResource(R.string.offline_cons), fontSize = 12.sp, color = LocalNgExtra.current.subtle)
            vm.offlineDbInfo?.let { info ->
                Text(stringResource(R.string.offline_size, info.downloadBytes / (1024 * 1024)), fontSize = 12.sp, color = LocalNgExtra.current.subtle)
            }
            val dbLabels = mapOf(
                nl.spoonpaco.ninegrid.data.OfflineDb.OFF to stringResource(R.string.offline_off),
                nl.spoonpaco.ninegrid.data.OfflineDb.NEW_SET to stringResource(R.string.offline_newset),
                nl.spoonpaco.ninegrid.data.OfflineDb.MANUAL to stringResource(R.string.offline_manual),
            )
            ChoiceRow(stringResource(R.string.offline_update), dbLabels.keys.toList(), settings.offlineDb, { dbLabels[it] ?: it }) { vm.setOfflineDb(it) }
            if (settings.offlineDb != nl.spoonpaco.ninegrid.data.OfflineDb.OFF) {
                when {
                    vm.offlineDbRunning -> {
                        if (vm.offlineDbPct >= 0) androidx.compose.material3.LinearProgressIndicator(progress = { vm.offlineDbPct / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        else androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        Text(
                            if (vm.offlineDbPct >= 0) stringResource(R.string.offline_running_pct, vm.offlineDbPct, vm.offlineDbProgress)
                            else stringResource(R.string.offline_running, vm.offlineDbProgress), fontSize = 12.sp,
                        )
                    }
                    vm.offlineDbWaiting -> Text(stringResource(if (settings.offlineDbMobile) R.string.offline_waiting_net else R.string.offline_waiting), fontSize = 12.sp, color = nl.spoonpaco.ninegrid.ui.theme.NgColors.Amber)
                    vm.offlineDbFailed -> Text(stringResource(R.string.offline_failed), fontSize = 12.sp, color = NgColorsError)
                    settings.offlineDbUpdated > 0 -> Text(
                        stringResource(
                            R.string.offline_status, settings.offlineDbCount,
                            nl.spoonpaco.ninegrid.domain.Dates.date(settings.offlineDbUpdated, settings.dateStyle),
                            vm.offlineDbBytes / (1024 * 1024),
                        ), fontSize = 12.sp,
                    )
                    else -> Text(stringResource(R.string.offline_waiting), fontSize = 12.sp)
                }
                val wifiL = stringResource(R.string.offline_via_wifi); val mobL = stringResource(R.string.offline_via_mobile)
                ChoiceRow(stringResource(R.string.offline_via), listOf("wifi", "mobile"), if (settings.offlineDbMobile) "mobile" else "wifi",
                    { if (it == "mobile") mobL else wifiL }) { vm.setOfflineDbMobile(it == "mobile") }
                if (settings.offlineDbMobile) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.offline_mobile_auto), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = settings.offlineDbMobileAuto, onCheckedChange = { vm.setOfflineDbMobileAuto(it) })
                }
                OutlinedButton(onClick = { vm.updateOfflineDbNow() }, enabled = !vm.offlineDbRunning) { Text(stringResource(R.string.offline_now)) }
            }
            if (vm.confirmMobileDownload) AlertDialog(
                onDismissRequest = { vm.confirmMobileDownload = false },
                title = { Text(stringResource(R.string.offline_db)) },
                text = { Text(stringResource(R.string.offline_mobile_confirm, (vm.offlineDbInfo?.downloadBytes ?: 0L) / (1024 * 1024))) },
                confirmButton = { TextButton(onClick = { vm.updateOfflineDbNow(confirmed = true) }) { Text(stringResource(R.string.offline_mobile_yes)) } },
                dismissButton = { TextButton(onClick = { vm.confirmMobileDownload = false }) { Text(stringResource(R.string.cancel)) } },
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.backup_export), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.backup_explain), fontSize = 12.sp, color = LocalNgExtra.current.subtle)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { backup.launch("ninegrid-backup-$today.json") }) { Icon(Icons.Filled.Backup, null); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.backup_now)) }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "*/*")) }) { Text(stringResource(R.string.restore)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_backup))
                    Text(
                        if (settings.backupFolder != null) stringResource(R.string.auto_backup_on, if (settings.lastBackup > 0) nl.spoonpaco.ninegrid.domain.Dates.date(settings.lastBackup, settings.dateStyle) else "–")
                        else stringResource(R.string.auto_backup_off),
                        fontSize = 12.sp, color = LocalNgExtra.current.subtle,
                    )
                }
                Switch(checked = settings.backupFolder != null, onCheckedChange = { on -> if (on) folder.launch(null) else vm.setBackupFolder(null) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { csv.launch("ninegrid-collectie-$today.csv") }) { Text(stringResource(R.string.export_csv)) }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.scanning), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setting_timing), modifier = Modifier.weight(1f))
                Switch(checked = settings.showTiming, onCheckedChange = { vm.setShowTiming(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setting_auto), modifier = Modifier.weight(1f))
                Switch(checked = settings.autoCapture, onCheckedChange = { vm.setAutoCapture(it) })
            }
            val speedLabels = mapOf("fast" to stringResource(R.string.auto_fast), "normal" to stringResource(R.string.auto_normal), "sure" to stringResource(R.string.auto_sure))
            ChoiceRow(stringResource(R.string.setting_auto_speed), listOf("fast", "normal", "sure"), settings.autoSpeed, { speedLabels[it] ?: it }) { vm.setAutoSpeed(it) }
            Text(stringResource(R.string.setting_volume), fontSize = 12.sp, color = LocalNgExtra.current.subtle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setting_haptics), modifier = Modifier.weight(1f))
                Switch(checked = settings.haptics, onCheckedChange = { vm.setHaptics(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setting_shutter), modifier = Modifier.weight(1f))
                Switch(checked = settings.shutterSound, onCheckedChange = { vm.setShutterSound(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.wipe_title), fontWeight = FontWeight.Bold, color = NgColorsError)
            Text(stringResource(R.string.wipe_explain), fontSize = 12.sp, color = LocalNgExtra.current.subtle)
            OutlinedButton(onClick = { wiping = true }) { Text(stringResource(R.string.wipe_button), color = NgColorsError) }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.updates), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.version_x, BuildConfig.VERSION_NAME + " – " + BuildConfig.CODENAME), fontSize = 13.sp)
            OutlinedButton(onClick = { vm.checkUpdate(manual = true) }) { Text(stringResource(R.string.check_updates)) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (wiping) WipeDialog(vm, hasFolder = settings.backupFolder != null, onDismiss = { wiping = false })

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.restore)) },
            text = { Text(stringResource(R.string.restore_warning)) },
            confirmButton = { TextButton(onClick = { vm.restoreFrom(uri); confirmRestore = null }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun themeLabelPlain(v: String) = when (v) {
    "day" -> stringResource(R.string.theme_day); "night" -> stringResource(R.string.theme_night); else -> stringResource(R.string.follow_phone)
}

@Composable
fun langLabelPlain(v: String) = when (v) { "nl" -> "Nederlands"; "en" -> "English"; else -> stringResource(R.string.follow_phone) }

@Composable
fun imageModeLabelPlain(v: String) = when (v) {
    ImageMode.OFF -> stringResource(R.string.images_off); ImageMode.FULL -> stringResource(R.string.images_full); else -> stringResource(R.string.images_small)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onTour: () -> Unit) {
    val uri = LocalUriHandler.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.about)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_ninegrid), null, Modifier.size(160.dp))
            Spacer(Modifier.height(12.dp))
            Text("NineGrid ${BuildConfig.VERSION_NAME} – ${BuildConfig.CODENAME}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_text), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.privacy_text), fontSize = 13.sp, textAlign = TextAlign.Center, color = LocalNgExtra.current.subtle)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.scryfall_credit), fontSize = 12.sp, textAlign = TextAlign.Center, color = LocalNgExtra.current.subtle)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.fan_content), fontSize = 11.sp, textAlign = TextAlign.Center, color = LocalNgExtra.current.subtle)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { uri.openUri("https://github.com/${UpdateChecker.REPO}") }) { Text("GitHub") }
            TextButton(onClick = onTour) { Text(stringResource(R.string.show_tour)) }
        }
    }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit, onGuide: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        R.string.tour1_title to R.string.tour1_body,
        R.string.tour2_title to R.string.tour2_body,
        R.string.tour3_title to R.string.tour3_body,
        R.string.guide_ask_title to R.string.guide_ask_body,
    )
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.logo_ninegrid), null, Modifier.size(180.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(pages[page].first), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(pages[page].second), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page < pages.lastIndex) {
                TextButton(onClick = { page = pages.lastIndex }) { Text(stringResource(R.string.skip)) }
                Button(onClick = { page++ }) { Text(stringResource(R.string.next)) }
            } else {
                OutlinedButton(onClick = onDone) { Text(stringResource(R.string.not_now)) }
                Button(onClick = onGuide) { Text(stringResource(R.string.read_guide)) }
            }
        }
        if (page == pages.lastIndex) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.guide_where), fontSize = 13.sp, textAlign = TextAlign.Center, color = LocalNgExtra.current.subtle)
        }
        Spacer(Modifier.height(12.dp))
        Text("${page + 1} / ${pages.size}", color = LocalNgExtra.current.subtle)
    }
}

private val NgColorsError = nl.spoonpaco.ninegrid.ui.theme.NgColors.Error

/**
 * Alles wissen: kiezen wat, eerst een back-up, en bevestigen door WISSEN te typen.
 */
@Composable
private fun WipeDialog(vm: AppViewModel, hasFolder: Boolean, onDismiss: () -> Unit) {
    var collection by remember { mutableStateOf(true) }
    var decks by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(false) }
    var changeLog by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.wipe_word)
    val options = nl.spoonpaco.ninegrid.data.Repository.WipeOptions(collection, decks, history, changeLog, images)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    // Geen back-upmap? Dan eerst zelf een plek kiezen voor de back-up.
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { vm.wipe(options, uri); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wipe_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CheckRow(R.string.wipe_collection, collection) { collection = it }
                CheckRow(R.string.wipe_decks, decks) { decks = it }
                CheckRow(R.string.wipe_history, history) { history = it }
                CheckRow(R.string.wipe_changelog, changeLog) { changeLog = it }
                CheckRow(R.string.wipe_images, images) { images = it }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(if (hasFolder) R.string.wipe_backup_folder else R.string.wipe_backup_choose), fontSize = 12.sp)
                if (changeLog && (collection || decks)) Text(stringResource(R.string.wipe_no_undo), fontSize = 12.sp, color = NgColorsError)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(stringResource(R.string.wipe_type, word)) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = typed.trim().equals(word, ignoreCase = true) && (collection || decks || history || changeLog || images),
                onClick = { if (hasFolder) { vm.wipe(options, null); onDismiss() } else saveBackup.launch("ninegrid-backup-voor-wissen-$today.json") },
            ) { Text(stringResource(R.string.wipe_button), color = NgColorsError) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CheckRow(label: Int, v: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = v, onCheckedChange = set); Text(stringResource(label))
    }
}
