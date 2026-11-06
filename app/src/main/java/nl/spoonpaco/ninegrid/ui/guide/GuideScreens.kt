package nl.spoonpaco.ninegrid.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

/** Opent een hoofdstuk van de handleiding; wordt door de navigatie gevuld. */
val LocalHelp = compositionLocalOf<(String) -> Unit> { {} }

/** Het ? rechtsboven op een scherm. */
@Composable
fun HelpButton(chapter: String) {
    val open = LocalHelp.current
    IconButton(onClick = { open(chapter) }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.guide)) }
}

@Composable
private fun isDutch(): Boolean = LocalConfiguration.current.locales[0].language == "nl"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit, onChapter: (String) -> Unit) {
    val chapters = Guide.chapters(isDutch())
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val hits = androidx.compose.runtime.remember(query, chapters) { Guide.search(chapters, query) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.guide), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                androidx.compose.material3.OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.guide_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (query.isBlank()) {
                itemsIndexed(chapters, key = { _, c -> c.id }) { i, c ->
                    ListItem(
                        leadingContent = { Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, modifier = Modifier.width(24.dp)) },
                        headlineContent = { Text(c.title, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(c.summary) },
                        modifier = Modifier.clickable { onChapter(c.id) },
                    )
                    HorizontalDivider()
                }
            } else {
                if (hits.isEmpty()) item { Text(stringResource(R.string.guide_search_none), modifier = Modifier.padding(16.dp), color = LocalNgExtra.current.subtle) }
                itemsIndexed(hits, key = { _, h -> h.chapter.id }) { _, h ->
                    ListItem(
                        headlineContent = { Text(h.chapter.title, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(h.snippet, maxLines = 3) },
                        modifier = Modifier.clickable { onChapter(h.chapter.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideChapterScreen(id: String, onBack: () -> Unit, onChapter: (String) -> Unit) {
    val dutch = isDutch()
    val chapters = Guide.chapters(dutch)
    val idx = chapters.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val c = chapters[idx]
    val extra = LocalNgExtra.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c.title, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(c.summary, color = extra.subtle)
            Spacer(Modifier.height(8.dp))
            c.blocks.forEach { b ->
                when (b) {
                    is H -> Text(b.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    is P -> Text(
                        b.text,
                        fontFamily = if (b.text.contains("\n") && b.text.first().isDigit()) FontFamily.Monospace else null,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    is Steps -> Column(Modifier.padding(vertical = 4.dp)) {
                        b.items.forEachIndexed { n, t ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("${n + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(26.dp))
                                Text(t, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    is Tip -> Callout(stringResource(R.string.guide_tip), b.text, if (extra.isDark) NgColors.Amber else NgColors.DonkerAmber)
                    is Note -> Callout(stringResource(R.string.guide_note), b.text, if (extra.isDark) NgColors.Pending else NgColors.DiepBlauw)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row {
                if (idx > 0) OutlinedButton(onClick = { onChapter(chapters[idx - 1].id) }) { Text("← " + chapters[idx - 1].title, maxLines = 1) }
                Spacer(Modifier.weight(1f))
                if (idx < chapters.lastIndex) OutlinedButton(onClick = { onChapter(chapters[idx + 1].id) }) { Text(chapters[idx + 1].title + " →", maxLines = 1) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Callout(label: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f)),
    ) {
        Spacer(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
            Text(text)
        }
    }
}
