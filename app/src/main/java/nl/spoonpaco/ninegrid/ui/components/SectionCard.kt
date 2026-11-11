package nl.spoonpaco.ninegrid.ui.components

import nl.spoonpaco.ninegrid.ui.theme.NgText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import nl.spoonpaco.ninegrid.R
import nl.spoonpaco.ninegrid.ui.theme.LocalNgExtra

/** Een kaart met titel en eventueel een ⓘ (uitleg in een venster): dezelfde stijl als de decktabbladen. */
@Composable
fun SectionCard(
    title: String, info: String? = null, modifier: Modifier = Modifier.padding(vertical = 5.dp),
    trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val extra = LocalNgExtra.current
    Card(colors = CardDefaults.cardColors(containerColor = extra.cardSurface), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = NgText.heading)
                if (info != null) IconButton(onClick = { open = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp), tint = extra.subtle)
                }
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
    if (open && info != null) AlertDialog(
        onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(info) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.done)) } },
    )
}
