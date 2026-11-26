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
import io.github.onneau.sceneradio.i18n.Lang
import io.github.onneau.sceneradio.i18n.tr


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
            viewModel.reportUpdateError(tr("Het installatiescherm kon niet worden geopend: ${e.message ?: "onbekende fout"}", "The install screen could not be opened: ${e.message ?: "unknown error"}"))
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
                        tr("Open zelf: Instellingen > Apps > Speciale app-toegang > Onbekende apps installeren > SceneRadio.", "Open it yourself: Settings > Apps > Special app access > Install unknown apps > SceneRadio.")
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
                    is UpdateDownload.Downloading, is UpdateDownload.Verifying -> tr("Update downloaden…", "Downloading update…")
                    is UpdateDownload.Ready -> if (saveOnly) tr("Update gedownload", "Update downloaded") else tr("Klaar om te installeren", "Ready to install")
                    else -> tr("Update beschikbaar", "Update available")
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr("Versie ${info.version} · jij hebt ${AppVersion.NUMBER}", "Version ${info.version} · you have ${AppVersion.NUMBER}"),
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
                                text = tr("${megabytes(download.done)} van ${megabytes(download.total)}", "${megabytes(download.done)} of ${megabytes(download.total)}"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = tr("Het pakket wordt gecontroleerd op de juiste ondertekening voordat Android het mag installeren.", "The package is checked for the correct signature before Android may install it."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateDownload.Verifying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = tr("Ondertekening controleren…", "Checking signature…"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is UpdateDownload.Ready -> {
                        Text(
                            text = if (saveOnly) {
                                tr("De update is gedownload en de ondertekening klopt. Het installatiescherm is niet geopend.", "The update is downloaded and the signature is valid. The install screen was not opened.")
                            } else {
                                tr("De update is gedownload en de ondertekening klopt. Android vraagt zo om bevestiging. ", "The update is downloaded and the signature is valid. Android will ask for confirmation shortly. ") +
                                    tr("Je instellingen, favorieten en lijsten blijven bewaard.", "Your settings, favorites and lists are kept.")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!saveOnly) {
                            Text(
                                text = tr("Opent het installatiescherm niet (bijvoorbeeld op een auto-radio)? ", "Does the install screen not open (for example on a car radio)? ") +
                                    tr("Zet het bestand dan in Downloads en open het daar.", "Then save the file to Downloads and open it there."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onSaveToDownloads, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Opslaan in Downloads", "Save to Downloads"))
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
                                text = tr("WAT IS NIEUW", "WHAT'S NEW"),
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
                            publishedText(info.publishedAt)?.let { tr("Gepubliceerd $it", "Published $it") },
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
                            text = tr("Opent het installatiescherm niet (bijvoorbeeld op een auto-radio)? ", "Does the install screen not open (for example on a car radio)? ") +
                                tr("Kies dan Alleen downloaden: de update gaat naar Downloads en je opent hem daar zelf.", "Then choose Download only: the update goes to Downloads and you open it there yourself."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onDownloadOnly, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Alleen downloaden (in Downloads)", "Download only (to Downloads)"))
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
                is UpdateDownload.Ready -> Button(onClick = onInstall) { Text(tr("Installeren", "Install")) }
                else -> Button(onClick = onUpdate) { Text(if (error != null) tr("Opnieuw proberen", "Try again") else tr("Nu updaten", "Update now")) }
            }
        },
        dismissButton = {
            when (download) {
                is UpdateDownload.Downloading -> TextButton(onClick = onCancel) { Text(tr("Annuleren", "Cancel")) }
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
        title = { Text(tr("Eenmalige toestemming", "One-time permission")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = tr("Om updates zelf te kunnen installeren moet Android het toestaan: ", "To install updates by itself, Android must allow it: ") +
                        tr("Onbekende apps installeren voor SceneRadio.", "Install unknown apps for SceneRadio."),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = tr("Je zet één schakelaar om en tikt op Terug. Daarna gaat de update gewoon door.", "You flip one switch and tap Back. Then the update simply continues."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text(tr("Instellingen openen", "Open settings")) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(tr("Niet nu", "Not now")) } }
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
                        text = status ?: tr("Controleer of er een nieuwe versie is", "Check whether a new version is available"),
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
                Text(if (checking) tr("Controleren…", "Checking…") else tr("Nu controleren", "Check now"))
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
                    text = tr("Automatisch controleren", "Check automatically"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tr("Eén keer per dag, bij het openen van de app", "Once a day, when opening the app"),
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
                text = tr("Update gevonden", "Update found"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (download is UpdateDownload.Ready) {
                    tr("Versie ${found.version} is gedownload en staat klaar om te installeren.", "Version ${found.version} has been downloaded and is ready to install.")
                } else {
                    tr("Versie ${found.version} staat klaar. Tik om het overzicht van wat er nieuw is te zien en te updaten.", "Version ${found.version} is ready. Tap to see what's new and to update.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(onClick = { viewModel.openUpdateDialog() }) {
                Text(if (download is UpdateDownload.Ready) tr("Installeren", "Install") else tr("Bekijk update", "View update"))
            }
            if (download is UpdateDownload.Idle) {
                // Zonder eerst het installatiescherm: de update gaat naar Downloads (voor een auto-radio).
                OutlinedButton(onClick = { viewModel.downloadUpdateOnly() }) {
                    Text(tr("Alleen downloaden (in Downloads)", "Download only (to Downloads)"))
                }
            }
            if (download is UpdateDownload.Ready) {
                OutlinedButton(onClick = { viewModel.saveUpdateToDownloads() }) {
                    Text(tr("Opslaan in Downloads", "Save to Downloads"))
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
    String.format(Lang.locale, "%.1f MB", bytes / (1024.0 * 1024.0))

// "3 nov. 2026" uit het ISO-tijdstip van GitHub; null als het niet te lezen is.
private fun publishedText(iso: String): String? {
    if (iso.isBlank()) return null
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(iso) ?: return null
        SimpleDateFormat("d MMM yyyy", Lang.locale).format(date)
    } catch (e: Exception) {
        null
    }
}

// "Laatst gecontroleerd: vandaag 14:28", of met datum als het langer geleden is.
internal fun lastCheckedText(timestamp: Long): String {
    if (timestamp <= 0L) return tr("Nog niet gecontroleerd", "Not checked yet")
    val date = Date(timestamp)
    val day = SimpleDateFormat("yyyyMMdd", Lang.locale)
    val time = SimpleDateFormat("HH:mm", Lang.locale).format(date)
    return if (day.format(date) == day.format(Date())) {
        tr("Laatst gecontroleerd: vandaag $time", "Last checked: today $time")
    } else {
        tr("Laatst gecontroleerd: ${SimpleDateFormat("d MMM", Lang.locale).format(date)} $time", "Last checked: ${SimpleDateFormat("d MMM", Lang.locale).format(date)} $time")
    }
}
