package io.github.onneau.sceneradio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.data.backup.BackupCounts
import io.github.onneau.sceneradio.data.backup.BackupException
import io.github.onneau.sceneradio.data.backup.BackupInfo
import io.github.onneau.sceneradio.data.backup.BackupManager
import io.github.onneau.sceneradio.data.backup.BackupPart
import io.github.onneau.sceneradio.data.backup.ParsedBackup
import io.github.onneau.sceneradio.data.backup.RestoreResult
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.i18n.Lang
import io.github.onneau.sceneradio.i18n.tr
import io.github.onneau.sceneradio.ui.RadioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Back-up: de instellingen en je eigen wijzigingen in één bestand bewaren (in Downloads of via het deelmenu) en
 * later terugzetten, ook op een andere telefoon of na opnieuw installeren.
 */
@Composable
fun BackupScreen(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BackupManager(context) }

    var counts by remember { mutableStateOf<BackupCounts?>(null) }
    var withSettings by rememberSaveable { mutableStateOf(true) }
    var withFavorites by rememberSaveable { mutableStateOf(true) }
    var withOwn by rememberSaveable { mutableStateOf(true) }
    var withLogos by rememberSaveable { mutableStateOf(true) }
    var withPlaylists by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ParsedBackup?>(null) }
    var listMissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BackupInfo.markSeen(context)
        counts = manager.counts()
    }

    val parts: Set<BackupPart> = buildSet<BackupPart> {
        if (withSettings) add(BackupPart.SETTINGS)
        if (withFavorites) add(BackupPart.FAVORITES)
        if (withOwn) add(BackupPart.OWN_STATIONS)
        if (withLogos) add(BackupPart.OWN_LOGOS)
        if (withPlaylists) add(BackupPart.PLAYLISTS)
    }

    fun save() {
        if (busy || parts.isEmpty()) return
        busy = true
        message = null
        scope.launch {
            try {
                val json = manager.create(parts)
                val result = manager.saveToDownloads(json)
                val saved = result.getOrNull()
                if (saved != null) {
                    BackupInfo.markMade(context)
                    message = tr("Opgeslagen in ${saved.place} als ${saved.name}", "Saved in ${saved.place} as ${saved.name}")
                } else {
                    val reason = result.exceptionOrNull()?.message ?: tr("onbekende fout", "unknown error")
                    message = tr("Opslaan mislukt: $reason", "Saving failed: $reason")
                }
            } catch (e: Exception) {
                AppLog.e(LogCategory.DOWNLOADS, "back-up maken mislukt", e)
                message = tr("Back-up maken mislukt: ${e.message ?: "onbekende fout"}", "Making the backup failed: ${e.message ?: "unknown error"}")
            }
            busy = false
        }
    }

    fun share() {
        if (busy || parts.isEmpty()) return
        busy = true
        message = null
        scope.launch {
            try {
                val json = manager.create(parts)
                val chooser = manager.shareIntent(json).getOrNull()
                if (chooser != null) {
                    try {
                        context.startActivity(chooser)
                        BackupInfo.markMade(context)
                    } catch (e: Exception) {
                        message = tr("Delen mislukt: ${e.message ?: "geen app gevonden"}", "Sharing failed: ${e.message ?: "no app found"}")
                    }
                } else {
                    message = tr("Delen mislukt.", "Sharing failed.")
                }
            } catch (e: Exception) {
                AppLog.e(LogCategory.DOWNLOADS, "back-up maken mislukt", e)
                message = tr("Back-up maken mislukt: ${e.message ?: "onbekende fout"}", "Making the backup failed: ${e.message ?: "unknown error"}")
            }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !busy) {
            busy = true
            message = null
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw BackupException(tr("Dit bestand kon niet worden geopend.", "This file could not be opened."))
                        stream.use { BackupManager.readLimited(it) }
                    }
                    val parsed = withContext(Dispatchers.Default) { manager.parse(bytes) }
                    listMissing = parsed.stations.isNotEmpty() && !manager.defaultListPresent()
                    pending = parsed
                } catch (e: BackupException) {
                    message = e.message
                } catch (e: Exception) {
                    AppLog.e(LogCategory.DOWNLOADS, "back-up lezen mislukt", e)
                    message = tr("Dit bestand kon niet worden gelezen.", "This file could not be read.")
                }
                busy = false
            }
        }
    }

    fun restore(parsed: ParsedBackup) {
        pending = null
        busy = true
        message = null
        scope.launch {
            try {
                val result = manager.restore(parsed)
                viewModel.reloadSortOrder()
                counts = manager.counts()
                message = restoredText(result)
            } catch (e: Exception) {
                AppLog.e(LogCategory.DOWNLOADS, "terugzetten van de back-up mislukt", e)
                message = tr("Terugzetten mislukt: ${e.message ?: "onbekende fout"}", "Restore failed: ${e.message ?: "unknown error"}")
            }
            busy = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader(tr("Back-up", "Backup"), onBack) }

        item { GroupTitle(tr("Wat zit er in de back-up", "What goes in the backup")) }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                    PartRow(
                        checked = withSettings,
                        onChange = { withSettings = it },
                        title = tr("Instellingen", "Settings"),
                        detail = tr("Taal, thema, visualizer, slogans, grote bediening, update-voorkeur", "Language, theme, visualizer, slogans, large controls, update preference"),
                        count = null
                    )
                    PartRow(
                        checked = withFavorites,
                        onChange = { withFavorites = it },
                        title = tr("Favorieten en verborgen zenders", "Favorites and hidden stations"),
                        detail = tr("Ook je gekozen sortering", "Also your chosen sort order"),
                        count = counts?.let { "${it.favorites} · ${it.hidden}" }
                    )
                    PartRow(
                        checked = withOwn,
                        onChange = { withOwn = it },
                        title = tr("Eigen zenders", "Own stations"),
                        detail = tr("Zenders die je zelf hebt toegevoegd", "Stations you added yourself"),
                        count = counts?.own?.toString()
                    )
                    PartRow(
                        checked = withLogos,
                        onChange = { withLogos = it },
                        title = tr("Eigen logo's", "Own logos"),
                        detail = tr("Logo's die je zelf voor een zender koos", "Logos you chose for a station yourself"),
                        count = counts?.let { "${it.logos} · ${(it.logoBytes + 1023) / 1024} KB" }
                    )
                    PartRow(
                        checked = withPlaylists,
                        onChange = { withPlaylists = it },
                        title = tr("Geïmporteerde afspeellijsten", "Imported playlists"),
                        detail = tr("Met hun zenders, favorieten en verborgen zenders", "With their stations, favorites and hidden stations"),
                        count = counts?.playlists?.toString()
                    )
                }
            }
        }
        item {
            Text(
                text = tr(
                    "Niet in de back-up: de standaard zenderlijst zelf (die komt weer van GitHub), hoezen en artiestenfoto's (cache) en het logboek.",
                    "Not in the backup: the default station list itself (it comes from GitHub again), covers and artist photos (cache) and the log."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Button(
                onClick = { save() },
                enabled = !busy && parts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Back-up opslaan in Downloads", "Save backup in Downloads")) }
        }
        item {
            OutlinedButton(
                onClick = { share() },
                enabled = !busy && parts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Back-up delen…", "Share backup…")) }
        }
        item {
            Text(
                text = if (parts.isEmpty()) {
                    tr("Kies minstens één onderdeel.", "Choose at least one part.")
                } else {
                    tr(
                        "Het bestand is niet versleuteld: streamadressen staan er leesbaar in. Bewaar het privé.",
                        "The file is not encrypted: stream addresses are readable in it. Keep it private."
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        message?.let { text ->
            item {
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        item { GroupTitle(tr("Terugzetten", "Restore")) }
        item {
            Text(
                text = tr(
                    "Terugzetten voegt toe en overschrijft je instellingen; er wordt geen zender verwijderd.",
                    "Restoring adds and overwrites your settings; no station is removed."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("Back-up kiezen en terugzetten…", "Choose a backup and restore…")) }
        }
    }

    pending?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tr("Back-up terugzetten?", "Restore backup?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(summaryHeader(parsed))
                    for (line in summaryLines(parsed)) Text("• $line")
                    if (listMissing) {
                        Text(
                            tr(
                                "Tip: haal eerst de standaard zenderlijst op (Instellingen > Updates). Dan blijven ook verborgen zenders goed staan.",
                                "Tip: first get the default station list (Settings > Updates). Then hidden stations are restored correctly too."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        tr(
                            "Je huidige instellingen worden overschreven. Zenders blijven staan; er wordt niets verwijderd.",
                            "Your current settings are overwritten. Stations stay; nothing is removed."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { restore(parsed) }) { Text(tr("Terugzetten", "Restore")) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(tr("Annuleren", "Cancel")) } }
        )
    }
}

@Composable
private fun PartRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    title: String,
    detail: String,
    count: String?
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(modifier = Modifier
            .weight(1f)
            .padding(top = 12.dp, bottom = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (count != null) {
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}

private fun appLabel(app: String): String = when (app) {
    "sceneradio" -> "SceneRadio"
    "etherwave" -> "EtherWave"
    else -> tr("een andere app", "another app")
}

private fun summaryHeader(parsed: ParsedBackup): String {
    val date = if (parsed.createdAt > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Lang.locale).format(Date(parsed.createdAt))
    } else {
        "?"
    }
    val version = if (parsed.appVersion.isNotEmpty()) " ${parsed.appVersion}" else ""
    return tr(
        "Back-up van $date, gemaakt met ${appLabel(parsed.app)}$version. Hierin staan:",
        "Backup from $date, made with ${appLabel(parsed.app)}$version. It contains:"
    )
}

private fun summaryLines(parsed: ParsedBackup): List<String> {
    val lines = ArrayList<String>()
    if (parsed.hasSettings) lines.add(tr("de instellingen en de taal", "the settings and the language"))
    if (parsed.favorites > 0 || parsed.hidden > 0) {
        lines.add(tr("${parsed.favorites} favorieten, ${parsed.hidden} verborgen zenders", "${parsed.favorites} favorites, ${parsed.hidden} hidden stations"))
    }
    if (parsed.own > 0) lines.add(tr("${parsed.own} eigen zenders", "${parsed.own} own stations"))
    if (parsed.logos.isNotEmpty()) lines.add(tr("${parsed.logos.size} eigen logo's", "${parsed.logos.size} own logos"))
    if (parsed.playlists.isNotEmpty()) {
        lines.add(
            tr(
                "${parsed.playlists.size} geïmporteerde afspeellijsten (${parsed.playlistStations} zenders)",
                "${parsed.playlists.size} imported playlists (${parsed.playlistStations} stations)"
            )
        )
    }
    return lines
}

private fun restoredText(result: RestoreResult): String {
    val nl = ArrayList<String>()
    val en = ArrayList<String>()
    if (result.settings) { nl.add("instellingen"); en.add("settings") }
    if (result.addedStations > 0) { nl.add("${result.addedStations} zenders erbij"); en.add("${result.addedStations} stations added") }
    if (result.addedPlaylists > 0) { nl.add("${result.addedPlaylists} afspeellijsten erbij"); en.add("${result.addedPlaylists} playlists added") }
    if (result.logos > 0) { nl.add("${result.logos} logo's"); en.add("${result.logos} logos") }
    val main = if (nl.isEmpty()) {
        tr("Teruggezet.", "Restored.")
    } else {
        tr("Teruggezet: ${nl.joinToString(", ")}.", "Restored: ${en.joinToString(", ")}.")
    }
    val skipped = if (result.logosSkipped > 0) {
        tr(" ${result.logosSkipped} logo's konden niet worden gelezen en zijn overgeslagen.", " ${result.logosSkipped} logos could not be read and were skipped.")
    } else {
        ""
    }
    return main + skipped
}
