package nl.spoonpaco.ninegrid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.data.AppSettings
import nl.spoonpaco.ninegrid.data.ImageMode
import nl.spoonpaco.ninegrid.data.Images
import nl.spoonpaco.ninegrid.data.db.CardPrint
import nl.spoonpaco.ninegrid.data.db.Entry
import nl.spoonpaco.ninegrid.data.db.Finish
import nl.spoonpaco.ninegrid.domain.Money
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra
import nl.spoonpaco.ninegrid.ui.theme.NgColors

@Composable
fun ManaPips(colors: String, modifier: Modifier = Modifier, size: Int = 18) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val list = if (colors.isEmpty()) "C" else colors
        list.forEach { c ->
            val bg = NgColors.mana(c)
            val fg = if (c == 'W' || c == 'C') NgColors.KaartBruin else Color.White
            Box(
                Modifier.size(size.dp).clip(CircleShape).background(bg)
                    .border(1.dp, if (c == 'B') Color(0x66F1E4C6) else Color(0x33000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.toString(), color = fg, fontSize = (size * 0.58).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FoilBadge(finish: String) {
    if (finish == Finish.NONFOIL) return
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF7AD3FF), Color(0xFFC79BFF), Color(0xFFFFB3C7))))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            if (finish == Finish.ETCHED) stringResource(R.string.etched).uppercase() else stringResource(R.string.foil).uppercase(),
            color = NgColors.KaartBruin, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SmallTag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun CardThumb(print: CardPrint?, images: Images, settings: AppSettings, width: Int = 40, foil: Boolean = false) {
    if (settings.imageMode == ImageMode.OFF) return
    var issue by androidx.compose.runtime.remember(print?.id) { androidx.compose.runtime.mutableStateOf<Images.Issue?>(null) }
    Box(
        Modifier.width(width.dp).height((width * 1.4).dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).foilShine(foil),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = images.request(images.urlFor(print, large = false), settings),
            imageLoader = images.loader,
            contentDescription = print?.name,
            contentScale = ContentScale.Crop,
            onError = { issue = images.issueFor(it.result.throwable, settings) },
            onSuccess = { issue = null },
            modifier = Modifier.matchParentSize(),
        )
        // Leeg vak? Een klein teken waarom.
        issue?.let { ImageIssueMark(it, small = width < 80) }
    }
}

/** Waarom er geen plaatje is: klein een icoon, groter een korte tekst. */
@Composable
fun ImageIssueMark(issue: Images.Issue, small: Boolean) {
    val icon = when (issue) {
        Images.Issue.WIFI_ONLY -> androidx.compose.material.icons.Icons.Filled.WifiOff
        Images.Issue.OFFLINE -> androidx.compose.material.icons.Icons.Filled.CloudOff
        Images.Issue.MISSING -> androidx.compose.material.icons.Icons.Filled.HideImage
        Images.Issue.FAILED -> androidx.compose.material.icons.Icons.Filled.BrokenImage
    }
    val text = stringResource(when (issue) {
        Images.Issue.WIFI_ONLY -> R.string.img_wifi_only
        Images.Issue.OFFLINE -> R.string.img_offline
        Images.Issue.MISSING -> R.string.img_missing
        Images.Issue.FAILED -> R.string.img_failed
    })
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    if (small) androidx.compose.material3.Icon(icon, text, tint = tint, modifier = Modifier.size(16.dp))
    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
        androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Text(text, fontSize = 11.sp, color = tint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Eén regel in een lijst: rarity-streep, naam, set, manabolletjes en prijs. */
@Composable
fun CardRow(
    entry: Entry,
    print: CardPrint?,
    settings: AppSettings,
    images: Images,
    location: String? = null,
    /** Groen label, bijv. "In collectie ×3" bij Alle kaarten. */
    tag: String? = null,
    /** Eigen prijstekst (bijv. "vanaf € 3,40" of "–") in plaats van de waarde van dit exemplaar. */
    priceText: String? = null,
    /** Iets rechts naast de prijs, bijv. de knop Gekocht op de verlanglijst. */
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val extra = LocalNgExtra.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = extra.cardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(NgColors.rarity(print?.rarity)))
            Spacer(Modifier.width(8.dp))
            CardThumb(print, images, settings, 30, foil = entry.finish != Finish.NONFOIL)
            if (settings.imageMode != ImageMode.OFF) Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        print?.name ?: "?",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (entry.proxy) TextDecoration.LineThrough else null,
                        color = if (entry.proxy) NgColors.Proxy else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Manakosten zoals op de kaart; bij een lange naam of veel symbolen eerst de symbolen kleiner.
                    val cost = print?.manaCost.orEmpty()
                    if (cost.isNotBlank()) {
                        val symbols = Regex("""\{[^}]+\}""").findAll(cost.substringBefore(" // ")).count()
                        val tight = (print?.name?.length ?: 0) + symbols * 2 > 24
                        Spacer(Modifier.width(5.dp))
                        ManaCost(cost, size = if (tight) 12 else 15)
                    }
                    FoilBadge(entry.finish)
                    if (entry.language.isNotBlank()) SmallTag(entry.language)
                    if (entry.proxy) SmallTag(stringResource(R.string.proxy), NgColors.Proxy)
                    if (entry.condition != "NM") SmallTag(entry.condition)
                }
                Text(
                    listOfNotNull("${print?.setCode ?: ""} ${print?.collectorNumber ?: ""}", print?.rarity, location).joinToString(" · "),
                    color = extra.subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (tag != null) Text(tag, color = NgColors.Synced, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Lands hebben geen manakosten: die tonen de kleuren die ze maken.
            if (print != null && print.manaCost.isBlank() && print.typeLine.contains("Land")) {
                Spacer(Modifier.width(6.dp))
                ManaPips(print.producedMana.filter { it in "WUBRGC" }.ifEmpty { "C" }, size = 16)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(priceText ?: nl.spoonpaco.ninegrid.ui.components.money(Money.total(entry, print, settings.currency), settings.currency), color = extra.price, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.then(nl.spoonpaco.ninegrid.ui.components.privacyMask()))
                if (entry.quantity > 1) Text("${entry.quantity}×", color = extra.subtle, fontSize = 12.sp)
            }
            trailing?.let { Spacer(Modifier.width(6.dp)); it() }
        }
    }
}

@Composable
fun SectionHeader(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = LocalNgExtra.current.subtle)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalNgExtra.current.subtle)
    }
}

@Composable
fun StatRow(label: String, value: String, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

