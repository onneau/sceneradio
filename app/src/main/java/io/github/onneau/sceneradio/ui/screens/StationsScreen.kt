package io.github.onneau.sceneradio.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.ui.components.PlaylistUpdateBanner
import io.github.onneau.sceneradio.ui.components.StationLogo
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.ALL_GENRES
import io.github.onneau.sceneradio.data.model.genreLabel
import io.github.onneau.sceneradio.data.model.genreText
import io.github.onneau.sceneradio.data.model.genres
import io.github.onneau.sceneradio.ui.RadioViewModel
import io.github.onneau.sceneradio.ui.StationSort
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import io.github.onneau.sceneradio.i18n.tr

// Liggend scherm: de zenders als afgeronde tegels in een raster van 7 kolommen en 2 rijen, per pagina te swipen.
private const val GRID_COLUMNS = 7
private const val GRID_ROWS = 2
private const val GRID_PAGE_SIZE = GRID_COLUMNS * GRID_ROWS
private const val GRID_MAX_DOTS = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    viewModel: RadioViewModel,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    favoritesOnly: Boolean = false,
    // De scrollpositie wordt door de aanroeper bewaard, zodat je na een bezoek aan de speler terugkomt
    // waar je gebleven was.
    listState: LazyListState = rememberLazyListState(),
    // Liggend: de pagina van het raster wordt ook door de aanroeper bewaard.
    gridPage: Int = 0,
    onGridPageChange: (Int) -> Unit = {}
) {
    val allFiltered by viewModel.filteredStations.collectAsState()
    val favoriteFiltered by viewModel.filteredFavorites.collectAsState()
    val stations = if (favoritesOnly) favoriteFiltered else allFiltered
    val sortOrder by viewModel.sortOrder.collectAsState()
    val allStations by viewModel.allStations.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val playlistUpdate by viewModel.defaultPlaylistUpdate.collectAsState()
    val isApplyingPlaylist by viewModel.isApplyingPlaylist.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val largeControls by viewModel.largeControls.collectAsState()

    // Distinct groups / genres
    val groups = remember(allStations) {
        listOf(ALL_GENRES) + allStations.flatMap { it.genres() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    Scaffold(
        topBar = {
            if (landscape) Column {
                playlistUpdate?.takeIf { !favoritesOnly }?.let { update ->
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        PlaylistUpdateBanner(
                            isFirstImport = update.isFirstImport,
                            isBusy = isApplyingPlaylist,
                            onUpdate = { viewModel.applyDefaultPlaylistUpdate() },
                            onDismiss = { viewModel.dismissDefaultPlaylistUpdate() }
                        )
                    }
                }
                LandscapeTopBar(
                    searchQuery = searchQuery,
                    onSearch = { viewModel.searchQuery.value = it },
                    groups = if (!favoritesOnly && groups.size > 1) groups else emptyList(),
                    selectedGroup = selectedGroup,
                    onGroup = { viewModel.selectedGroup.value = it },
                    sortOrder = sortOrder,
                    onSort = { viewModel.setSortOrder(it) },
                    onAdd = if (favoritesOnly) null else onOpenImport
                )
            } else Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Nieuwe standaard zenderlijst beschikbaar
                playlistUpdate?.takeIf { !favoritesOnly }?.let { update ->
                    PlaylistUpdateBanner(
                        isFirstImport = update.isFirstImport,
                        isBusy = isApplyingPlaylist,
                        onUpdate = { viewModel.applyDefaultPlaylistUpdate() },
                        onDismiss = { viewModel.dismissDefaultPlaylistUpdate() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Zoekbalk met sorteermenu
                Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text(tr("Zoek op station of genre…", "Search by station or genre…")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = tr("Wissen", "Clear"))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                SortButton(sortOrder) { viewModel.setSortOrder(it) }
                }

                if (!favoritesOnly && groups.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    // Genre / groep filterchips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups) { grp ->
                            val isSelected = (grp == ALL_GENRES && selectedGroup == null) || (grp == selectedGroup)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectedGroup.value = if (grp == ALL_GENRES) null else grp
                                },
                                label = { Text(genreLabel(grp)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!favoritesOnly && !landscape) ExtendedFloatingActionButton(
                onClick = onOpenImport,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(tr("Lijst Toevoegen", "Add List")) },
                shape = RoundedCornerShape(16.dp)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (stations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when {
                            searchQuery.isNotEmpty() -> tr("Geen zenders gevonden voor '$searchQuery'", "No stations found for '$searchQuery'")
                            favoritesOnly -> tr("Nog geen favorieten", "No favorites yet")
                            else -> tr("Geen zenders in de lijst", "No stations in the list")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (favoritesOnly) {
                            tr("Tik bij Stations op de ster naast een zender om hem hier te bewaren.", "On Stations, tap the star next to a station to keep it here.")
                        } else {
                            tr("Tik op 'Lijst Toevoegen' om een M3U-afspeellijst te importeren via een URL, bestand, tekst of Radio Browser.", "Tap 'Add List' to import an M3U playlist via a URL, file, text or Radio Browser.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (landscape) {
            StationGrid(
                title = if (favoritesOnly) tr("Favorieten", "Favorites") else tr("Alle zenders", "All stations"),
                largeControls = largeControls,
                stations = stations,
                isCurrent = { playbackState.currentStationId == it.id },
                isPlaying = playbackState.isPlaying,
                // Terug naar pagina 1 zodra zoeken, filter of sortering verandert.
                resetKey = listOf(searchQuery, selectedGroup, sortOrder, favoritesOnly),
                initialPage = gridPage,
                onPageChange = onGridPageChange,
                onPlay = { viewModel.playStation(it) },
                onToggleFavorite = { viewModel.toggleStationFavorite(it) },
                onToggleVisibility = { viewModel.toggleStationVisibility(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(stations, key = { it.id }) { station ->
                    val isCurrentlyPlaying = playbackState.currentStationId == station.id

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCurrentlyPlaying) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else if (!station.isEnabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (station.isEnabled) {
                                    viewModel.playStation(station)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(if (largeControls) 16.dp else 12.dp)
                        ) {
                            // Zenderlogo, of tegel met beginletters als het logo ontbreekt of niet laadt
                            StationLogo(
                                logoUrl = station.logoUrl,
                                stationName = station.name,
                                size = if (largeControls) 58.dp else 46.dp,
                                shape = RoundedCornerShape(10.dp),
                                imageBackground = Color.White,
                                imagePadding = 4.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Naam & Genre
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = station.name,
                                    style = if (largeControls) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrentlyPlaying) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (station.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (!station.isEnabled) tr("Verborgen zender", "Hidden station") else station.genreText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!station.isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            // Afspeelindicator
                            if (isCurrentlyPlaying && playbackState.isPlaying) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = tr("Nu aan het spelen", "Now playing"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }

                            // Ster voor favorieten
                            IconButton(
                                onClick = { viewModel.toggleStationFavorite(station) },
                                modifier = if (largeControls) Modifier.size(56.dp) else Modifier
                            ) {
                                Icon(
                                    imageVector = if (station.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = tr("Favoriet", "Favorite"),
                                    tint = if (station.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = if (largeControls) Modifier.size(30.dp) else Modifier
                                )
                            }

                            // Oogknop verbergt een zender (ook bij vorige en volgende en auto catalogus)
                            IconButton(
                                onClick = { viewModel.toggleStationVisibility(station) },
                                modifier = if (largeControls) Modifier.size(56.dp) else Modifier
                            ) {
                                Icon(
                                    imageVector = if (station.isEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (station.isEnabled) tr("Verberg zender", "Hide station") else tr("Toon zender", "Show station"),
                                    tint = if (station.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                    modifier = if (largeControls) Modifier.size(30.dp) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortButton(sortOrder: StationSort, onSelect: (StationSort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = tr("Sorteren", "Sort"))
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            StationSort.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text((if (option == sortOrder) "✓ " else "") + option.label) },
                    onClick = {
                        onSelect(option)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

/**
 * Liggend is er weinig hoogte: zoeken, genrefilter, sorteren en (bij Stations) lijst toevoegen staan op één regel.
 * Zonder genres (Favorieten) neemt het zoekveld de ruimte.
 */
@Composable
private fun LandscapeTopBar(
    searchQuery: String,
    onSearch: (String) -> Unit,
    groups: List<String>,
    selectedGroup: String?,
    onGroup: (String?) -> Unit,
    sortOrder: StationSort,
    onSort: (StationSort) -> Unit,
    onAdd: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 4.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = tr("Zoek op station of genre…", "Search by station or genre…"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearch,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = tr("Wissen", "Clear"), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (groups.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1.4f)
            ) {
                items(groups) { grp ->
                    val isSelected = (grp == ALL_GENRES && selectedGroup == null) || (grp == selectedGroup)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGroup(if (grp == ALL_GENRES) null else grp) },
                        label = { Text(genreLabel(grp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        SortButton(sortOrder, onSort)
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = tr("Lijst toevoegen", "Add list"))
            }
        }
    }
}

/**
 * Het raster: 7 × 2 tegels per pagina, horizontaal te swipen. Tik = afspelen, lang indrukken = favoriet of
 * verbergen. Boven het raster staat de titel, onderaan de paginapuntjes (bij veel pagina's alleen "n / m").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationGrid(
    title: String,
    largeControls: Boolean,
    stations: List<RadioStation>,
    isCurrent: (RadioStation) -> Boolean,
    isPlaying: Boolean,
    resetKey: Any,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onPlay: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onToggleVisibility: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = max(1, ceil(stations.size / GRID_PAGE_SIZE.toFloat()).toInt())
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, pageCount - 1), pageCount = { pageCount })

    var lastKey by remember { mutableStateOf(resetKey) }
    LaunchedEffect(resetKey) {
        if (resetKey != lastKey) {
            lastKey = resetKey
            pagerState.scrollToPage(0)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val from = page * GRID_PAGE_SIZE
            val pageItems = stations.subList(min(from, stations.size), min(stations.size, from + GRID_PAGE_SIZE))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                for (row in 0 until GRID_ROWS) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        for (col in 0 until GRID_COLUMNS) {
                            val station = pageItems.getOrNull(row * GRID_COLUMNS + col)
                            if (station != null) {
                                StationTile(
                                    station = station,
                                    current = isCurrent(station),
                                    playing = isPlaying,
                                    largeControls = largeControls,
                                    onPlay = { onPlay(station) },
                                    onToggleFavorite = { onToggleFavorite(station) },
                                    onToggleVisibility = { onToggleVisibility(station) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            if (pageCount in 2..GRID_MAX_DOTS) {
                for (i in 0 until pageCount) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Eén tegel: een afgeronde vierkant (hoekstraal 20%) waar het logo de hele tegel vult, met zachte schaduw,
 * en de zendernaam links uitgelijnd eronder. De spelende zender heeft een gekleurde rand en een equalizer-icoontje.
 * Met "Grote bediening" staat er op elke tegel een tikbaar sterretje.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationTile(
    station: RadioStation,
    current: Boolean,
    playing: Boolean,
    largeControls: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val tileShape = RoundedCornerShape(percent = 20)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val pad = 4.dp
        val nameHeight = if (largeControls) 22.dp else 18.dp
        val side = minOf(maxWidth - pad * 2, maxHeight - nameHeight - 4.dp - pad * 2).coerceAtLeast(24.dp)

        Column(
            modifier = Modifier
                .width(side + pad * 2)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = { if (station.isEnabled) onPlay() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                )
                .padding(pad)
        ) {
            Box(modifier = Modifier.size(side)) {
                Surface(
                    shape = tileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (station.isEnabled) 3.dp else 0.dp,
                    border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxSize()
                ) {
                    StationLogo(
                        logoUrl = station.logoUrl,
                        stationName = station.name,
                        size = side,
                        shape = tileShape,
                        imageBackground = Color.White,
                        imagePadding = side * 0.08f,
                        modifier = Modifier.alpha(if (station.isEnabled) 1f else 0.4f)
                    )
                }

                // Spelend: equalizer-icoontje rechtsonder
                if (current && playing) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = tr("Nu aan het spelen", "Now playing"),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Verborgen zender: doorgestreept oog linksboven
                if (!station.isEnabled) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Icon(
                            Icons.Outlined.VisibilityOff,
                            contentDescription = tr("Verborgen zender", "Hidden station"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Sterretje rechtsboven: bij "Grote bediening" altijd zichtbaar en tikbaar, anders alleen bij favorieten
                if (largeControls) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { onToggleFavorite() }
                    ) {
                        Icon(
                            imageVector = if (station.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (station.isFavorite) tr("Uit favorieten halen", "Remove from favorites") else tr("Toevoegen aan favorieten", "Add to favorites"),
                            tint = if (station.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (station.isFavorite) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = tr("Favoriet", "Favorite"),
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = station.name,
                style = if (largeControls) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (station.isEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(nameHeight)
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (station.isFavorite) tr("Uit favorieten halen", "Remove from favorites") else tr("Toevoegen aan favorieten", "Add to favorites")) },
                leadingIcon = {
                    Icon(
                        if (station.isFavorite) Icons.Outlined.StarBorder else Icons.Default.Star,
                        contentDescription = null
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleFavorite()
                }
            )
            DropdownMenuItem(
                text = { Text(if (station.isEnabled) tr("Zender verbergen", "Hide station") else tr("Zender tonen", "Show station")) },
                leadingIcon = {
                    Icon(
                        if (station.isEnabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleVisibility()
                }
            )
        }
    }
}
