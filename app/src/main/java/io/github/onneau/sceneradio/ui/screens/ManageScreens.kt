package io.github.onneau.sceneradio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.data.manage.LocalLogos
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.genreText
import io.github.onneau.sceneradio.data.model.genres
import io.github.onneau.sceneradio.data.repository.LOCAL_STATION_MARK
import io.github.onneau.sceneradio.ui.ManageViewModel
import io.github.onneau.sceneradio.ui.StreamTest
import io.github.onneau.sceneradio.ui.components.StationLogo
import io.github.onneau.sceneradio.i18n.tr

// Gedeelde stukjes voor de beheerschermen.

@Composable
private fun ManageTitleBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Terug", "Back"))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = tr("Sluiten", "Close"))
            }
        }
    }
}

private fun genreLine(station: RadioStation): String =
    station.genreText()

// ---------------------------------------------------------------- Streams controleren

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamCheckScreen(
    manage: ManageViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by manage.streamCheck.collectAsState()
    val stations by manage.stations.collectAsState()
    var showWorking by rememberSaveable { mutableStateOf(false) }

    // Meteen beginnen, tenzij er al een lopende of afgeronde controle is.
    LaunchedEffect(Unit) {
        if (!state.running && !state.finished) manage.startStreamCheck()
    }

    val byId = remember(stations) { stations.associateBy { it.id } }
    val failing = state.results.entries.filter { !it.value.ok }
        .sortedBy { byId[it.key]?.name?.lowercase().orEmpty() }
    val working = state.results.entries.filter { it.value.ok }
        .sortedBy { byId[it.key]?.name?.lowercase().orEmpty() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ManageTitleBar(tr("Streams controleren", "Check streams"), onBack) }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when {
                            state.running -> tr("Bezig: ${state.done} van ${state.total}", "Working: ${state.done} of ${state.total}")
                            state.finished -> tr("Klaar: ${working.size} werken, ${failing.size} reageren niet", "Done: ${working.size} working, ${failing.size} not responding")
                            else -> tr("Gestopt na ${state.done} van ${state.total}", "Stopped after ${state.done} of ${state.total}")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = tr("${state.done} van ${state.total} gecontroleerd · alleen HTTP 200/206 met audio telt als goed", "${state.done} of ${state.total} checked · only HTTP 200/206 with audio counts as good"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.running) {
                        TextButton(onClick = { manage.stopStreamCheck() }) { Text(tr("Stoppen", "Stop")) }
                    }
                }
            }
        }

        if (failing.isNotEmpty()) {
            item {
                Text(
                    text = tr("Reageren niet (${failing.size})", "Not responding (${failing.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(failing, key = { it.key }) { entry ->
                val station = byId[entry.key]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        StationLogo(
                            logoUrl = station?.logoUrl,
                            stationName = station?.name ?: "?",
                            size = 40.dp,
                            shape = RoundedCornerShape(8.dp),
                            imageBackground = Color.White,
                            imagePadding = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station?.name ?: tr("Onbekende zender", "Unknown station"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = entry.value.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (station != null && station.isEnabled) {
                            OutlinedButton(onClick = { manage.hideStation(station.id) }) { Text(tr("Verberg", "Hide")) }
                        } else {
                            Text(
                                text = tr("Verborgen", "Hidden"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { manage.startStreamCheck() },
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("Alles opnieuw controleren", "Check everything again"))
            }
        }

        if (working.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onClick = { showWorking = !showWorking },
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
                                text = tr("Werken (${working.size})", "Working (${working.size})"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!showWorking) {
                                Text(
                                    text = tr("Tik om de lijst uit te klappen", "Tap to expand the list"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showWorking) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }
            if (showWorking) {
                items(working, key = { it.key }) { entry ->
                    val station = byId[entry.key]
                    Text(
                        text = tr("✓ ${station?.name ?: "Onbekende zender"} · ${entry.value.contentType}", "✓ ${station?.name ?: "Unknown station"} · ${entry.value.contentType}"),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Logo's beheren (bulk)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoManagerScreen(
    manage: ManageViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stations by manage.stations.collectAsState()
    val selected by manage.selected.collectAsState()
    val notice by manage.notice.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // Terug sluit eerst de selectie.
    BackHandler(enabled = selected.isNotEmpty()) { manage.clearSelection() }

    val visible = remember(stations, query) {
        stations.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    val chosen = remember(stations, selected) { stations.filter { it.id in selected } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = if (selected.isNotEmpty()) 96.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ManageTitleBar(
                    title = if (selected.isEmpty()) tr("Logo's beheren", "Manage logos") else tr("${selected.size} geselecteerd", "${selected.size} selected"),
                    onBack = { if (selected.isEmpty()) onBack() else manage.clearSelection() }
                ) {
                    TextButton(onClick = { manage.selectAll(visible.map { it.id }) }) { Text(tr("Alles", "All")) }
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { manage.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = tr("Selectie wissen", "Clear selection"))
                        }
                    }
                }
            }

            notice?.let { text ->
                item { NoticeCard(text = text, onDismiss = { manage.clearNotice() }) }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Zoek een zender…", "Find a station…")) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(visible, key = { it.id }) { station ->
                val isSelected = station.id in selected
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    onClick = { manage.toggleSelected(station.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        StationLogo(
                            logoUrl = station.logoUrl,
                            stationName = station.name,
                            size = 44.dp,
                            shape = RoundedCornerShape(8.dp),
                            imageBackground = Color.White,
                            imagePadding = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = genreLine(station) + when {
                                    LocalLogos.isLocal(station.logoUrl) -> tr(" · eigen logo op dit toestel", " · own logo on this device")
                                    station.logoUrl.isNullOrBlank() -> tr(" · geen logo", " · no logo")
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        manage.prepareBulkSheet()
                        showSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(tr("Logo koppelen aan ${selected.size} ${if (selected.size == 1) "zender" else "zenders"}", "Link logo to ${selected.size} ${if (selected.size == 1) "station" else "stations"}"))
                }
            }
        }
    }

    if (showSheet && chosen.isNotEmpty()) {
        BulkLogoSheet(
            manage = manage,
            count = chosen.size,
            firstName = chosen.first().name,
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkLogoSheet(
    manage: ManageViewModel,
    count: Int,
    firstName: String,
    onDismiss: () -> Unit
) {
    val logo by manage.bulkLogo.collectAsState()
    val busy by manage.bulkBusy.collectAsState()
    val message by manage.bulkMessage.collectAsState()
    var showUrl by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) manage.loadBulkLogoFromUri(uri)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("Logo koppelen aan $count ${if (count == 1) "zender" else "zenders"}", "Link logo to $count ${if (count == 1) "station" else "stations"}"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = logo?.preview
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = tr("Voorbeeld van het logo", "Logo preview"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("?", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (preview != null) {
                        tr("Voorbeeld: $firstName wordt JPEG, max. 500 px", "Example: $firstName becomes JPEG, max. 500 px")
                    } else {
                        tr("Kies een afbeelding. Die wordt omgezet naar JPEG, max. 500 px.", "Choose an image. It is converted to JPEG, max. 500 px.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
                    Text(tr("Kies afbeelding…", "Choose image…"))
                }
                OutlinedButton(onClick = { showUrl = !showUrl }, enabled = !busy) { Text(tr("Plak URL", "Paste URL")) }
            }

            if (showUrl) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("https://…/logo.png") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { manage.loadBulkLogoFromUrl(urlText) },
                    enabled = urlText.isNotBlank() && !busy
                ) {
                    Text(tr("Ophalen", "Fetch"))
                }
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.endsWith("…")) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { manage.applyBulkLogo { onDismiss() } },
                    enabled = logo != null && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(tr("Koppelen", "Link"))
                    }
                }
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(tr("Annuleren", "Cancel")) }
            }
        }
    }
}

// ---------------------------------------------------------------- Zender toevoegen of bewerken

@Composable
fun StationPickerScreen(
    manage: ManageViewModel,
    onBack: () -> Unit,
    onOpenForm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stations by manage.stations.collectAsState()
    val notice by manage.notice.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val visible = remember(stations, query) {
        stations.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ManageTitleBar(tr("Zender toevoegen of bewerken", "Add or edit station"), onBack) }

        notice?.let { text ->
            item { NoticeCard(text = text, onDismiss = { manage.clearNotice() }) }
        }

        item {
            Button(
                onClick = {
                    manage.startNewStation()
                    onOpenForm()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Nieuwe zender toevoegen", "Add new station"))
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Of zoek een zender om te bewerken…", "Or find a station to edit…")) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(visible, key = { it.id }) { station ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            manage.startEditStation(station)
                            onOpenForm()
                        }
                        .padding(12.dp)
                ) {
                    StationLogo(
                        logoUrl = station.logoUrl,
                        stationName = station.name,
                        size = 44.dp,
                        shape = RoundedCornerShape(8.dp),
                        imageBackground = Color.White,
                        imagePadding = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = genreLine(station) + if (station.format == LOCAL_STATION_MARK) tr(" · eigen zender", " · own station") else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationFormScreen(
    manage: ManageViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val form by manage.form.collectAsState()
    val logo by manage.formLogo.collectAsState()
    val logoMessage by manage.formLogoMessage.collectAsState()
    val test by manage.formTest.collectAsState()
    val busy by manage.formBusy.collectAsState()
    val message by manage.formMessage.collectAsState()
    val stations by manage.stations.collectAsState()
    var showLogoUrl by remember { mutableStateOf(false) }
    var logoUrlText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) manage.loadFormLogoFromUri(uri)
    }

    // De meest gebruikte genres, om met één tik toe te voegen.
    val commonGenres = remember(stations) {
        stations.flatMap { it.genres() }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(14)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } }
    }

    val isNew = form.editId == null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ManageTitleBar(if (isNew) tr("Zender toevoegen", "Add station") else tr("Zender bewerken", "Edit station"), onBack) }

        item {
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> manage.updateForm { it.copy(name = value) } },
                label = { Text(tr("Naam", "Name")) },
                singleLine = true,
                enabled = isNew,
                supportingText = if (isNew) null else {
                    { Text(tr("De naam kan niet worden gewijzigd: logo en controle hangen eraan.", "The name cannot be changed: logo and check depend on it.")) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = form.url,
                onValueChange = { value -> manage.updateForm { it.copy(url = value) } },
                label = { Text(tr("Stream-URL", "Stream URL")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = form.genres,
                    onValueChange = { value -> manage.updateForm { it.copy(genres = value) } },
                    label = { Text("Genres") },
                    singleLine = true,
                    supportingText = { Text(tr("Meerdere genres scheid je met ;  bijvoorbeeld Pop;Dance. Het eerste is het hoofdgenre.", "Separate multiple genres with ;  for example Pop;Dance. The first is the main genre.")) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (commonGenres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(commonGenres) { genre ->
                            SuggestionChip(
                                onClick = {
                                    manage.updateForm {
                                        val current = it.genres.trim().trimEnd(';')
                                        val has = current.split(';', ',').any { part -> part.trim().equals(genre, ignoreCase = true) }
                                        if (has) it else it.copy(genres = if (current.isEmpty()) genre else "$current;$genre")
                                    }
                                },
                                label = { Text(genre) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Logo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val preview = logo?.preview
                        if (preview != null) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = tr("Nieuw logo", "New logo"),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                            )
                        } else {
                            StationLogo(
                                logoUrl = form.existingLogoUrl,
                                stationName = form.name.ifBlank { tr("Nieuwe zender", "New station") },
                                size = 56.dp,
                                shape = RoundedCornerShape(10.dp),
                                imageBackground = Color.White,
                                imagePadding = 4.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                preview != null -> tr("Nieuw logo gekozen", "New logo chosen")
                                form.existingLogoUrl.isNullOrBlank() -> tr("Nog geen logo", "No logo yet")
                                else -> tr("Huidig logo", "Current logo")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text(tr("Wijzigen", "Change")) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showLogoUrl = !showLogoUrl }) { Text(tr("Plak URL", "Paste URL")) }
                        if (logo != null) {
                            TextButton(onClick = { manage.clearFormLogo() }) { Text(tr("Nieuw logo wissen", "Clear new logo")) }
                        }
                    }
                    if (showLogoUrl) {
                        OutlinedTextField(
                            value = logoUrlText,
                            onValueChange = { logoUrlText = it },
                            label = { Text("https://…/logo.png") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { manage.loadFormLogoFromUrl(logoUrlText) },
                            enabled = logoUrlText.isNotBlank()
                        ) {
                            Text(tr("Ophalen", "Fetch"))
                        }
                    }
                    logoMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.endsWith("…")) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { manage.testFormStream() },
                enabled = form.url.isNotBlank() && test !is StreamTest.Running
            ) {
                Text(tr("Stream testen", "Test stream"))
            }
        }

        when (val current = test) {
            is StreamTest.Running -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(tr("Bezig met testen…", "Testing…"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is StreamTest.Done -> item {
                val result = current.result
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (result.ok) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (result.ok) {
                            tr("✓ Werkt · HTTP ${result.status} · ${result.contentType}", "✓ Works · HTTP ${result.status} · ${result.contentType}")
                        } else {
                            tr("✕ Werkt niet · ${result.error}", "✕ Does not work · ${result.error}")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (result.ok) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            else -> Unit
        }

        message?.let {
            item {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Button(
                onClick = { manage.saveForm { onBack() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(tr("Opslaan", "Save"))
                }
            }
        }
    }
}
