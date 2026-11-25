package io.github.onneau.sceneradio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import io.github.onneau.sceneradio.AppVersion
import io.github.onneau.sceneradio.data.DefaultPlaylistSource
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.ui.components.lastCheckedText
import io.github.onneau.sceneradio.ui.components.updateSettingsItems
import io.github.onneau.sceneradio.ui.ManageViewModel
import io.github.onneau.sceneradio.ui.RadioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// De schermen van Instellingen.
private const val PAGE_MAIN = "hoofd"
private const val PAGE_DISPLAY = "weergave"
private const val PAGE_VISUALIZER = "visualizer"
private const val PAGE_SLOGANS = "slogans"
private const val PAGE_CHANNELS = "zenderbeheer"
private const val PAGE_STREAMS = "streams"
private const val PAGE_LOGOS = "logos"
private const val PAGE_STATION_PICKER = "zender-kiezen"
private const val PAGE_STATION_FORM = "zender"
private const val PAGE_UPDATES = "updates"
private const val PAGE_ABOUT = "over"
private const val PAGE_LOG = "logboek"

/**
 * Instellingen in lagen, in de volgorde waarin je ze nodig hebt: Weergave (thema, Visualizer met daaronder Slogans),
 * Zenders (toevoegen of bewerken, logo's, streams controleren, afspeellijsten), Updates (de app en de standaard
 * zenderlijst) en Over. De terugknop van Android gaat steeds een laag terug.
 */
@Composable
fun SettingsScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(PAGE_MAIN) }
    val manage: ManageViewModel = composeViewModel()

    BackHandler(enabled = page != PAGE_MAIN) {
        page = when (page) {
            PAGE_STATION_FORM -> PAGE_STATION_PICKER
            PAGE_SLOGANS -> PAGE_VISUALIZER
            PAGE_LOG -> PAGE_ABOUT
            PAGE_VISUALIZER -> PAGE_DISPLAY
            PAGE_STREAMS, PAGE_LOGOS, PAGE_STATION_PICKER -> PAGE_CHANNELS
            else -> PAGE_MAIN
        }
    }

    when (page) {
        PAGE_DISPLAY -> DisplaySettings(
            viewModel = viewModel,
            onBack = { page = PAGE_MAIN },
            onOpenVisualizer = { page = PAGE_VISUALIZER },
            modifier = modifier
        )
        PAGE_VISUALIZER -> VisualizerSettingsScreen(
            viewModel = viewModel,
            onBack = { page = PAGE_DISPLAY },
            onOpenSlogans = { page = PAGE_SLOGANS },
            modifier = modifier
        )
        PAGE_SLOGANS -> SloganSettingsScreen(
            viewModel = viewModel,
            onBack = { page = PAGE_VISUALIZER },
            modifier = modifier
        )
        PAGE_CHANNELS -> ChannelSettings(
            viewModel = viewModel,
            manage = manage,
            onBack = { page = PAGE_MAIN },
            onOpenStreams = { page = PAGE_STREAMS },
            onOpenLogos = { page = PAGE_LOGOS },
            onOpenStations = { page = PAGE_STATION_PICKER },
            modifier = modifier
        )
        PAGE_STREAMS -> StreamCheckScreen(
            manage = manage,
            onBack = { page = PAGE_CHANNELS },
            modifier = modifier
        )
        PAGE_LOGOS -> LogoManagerScreen(
            manage = manage,
            onBack = { page = PAGE_CHANNELS },
            modifier = modifier
        )
        PAGE_STATION_PICKER -> StationPickerScreen(
            manage = manage,
            onBack = { page = PAGE_CHANNELS },
            onOpenForm = { page = PAGE_STATION_FORM },
            modifier = modifier
        )
        PAGE_STATION_FORM -> StationFormScreen(
            manage = manage,
            onBack = { page = PAGE_STATION_PICKER },
            modifier = modifier
        )
        PAGE_UPDATES -> UpdatesSettings(
            viewModel = viewModel,
            onBack = { page = PAGE_MAIN },
            modifier = modifier
        )
        PAGE_ABOUT -> AboutSettings(
            onBack = { page = PAGE_MAIN },
            onOpenLog = { page = PAGE_LOG },
            modifier = modifier
        )
        PAGE_LOG -> LogScreen(
            onBack = { page = PAGE_ABOUT },
            modifier = modifier
        )
        else -> MainSettings(
            viewModel = viewModel,
            manage = manage,
            onOpenDisplay = { page = PAGE_DISPLAY },
            onOpenChannels = { page = PAGE_CHANNELS },
            onOpenUpdates = { page = PAGE_UPDATES },
            onOpenAbout = { page = PAGE_ABOUT },
            modifier = modifier
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Gedeelde onderdelen
// ------------------------------------------------------------------------------------------------

@Composable
internal fun SettingsHeader(title: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            modifier = if (onBack == null) Modifier.padding(vertical = 8.dp) else Modifier
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Een onderwerp op het hoofdscherm van Instellingen: icoon, titel, uitleg, een regel met de stand van zaken en eventueel een badge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    badge: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Hoofdscherm: vier onderwerpen op volgorde van gebruik
// ------------------------------------------------------------------------------------------------

@Composable
private fun MainSettings(
    viewModel: RadioViewModel,
    manage: ManageViewModel,
    onOpenDisplay: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val listUpdate by viewModel.defaultPlaylistUpdate.collectAsState()
    val updatePrefs by viewModel.updatePrefs.collectAsState()
    val stations by manage.stations.collectAsState()
    val defaultId by manage.defaultPlaylistId.collectAsState()

    val themeLabel = when (themeMode) {
        "dark" -> "Donker"
        "light" -> "Licht"
        else -> "Systeem"
    }
    val stationCount = stations.count { it.playlistId == defaultId }
    val newCount = (if (appUpdate != null) 1 else 0) + (if (listUpdate != null) 1 else 0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("Instellingen", onBack = null) }
        item {
            MenuCard(
                icon = Icons.Default.Palette,
                title = "Weergave",
                subtitle = "Thema en de visualizer van het liggende scherm",
                status = "Thema: $themeLabel · Visualizer",
                badge = null,
                onClick = onOpenDisplay
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Radio,
                title = "Zenders",
                subtitle = "Zenders en logo's beheren, afspeellijsten",
                status = "$stationCount zenders · ${playlists.size} afspeellijsten",
                badge = null,
                onClick = onOpenChannels
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Sync,
                title = "Updates",
                subtitle = "De app en de standaard zenderlijst",
                status = lastCheckedText(updatePrefs.checkedAt),
                badge = if (newCount > 0) "$newCount nieuw" else null,
                onClick = onOpenUpdates
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Info,
                title = "Over SceneRadio",
                subtitle = "Versie en privacy",
                status = "Versie ${AppVersion.NUMBER}",
                badge = null,
                onClick = onOpenAbout
            )
        }
        item {
            Text(
                text = "De onderwerpen staan op volgorde van gebruik. Bij Updates laat een kleine badge zien of er iets nieuws is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Weergave
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySettings(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    onOpenVisualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val largeControls by viewModel.largeControls.collectAsState()
    val visualizerSettings by viewModel.visualizerSettings.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SettingsHeader("Weergave", onBack) }

        item { GroupTitle("Thema") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kleurthema van de app",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("system" to "Systeem", "dark" to "Donker", "light" to "Licht").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }

        item { GroupTitle("Bediening") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Grote bediening",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Grotere tikvlakken, groter menu onderin en een zichtbaar sterretje op elke zender. Handig op een auto-radio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = largeControls,
                            onCheckedChange = { viewModel.setLargeControls(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Staat vanzelf aan op een apparaat dat zichzelf als auto meldt; je kunt hem hier altijd zelf aan of uit zetten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { GroupTitle("Speler") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hoes in de speler",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Toont de hoes van het nummer groot in de speler, met het zenderlogo als ronde badge. Tik op de hoes voor volledig scherm. Is er geen hoes, dan blijft het zenderlogo groot staan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = visualizerSettings.coverInPlayer,
                            onCheckedChange = { on -> viewModel.updateVisualizerSettings { it.copy(coverInPlayer = on) } }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "De hoes wordt via internet opgezocht: alleen artiest en titel gaan naar MusicBrainz en Cover Art Archive. Staat standaard uit. De positie van de hoes en de foto van de artiest stel je in bij Visualizer > Hoes van het nummer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { GroupTitle("Visualizer") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                onClick = onOpenVisualizer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Visualizer",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Snelheid, grootte, kleuren, sterren, hoes, equalizer, slogans en dimmen van het liggende scherm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Zenders
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSettings(
    viewModel: RadioViewModel,
    manage: ManageViewModel,
    onBack: () -> Unit,
    onOpenStreams: () -> Unit,
    onOpenLogos: () -> Unit,
    onOpenStations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val stations by manage.stations.collectAsState()
    val defaultId by manage.defaultPlaylistId.collectAsState()
    val count = stations.count { it.playlistId == defaultId }
    val favorites by viewModel.favoriteStations.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExport by remember { mutableStateOf(false) }

    // De melding na opslaan of delen verdwijnt na een paar seconden vanzelf.
    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            delay(8_000)
            viewModel.exportMessage.value = null
        }
    }

    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistToClear by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SettingsHeader("Zenders", onBack) }

        exportMessage?.let { message ->
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        item { GroupTitle("Zenders beheren") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    HubRow(Icons.Default.Add, "Zender toevoegen of bewerken", "Naam, stream, groep en logo", false, onOpenStations)
                    HubRow(Icons.Default.Image, "Logo's beheren", "Eén logo aan meerdere zenders koppelen", false, onOpenLogos)
                    HubRow(Icons.Default.GraphicEq, "Streams controleren", "Test alle zenders op deze telefoon", false, onOpenStreams)
                    HubRow(Icons.Default.Download, "Zenderlijst downloaden (.m3u)", "Favorieten, standaardlijst of een geïmporteerde lijst", false) { showExport = true }
                }
            }
        }

        item { GroupTitle("Standaard zenderlijst") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${DefaultPlaylistSource.PLAYLIST_NAME} · $count zenders",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Repository ${DefaultPlaylistSource.REPO}. Bijwerken en controleren staat onder Updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(
                text = "Wijzigingen aan zenders en logo's blijven op dit toestel. Een update van de standaardlijst zet de naam, de stream en de genres van zenders uit die lijst weer terug; zenders die je zelf toevoegt en logo's die je zelf kiest blijven staan.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Afspeellijsten beheer (hernoemen, verwijderen, legen)
        item { GroupTitle("Geïmporteerde afspeellijsten (${playlists.size})") }

        if (playlists.isEmpty()) {
            item {
                Text(
                    text = "Nog geen afspeellijsten geïmporteerd.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(playlists, key = { it.id }) { pl ->
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${pl.stationCount} zenders • ${if (pl.isLocalFile) "Lokaal bestand" else "URL / Online"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Hernoemen
                        IconButton(onClick = {
                            playlistToRename = pl
                            renameText = pl.name
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Hernoemen")
                        }

                        // Legen (alle zenders in de lijst wissen)
                        IconButton(onClick = {
                            playlistToClear = pl
                        }) {
                            Icon(Icons.Default.CleaningServices, contentDescription = "Lijst legen")
                        }

                        // Verwijderen
                        IconButton(onClick = {
                            playlistToDelete = pl
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Verwijderen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // Zenderlijst als .m3u bewaren in Downloads of delen met een andere app
    if (showExport) {
        val choices = buildList {
            add(ExportChoice(null, "Favorieten", "${favorites.size} zenders", "favorieten"))
            playlists.sortedByDescending { it.id == defaultId }.forEach { pl ->
                val isDefault = pl.id == defaultId
                add(
                    ExportChoice(
                        id = pl.id,
                        title = if (isDefault) "Standaard zenderlijst" else pl.name,
                        subtitle = "${pl.stationCount} zenders" + if (isDefault) " · ${pl.name}" else "",
                        label = pl.name
                    )
                )
            }
        }
        M3uExportDialog(
            choices = choices,
            onDismiss = { showExport = false },
            onSave = { choice ->
                showExport = false
                viewModel.saveM3uToDownloads(choice.id, choice.label)
            },
            onShare = { choice ->
                showExport = false
                scope.launch {
                    val intent = viewModel.m3uShareIntent(choice.id, choice.label)
                    if (intent != null) {
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            viewModel.exportMessage.value = "Delen mislukt: ${e.message ?: "geen app gevonden"}"
                        }
                    }
                }
            }
        )
    }

    // Hernoemen dialoog
    if (playlistToRename != null) {
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Afspeellijst Hernoemen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nieuwe naam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToRename?.let { viewModel.renamePlaylist(it.id, renameText.trim()) }
                        playlistToRename = null
                    }
                ) {
                    Text("Opslaan")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Annuleren")
                }
            }
        )
    }

    // Legen bevestiging
    if (playlistToClear != null) {
        AlertDialog(
            onDismissRequest = { playlistToClear = null },
            title = { Text("Afspeellijst Legen") },
            text = {
                Text("Weet je zeker dat je alle zenders uit '${playlistToClear?.name}' wilt wissen? De lege afspeellijst blijft bewaard.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToClear?.let { viewModel.clearPlaylist(it) }
                        playlistToClear = null
                    }
                ) {
                    Text("Lijst Legen")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToClear = null }) {
                    Text("Annuleren")
                }
            }
        )
    }

    // Verwijderen bevestiging
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Afspeellijst Verwijderen") },
            text = {
                Text("Weet je zeker dat je de afspeellijst '${playlistToDelete?.name}' en alle bijbehorende zenders wilt verwijderen?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it) }
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Verwijderen")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Annuleren")
                }
            }
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Updates: de app en de standaard zenderlijst, alles bij elkaar
// ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesSettings(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlistStatus by viewModel.defaultPlaylistStatus.collectAsState()
    val isChecking by viewModel.isCheckingPlaylist.collectAsState()
    val isApplying by viewModel.isApplyingPlaylist.collectAsState()
    val listPrefs by viewModel.defaultPlaylistPrefs.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SettingsHeader("Updates", onBack) }

        // De app: handmatig of automatisch controleren op een nieuwe versie
        updateSettingsItems(viewModel, "De app")

        // Standaard zenderlijst (openbare GitHub-repository)
        item { GroupTitle("Standaard zenderlijst") }
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
                        text = "Nederlandse radio",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (listPrefs.updatedAt > 0L) {
                            "Laatst bijgewerkt: " + java.text.DateFormat
                                .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                .format(java.util.Date(listPrefs.updatedAt))
                        } else {
                            "Nog niet gedownload."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    playlistStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Eén knop: kijkt of er een nieuwe lijst is en past hem meteen toe. Grijs zolang er iets bezig is.
                        Button(
                            onClick = { viewModel.refreshDefaultPlaylistAndApply() },
                            enabled = !isChecking && !isApplying
                        ) {
                            Text(
                                when {
                                    isApplying -> "Bezig met bijwerken…"
                                    isChecking -> "Controleren…"
                                    else -> "Controleren en bijwerken"
                                }
                            )
                        }
                    }
                }
            }
        }

    }
}

// ------------------------------------------------------------------------------------------------
// Over
// ------------------------------------------------------------------------------------------------

@Composable
private fun AboutSettings(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SettingsHeader("Over SceneRadio", onBack) }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SceneRadio",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Versie ${AppVersion.NUMBER} · build ${AppVersion.BUILD}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Privacy en lokale werking
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Geen trackers, geen reclame, geen account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SceneRadio bevat geen trackers, geen Firebase, geen reclame en vraagt geen account. Zenders, favorieten en instellingen blijven op dit apparaat. " +
                            "De app maakt alleen verbinding met de streams van de zenders zelf, met GitHub (de zenderlijst, de logo's en updates; GitHub ziet daarbij je IP-adres), " +
                            "met Radio Browser als je daar zoekt, en met de bronnen voor hoezen en foto's als je die zelf aanzet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bronnen van hoezen en foto's
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bronnen van hoezen en foto's",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hoezen: MusicBrainz en Cover Art Archive. Foto's van artiesten: Wikimedia Commons (via MusicBrainz en Wikidata). " +
                            "Elke hoes en elke foto is van zijn maker of rechthebbende; Wikimedia Commons-foto's hebben elk een eigen licentie. " +
                            "Er wordt alleen opgezocht als je dat in de instellingen aanzet, en dan gaan alleen artiest, titel of de naam van de artiest over de lijn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Logboek: wat de app deed, om fouten op te sporen
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HubRow(
                        icon = Icons.Default.Description,
                        title = "Logboek",
                        subtitle = "Wat de app deed, ook na een crash. Om fouten op te sporen.",
                        busy = false,
                        onClick = onOpenLog
                    )
                }
            }
        }
        item {
            Text(
                text = "Het logboek staat alleen op dit toestel en wordt nooit vanzelf verstuurd. Wachtwoorden en sleutels komen er niet in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** Een keuze in het downloadvenster: id = null betekent Favorieten, anders de id van een afspeellijst. */
private data class ExportChoice(val id: Long?, val title: String, val subtitle: String, val label: String) {
    val key: Long get() = id ?: -1L
}

@Composable
private fun M3uExportDialog(
    choices: List<ExportChoice>,
    onDismiss: () -> Unit,
    onSave: (ExportChoice) -> Unit,
    onShare: (ExportChoice) -> Unit
) {
    var selectedKey by remember { mutableStateOf(choices.first().key) }
    val selected = choices.firstOrNull { it.key == selectedKey } ?: choices.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zenderlijst downloaden") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Kies een lijst en bewaar hem als .m3u-bestand in Downloads, of deel hem met een andere app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                choices.forEach { choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = choice.key }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = choice.key == selectedKey,
                            onClick = { selectedKey = choice.key }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(choice.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = choice.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected) }) { Text("Opslaan in Downloads") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Annuleren") }
                TextButton(onClick = { onShare(selected) }) { Text("Delen") }
            }
        }
    )
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    busy: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
