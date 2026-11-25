package io.github.onneau.sceneradio.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.github.onneau.sceneradio.AppVersion
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.ui.RadioViewModel
import io.github.onneau.sceneradio.update.AppUpdater
import io.github.onneau.sceneradio.update.UpdateDownload
import io.github.onneau.sceneradio.update.UpdateInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val DUTCH = Locale.forLanguageTag("nl-NL")

/**
 * Toont de updatemelding ("Update beschikbaar" met wat er nieuw is, het downloaden en de eenmalige
 * toestemming om apps te installeren). Staat één keer in MainActivity; de dialogen verschijnen vanzelf.
 */
@Composable
fun UpdateHost(viewModel: RadioViewModel) {
    val context = LocalContext.current
    val info by viewModel.appUpdate.collectAsState()
    val show by viewModel.showUpdateDialog.collectAsState()
    val download by viewModel.updateDownload.collectAsState()
    val error by viewModel.updateError.collectAsState()
    val savedMessage by viewModel.updateSavedMessage.collectAsState()
    val saveOnly by viewModel.updateSaveOnly.collectAsState()
    var showPermission by remember { mutableStateOf(false) }

    fun launchInstall(file: File) {
        if (!AppUpdater.canInstall(context)) {
            AppLog.i(LogCategory.UPDATE, "toestemming \"onbekende apps installeren\" ontbreekt; uitleg getoond")
            showPermission = true
            return
        }
        try {
            context.startActivity(AppUpdater.installIntent(context, file))
            viewModel.onInstallLaunched()
        } catch (e: Exception) {
            AppLog.e(LogCategory.UPDATE, "installatiescherm kon niet worden geopend", e)
            viewModel.reportUpdateError("Het installatiescherm kon niet worden geopend: ${e.message ?: "onbekende fout"}")
        }
    }

    // Is de update gedownload en gecontroleerd terwijl de app in beeld is? Dan meteen naar het installatiescherm.
    // Anders (app op de achtergrond) blijft de knop "Installeren" in het overzicht staan.
    LaunchedEffect(download) {
        val ready = download as? UpdateDownload.Ready ?: return@LaunchedEffect
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        // Bij "Alleen downloaden" opent het installatiescherm niet: de update gaat naar Downloads.
        if (show && !viewModel.updateSaveOnly.value && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            launchInstall(ready.file)
        }
    }

    val current = info
    if (show && current != null) {
        UpdateDialog(
            info = current,
            download = download,
            error = error,
            savedMessage = savedMessage,
            saveOnly = saveOnly,
            onSaveToDownloads = { viewModel.saveUpdateToDownloads() },
            onDownloadOnly = { viewModel.downloadUpdateOnly() },
            onLater = { viewModel.dismissUpdateDialog() },
            onUpdate = { viewModel.startUpdateDownload() },
            onCancel = { viewModel.cancelUpdateDownload() },
            onInstall = { (download as? UpdateDownload.Ready)?.let { launchInstall(it.file) } }
        )
    }

    if (showPermission) {
        InstallPermissionDialog(
            onNotNow = { showPermission = false },
            onOpenSettings = {
                showPermission = false
                try {
                    context.startActivity(AppUpdater.permissionIntent(context))
                } catch (e: Exception) {
                    viewModel.reportUpdateError(
                        "Open zelf: Instellingen > Apps > Speciale app-toegang > Onbekende apps installeren > SceneRadio."
                    )
                }
            }
        )
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    download: UpdateDownload,
    error: String?,
    savedMessage: String?,
    saveOnly: Boolean,
    onSaveToDownloads: () -> Unit,
    onDownloadOnly: () -> Unit,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    val busy = download is UpdateDownload.Downloading || download is UpdateDownload.Verifying
    AlertDialog(
        // Tijdens het downloaden sluit alleen "Annuleren" het overzicht.
        onDismissRequest = { if (!busy) onLater() },
        icon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                when (download) {
                    is UpdateDownload.Downloading, is UpdateDownload.Verifying -> "Update downloaden…"
                    is UpdateDownload.Ready -> if (saveOnly) "Update gedownload" else "Klaar om te installeren"
                    else -> "Update beschikbaar"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Versie ${info.version} · jij hebt ${AppVersion.NUMBER}",
                    style = MaterialTheme.typography.bodyMedium
                )
                when (download) {
                    is UpdateDownload.Downloading -> {
                        if (download.total > 0) {
                            LinearProgressIndicator(
                                progress = { (download.done.toFloat() / download.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${megabytes(download.done)} van ${megabytes(download.total)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = "Het pakket wordt gecontroleerd op de juiste ondertekening voordat Android het mag installeren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateDownload.Verifying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Ondertekening controleren…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is UpdateDownload.Ready -> {
                        Text(
                            text = if (saveOnly) {
                                "De update is gedownload en de ondertekening klopt. Het installatiescherm is niet geopend."
                            } else {
                                "De update is gedownload en de ondertekening klopt. Android vraagt zo om bevestiging. " +
                                    "Je instellingen, favorieten en lijsten blijven bewaard."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!saveOnly) {
                            Text(
                                text = "Opent het installatiescherm niet (bijvoorbeeld op een auto-radio)? " +
                                    "Zet het bestand dan in Downloads en open het daar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onSaveToDownloads, modifier = Modifier.fillMaxWidth()) {
                            Text("Opslaan in Downloads")
                        }
                        if (savedMessage != null) {
                            Text(
                                text = savedMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        if (info.notes.isNotEmpty()) {
                            Text(
                                text = "WAT IS NIEUW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            info.notes.forEach { note ->
                                Row {
                                    Text("•", color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        val meta = listOfNotNull(
                            publishedText(info.publishedAt)?.let { "Gepubliceerd $it" },
                            if (info.sizeBytes > 0) megabytes(info.sizeBytes) else null
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // De keuze staat hier, vóór het downloaden: op een auto-radio opent het installatiescherm soms niet.
                        Text(
                            text = "Opent het installatiescherm niet (bijvoorbeeld op een auto-radio)? " +
                                "Kies dan Alleen downloaden: de update gaat naar Downloads en je opent hem daar zelf.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onDownloadOnly, modifier = Modifier.fillMaxWidth()) {
                            Text("Alleen downloaden (in Downloads)")
                        }
                    }
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when (download) {
                is UpdateDownload.Downloading, is UpdateDownload.Verifying -> {}
                is UpdateDownload.Ready -> Button(onClick = onInstall) { Text("Installeren") }
                else -> Button(onClick = onUpdate) { Text(if (error != null) "Opnieuw proberen" else "Nu updaten") }
            }
        },
        dismissButton = {
            when (download) {
                is UpdateDownload.Downloading -> TextButton(onClick = onCancel) { Text("Annuleren") }
                is UpdateDownload.Verifying -> {}
                else -> TextButton(onClick = onLater) { Text("Later") }
            }
        }
    )
}

@Composable
private fun InstallPermissionDialog(onNotNow: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Eenmalige toestemming") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Om updates zelf te kunnen installeren moet Android het toestaan: " +
                        "Onbekende apps installeren voor SceneRadio.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Je zet één schakelaar om en tikt op Terug. Daarna gaat de update gewoon door.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Instellingen openen") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Niet nu") } }
    )
}

/** Het blok met de updates van de app onder Instellingen > Updates. */
fun LazyListScope.updateSettingsItems(viewModel: RadioViewModel, title: String = "Updates") {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    item { UpdateStatusCard(viewModel) }
    item { AutoCheckRow(viewModel) }
    item { UpdateFoundCard(viewModel) }
}

@Composable
private fun UpdateStatusCard(viewModel: RadioViewModel) {
    val status by viewModel.updateStatus.collectAsState()
    val checking by viewModel.isCheckingUpdate.collectAsState()
    val prefs by viewModel.updatePrefs.collectAsState()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status ?: "Controleer of er een nieuwe versie is",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = lastCheckedText(prefs.checkedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = { viewModel.checkAppUpdateNow() },
                enabled = !checking
            ) {
                Text(if (checking) "Controleren…" else "Nu controleren")
            }
        }
    }
}

@Composable
private fun AutoCheckRow(viewModel: RadioViewModel) {
    val prefs by viewModel.updatePrefs.collectAsState()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Automatisch controleren",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Eén keer per dag, bij het openen van de app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = prefs.autoCheck,
                onCheckedChange = { viewModel.setAutoUpdateCheck(it) }
            )
        }
    }
}

@Composable
private fun UpdateFoundCard(viewModel: RadioViewModel) {
    val info by viewModel.appUpdate.collectAsState()
    val download by viewModel.updateDownload.collectAsState()
    val savedMessage by viewModel.updateSavedMessage.collectAsState()
    val found = info ?: return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Update gevonden",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (download is UpdateDownload.Ready) {
                    "Versie ${found.version} is gedownload en staat klaar om te installeren."
                } else {
                    "Versie ${found.version} staat klaar. Tik om het overzicht van wat er nieuw is te zien en te updaten."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(onClick = { viewModel.openUpdateDialog() }) {
                Text(if (download is UpdateDownload.Ready) "Installeren" else "Bekijk update")
            }
            if (download is UpdateDownload.Idle) {
                // Zonder eerst het installatiescherm: de update gaat naar Downloads (voor een auto-radio).
                OutlinedButton(onClick = { viewModel.downloadUpdateOnly() }) {
                    Text("Alleen downloaden (in Downloads)")
                }
            }
            if (download is UpdateDownload.Ready) {
                OutlinedButton(onClick = { viewModel.saveUpdateToDownloads() }) {
                    Text("Opslaan in Downloads")
                }
                savedMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun megabytes(bytes: Long): String =
    String.format(DUTCH, "%.1f MB", bytes / (1024.0 * 1024.0))

// "3 nov. 2026" uit het ISO-tijdstip van GitHub; null als het niet te lezen is.
private fun publishedText(iso: String): String? {
    if (iso.isBlank()) return null
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(iso) ?: return null
        SimpleDateFormat("d MMM yyyy", DUTCH).format(date)
    } catch (e: Exception) {
        null
    }
}

// "Laatst gecontroleerd: vandaag 14:28", of met datum als het langer geleden is.
internal fun lastCheckedText(timestamp: Long): String {
    if (timestamp <= 0L) return "Nog niet gecontroleerd"
    val date = Date(timestamp)
    val day = SimpleDateFormat("yyyyMMdd", DUTCH)
    val time = SimpleDateFormat("HH:mm", DUTCH).format(date)
    return if (day.format(date) == day.format(Date())) {
        "Laatst gecontroleerd: vandaag $time"
    } else {
        "Laatst gecontroleerd: ${SimpleDateFormat("d MMM", DUTCH).format(date)} $time"
    }
}
