package nl.spoonpaco.ninegrid.ui.screens

import nl.spoonpaco.ninegrid.ui.theme.NgText
import nl.spoonpaco.ninegrid.ui.theme.NgShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.ImageMode
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.AppViewModel
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/** Eén set in je collectie: wat je hebt, wat het waard is, en hoe compleet je bent. */
data class SetRow(
    val code: String,
    val name: String,
    val released: String,
    val icon: String?,
    val mythic: Int, val rare: Int, val uncommon: Int, val common: Int, val other: Int,
    val value: Double,
    /** Verschillende gewone kaartnummers die je hebt (ook in decks), tegenover de grootte van de set. */
    val owned: Int,
    val size: Int,
) {
    val cards get() = mythic + rare + uncommon + common + other
    val pct get() = if (size > 0) owned * 100 / size else 0
}

/** Alle sets onder elkaar, met zoeken en sorteren. Tik op een set voor de setlijst. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetsScreen(vm: AppViewModel, onBack: () -> Unit, onSet: (String) -> Unit) {
    val all by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val info = vm.setInfo
    val cur = settings.currency
    val extra = LocalNgExtra.current
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("value") }

    val rows = remember(all, info, cur, settings.completionist) {
        val loose = all.filter { it.entry.deckId == null && !it.entry.proxy }.groupBy { it.print?.setCode ?: "?" }
        // Compleetheid telt alles wat je hebt, ook in decks; alleen de gewone nummers van de set.
        // Completionist: alleen wat los in je binders ligt.
        val ownedNumbers = all.filter { !it.entry.proxy && (!settings.completionist || it.entry.deckId == null) }.groupBy { it.print?.setCode ?: "?" }
        loose.map { (code, list) ->
            val i = info[code]
            val size = i?.printedSize ?: i?.cardCount ?: 0
            fun n(r: String) = list.filter { it.print?.rarity == r }.sumOf { it.entry.quantity }
            val owned = ownedNumbers[code].orEmpty().mapNotNull { it.print?.collectorNumber?.toIntOrNull() }.filter { size == 0 || it <= size }.distinct().size
            SetRow(
                code, i?.name ?: list.firstOrNull()?.print?.setName ?: code, i?.releasedAt ?: "", i?.iconSvgUri,
                n("mythic"), n("rare"), n("uncommon"), n("common"),
                list.filter { it.print?.rarity !in listOf("mythic", "rare", "uncommon", "common") }.sumOf { it.entry.quantity },
                list.sumOf { Money.total(it.entry, it.print, cur) }, owned, size,
            )
        }
    }
    val shown = remember(rows, query, sort) {
        val q = query.trim()
        val f = if (q.isEmpty()) rows else rows.filter { it.name.contains(q, true) || it.code.equals(q, true) }
        when (sort) {
            "name" -> f.sortedBy { it.name.lowercase() }
            "date" -> f.sortedByDescending { it.released }
            "count" -> f.sortedByDescending { it.cards }
            else -> f.sortedByDescending { it.value }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            actions = { nl.spoonpaco.ninegrid.ui.guide.HelpButton("overview") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            title = {
                Column {
                    Text(stringResource(R.string.sets_title), fontWeight = FontWeight.Bold)
                    Text(
                        pluralStringResource(R.plurals.n_sets, rows.size, rows.size) + " · " + nl.spoonpaco.ninegrid.ui.components.money(rows.sumOf { it.value }, cur),
                        style = MaterialTheme.typography.bodySmall, color = extra.subtle,
                    )
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Zelfde zoekbalk als elders, alleen met sorteren (pijltjes); geen filter.
            nl.spoonpaco.ninegrid.ui.components.FilterSearchBar(
                query = query, onQuery = { query = it }, placeholder = stringResource(R.string.sets_search),
                filter = nl.spoonpaco.ninegrid.ui.components.CardFilter(), onFilter = {}, groups = emptySet(),
                sortOptions = listOf("value" to stringResource(R.string.sort_value), "name" to stringResource(R.string.sort_name),
                    "date" to stringResource(R.string.sort_date), "count" to stringResource(R.string.sort_count)),
                sort = sort, onSort = { sort = it },
            )
            CompletionistToggle(settings.completionist, onChange = { vm.setCompletionist(it) }, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
            LazyColumn {
                items(shown, key = { it.code }) { r ->
                    SetRowView(r, cur, settings.imageMode != ImageMode.OFF, vm) { onSet(r.code) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SetRowView(r: SetRow, cur: String, icons: Boolean, vm: AppViewModel, onClick: () -> Unit) {
    val extra = LocalNgExtra.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icons && r.icon != null) {
            coil.compose.AsyncImage(
                model = r.icon, imageLoader = vm.images.loader, contentDescription = r.code,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.name} (${r.code})", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(nl.spoonpaco.ninegrid.ui.components.money(r.value, cur), fontWeight = FontWeight.Bold, color = extra.price, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
            }
            // Rarity als één balkje; tik erop voor de getallen.
            val parts = listOf(r.mythic to NgColors.Mythic, r.rare to NgColors.Rare, r.uncommon to NgColors.Uncommon, r.common to NgColors.Common, r.other to NgColors.Special)
            Row(
                Modifier.padding(vertical = 4.dp).fillMaxWidth().height(8.dp).clip(NgShape.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { open = !open },
            ) {
                if (r.cards > 0) parts.filter { it.first > 0 }.forEach { (n, c) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) }
            }
            if (open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M" to parts[0], "R" to parts[1], "U" to parts[2], "C" to parts[3], "O" to parts[4]).forEach { (l, p) -> Text("$l ${p.first}", color = p.second, fontSize = NgText.body) }
            }
            Text(
                pluralStringResource(R.plurals.n_cards, r.cards, r.cards) +
                    if (r.size > 0) "  ·  " + stringResource(R.string.sets_complete, r.owned, r.size, r.pct) else "",
                fontSize = NgText.body, color = extra.subtle,
            )
            if (r.size > 0 && r.owned >= r.size) Row(Modifier.padding(top = 4.dp)) {
                nl.spoonpaco.ninegrid.ui.components.Stamp(stringResource(R.string.stamp_complete), NgColors.Rare, "✓", angle = -4f)
            }
        }
    }
}

/** "☐ Completionist ⓘ": vinkje, met de uitleg achter het ⓘ. Overal dezelfde instelling als in Instellingen. */
@Composable
fun CompletionistToggle(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var info by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val help = nl.spoonpaco.ninegrid.ui.guide.LocalHelp.current
    Row(modifier.clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = on, onCheckedChange = onChange)
        Text(stringResource(R.string.completionist))
        androidx.compose.material3.IconButton(onClick = { info = true }, modifier = Modifier.size(32.dp)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Info, stringResource(R.string.completionist_info_title), modifier = Modifier.size(18.dp))
        }
    }
    if (info) androidx.compose.material3.AlertDialog(
        onDismissRequest = { info = false },
        title = { Text(stringResource(R.string.completionist_info_title)) },
        text = { Text(stringResource(R.string.completionist_info_body)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { info = false }) { Text(stringResource(R.string.done)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { info = false; help("collection") }) { Text(stringResource(R.string.more_in_guide)) } },
    )
}
