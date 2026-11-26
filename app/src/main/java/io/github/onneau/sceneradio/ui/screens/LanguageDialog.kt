package io.github.onneau.sceneradio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.i18n.Lang
import io.github.onneau.sceneradio.i18n.LanguageSetting
import io.github.onneau.sceneradio.i18n.tr

/** Wat er onder "Taal / Language" in Instellingen staat: de huidige keuze. */
fun languageStatus(): String = when (Lang.current) {
    LanguageSetting.SYSTEM -> tr("Volg het systeem", "Follow the system") + " · " + (if (Lang.isEnglish) "English" else "Nederlands")
    LanguageSetting.DUTCH -> "Nederlands"
    LanguageSetting.ENGLISH -> "English"
}

/** Kies Nederlands, English of het systeem. De app past zich meteen aan, zonder opnieuw te starten. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val options = listOf(
        Triple(
            LanguageSetting.SYSTEM,
            tr("Volg het systeem", "Follow the system"),
            tr("Nederlands op een Nederlands toestel, anders English", "Dutch on a Dutch device, otherwise English")
        ),
        Triple(LanguageSetting.DUTCH, "Nederlands", null),
        Triple(LanguageSetting.ENGLISH, "English", null)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Taal / Language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (setting, title, subtitle) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { Lang.set(context, setting) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = Lang.current == setting, onClick = { Lang.set(context, setting) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tr(
                        "De app past zich meteen aan, zonder opnieuw te starten.",
                        "The app switches right away, without restarting."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("Sluiten", "Close")) }
        }
    )
}
