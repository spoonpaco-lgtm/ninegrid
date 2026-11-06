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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Manakosten zoals op de kaart: {3}{B}{B} als bolletjes. Hybride krijgt de eerste kleur, Phyrexisch een P. */
@Composable
fun ManaCost(cost: String, size: Int = 15) {
    val symbols = Regex("""\{([^}]+)\}""").findAll(cost.substringBefore(" // ")).map { it.groupValues[1] }.toList()
    if (symbols.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
        symbols.forEach { sym ->
            val c = sym.firstOrNull { it in "WUBRG" }
            val bg = if (c != null) NgColors.mana(c) else NgColors.ManaC
            val fg = if (c == null || c == 'W') NgColors.KaartBruin else Color.White
            val label = when {
                c == null -> sym.take(2)
                sym.contains("P") -> "$c"
                else -> "$c"
            }
            Box(
                Modifier.size(size.dp).clip(CircleShape).background(bg).border(1.dp, if (c == 'B') Color(0x66F1E4C6) else Color(0x33000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = fg, fontSize = (size * 0.58).sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

/** Een klein label, bijv. GC (Game Changer). */
@Composable
fun MiniTag(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 5.dp))
}

/** Compacte regel voor een deck: klein plaatje, naam met manakosten, set en labels klein, prijs rechts. */
@Composable
fun DeckCardRow(entry: Entry, print: CardPrint?, settings: AppSettings, images: Images, onClick: () -> Unit) {
    val extra = LocalNgExtra.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(extra.cardSurface)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(NgColors.rarity(print?.rarity)))
        Spacer(Modifier.width(7.dp))
        if (settings.imageMode != ImageMode.OFF) { CardThumb(print, images, settings, 22, foil = entry.finish != Finish.NONFOIL); Spacer(Modifier.width(7.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (entry.quantity > 1) "${entry.quantity}× " else "") + (print?.name ?: "?"),
                    fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (entry.proxy) TextDecoration.LineThrough else null,
                    color = if (entry.proxy) NgColors.Proxy else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(5.dp))
                ManaCost(print?.manaCost ?: "")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${print?.setCode ?: ""} ${print?.collectorNumber ?: ""} · ${print?.rarity ?: ""}", fontSize = 11.sp, color = extra.subtle, maxLines = 1)
                if (print?.gameChanger == true) MiniTag("GC", NgColors.Amber, NgColors.KaartBruin)
                if (entry.finish != Finish.NONFOIL) MiniTag(if (entry.finish == Finish.ETCHED) stringResource(R.string.etched).uppercase() else "FOIL", Color(0xFFC79BFF), NgColors.KaartBruin)
                if (entry.proxy) MiniTag(stringResource(R.string.proxy).uppercase(), NgColors.Proxy, Color.White)
                if (entry.language.isNotBlank()) MiniTag(entry.language, extra.subtle.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(money(Money.total(entry, print, settings.currency), settings.currency), color = extra.price, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = privacyMask())
    }
}

/** Hoofdtype van een kaart voor de groepen in een deck (de voorkant telt; een artifact creature is een creature). */
fun deckGroupOf(typeLine: String): String {
    val t = typeLine.substringBefore("//").substringBefore('—')
    return listOf("Creature", "Planeswalker", "Battle", "Land", "Instant", "Sorcery", "Artifact", "Enchantment").firstOrNull { t.contains(it) } ?: "Other"
}

val DECK_GROUP_ORDER = listOf("Creature", "Planeswalker", "Instant", "Sorcery", "Artifact", "Enchantment", "Battle", "Land", "Other")
