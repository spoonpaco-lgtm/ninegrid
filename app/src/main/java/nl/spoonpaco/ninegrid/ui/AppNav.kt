package nl.spoonpaco.ninegrid.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.ui.guide.GuideChapterScreen
import nl.spoonpaco.ninegrid.ui.guide.GuideScreen
import nl.spoonpaco.ninegrid.ui.guide.LocalHelp
import nl.spoonpaco.ninegrid.ui.screens.AboutScreen
import nl.spoonpaco.ninegrid.ui.screens.CardDetailScreen
import nl.spoonpaco.ninegrid.ui.screens.ChangeLogScreen
import nl.spoonpaco.ninegrid.ui.screens.CollectionScreen
import nl.spoonpaco.ninegrid.ui.screens.DeckDetailScreen
import nl.spoonpaco.ninegrid.ui.screens.DecksScreen
import nl.spoonpaco.ninegrid.ui.screens.MoreScreen
import nl.spoonpaco.ninegrid.ui.screens.OnboardingScreen
import nl.spoonpaco.ninegrid.ui.screens.OverviewScreen
import nl.spoonpaco.ninegrid.ui.screens.PasteScreen
import nl.spoonpaco.ninegrid.ui.screens.ReviewScreen
import nl.spoonpaco.ninegrid.ui.screens.ScanScreen
import nl.spoonpaco.ninegrid.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("collection", R.string.collection, Icons.Filled.Collections),
    Tab("decks", R.string.decks, Icons.Filled.Style),
    Tab("scan", R.string.scan_page, Icons.Filled.CameraAlt),
    Tab("overview", R.string.overview, Icons.Filled.Insights),
    Tab("more", R.string.more, Icons.Filled.MoreHoriz),
)

@Composable
fun AppNav(vm: AppViewModel, showOnboarding: Boolean) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val uri = LocalUriHandler.current

    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        val undo = vm.messageUndo
        vm.messageUndo = false
        // Na opslaan: meteen ongedaan te maken vanuit de melding.
        val r = snackbar.showSnackbar(
            m, actionLabel = if (undo) undoLabel else null,
            duration = if (undo) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
        )
        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undo()
        vm.message = null
    }
    LaunchedEffect(Unit) { vm.checkUpdate(manual = false) }
    LaunchedEffect(vm.openImport) { if (vm.openImport) { vm.openImport = false; nav.navigate("import") } }

    // Na de eerste 20 kaarten één keer vragen om een back-upmap, zodat je collectie niet zomaar weg kan zijn.
    val entries by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val cardCount = entries.sumOf { it.entry.quantity }
    var askBackup by remember { mutableStateOf(false) }
    LaunchedEffect(cardCount, settings.backupFolder, settings.backupAsked) {
        if (cardCount >= 20 && settings.backupFolder == null && !settings.backupAsked) askBackup = true
    }
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }

    fun tab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(LocalHelp provides { id: String -> nav.navigate("guide/$id") }) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route } && route != "scan") {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { tab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = if (showOnboarding) "onboarding" else "collection", modifier = Modifier.padding(pad)) {
            composable("onboarding") {
                OnboardingScreen(
                    onDone = {
                        vm.setOnboardingDone()
                        nav.navigate("collection") { popUpTo("onboarding") { inclusive = true } }
                    },
                    onGuide = {
                        vm.setOnboardingDone()
                        nav.navigate("collection") { popUpTo("onboarding") { inclusive = true } }
                        nav.navigate("guide")
                    },
                )
            }
            composable("collection") {
                CollectionScreen(
                    vm,
                    onCard = { nav.navigate("card/$it") },
                    onScan = { tab("scan") },
                    onPaste = { nav.navigate("paste") },
                    onReview = { nav.navigate("review") },
                    onImport = { nav.navigate("import") },
                )
            }
            composable("decks") {
                DecksScreen(vm, onDeck = { nav.navigate("deck/$it") }, onNewDeck = { nav.navigate("paste?asNewDeck=true") })
            }
            composable("scan") { ScanScreen(vm, onBack = { if (!nav.popBackStack()) tab("collection") }, onResult = { nav.navigate("review") }) }
            composable("overview") { OverviewScreen(vm, onSet = { nav.navigate("set/$it") }) }
            composable("set/{code}", arguments = listOf(navArgument("code") { type = NavType.StringType })) {
                nl.spoonpaco.ninegrid.ui.screens.SetScreen(vm, it.arguments?.getString("code") ?: "", onBack = { nav.popBackStack() }, onReview = { nav.navigate("review") })
            }
            composable("more") {
                MoreScreen(
                    onPaste = { nav.navigate("paste") },
                    onChanges = { nav.navigate("changes") },
                    onSettings = { nav.navigate("settings") },
                    onAbout = { nav.navigate("about") },
                    onGuide = { nav.navigate("guide") },
                    onImport = { nav.navigate("import") },
                )
            }
            composable("card/{printId}", arguments = listOf(navArgument("printId") { type = NavType.StringType })) {
                CardDetailScreen(vm, it.arguments?.getString("printId") ?: "", onBack = { nav.popBackStack() })
            }
            composable("deck/{deckId}", arguments = listOf(navArgument("deckId") { type = NavType.LongType })) {
                DeckDetailScreen(
                    vm, it.arguments?.getLong("deckId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onCard = { id -> nav.navigate("card/$id") },
                    onAddCards = { id -> nav.navigate("paste?intoDeck=$id") },
                )
            }
            composable(
                "paste?intoDeck={intoDeck}&asNewDeck={asNewDeck}",
                arguments = listOf(
                    navArgument("intoDeck") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("asNewDeck") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                val into = it.arguments?.getLong("intoDeck")?.takeIf { v -> v >= 0 }
                PasteScreen(vm, into, it.arguments?.getBoolean("asNewDeck") ?: false, onBack = { nav.popBackStack() }, onNext = { nav.navigate("review") })
            }
            composable("review") {
                ReviewScreen(vm, onBack = { nav.popBackStack() }, onSaved = { deckId ->
                    if (deckId != null) {
                        nav.navigate("deck/$deckId") { popUpTo(nav.graph.findStartDestination().id) }
                    } else if (vm.importSource == ImportSource.SCAN) {
                        nav.popBackStack("scan", inclusive = false)
                    } else {
                        nav.navigate("collection") { popUpTo(nav.graph.findStartDestination().id) { inclusive = false }; launchSingleTop = true }
                    }
                })
            }
            composable("guide") { GuideScreen(onBack = { nav.popBackStack() }, onChapter = { nav.navigate("guide/$it") }) }
            composable("guide/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                GuideChapterScreen(it.arguments?.getString("id") ?: "start", onBack = { nav.popBackStack() }, onChapter = { id ->
                    nav.navigate("guide/$id") { popUpTo("guide") }
                })
            }
            composable("import") {
                nl.spoonpaco.ninegrid.ui.screens.ImportScreen(vm, onBack = { nav.popBackStack() }, onDone = { tab("collection") })
            }
            composable("changes") { ChangeLogScreen(vm, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("about") {
                AboutScreen(onBack = { nav.popBackStack() }, onTour = { nav.navigate("onboarding") })
            }
        }
    }

    }

    if (askBackup) {
        AlertDialog(
            onDismissRequest = { askBackup = false; vm.setBackupAsked() },
            title = { Text(stringResource(R.string.backup_ask_title)) },
            text = { Text(stringResource(R.string.backup_ask_body)) },
            confirmButton = { TextButton(onClick = { askBackup = false; vm.setBackupAsked(); folderPicker.launch(null) }) { Text(stringResource(R.string.backup_ask_choose)) } },
            dismissButton = { TextButton(onClick = { askBackup = false; vm.setBackupAsked() }) { Text(stringResource(R.string.later)) } },
        )
    }

    vm.update?.let { u ->
        AlertDialog(
            onDismissRequest = { vm.update = null },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_body, u.version)) },
            confirmButton = { TextButton(onClick = { uri.openUri(u.url); vm.update = null }) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton(onClick = { vm.update = null }) { Text(stringResource(R.string.later)) } },
        )
    }
}
