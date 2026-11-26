package io.github.onneau.sceneradio.ui.tv
import androidx.compose.material.icons.automirrored.filled.VolumeUp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.genreLabel
import io.github.onneau.sceneradio.ui.RadioViewModel
import io.github.onneau.sceneradio.i18n.tr

@Composable
fun TvScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val enabledStations by viewModel.enabledStations.collectAsState()
    val favoriteStations by viewModel.favoriteStations.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: Alle zenders, 1: Favorieten
    val displayedStations = if (selectedFilterIndex == 1) favoriteStations else enabledStations

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Donkere TV achtergrond
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Linker paneel: Navigatie & Zenderlijst (geen onderbalk)
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
        ) {
            // App titel & filter chips met zichtbare focusrand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "SceneRadio",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                TvFocusableChip(
                    text = tr("Alle zenders (${enabledStations.size})", "All stations (${enabledStations.size})"),
                    isSelected = selectedFilterIndex == 0,
                    onClick = { selectedFilterIndex = 0 }
                )

                TvFocusableChip(
                    text = tr("Favorieten (${favoriteStations.size})", "Favorites (${favoriteStations.size})"),
                    isSelected = selectedFilterIndex == 1,
                    onClick = { selectedFilterIndex = 1 }
                )
            }

            // Zenderlijst geschikt voor D-pad navigatie
            if (displayedStations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFilterIndex == 1) tr("Nog geen favorieten gemarkeerd.", "No favorites marked yet.") else tr("Nog geen zenders geïmporteerd.", "No stations imported yet."),
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = tr("Importeer een M3U-afspeellijst of zoek zenders via de telefoon-app.", "Import an M3U playlist or search for stations via the phone app."),
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E293B))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedStations, key = { it.id }) { station ->
                        val isCurrentPlaying = playbackState.currentStationId == station.id
                        TvStationItemRow(
                            station = station,
                            isPlaying = isCurrentPlaying && playbackState.isPlaying,
                            isCurrent = isCurrentPlaying,
                            onPlay = { viewModel.playStation(station) },
                            onToggleFavorite = { viewModel.setStationFavorite(station.id, !station.isFavorite) }
                        )
                    }
                }
            }
        }

        // Rechter paneel: Nu Afspelen & Grote TV Speler
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E293B))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Statusbadge bovenaan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                playbackState.isBuffering -> Color(0xFFF59E0B)
                                playbackState.isPlaying -> Color(0xFF10B981)
                                else -> Color(0xFF64748B)
                            }
                        )
                )
                Text(
                    text = when {
                        playbackState.isBuffering -> tr("BUFFEREN…", "BUFFERING…")
                        playbackState.isPlaying -> "LIVE RADIO"
                        playbackState.stationName.isNotBlank() -> tr("GEPAUZEERD", "PAUSED")
                        else -> tr("SELECTEER EEN STATION", "SELECT A STATION")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (playbackState.format.isNotBlank()) {
                    Text(
                        text = "• ${playbackState.format}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Grote TV Zendertegel / Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
            ) {
                if (!playbackState.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playbackState.logoUrl,
                        contentDescription = playbackState.stationName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0284C7), Color(0xFF0F766E))
                                )
                            )
                    ) {
                        Text(
                            text = playbackState.stationName.take(2).uppercase().ifBlank { "EW" },
                            fontSize = 64.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Metadata & Zendernaam
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = playbackState.stationName.ifBlank { "SceneRadio" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val trackInfo = when {
                    !playbackState.currentTrackTitle.isNullOrBlank() && !playbackState.currentArtist.isNullOrBlank() ->
                        "${playbackState.currentArtist} - ${playbackState.currentTrackTitle}"
                    !playbackState.currentTrackTitle.isNullOrBlank() -> playbackState.currentTrackTitle!!
                    !playbackState.currentArtist.isNullOrBlank() -> playbackState.currentArtist!!
                    else -> tr("Live uitzending", "Live broadcast")
                }

                Text(
                    text = trackInfo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                // Foutmelding weergave met D-pad knop
                AnimatedVisibility(visible = playbackState.errorMessage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            text = playbackState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TvActionButton(
                            icon = Icons.Default.Refresh,
                            label = tr("Opnieuw proberen", "Try again"),
                            onClick = { viewModel.retryPlayback() }
                        )
                    }
                }
            }

            // D-Pad knoppenbalk (Play, Vorige, Volgende)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvActionButton(
                    icon = Icons.Default.SkipPrevious,
                    label = tr("Vorige", "Previous"),
                    onClick = { viewModel.playPreviousStation() }
                )

                TvActionButton(
                    icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (playbackState.isPlaying) tr("Pauze", "Pause") else tr("Afspelen", "Play"),
                    onClick = { viewModel.togglePlayPause() },
                    isPrimary = true
                )

                TvActionButton(
                    icon = Icons.Default.SkipNext,
                    label = tr("Volgende", "Next"),
                    onClick = { viewModel.playNextStation() }
                )
            }
        }
    }
}

@Composable
fun TvStationItemRow(
    station: RadioStation,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isCurrent -> Color(0xFF1E3A8A)
            isFocused -> Color(0xFF334155)
            else -> Color(0xFF0F172A)
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onPlay() }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Klein logo of lettertegel
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
            ) {
                if (!station.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = station.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = station.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    color = Color.White,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!station.groupTitle.isNullOrBlank()) {
                    Text(
                        text = genreLabel(station.groupTitle),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            if (station.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = tr("Favoriet", "Favorite"),
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isPlaying) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = tr("Speelt nu af", "Now playing"),
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun TvFocusableChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isFocused -> Color(0xFF334155)
            else -> Color(0xFF1E293B)
        },
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = Color(0xFF38BDF8),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .focusable()
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TvActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isPrimary -> if (isFocused) Color(0xFF0284C7) else Color(0xFF0369A1)
            isFocused -> Color(0xFF475569)
            else -> Color(0xFF334155)
        },
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = Color(0xFF38BDF8),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .focusable()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
