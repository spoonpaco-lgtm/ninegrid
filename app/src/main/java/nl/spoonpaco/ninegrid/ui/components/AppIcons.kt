package nl.spoonpaco.ninegrid.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.spoonpaco.ninegrid.R

/** Een startpictogram: de activity-alias in het manifest, de kleur erachter en het raster ervoor. */
data class AppIcon(val id: String, val alias: String, val bg: Long, val fg: Int, val label: Int)

/** De standaardicoon en negen extra's (drie sets: Metalen, Edelstenen, Licht en donker). Precies één alias staat aan. */
object AppIcons {
    val all = listOf(
        AppIcon("default", "IconDefault", 0xFF130C08, R.mipmap.ic_launcher_foreground, R.string.icon_default),
        AppIcon("zilver", "IconZilver", 0xFF0E1014, R.drawable.ic_alt_zilver_fg, R.string.icon_zilver),
        AppIcon("koper", "IconKoper", 0xFF140B07, R.drawable.ic_alt_koper_fg, R.string.icon_koper),
        AppIcon("zwartgoud", "IconZwartgoud", 0xFF0A0A0A, R.drawable.ic_alt_zwartgoud_fg, R.string.icon_zwartgoud),
        AppIcon("smaragd", "IconSmaragd", 0xFF07120D, R.drawable.ic_alt_smaragd_fg, R.string.icon_smaragd),
        AppIcon("robijn", "IconRobijn", 0xFF150709, R.drawable.ic_alt_robijn_fg, R.string.icon_robijn),
        AppIcon("amethist", "IconAmethist", 0xFF0F0A16, R.drawable.ic_alt_amethist_fg, R.string.icon_amethist),
        AppIcon("perkament", "IconPerkament", 0xFFEFE3C8, R.drawable.ic_alt_perkament_fg, R.string.icon_perkament),
        AppIcon("inkt", "IconInkt", 0xFFFFFFFF, R.drawable.ic_alt_inkt_fg, R.string.icon_inkt),
        AppIcon("neon", "IconNeon", 0xFF08060F, R.drawable.ic_alt_neon_fg, R.string.icon_neon),
    )

    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, "nl.spoonpaco.ninegrid." + icon.alias)

    /** Welk pictogram staat nu aan? Een alias zonder eigen instelling volgt het manifest (alleen de standaard staat dan aan). */
    fun current(context: Context): String {
        val pm = context.packageManager
        for (icon in all) {
            val st = pm.getComponentEnabledSetting(component(context, icon))
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon.id == "default")) return icon.id
        }
        return "default"
    }

    /** Zet het gekozen pictogram aan (eerst) en de andere uit, zonder de app te sluiten. */
    fun set(context: Context, id: String) {
        val pm = context.packageManager
        val chosen = all.firstOrNull { it.id == id } ?: all.first()
        pm.setComponentEnabledSetting(component(context, chosen), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (icon in all) if (icon.id != chosen.id) {
            pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

/** De keuzerij in Instellingen → Weergave: tik op een pictogram om het te gebruiken. */
@Composable
fun AppIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        AppIcons.all.forEach { icon ->
            val selected = icon.id == current
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(width = 64.dp, height = 84.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(icon.bg))
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33808080), RoundedCornerShape(16.dp))
                        .clickable { if (!selected) { AppIcons.set(context, icon.id); current = icon.id } },
                ) {
                    Image(painterResource(icon.fg), null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                }
                Text(stringResource(icon.label), fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
