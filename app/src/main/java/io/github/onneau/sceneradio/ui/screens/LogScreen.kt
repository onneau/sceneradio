@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.onneau.sceneradio.ui.screens

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.data.log.LogEntry
import io.github.onneau.sceneradio.data.log.LogExport
import io.github.onneau.sceneradio.data.log.LogGroup
import io.github.onneau.sceneradio.data.log.LogProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import io.github.onneau.sceneradio.i18n.Lang
import io.github.onneau.sceneradio.i18n.tr

// Een regel in de lijst: een datumkopje of een logregel.
private sealed interface LogRow {
    data class Day(val text: String) : LogRow
    data class Line(val entry: LogEntry) : LogRow
}

private const val COPY_MAX_CHARS = 200_000

/**
 * Instellingen > Over > Logboek: wat de app deed, ook na een crash of vastloper. Bovenaan een melding als de vorige
 * keer iets misging, een schakelaar voor uitgebreid loggen, filters, de regels zelf en knoppen om te delen,
 * te kopiëren en te wissen. Liggend staan de bediening en de regels naast elkaar.
 */
@Composable
fun LogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val verbose by AppLog.verbose.collectAsState()
    val problem by AppLog.problem.collectAsState()
    val revision by AppLog.revision.collectAsState()

    var entries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showShare by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Het bestand opnieuw lezen als er regels bijkomen (even bundelen, zodat het scherm niet blijft verspringen).
    LaunchedEffect(revision) {
        delay(250)
        entries = withContext(Dispatchers.IO) { AppLog.readEntries() }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(8_000)
            message = null
        }
    }

    val shown = remember(entries, filterKey) {
        if (filterKey == null) entries else entries.filter { it.group?.name == filterKey }
    }

    fun saveToDownloads(withSystemLog: Boolean, withDeviceInfo: Boolean) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { LogExport.saveToDownloads(context, withSystemLog, withDeviceInfo) }
            message = result.fold(
                onSuccess = { tr("✓ Opgeslagen in ${it.place} als ${it.name}", "✓ Saved in ${it.place} as ${it.name}") },
                onFailure = { tr("Opslaan mislukt: ${it.message ?: "onbekende fout"}", "Saving failed: ${it.message ?: "unknown error"}") }
            )
        }
    }

    fun share(withSystemLog: Boolean, withDeviceInfo: Boolean) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { LogExport.shareIntent(context, withSystemLog, withDeviceInfo) }
            result.onSuccess { chooser: Intent ->
                try {
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    message = tr("Delen mislukt: ${e.message ?: "geen app gevonden"}", "Sharing failed: ${e.message ?: "no app found"}")
                }
            }.onFailure { message = tr("Delen mislukt: ${it.message ?: "onbekende fout"}", "Sharing failed: ${it.message ?: "unknown error"}") }
        }
    }

    fun copy() {
        val text = shown.joinToString("\n") { "${it.date} ${it.time} ${it.label.padEnd(9)} ${it.message}" }
        val part = if (text.length > COPY_MAX_CHARS) text.takeLast(COPY_MAX_CHARS) else text
        clipboard.setText(AnnotatedString(part))
        message = if (text.length > COPY_MAX_CHARS) tr("Gekopieerd (alleen het laatste deel)", "Copied (last part only)") else tr("Gekopieerd (${shown.size} regels)", "Copied (${shown.size} lines)")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SettingsHeader(tr("Logboek", "Log"), onBack)
        Spacer(modifier = Modifier.height(8.dp))

        if (landscape) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    problem?.let { p ->
                        LogBanner(p, onView = {
                            filterKey = LogGroup.FOUTEN.name
                            AppLog.clearProblem()
                        })
                    }
                    VerboseRow(verbose) { AppLog.setVerbose(it) }
                    LogFilterRow(filterKey) { filterKey = it }
                    LogActions(
                        onShare = { showShare = true },
                        onCopy = { copy() },
                        onClear = { confirmClear = true }
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    LogNote()
                }
                LogList(
                    entries = shown,
                    filterKey = filterKey,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                problem?.let { p ->
                    LogBanner(p, onView = {
                        filterKey = LogGroup.FOUTEN.name
                        AppLog.clearProblem()
                    })
                }
                VerboseRow(verbose) { AppLog.setVerbose(it) }
                LogFilterRow(filterKey) { filterKey = it }
                LogList(
                    entries = shown,
                    filterKey = filterKey,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                LogActions(
                    onShare = { showShare = true },
                    onCopy = { copy() },
                    onClear = { confirmClear = true }
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                LogNote()
            }
        }
    }

    if (showShare) {
        LogShareDialog(
            onDismiss = { showShare = false },
            onSave = { withSystemLog, withDeviceInfo ->
                showShare = false
                saveToDownloads(withSystemLog, withDeviceInfo)
            },
            onShare = { withSystemLog, withDeviceInfo ->
                showShare = false
                share(withSystemLog, withDeviceInfo)
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Logboek wissen?", "Clear log?")) },
            text = { Text(tr("Alle regels worden van dit toestel verwijderd. Dat kan niet ongedaan worden gemaakt.", "All lines are removed from this device. This cannot be undone.")) },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    AppLog.clear()
                    message = tr("Logboek gewist", "Log cleared")
                }) { Text(tr("Wissen", "Clear")) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Annuleren", "Cancel")) } }
        )
    }
}

/** De filterknop van een logboekgroep, in de taal van de app. De regels in het logboek zelf blijven Nederlands. */
private fun groupTitle(group: LogGroup): String = when (group) {
    LogGroup.FOUTEN -> tr("Fouten", "Errors")
    LogGroup.SCHERM -> tr("Scherm", "Screen")
    LogGroup.UPDATE -> "Update"
    LogGroup.AFSPELEN -> tr("Afspelen", "Playback")
    LogGroup.AUTO -> "Auto"
}

@Composable
private fun LogBanner(problem: LogProblem, onView: () -> Unit) {
    val whenText = remember(problem.timeMillis, Lang.isEnglish) {
        if (problem.timeMillis <= 0L) "" else SimpleDateFormat("d MMM HH:mm", Lang.locale).format(Date(problem.timeMillis)) + " — "
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = if (problem.isHang) tr("De app is de vorige keer vastgelopen", "The app froze last time") else tr("De app is de vorige keer gecrasht", "The app crashed last time"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = tr("$whenText${problem.text}. Details staan in het logboek.", "$whenText${problem.text}. Details are in the log."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onView, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                Text(tr("Bekijk ›", "View ›"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VerboseRow(verbose: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("Uitgebreid loggen", "Verbose logging"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = tr("Ook elke schermwissel, afspeelstap en netwerkfout. Aan zolang je iets zoekt.", "Also every screen change, playback step and network error. On while you are looking for something."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = verbose, onCheckedChange = onChange)
    }
}

@Composable
private fun LogFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    val options: List<Pair<String?, String>> =
        listOf<Pair<String?, String>>(null to tr("Alles", "All")) + LogGroup.values().map { Pair<String?, String>(it.name, groupTitle(it)) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(
                selected = selected == option.first,
                onClick = { onSelect(option.first) },
                label = { Text(option.second) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun LogList(entries: List<LogEntry>, filterKey: String?, modifier: Modifier = Modifier) {
    val rows = remember(entries) { buildRows(entries) }
    val state = rememberLazyListState()
    // De nieuwste regel onderaan; bij nieuwe regels of een ander filter naar het einde.
    LaunchedEffect(rows.size, filterKey) {
        if (rows.isNotEmpty()) state.scrollToItem(rows.lastIndex)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
    ) {
        if (rows.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = tr("Nog geen regels.", "No lines yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(state = state, contentPadding = PaddingValues(10.dp), modifier = Modifier.fillMaxSize()) {
                items(rows) { row ->
                    when (row) {
                        is LogRow.Day -> Text(
                            text = row.text,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
                        )
                        is LogRow.Line -> LogLine(row.entry)
                    }
                }
            }
        }
    }
}

private fun buildRows(entries: List<LogEntry>): List<LogRow> {
    val rows = ArrayList<LogRow>(entries.size + 8)
    var lastDate = ""
    for (entry in entries) {
        if (entry.date != lastDate) {
            lastDate = entry.date
            rows.add(LogRow.Day(dayText(entry.date)))
        }
        rows.add(LogRow.Line(entry))
    }
    return rows
}

// 2026-11-12 -> 12-11-2026
private fun dayText(date: String): String =
    if (date.length == 10) date.substring(8, 10) + "-" + date.substring(5, 7) + "-" + date.substring(0, 4) else date

@Composable
private fun LogLine(entry: LogEntry) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val plain = MaterialTheme.colorScheme.onSurface
    val tone = when (entry.label) {
        LogCategory.FOUT.label -> MaterialTheme.colorScheme.error
        LogCategory.HANGT.label -> Color(0xFFF59E0B)
        LogCategory.SCHERM.label, LogCategory.TAB.label, LogCategory.SENSOR.label -> MaterialTheme.colorScheme.primary
        else -> plain
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = dim)) { append(entry.time) }
            append("  ")
            withStyle(SpanStyle(color = tone, fontWeight = FontWeight.Bold)) { append(entry.label.padEnd(9)) }
            append(" ")
            withStyle(SpanStyle(color = tone)) { append(entry.message) }
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun LogActions(onShare: () -> Unit, onCopy: () -> Unit, onClear: () -> Unit) {
    val padding = PaddingValues(horizontal = 8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onShare, contentPadding = padding, modifier = Modifier.weight(1f)) { Text(tr("Delen", "Share")) }
        OutlinedButton(onClick = onCopy, contentPadding = padding, modifier = Modifier.weight(1f)) { Text(tr("Kopiëren", "Copy")) }
        OutlinedButton(onClick = onClear, contentPadding = padding, modifier = Modifier.weight(1f)) { Text(tr("Wissen", "Clear")) }
    }
}

@Composable
private fun LogNote() {
    Text(
        text = tr("Het logboek bewaart de laatste 7 dagen, hooguit 500 KB.", "The log keeps the last 7 days, at most 500 KB."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LogShareDialog(
    onDismiss: () -> Unit,
    onSave: (withSystemLog: Boolean, withDeviceInfo: Boolean) -> Unit,
    onShare: (withSystemLog: Boolean, withDeviceInfo: Boolean) -> Unit
) {
    var withSystemLog by remember { mutableStateOf(true) }
    var withDeviceInfo by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Logboek delen", "Share log")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = tr("Het logboek gaat als tekstbestand naar de app die je kiest (mail, WhatsApp, Drive…). ", "The log goes as a text file to the app you choose (email, WhatsApp, Drive…). ") +
                        tr("Plak of stuur het naar mij als je een fout meldt.", "Paste or send it to me when you report a problem."),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LogCheckRow(
                    title = tr("Systeemlog van de app erbij", "Include the app's system log"),
                    subtitle = tr("Foutmeldingen die Android zelf over SceneRadio bijhield. Dat helpt bij crashes en vastlopers.", "Error messages Android itself kept about SceneRadio. This helps with crashes and freezes."),
                    checked = withSystemLog,
                    onChange = { withSystemLog = it }
                )
                LogCheckRow(
                    title = tr("Apparaatgegevens erbij", "Include device information"),
                    subtitle = tr("Android-versie, schermgrootte, automatisch draaien aan of uit. Geen naam, geen e-mailadres.", "Android version, screen size, auto-rotate on or off. No name, no email address."),
                    checked = withDeviceInfo,
                    onChange = { withDeviceInfo = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onShare(withSystemLog, withDeviceInfo) }) { Text(tr("Delen", "Share")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Annuleren", "Cancel")) }
                TextButton(onClick = { onSave(withSystemLog, withDeviceInfo) }) { Text(tr("Opslaan in Downloads", "Save to Downloads")) }
            }
        }
    )
}

@Composable
private fun LogCheckRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
