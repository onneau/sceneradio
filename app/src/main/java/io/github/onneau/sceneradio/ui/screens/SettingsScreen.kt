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
import io.github.onneau.sceneradio.data.backup.BackupInfo
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.ui.components.lastCheckedText
import io.github.onneau.sceneradio.ui.components.updateSettingsItems
import io.github.onneau.sceneradio.ui.ManageViewModel
import io.github.onneau.sceneradio.ui.RadioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.onneau.sceneradio.i18n.Lang
import io.github.onneau.sceneradio.i18n.tr

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
private const val PAGE_BACKUP = "backup"
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
        PAGE_BACKUP -> BackupScreen(
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
            onOpenBackup = { page = PAGE_BACKUP },
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Terug", "Back"))
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
internal fun GroupTitle(text: String) {
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
    onOpenBackup: () -> Unit,
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

    val context = LocalContext.current
    var showLanguage by remember { mutableStateOf(false) }
    // Opnieuw gelezen bij elke keer dat dit scherm in beeld komt (na het terugkeren uit het scherm Back-up).
    val backupMadeAt = remember { BackupInfo.lastMadeAt(context) }
    val backupSeen = remember { BackupInfo.seen(context) }

    val themeLabel = when (themeMode) {
        "dark" -> tr("Donker", "Dark")
        "light" -> tr("Licht", "Light")
        else -> tr("Systeem", "System")
    }
    val stationCount = stations.count { it.playlistId == defaultId }
    val newCount = (if (appUpdate != null) 1 else 0) + (if (listUpdate != null) 1 else 0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader(tr("Instellingen", "Settings"), onBack = null) }
        item {
            MenuCard(
                icon = Icons.Default.Language,
                title = "Taal / Language",
                subtitle = tr("Kies Nederlands of English", "Choose Dutch or English"),
                status = languageStatus(),
                badge = if (Lang.hintSeen) null else tr("nieuw", "new"),
                onClick = {
                    Lang.markHintSeen(context)
                    showLanguage = true
                }
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Palette,
                title = tr("Weergave", "Display"),
                subtitle = tr("Thema en de visualizer van het liggende scherm", "Theme and the landscape-screen visualizer"),
                status = tr("Thema: $themeLabel · Visualizer", "Theme: $themeLabel · Visualizer"),
                badge = null,
                onClick = onOpenDisplay
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Radio,
                title = tr("Zenders", "Stations"),
                subtitle = tr("Zenders en logo's beheren, afspeellijsten", "Manage stations and logos, playlists"),
                status = tr("$stationCount zenders · ${playlists.size} afspeellijsten", "$stationCount stations · ${playlists.size} playlists"),
                badge = null,
                onClick = onOpenChannels
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Sync,
                title = "Updates",
                subtitle = tr("De app en de standaard zenderlijst", "The app and the default station list"),
                status = lastCheckedText(updatePrefs.checkedAt),
                badge = if (newCount > 0) tr("$newCount nieuw", "$newCount new") else null,
                onClick = onOpenUpdates
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Backup,
                title = tr("Back-up", "Backup"),
                subtitle = tr("Instellingen en eigen wijzigingen bewaren of terugzetten", "Save or restore your settings and own changes"),
                status = backupStatus(backupMadeAt),
                badge = if (backupSeen) null else tr("nieuw", "new"),
                onClick = onOpenBackup
            )
        }
        item {
            MenuCard(
                icon = Icons.Default.Info,
                title = tr("Over SceneRadio", "About SceneRadio"),
                subtitle = tr("Versie en privacy", "Version and privacy"),
                status = tr("Versie ${AppVersion.NUMBER}", "Version ${AppVersion.NUMBER}"),
                badge = null,
                onClick = onOpenAbout
            )
        }
        item {
            Text(
                text = tr("De onderwerpen staan op volgorde van gebruik. Bij Updates laat een kleine badge zien of er iets nieuws is.", "The topics are in order of use. On Updates, a small badge shows whether there is something new."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
}

private fun backupStatus(madeAt: Long): String =
    if (madeAt > 0L) {
        val date = java.text.DateFormat
            .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, Lang.locale)
            .format(java.util.Date(madeAt))
        tr("Laatste back-up: $date", "Last backup: $date")
    } else {
        tr("Nog geen back-up gemaakt", "No backup made yet")
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
        item { SettingsHeader(tr("Weergave", "Display"), onBack) }

        item { GroupTitle(tr("Thema", "Theme")) }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = tr("Kleurthema van de app", "Color theme of the app"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("system" to tr("Systeem", "System"), "dark" to tr("Donker", "Dark"), "light" to tr("Licht", "Light")).forEach { (mode, label) ->
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

        item { GroupTitle(tr("Bediening", "Controls")) }
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
                                text = tr("Grote bediening", "Large controls"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = tr("Grotere tikvlakken, groter menu onderin en een zichtbaar sterretje op elke zender. Handig op een auto-radio.", "Larger tap targets, a larger menu at the bottom and a visible star on every station. Handy on a car radio."),
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
                        text = tr("Staat vanzelf aan op een apparaat dat zichzelf als auto meldt; je kunt hem hier altijd zelf aan of uit zetten.", "Turns on by itself on a device that reports itself as a car; you can always switch it on or off here."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { GroupTitle(tr("Speler", "Player")) }
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
                                text = tr("Hoes in de speler", "Cover in the player"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = tr("Toont de hoes van het nummer groot in de speler, met het zenderlogo als ronde badge. Tik op de hoes voor volledig scherm. Is er geen hoes, dan blijft het zenderlogo groot staan.", "Shows the track's cover large in the player, with the station logo as a round badge. Tap the cover for full screen. If there is no cover, the station logo stays large."),
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
                        text = tr("De hoes wordt via internet opgezocht: alleen artiest en titel gaan naar MusicBrainz en Cover Art Archive. Staat standaard uit. De positie van de hoes en de foto van de artiest stel je in bij Visualizer > Hoes van het nummer.", "The cover is looked up online: only artist and title go to MusicBrainz and Cover Art Archive. Off by default. You set the cover position and the artist photo under Visualizer > Track cover."),
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
                            text = tr("Snelheid, grootte, kleuren, sterren, hoes, equalizer, slogans en dimmen van het liggende scherm", "Speed, size, colors, stars, cover, equalizer, slogans and dimming of the landscape screen"),
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
        item { SettingsHeader(tr("Zenders", "Stations"), onBack) }

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

        item { GroupTitle(tr("Zenders beheren", "Manage stations")) }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    HubRow(Icons.Default.Add, tr("Zender toevoegen of bewerken", "Add or edit station"), tr("Naam, stream, groep en logo", "Name, stream, group and logo"), false, onOpenStations)
                    HubRow(Icons.Default.Image, tr("Logo's beheren", "Manage logos"), tr("Eén logo aan meerdere zenders koppelen", "Link one logo to several stations"), false, onOpenLogos)
                    HubRow(Icons.Default.GraphicEq, tr("Streams controleren", "Check streams"), tr("Test alle zenders op deze telefoon", "Test all stations on this phone"), false, onOpenStreams)
                    HubRow(Icons.Default.Download, tr("Zenderlijst downloaden (.m3u)", "Download station list (.m3u)"), tr("Favorieten, standaardlijst of een geïmporteerde lijst", "Favorites, default list or an imported list"), false) { showExport = true }
                }
            }
        }

        item { GroupTitle(tr("Standaard zenderlijst", "Default station list")) }
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
                        text = tr("${DefaultPlaylistSource.PLAYLIST_NAME} · $count zenders", "${DefaultPlaylistSource.PLAYLIST_NAME} · $count stations"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tr("Repository ${DefaultPlaylistSource.REPO}. Bijwerken en controleren staat onder Updates.", "Repository ${DefaultPlaylistSource.REPO}. Updating and checking are under Updates."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(
                text = tr("Wijzigingen aan zenders en logo's blijven op dit toestel. Een update van de standaardlijst zet de naam, de stream en de genres van zenders uit die lijst weer terug; zenders die je zelf toevoegt en logo's die je zelf kiest blijven staan.", "Changes to stations and logos stay on this device. An update of the default list restores the name, stream and genres of stations from that list; stations you add yourself and logos you choose yourself are kept."),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Afspeellijsten beheer (hernoemen, verwijderen, legen)
        item { GroupTitle(tr("Geïmporteerde afspeellijsten (${playlists.size})", "Imported playlists (${playlists.size})")) }

        if (playlists.isEmpty()) {
            item {
                Text(
                    text = tr("Nog geen afspeellijsten geïmporteerd.", "No playlists imported yet."),
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
                                text = tr("${pl.stationCount} zenders • ${if (pl.isLocalFile) "Lokaal bestand" else "URL / Online"}", "${pl.stationCount} stations • ${if (pl.isLocalFile) "Local file" else "URL / Online"}"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Hernoemen
                        IconButton(onClick = {
                            playlistToRename = pl
                            renameText = pl.name
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = tr("Hernoemen", "Rename"))
                        }

                        // Legen (alle zenders in de lijst wissen)
                        IconButton(onClick = {
                            playlistToClear = pl
                        }) {
                            Icon(Icons.Default.CleaningServices, contentDescription = tr("Lijst legen", "Empty list"))
                        }

                        // Verwijderen
                        IconButton(onClick = {
                            playlistToDelete = pl
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = tr("Verwijderen", "Delete"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // Zenderlijst als .m3u bewaren in Downloads of delen met een andere app
    if (showExport) {
        val choices = buildList {
            add(ExportChoice(null, tr("Favorieten", "Favorites"), tr("${favorites.size} zenders", "${favorites.size} stations"), "favorieten"))
            playlists.sortedByDescending { it.id == defaultId }.forEach { pl ->
                val isDefault = pl.id == defaultId
                add(
                    ExportChoice(
                        id = pl.id,
                        title = if (isDefault) tr("Standaard zenderlijst", "Default station list") else pl.name,
                        subtitle = tr("${pl.stationCount} zenders", "${pl.stationCount} stations") + if (isDefault) " · ${pl.name}" else "",
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
                            viewModel.exportMessage.value = tr("Delen mislukt: ${e.message ?: "geen app gevonden"}", "Sharing failed: ${e.message ?: "no app found"}")
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
            title = { Text(tr("Afspeellijst Hernoemen", "Rename Playlist")) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(tr("Nieuwe naam", "New name")) },
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
                    Text(tr("Opslaan", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text(tr("Annuleren", "Cancel"))
                }
            }
        )
    }

    // Legen bevestiging
    if (playlistToClear != null) {
        AlertDialog(
            onDismissRequest = { playlistToClear = null },
            title = { Text(tr("Afspeellijst Legen", "Empty Playlist")) },
            text = {
                Text(tr("Weet je zeker dat je alle zenders uit '${playlistToClear?.name}' wilt wissen? De lege afspeellijst blijft bewaard.", "Are you sure you want to remove all stations from '${playlistToClear?.name}'? The empty playlist is kept."))
            },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToClear?.let { viewModel.clearPlaylist(it) }
                        playlistToClear = null
                    }
                ) {
                    Text(tr("Lijst Legen", "Empty List"))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToClear = null }) {
                    Text(tr("Annuleren", "Cancel"))
                }
            }
        )
    }

    // Verwijderen bevestiging
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(tr("Afspeellijst Verwijderen", "Delete Playlist")) },
            text = {
                Text(tr("Weet je zeker dat je de afspeellijst '${playlistToDelete?.name}' en alle bijbehorende zenders wilt verwijderen?", "Are you sure you want to delete the playlist '${playlistToDelete?.name}' and all its stations?"))
            },
            confirmButton = {
                Button(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it) }
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(tr("Verwijderen", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(tr("Annuleren", "Cancel"))
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
        updateSettingsItems(viewModel, tr("De app", "The app"))

        // Standaard zenderlijst (openbare GitHub-repository)
        item { GroupTitle(tr("Standaard zenderlijst", "Default station list")) }
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
                        text = tr("Nederlandse radio", "Dutch radio"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (listPrefs.updatedAt > 0L) {
                            tr("Laatst bijgewerkt: ", "Last updated: ") + java.text.DateFormat
                                .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                .format(java.util.Date(listPrefs.updatedAt))
                        } else {
                            tr("Nog niet gedownload.", "Not downloaded yet.")
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
                                    isApplying -> tr("Bezig met bijwerken…", "Updating…")
                                    isChecking -> tr("Controleren…", "Checking…")
                                    else -> tr("Controleren en bijwerken", "Check and update")
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
        item { SettingsHeader(tr("Over SceneRadio", "About SceneRadio"), onBack) }

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
                        text = tr("Versie ${AppVersion.NUMBER} · build ${AppVersion.BUILD}", "Version ${AppVersion.NUMBER} · build ${AppVersion.BUILD}"),
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
                        text = tr("Geen trackers, geen reclame, geen account", "No trackers, no ads, no account"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr("SceneRadio bevat geen trackers, geen Firebase, geen reclame en vraagt geen account. Zenders, favorieten en instellingen blijven op dit apparaat. ", "SceneRadio contains no trackers, no Firebase, no ads and asks for no account. Stations, favorites and settings stay on this device. ") +
                            tr("De app maakt alleen verbinding met de streams van de zenders zelf, met GitHub (de zenderlijst, de logo's en updates; GitHub ziet daarbij je IP-adres), ", "The app only connects to the streams of the stations themselves, to GitHub (the station list, the logos and updates; GitHub sees your IP address in the process), ") +
                            tr("met Radio Browser als je daar zoekt, en met de bronnen voor hoezen en foto's als je die zelf aanzet.", "to Radio Browser when you search there, and to the sources for covers and photos when you turn those on yourself."),
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
                        text = tr("Bronnen van hoezen en foto's", "Sources of covers and photos"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr("Hoezen: MusicBrainz en Cover Art Archive. Foto's van artiesten: Wikimedia Commons (via MusicBrainz en Wikidata). ", "Covers: MusicBrainz and Cover Art Archive. Artist photos: Wikimedia Commons (via MusicBrainz and Wikidata). ") +
                            tr("Elke hoes en elke foto is van zijn maker of rechthebbende; Wikimedia Commons-foto's hebben elk een eigen licentie. ", "Every cover and every photo belongs to its creator or rights holder; Wikimedia Commons photos each have their own license. ") +
                            tr("Er wordt alleen opgezocht als je dat in de instellingen aanzet, en dan gaan alleen artiest, titel of de naam van de artiest over de lijn.", "Lookups only happen when you turn that on in settings, and then only the artist, the title or the artist's name are sent over the wire."),
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
                        title = tr("Logboek", "Log"),
                        subtitle = tr("Wat de app deed, ook na een crash. Om fouten op te sporen.", "What the app did, even after a crash. For tracking down problems."),
                        busy = false,
                        onClick = onOpenLog
                    )
                }
            }
        }
        item {
            Text(
                text = tr("Het logboek staat alleen op dit toestel en wordt nooit vanzelf verstuurd. Wachtwoorden en sleutels komen er niet in.", "The log is only on this device and is never sent by itself. Passwords and keys are not included."),
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
        title = { Text(tr("Zenderlijst downloaden", "Download station list")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = tr("Kies een lijst en bewaar hem als .m3u-bestand in Downloads, of deel hem met een andere app.", "Choose a list and save it as an .m3u file in Downloads, or share it with another app."),
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
            Button(onClick = { onSave(selected) }) { Text(tr("Opslaan in Downloads", "Save to Downloads")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Annuleren", "Cancel")) }
                TextButton(onClick = { onShare(selected) }) { Text(tr("Delen", "Share")) }
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
