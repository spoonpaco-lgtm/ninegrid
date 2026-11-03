package nl.spoonpaco.ninegrid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object NgColors {
    val KaartBruin = Color(0xFF2B1A0E)
    val LeerBruin = Color(0xFF4A2F1A)
    val LeerBruinLicht = Color(0xFF6B4628)
    val Perkament = Color(0xFFF1E4C6)
    val PerkamentDonker = Color(0xFFE3CFA3)
    val Amber = Color(0xFFE58A1F)
    val DonkerAmber = Color(0xFFB5650F)
    val DiepBlauw = Color(0xFF1F4F8C)

    val Common = Color(0xFF9AA3B5)
    val Uncommon = Color(0xFFB8C7D9)
    val Rare = Color(0xFFD4AF37)
    val Mythic = Color(0xFFD9412B)
    val Special = Color(0xFF8E6CC2)

    val Synced = Color(0xFF3FB37F)
    val Pending = Color(0xFF6EC1E4)
    val Error = Color(0xFFE5484D)
    val Proxy = Color(0xFF6B7590)

    val ManaW = Color(0xFFF4E6B8)
    val ManaU = Color(0xFF1F6FB2)
    val ManaB = Color(0xFF2A2420)
    val ManaR = Color(0xFFD2322D)
    val ManaG = Color(0xFF1E7A45)
    val ManaC = Color(0xFFB8B0A3)

    fun rarity(r: String?): Color = when (r?.lowercase()) {
        "common" -> Common
        "uncommon" -> Uncommon
        "rare" -> Rare
        "mythic" -> Mythic
        else -> Special
    }

    fun mana(c: Char): Color = when (c) {
        'W' -> ManaW; 'U' -> ManaU; 'B' -> ManaB; 'R' -> ManaR; 'G' -> ManaG; else -> ManaC
    }
}

data class NgExtra(val isDark: Boolean, val price: Color, val cardSurface: Color, val subtle: Color)

val LocalNgExtra = staticCompositionLocalOf { NgExtra(true, NgColors.Amber, NgColors.LeerBruin, Color(0xFFC9B48E)) }

private val NightScheme = darkColorScheme(
    primary = NgColors.Amber,
    onPrimary = NgColors.KaartBruin,
    secondary = NgColors.DiepBlauw,
    onSecondary = NgColors.Perkament,
    tertiary = NgColors.Pending,
    background = NgColors.KaartBruin,
    onBackground = NgColors.Perkament,
    surface = NgColors.KaartBruin,
    onSurface = NgColors.Perkament,
    surfaceVariant = NgColors.LeerBruin,
    onSurfaceVariant = Color(0xFFC9B48E),
    surfaceContainer = NgColors.LeerBruin,
    surfaceContainerHigh = NgColors.LeerBruinLicht,
    surfaceContainerLow = Color(0xFF3A2414),
    outline = NgColors.DiepBlauw,
    error = NgColors.Error,
)

private val DayScheme = lightColorScheme(
    primary = NgColors.DonkerAmber,
    onPrimary = Color.White,
    secondary = NgColors.DiepBlauw,
    onSecondary = Color.White,
    tertiary = Color(0xFF2E8BB5),
    background = NgColors.Perkament,
    onBackground = NgColors.KaartBruin,
    surface = NgColors.Perkament,
    onSurface = NgColors.KaartBruin,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF7A5C3E),
    surfaceContainer = Color.White,
    surfaceContainerHigh = NgColors.PerkamentDonker,
    surfaceContainerLow = Color(0xFFF7EDD6),
    outline = NgColors.DiepBlauw,
    error = NgColors.Error,
)

/** themeMode: "system", "day" of "night". */
@Composable
fun NineGridTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "day" -> false
        "night" -> true
        else -> isSystemInDarkTheme()
    }
    val extra = if (dark) NgExtra(true, NgColors.Amber, NgColors.LeerBruin, Color(0xFFC9B48E))
    else NgExtra(false, NgColors.DonkerAmber, Color.White, Color(0xFF7A5C3E))
    CompositionLocalProvider(LocalNgExtra provides extra) {
        MaterialTheme(colorScheme = if (dark) NightScheme else DayScheme, content = content)
    }
}
