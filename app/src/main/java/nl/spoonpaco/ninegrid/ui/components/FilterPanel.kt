package nl.spoonpaco.ninegrid.ui.components

import nl.spoonpaco.ninegrid.ui.theme.NgText
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
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
    /** Binders (leeg = "Geen binder"); meer tegelijk mag. */
    val binders: Set<String> = emptySet(),
    /** Alleen bij Alle kaarten: alleen wat ik niet heb. */
    val notOwned: Boolean = false,
    /** Alleen Game Changers (de lijst van Wizards voor Commander-brackets). */
    val gameChangers: Boolean = false,
    /** Alleen kaarten met het label NIEUW. */
    val onlyNew: Boolean = false,
    /** Prijs per stuk van–tot (foil: de foilprijs), in de gekozen valuta; null = geen grens. */
    val priceMin: Double? = null,
    val priceMax: Double? = null,
) {
    val count get() = rarities.size + colors.size + types.size + binders.size + (if (foil) 1 else 0) + (if (decks) 1 else 0) + (if (inText) 1 else 0) + (if (notOwned) 1 else 0) + (if (gameChangers) 1 else 0) + (if (onlyNew) 1 else 0) +
        (if (hasPrice) 1 else 0)

    val hasPrice get() = priceMin != null || priceMax != null

    /** Valt deze prijs binnen het prijsfilter? Zonder prijs telt een kaart niet mee zodra er een grens is. */
    fun priceOk(v: Double?): Boolean {
        if (!hasPrice) return true
        val x = v ?: return false
        if (priceMin != null && x < priceMin) return false
        if (priceMax != null && x > priceMax) return false
        return true
    }

    fun matches(e: EntryWithPrint, query: String, currency: String = "EUR", isNew: (nl.spoonpaco.ninegrid.data.db.Entry) -> Boolean = { false }): Boolean {
        val p = e.print
        val q = query.trim()
        if (!decks && e.entry.deckId != null) return false
        if (q.isNotEmpty()) {
            val inName = (p?.name ?: "").contains(q, true) || (p?.setCode ?: "").equals(q, true)
            val inRules = inText && ((p?.oracleText ?: "").contains(q, true) || (p?.typeLine ?: "").contains(q, true))
            if (!inName && !inRules) return false
        }
        if (rarities.isNotEmpty() && (p?.rarity ?: "") !in rarities) return false
        if (binders.isNotEmpty() && (e.entry.binder?.takeIf { it.isNotBlank() } ?: "") !in binders) return false
        if (foil && e.entry.finish == Finish.NONFOIL) return false
        if (gameChangers && p?.gameChanger != true) return false
        if (onlyNew && !isNew(e.entry)) return false
        if (hasPrice && !priceOk(nl.spoonpaco.ninegrid.domain.Money.unitPrice(e.entry, p, currency).takeIf { p != null && it > 0.0 })) return false
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
        /** Snelle keuzes voor het prijsfilter. */
        val PRICE_RANGES: List<Pair<Double?, Double?>> = listOf(null to 1.0, 1.0 to 5.0, 5.0 to 20.0, 20.0 to null)

        /** Zoekvraag voor Scryfall uit tekst en filters (alleen de filters die daar bestaan). */
        fun scryfallQuery(text: String, f: CardFilter): String {
            val parts = mutableListOf<String>()
            val t = text.trim().replace("\"", "")
            if (t.isNotEmpty()) parts += if (f.inText) "(\"$t\" or o:\"$t\")" else "\"$t\""
            fun or(items: List<String>) = if (items.size == 1) items[0] else items.joinToString(" or ", "(", ")")
            if (f.rarities.isNotEmpty()) parts += or(f.rarities.map { "r:$it" })
            if (f.colors.isNotEmpty()) parts += or(f.colors.map { if (it == "C") "c:c" else "c:${it.lowercase()}" })
            if (f.types.isNotEmpty()) parts += or(f.types.map { "t:${it.lowercase()}" })
            if (f.gameChangers) parts += "is:gamechanger"
            // Prijs: Scryfall kent eur en usd; de app filtert daarna nog op de eigen valuta.
            f.priceMin?.let { parts += "eur>=" + String.format(java.util.Locale.ROOT, "%.2f", it) }
            f.priceMax?.let { parts += "eur<=" + String.format(java.util.Locale.ROOT, "%.2f", it) }
            // Alleen "alleen wat ik niet heb": Scryfall wil een zoekopdracht, dus alle papieren kaarten.
            return parts.joinToString(" ").ifBlank { "game:paper" }
        }
    }
}

enum class FilterGroup { RARITY, COLOR, TYPE, FOIL, DECKS, TEXT, BINDER, NOT_OWNED, GAME_CHANGER, NEW, PRICE }

/**
 * Sorteren met een richting: de sleutel ("price") en, omgekeerd, met "~r" erachter ("price~r"). Elk scherm sorteert
 * op de sleutel zoals altijd en draait de lijst om als [rev] waar is.
 */
object SortKey {
    private const val REV = "~r"
    fun key(s: String) = s.removeSuffix(REV)
    fun rev(s: String) = s.endsWith(REV)
    fun of(key: String, rev: Boolean) = if (rev) key + REV else key
}

fun <T> List<T>.reversedIf(rev: Boolean): List<T> = if (rev) reversed() else this

/** Een bewaarde combinatie van filters en sortering, onder een eigen naam. */
@Serializable
data class SavedView(val name: String, val filter: CardFilter = CardFilter(), val sort: String = "")

/** De bewaarde combinaties van één scherm, met bewaren (huidige filters en sortering) en weghalen. */
class SavedViews(val list: List<SavedView>, val save: (String) -> Unit, val delete: (String) -> Unit)

/** "€ 5–20", "< € 1", "> € 20". */
fun priceLabel(min: Double?, max: Double?, currency: String): String {
    val sym = if (currency == "USD") "$" else "€"
    fun n(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.2f", v)
    return when {
        min != null && max != null -> "$sym ${n(min)}–${n(max)}"
        max != null -> "< $sym ${n(max)}"
        min != null -> "> $sym ${n(min)}"
        else -> ""
    }
}

/**
 * Zoekbalk met één knop Filteren en sorteren (met het aantal actieve filters). Onder de balk wat aan staat, met een
 * kruisje: filters en een sortering die niet de standaard is. Het paneel komt van onderaf: sorteren (tik nog eens =
 * andersom), bewaarde combinaties en de filters.
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
    /** Binders om op te filteren (alleen getoond als er minstens één is). */
    binderOptions: List<String> = emptyList(),
    /** Andere kleur zoekbalk (bij Alle kaarten). */
    accent: Boolean = false,
    /** Valuta voor het prijsfilter. */
    currency: String = "EUR",
    /** Bewaarde combinaties van dit scherm (null = niet bewaren). */
    views: SavedViews? = null,
) {
    var panel by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val sortKey = SortKey.key(sort)
    val sortRev = SortKey.rev(sort)
    val defaultSort = sortOptions.firstOrNull()?.first
    val sortChanged = sortOptions.isNotEmpty() && sort.isNotBlank() && (sortKey != defaultSort || sortRev)
    fun sortLabel() = (if (sortRev) "↑ " else "↓ ") + (sortOptions.firstOrNull { it.first == sortKey }?.second ?: sortKey)
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, null) } },
                modifier = Modifier.weight(1f),
                colors = if (accent) androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                ) else androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
            )
            // Eén knop voor filteren en sorteren; zonder filters alleen het sorteericoon.
            if (groups.isNotEmpty() || sortOptions.isNotEmpty()) IconButton(onClick = { panel = true }) {
                BadgedBox(badge = { if (filter.count > 0) Badge { Text("${filter.count}") } }) {
                    Icon(if (groups.isNotEmpty()) Icons.Filled.FilterAlt else Icons.Filled.SwapVert, stringResource(R.string.filter_and_sort))
                }
            }
        }
        // Wat er aan staat, met een kruisje om het uit te zetten.
        if (filter.count > 0 || sortChanged) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sortChanged) ActiveChip(sortLabel()) { onSort(defaultSort ?: "") }
            if (filter.hasPrice) ActiveChip(priceLabel(filter.priceMin, filter.priceMax, currency)) { onFilter(filter.copy(priceMin = null, priceMax = null)) }
            filter.rarities.forEach { r -> ActiveChip(r.replaceFirstChar { it.uppercase() }) { onFilter(filter.copy(rarities = filter.rarities - r)) } }
            filter.colors.forEach { c -> ActiveChip(c) { onFilter(filter.copy(colors = filter.colors - c)) } }
            filter.types.forEach { t -> ActiveChip(t) { onFilter(filter.copy(types = filter.types - t)) } }
            if (filter.foil) ActiveChip(stringResource(R.string.foil)) { onFilter(filter.copy(foil = false)) }
            if (filter.decks) ActiveChip(stringResource(R.string.include_decks)) { onFilter(filter.copy(decks = false)) }
            if (filter.inText) ActiveChip(stringResource(R.string.search_in_text)) { onFilter(filter.copy(inText = false)) }
            filter.binders.forEach { b -> ActiveChip(b.ifBlank { stringResource(R.string.no_binder) }) { onFilter(filter.copy(binders = filter.binders - b)) } }
            if (filter.notOwned) ActiveChip(stringResource(R.string.only_not_owned)) { onFilter(filter.copy(notOwned = false)) }
            if (filter.gameChangers) ActiveChip(stringResource(R.string.filter_game_changers)) { onFilter(filter.copy(gameChangers = false)) }
            if (filter.onlyNew) ActiveChip(stringResource(R.string.filter_new)) { onFilter(filter.copy(onlyNew = false)) }
            if (filter.count > 0) TextButton(onClick = { onFilter(CardFilter()) }) { Text(stringResource(R.string.clear_filters)) }
        }
    }

    if (panel) ModalBottomSheet(onDismissRequest = { panel = false }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_and_sort), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (filter.count > 0) TextButton(onClick = { onFilter(CardFilter()) }) { Text(stringResource(R.string.clear_filters)) }
            }
            if (sortOptions.isNotEmpty()) {
                Group(stringResource(R.string.sort)) {
                    sortOptions.forEach { (k, l) ->
                        val on = k == sortKey || (sort.isBlank() && k == defaultSort)
                        FilterChip(
                            selected = on,
                            onClick = { onSort(if (on) SortKey.of(k, !sortRev) else k) },
                            label = { Text((if (on) (if (sortRev) "↑ " else "↓ ") else "") + l) },
                        )
                    }
                }
                Text(stringResource(R.string.sort_tap_again), fontSize = NgText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (views != null) {
                Group(stringResource(R.string.views_saved)) {
                    views.list.forEach { v ->
                        InputChip(
                            selected = false,
                            onClick = { onFilter(v.filter); if (sortOptions.isNotEmpty() && v.sort.isNotBlank()) onSort(v.sort); panel = false },
                            label = { Text(v.name, maxLines = 1) },
                            trailingIcon = { IconButton(onClick = { views.delete(v.name) }, modifier = Modifier.size(20.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.delete), Modifier.size(14.dp)) } },
                        )
                    }
                    TextButton(onClick = { naming = true }) { Text("+ " + stringResource(R.string.views_save)) }
                }
            }
            if (FilterGroup.PRICE in groups) {
                Group(stringResource(R.string.filter_price)) {
                    CardFilter.PRICE_RANGES.forEach { (lo, hi) ->
                        val on = filter.priceMin == lo && filter.priceMax == hi
                        FilterChip(selected = on, onClick = { onFilter(if (on) filter.copy(priceMin = null, priceMax = null) else filter.copy(priceMin = lo, priceMax = hi)) },
                            label = { Text(priceLabel(lo, hi, currency)) })
                    }
                }
                // Zelf invullen: van en tot.
                fun parse(t: String) = t.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 }
                var lo by remember { mutableStateOf(filter.priceMin?.let { fmtPrice(it) } ?: "") }
                var hi by remember { mutableStateOf(filter.priceMax?.let { fmtPrice(it) } ?: "") }
                // Een snelle keuze of het kruisje zet de velden gelijk; tijdens het typen blijft staan wat je typt.
                androidx.compose.runtime.LaunchedEffect(filter.priceMin) { if (parse(lo) != filter.priceMin) lo = filter.priceMin?.let { fmtPrice(it) } ?: "" }
                androidx.compose.runtime.LaunchedEffect(filter.priceMax) { if (parse(hi) != filter.priceMax) hi = filter.priceMax?.let { fmtPrice(it) } ?: "" }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(lo, { t -> lo = t.filter { it.isDigit() || it == ',' || it == '.' }; onFilter(filter.copy(priceMin = parse(lo))) }, singleLine = true,
                        label = { Text(stringResource(R.string.price_from)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(hi, { t -> hi = t.filter { it.isDigit() || it == ',' || it == '.' }; onFilter(filter.copy(priceMax = parse(hi))) }, singleLine = true,
                        label = { Text(stringResource(R.string.price_to)) }, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                Text(stringResource(R.string.filter_price_note), fontSize = NgText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (FilterGroup.BINDER in groups && binderOptions.isNotEmpty()) Group(stringResource(R.string.binder_short)) {
                (binderOptions + "").forEach { b ->
                    FilterChip(selected = b in filter.binders, onClick = { onFilter(filter.copy(binders = filter.binders.toggle(b))) }, label = { Text(b.ifBlank { stringResource(R.string.no_binder) }) })
                }
            }
            val more = listOfNotNull(
                (FilterGroup.NOT_OWNED in groups).takeIf { it }?.let { Triple(stringResource(R.string.only_not_owned), filter.notOwned) { onFilter(filter.copy(notOwned = !filter.notOwned)) } },
                (FilterGroup.NEW in groups).takeIf { it }?.let { Triple(stringResource(R.string.filter_new), filter.onlyNew) { onFilter(filter.copy(onlyNew = !filter.onlyNew)) } },
                (FilterGroup.GAME_CHANGER in groups).takeIf { it }?.let { Triple(stringResource(R.string.filter_game_changers), filter.gameChangers) { onFilter(filter.copy(gameChangers = !filter.gameChangers)) } },
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

    if (naming && views != null) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.views_save)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.views_name)) })
                    Text(stringResource(R.string.views_save_note), fontSize = NgText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { views.save(name.trim()); naming = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun fmtPrice(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.2f", v)

private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = NgText.body, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
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
