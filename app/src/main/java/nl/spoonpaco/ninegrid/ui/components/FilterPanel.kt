package nl.spoonpaco.ninegrid.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.db.EntryWithPrint
import nl.spoonpaco.ninegrid.data.db.Finish

/** Welke filters er aan staan. Binnen een groep is het "of" (Rare of Mythic), tussen groepen "en". */
@Serializable
data class CardFilter(
    val rarities: Set<String> = emptySet(),
    val colors: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val foil: Boolean = false,
    val decks: Boolean = false,
    val inText: Boolean = false,
) {
    val count get() = rarities.size + colors.size + types.size + (if (foil) 1 else 0) + (if (decks) 1 else 0) + (if (inText) 1 else 0)

    fun matches(e: EntryWithPrint, query: String): Boolean {
        val p = e.print
        val q = query.trim()
        if (!decks && e.entry.deckId != null) return false
        if (q.isNotEmpty()) {
            val inName = (p?.name ?: "").contains(q, true) || (p?.setCode ?: "").equals(q, true)
            val inRules = inText && ((p?.oracleText ?: "").contains(q, true) || (p?.typeLine ?: "").contains(q, true))
            if (!inName && !inRules) return false
        }
        if (rarities.isNotEmpty() && (p?.rarity ?: "") !in rarities) return false
        if (foil && e.entry.finish == Finish.NONFOIL) return false
        if (colors.isNotEmpty()) {
            val c = p?.colors ?: ""
            if (colors.none { k -> if (k == "C") c.isEmpty() else c.contains(k) }) return false
        }
        if (types.isNotEmpty()) {
            val front = (p?.typeLine ?: "").substringBefore('—')
            if (types.none { front.contains(it) }) return false
        }
        return true
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(s: String?): CardFilter = s?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: CardFilter()
        val RARITIES = listOf("mythic", "rare", "uncommon", "common")
        val TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")
    }
}

enum class FilterGroup { RARITY, COLOR, TYPE, FOIL, DECKS, TEXT }

/**
 * Zoekbalk met één knop Filter (met het aantal actieve filters) en eventueel een knop Sorteren.
 * Onder de balk alleen de filters die aan staan, met een kruisje. Het paneel komt van onderaf.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    filter: CardFilter,
    onFilter: (CardFilter) -> Unit,
    groups: Set<FilterGroup>,
    sortOptions: List<Pair<String, String>> = emptyList(),
    sort: String = "",
    onSort: (String) -> Unit = {},
) {
    var panel by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, null) } },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { panel = true }) {
                BadgedBox(badge = { if (filter.count > 0) Badge { Text("${filter.count}") } }) {
                    Icon(Icons.Filled.FilterList, stringResource(R.string.filter))
                }
            }
            if (sortOptions.isNotEmpty()) Column {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort)) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    sortOptions.forEach { (k, l) ->
                        DropdownMenuItem(text = { Text(l, fontWeight = if (k == sort) FontWeight.Bold else FontWeight.Normal) }, onClick = { sortMenu = false; onSort(k) })
                    }
                }
            }
        }
        // Wat er aan staat, met een kruisje om het uit te zetten.
        if (filter.count > 0) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            filter.rarities.forEach { r -> ActiveChip(r.replaceFirstChar { it.uppercase() }) { onFilter(filter.copy(rarities = filter.rarities - r)) } }
            filter.colors.forEach { c -> ActiveChip(c) { onFilter(filter.copy(colors = filter.colors - c)) } }
            filter.types.forEach { t -> ActiveChip(t) { onFilter(filter.copy(types = filter.types - t)) } }
            if (filter.foil) ActiveChip(stringResource(R.string.foil)) { onFilter(filter.copy(foil = false)) }
            if (filter.decks) ActiveChip(stringResource(R.string.include_decks)) { onFilter(filter.copy(decks = false)) }
            if (filter.inText) ActiveChip(stringResource(R.string.search_in_text)) { onFilter(filter.copy(inText = false)) }
            TextButton(onClick = { onFilter(CardFilter()) }) { Text(stringResource(R.string.clear_filters)) }
        }
    }

    if (panel) ModalBottomSheet(onDismissRequest = { panel = false }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (filter.count > 0) TextButton(onClick = { onFilter(CardFilter()) }) { Text(stringResource(R.string.clear_filters)) }
            }
            if (FilterGroup.RARITY in groups) Group(stringResource(R.string.rarity)) {
                CardFilter.RARITIES.forEach { r -> FilterChip(selected = r in filter.rarities, onClick = { onFilter(filter.copy(rarities = filter.rarities.toggle(r))) }, label = { Text(r.replaceFirstChar { it.uppercase() }) }) }
            }
            if (FilterGroup.COLOR in groups) Group(stringResource(R.string.color)) {
                listOf("W", "U", "B", "R", "G", "C").forEach { c ->
                    FilterChip(selected = c in filter.colors, onClick = { onFilter(filter.copy(colors = filter.colors.toggle(c))) }, label = { ManaPips(c, size = 20) })
                }
            }
            if (FilterGroup.TYPE in groups) Group(stringResource(R.string.card_type)) {
                CardFilter.TYPES.forEach { t -> FilterChip(selected = t in filter.types, onClick = { onFilter(filter.copy(types = filter.types.toggle(t))) }, label = { Text(t) }) }
            }
            val more = listOfNotNull(
                (FilterGroup.FOIL in groups).takeIf { it }?.let { Triple(stringResource(R.string.foil), filter.foil) { onFilter(filter.copy(foil = !filter.foil)) } },
                (FilterGroup.DECKS in groups).takeIf { it }?.let { Triple(stringResource(R.string.include_decks), filter.decks) { onFilter(filter.copy(decks = !filter.decks)) } },
                (FilterGroup.TEXT in groups).takeIf { it }?.let { Triple(stringResource(R.string.search_in_text), filter.inText) { onFilter(filter.copy(inText = !filter.inText)) } },
            )
            if (more.isNotEmpty()) Group(stringResource(R.string.filter_more)) {
                more.forEach { (l, on, click) -> FilterChip(selected = on, onClick = click, label = { Text(l) }) }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { panel = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
        }
    }
}

private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true, onClick = onRemove, label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
    )
}
