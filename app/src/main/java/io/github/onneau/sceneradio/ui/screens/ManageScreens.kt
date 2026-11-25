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
import io.github.onneau.sceneradio.data.model.genres
import io.github.onneau.sceneradio.data.repository.LOCAL_STATION_MARK
import io.github.onneau.sceneradio.ui.ManageViewModel
import io.github.onneau.sceneradio.ui.StreamTest
import io.github.onneau.sceneradio.ui.components.StationLogo

// Gedeelde stukjes voor de beheerschermen.

@Composable
private fun ManageTitleBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
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
                Icon(Icons.Default.Close, contentDescription = "Sluiten")
            }
        }
    }
}

private fun genreLine(station: RadioStation): String =
    station.genres().joinToString(" · ").ifBlank { "Algemeen" }

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
        item { ManageTitleBar("Streams controleren", onBack) }

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
                            state.running -> "Bezig: ${state.done} van ${state.total}"
                            state.finished -> "Klaar: ${working.size} werken, ${failing.size} reageren niet"
                            else -> "Gestopt na ${state.done} van ${state.total}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${state.done} van ${state.total} gecontroleerd · alleen HTTP 200/206 met audio telt als goed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.running) {
                        TextButton(onClick = { manage.stopStreamCheck() }) { Text("Stoppen") }
                    }
                }
            }
        }

        if (failing.isNotEmpty()) {
            item {
                Text(
                    text = "Reageren niet (${failing.size})",
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
                                text = station?.name ?: "Onbekende zender",
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
                            OutlinedButton(onClick = { manage.hideStation(station.id) }) { Text("Verberg") }
                        } else {
                            Text(
                                text = "Verborgen",
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
                Text("Alles opnieuw controleren")
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
                                text = "Werken (${working.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!showWorking) {
                                Text(
                                    text = "Tik om de lijst uit te klappen",
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
                        text = "✓ ${station?.name ?: "Onbekende zender"} · ${entry.value.contentType}",
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
                    title = if (selected.isEmpty()) "Logo's beheren" else "${selected.size} geselecteerd",
                    onBack = { if (selected.isEmpty()) onBack() else manage.clearSelection() }
                ) {
                    TextButton(onClick = { manage.selectAll(visible.map { it.id }) }) { Text("Alles") }
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { manage.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Selectie wissen")
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
                    placeholder = { Text("Zoek een zender…") },
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
                                    LocalLogos.isLocal(station.logoUrl) -> " · eigen logo op dit toestel"
                                    station.logoUrl.isNullOrBlank() -> " · geen logo"
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
                    Text("Logo koppelen aan ${selected.size} ${if (selected.size == 1) "zender" else "zenders"}")
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
                text = "Logo koppelen aan $count ${if (count == 1) "zender" else "zenders"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = logo?.preview
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Voorbeeld van het logo",
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
                        "Voorbeeld: $firstName wordt JPEG, max. 500 px"
                    } else {
                        "Kies een afbeelding. Die wordt omgezet naar JPEG, max. 500 px."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
                    Text("Kies afbeelding…")
                }
                OutlinedButton(onClick = { showUrl = !showUrl }, enabled = !busy) { Text("Plak URL") }
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
                    Text("Ophalen")
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
                        Text("Koppelen")
                    }
                }
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Annuleren") }
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
        item { ManageTitleBar("Zender toevoegen of bewerken", onBack) }

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
                Text("Nieuwe zender toevoegen")
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Of zoek een zender om te bewerken…") },
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
                            text = genreLine(station) + if (station.format == LOCAL_STATION_MARK) " · eigen zender" else "",
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
        item { ManageTitleBar(if (isNew) "Zender toevoegen" else "Zender bewerken", onBack) }

        item {
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> manage.updateForm { it.copy(name = value) } },
                label = { Text("Naam") },
                singleLine = true,
                enabled = isNew,
                supportingText = if (isNew) null else {
                    { Text("De naam kan niet worden gewijzigd: logo en controle hangen eraan.") }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = form.url,
                onValueChange = { value -> manage.updateForm { it.copy(url = value) } },
                label = { Text("Stream-URL") },
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
                    supportingText = { Text("Meerdere genres scheid je met ;  bijvoorbeeld Pop;Dance. Het eerste is het hoofdgenre.") },
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
                                contentDescription = "Nieuw logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                            )
                        } else {
                            StationLogo(
                                logoUrl = form.existingLogoUrl,
                                stationName = form.name.ifBlank { "Nieuwe zender" },
                                size = 56.dp,
                                shape = RoundedCornerShape(10.dp),
                                imageBackground = Color.White,
                                imagePadding = 4.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                preview != null -> "Nieuw logo gekozen"
                                form.existingLogoUrl.isNullOrBlank() -> "Nog geen logo"
                                else -> "Huidig logo"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("Wijzigen") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showLogoUrl = !showLogoUrl }) { Text("Plak URL") }
                        if (logo != null) {
                            TextButton(onClick = { manage.clearFormLogo() }) { Text("Nieuw logo wissen") }
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
                            Text("Ophalen")
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
                Text("Stream testen")
            }
        }

        when (val current = test) {
            is StreamTest.Running -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Bezig met testen…", style = MaterialTheme.typography.bodyMedium)
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
                            "✓ Werkt · HTTP ${result.status} · ${result.contentType}"
                        } else {
                            "✕ Werkt niet · ${result.error}"
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
                    Text("Opslaan")
                }
            }
        }
    }
}
