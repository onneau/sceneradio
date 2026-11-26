package io.github.onneau.sceneradio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.i18n.tr

/** Melding bovenaan de zenderlijst: er is een nieuwe versie van de standaard zenderlijst. */
@Composable
fun PlaylistUpdateBanner(
    isFirstImport: Boolean,
    isBusy: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isFirstImport) tr("Zenderlijst beschikbaar", "Station list available") else tr("Nieuwe zenderlijst beschikbaar", "New station list available"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (isFirstImport) {
                    tr("Download de actuele lijst met Nederlandse zenders en logo's.", "Download the current list of Dutch stations and logos.")
                } else {
                    tr("Er is een bijgewerkte lijst. Je favorieten en verborgen zenders blijven behouden.", "An updated list is available. Your favorites and hidden stations are kept.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUpdate, enabled = !isBusy) {
                    Text(
                        when {
                            isBusy -> tr("Bezig…", "Working…")
                            isFirstImport -> tr("Downloaden", "Download")
                            else -> tr("Bijwerken", "Update")
                        }
                    )
                }
                TextButton(onClick = onDismiss, enabled = !isBusy) {
                    Text("Later")
                }
            }
        }
    }
}
