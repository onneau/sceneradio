package io.github.onneau.sceneradio.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.onneau.sceneradio.data.model.CoverPosition
import io.github.onneau.sceneradio.playback.PlaybackStateData
import io.github.onneau.sceneradio.ui.RadioViewModel
import io.github.onneau.sceneradio.ui.components.StationLogo
import io.github.onneau.sceneradio.ui.visualizer.AmigaVisualizer
import kotlinx.coroutines.delay
import io.github.onneau.sceneradio.i18n.tr

@Composable
fun PlayerScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier,
    immersive: Boolean = false
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val visualizerSettings by viewModel.visualizerSettings.collectAsState()
    val cover by viewModel.coverArt.collectAsState()
    val isPlaying = playbackState.isPlaying
    val isBuffering = playbackState.isBuffering
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Hoes in de speler (Instellingen > Weergave > Speler): alleen als hij aan staat en er een hoes is gevonden; anders blijft het zenderlogo.
    val playerCover: Bitmap? = if (visualizerSettings.coverInPlayer) cover else null
    var fullScreenCover by rememberSaveable { mutableStateOf(false) }

    // Dimmen: de visualizer vult het scherm; na de ingestelde tijd zonder aanraking (een aanraking haalt het menu
    // terug en dus ook deze toestand weg) komt er langzaam een zwarte laag overheen tot de gekozen helderheid.
    val showVisualizer = immersive && isLandscape
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(showVisualizer, visualizerSettings.dimEnabled, visualizerSettings.dimDelaySec) {
        dimmed = false
        if (showVisualizer && visualizerSettings.dimEnabled) {
            delay(visualizerSettings.dimDelaySec * 1000L)
            dimmed = true
        }
    }
    val dimAlpha by animateFloatAsState(
        targetValue = if (dimmed) 1f - visualizerSettings.dimLevel else 0f,
        animationSpec = tween(2000),
        label = "dim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.04f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Liggend en een tijdje niets aangeraakt: de menu's zijn weg (zie MainActivity) en de visualizer neemt het scherm over.
    Crossfade(
        targetState = showVisualizer,
        modifier = modifier.fillMaxSize(),
        animationSpec = tween(700),
        label = "visualizer"
    ) { visualizerVisible ->
        if (visualizerVisible) {
            Box(modifier = Modifier.fillMaxSize()) {
                AmigaVisualizer(
                    artist = playbackState.currentArtist,
                    title = playbackState.currentTrackTitle,
                    stationName = playbackState.stationName,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout),
                    settings = visualizerSettings,
                    cover = cover
                )
                // Zwarte laag voor het dimmen; de waarde wordt pas bij het tekenen gelezen, dus de animatie
                // laat niets opnieuw samenstellen.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind { drawRect(Color.Black.copy(alpha = dimAlpha)) }
                )
            }
        } else if (isLandscape && playerCover != null) {
            // Liggend met hoes (met het zenderlogo als badge). Standaard en Links: de hoes links, rechts de status, het nummer en de
            // bediening. Rechts: die kolom links en de hoes rechts. Midden: de bediening links, de hoes in het midden, de tekst rechts.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val artSize = minOf(220.dp, maxHeight - 24.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    val columnModifier = Modifier.weight(1f).fillMaxHeight()
                    when (visualizerSettings.coverPosition) {
                        CoverPosition.RIGHT -> {
                            LandscapeInfoColumn(viewModel, playbackState, isPlaying, isBuffering, withControls = true, modifier = columnModifier)
                            PlayerCover(
                                cover = playerCover,
                                playbackState = playbackState,
                                onClick = { fullScreenCover = true },
                                modifier = Modifier.size(artSize)
                            )
                        }
                        CoverPosition.CENTER -> {
                            LandscapeControlsColumn(viewModel, isPlaying, isBuffering, modifier = columnModifier)
                            PlayerCover(
                                cover = playerCover,
                                playbackState = playbackState,
                                onClick = { fullScreenCover = true },
                                modifier = Modifier.size(artSize)
                            )
                            LandscapeInfoColumn(viewModel, playbackState, isPlaying, isBuffering, withControls = false, modifier = columnModifier)
                        }
                        else -> {
                            PlayerCover(
                                cover = playerCover,
                                playbackState = playbackState,
                                onClick = { fullScreenCover = true },
                                modifier = Modifier.size(artSize)
                            )
                            LandscapeInfoColumn(viewModel, playbackState, isPlaying, isBuffering, withControls = true, modifier = columnModifier)
                        }
                    }
                }
            }
        } else if (isLandscape) {
            // Liggend: logo links, zender, artiest, titel en bediening rechts; scrollbaar voor kleine schermen.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    StatusBadge(playbackState)
                    Spacer(modifier = Modifier.height(12.dp))
                    LogoWithHalo(playbackState, haloSize = 168.dp, logoSize = 136.dp, pulseScale = pulseScale)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    NowPlaying(
                        playbackState = playbackState,
                        textAlign = TextAlign.Start,
                        nameStyle = MaterialTheme.typography.headlineSmall,
                        maxLines = 2
                    )
                    ErrorBox(playbackState, onRetry = { viewModel.retryPlayback() })
                    Spacer(modifier = Modifier.height(16.dp))
                    TransportControls(viewModel, isPlaying, isBuffering, arrangement = Arrangement.spacedBy(28.dp))
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // De hoes is hooguit 320 dp, en niet hoger dan ruim een derde van het scherm (voor kleine toestellen).
                val artSize = minOf(maxWidth - 48.dp, 320.dp, maxHeight * 0.42f)
                // Links of rechts wordt de hoes kleiner (70 % van de breedte) en schuift naar die kant, met de tekst mee.
                val position = visualizerSettings.coverPosition
                val sideCover = position == CoverPosition.LEFT || position == CoverPosition.RIGHT
                val coverSize = if (sideCover) minOf(artSize, (maxWidth - 48.dp) * 0.7f) else artSize
                val coverAlignment = when (position) {
                    CoverPosition.LEFT -> Alignment.Start
                    CoverPosition.RIGHT -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }
                val coverTextAlign = when (position) {
                    CoverPosition.LEFT -> TextAlign.Start
                    CoverPosition.RIGHT -> TextAlign.End
                    else -> TextAlign.Center
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusBadge(playbackState, modifier = Modifier.padding(top = 8.dp))

                    Column(
                        horizontalAlignment = if (playerCover != null) coverAlignment else Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (playerCover != null) {
                            PlayerCover(
                                cover = playerCover,
                                playbackState = playbackState,
                                onClick = { fullScreenCover = true },
                                modifier = Modifier.size(coverSize)
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                            NowPlayingWithCover(
                                playbackState = playbackState,
                                textAlign = coverTextAlign,
                                titleStyle = MaterialTheme.typography.headlineMedium,
                                maxLines = 1
                            )
                        } else {
                            LogoWithHalo(playbackState, haloSize = 230.dp, logoSize = 190.dp, pulseScale = pulseScale)
                            Spacer(modifier = Modifier.height(28.dp))
                            NowPlaying(
                                playbackState = playbackState,
                                textAlign = TextAlign.Center,
                                nameStyle = MaterialTheme.typography.headlineMedium,
                                maxLines = 1
                            )
                        }
                        ErrorBox(playbackState, onRetry = { viewModel.retryPlayback() })
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        TransportControls(viewModel, isPlaying, isBuffering, arrangement = Arrangement.SpaceEvenly)
                    }
                }
            }
        }
    }

    // Tik op de hoes: groot over het hele scherm, met artiest en titel. Tik ergens om te sluiten.
    if (fullScreenCover && playerCover != null) {
        FullScreenCover(cover = playerCover, playbackState = playbackState, onClose = { fullScreenCover = false })
    }
}

// Liggend met hoes: de kolom met de status, het nummer en (meestal) de bediening.
@Composable
private fun LandscapeInfoColumn(
    viewModel: RadioViewModel,
    playbackState: PlaybackStateData,
    isPlaying: Boolean,
    isBuffering: Boolean,
    withControls: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        StatusBadge(playbackState)
        Spacer(modifier = Modifier.height(12.dp))
        NowPlayingWithCover(
            playbackState = playbackState,
            textAlign = TextAlign.Start,
            titleStyle = MaterialTheme.typography.headlineSmall,
            maxLines = 2
        )
        ErrorBox(playbackState, onRetry = { viewModel.retryPlayback() })
        if (withControls) {
            Spacer(modifier = Modifier.height(16.dp))
            TransportControls(viewModel, isPlaying, isBuffering, arrangement = Arrangement.spacedBy(28.dp))
        }
    }
}

// Liggend met de hoes in het midden: de bediening staat alleen in de kolom links van de hoes.
@Composable
private fun LandscapeControlsColumn(
    viewModel: RadioViewModel,
    isPlaying: Boolean,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TransportControls(viewModel, isPlaying, isBuffering, arrangement = Arrangement.spacedBy(12.dp))
    }
}

// De hoes van het nummer, afgerond, met het zenderlogo als ronde badge op de hoek. Tik voor volledig scherm.
@Composable
private fun PlayerCover(
    cover: Bitmap,
    playbackState: PlaybackStateData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val image = remember(cover) { cover.asImageBitmap() }
    BoxWithConstraints(modifier = modifier) {
        val badge = (maxWidth * 0.26f).coerceIn(44.dp, 72.dp)
        Image(
            bitmap = image,
            contentDescription = tr("Hoes van het nummer, tik voor volledig scherm", "Track cover, tap for full screen"),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = badge * 0.2f, y = badge * 0.2f)
                .size(badge)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            StationLogo(
                logoUrl = playbackState.logoUrl,
                stationName = playbackState.stationName,
                size = badge - 6.dp
            )
        }
    }
}

// Met hoes: de titel van het nummer groot, daaronder de artiest en de zender.
@Composable
private fun NowPlayingWithCover(
    playbackState: PlaybackStateData,
    textAlign: TextAlign,
    titleStyle: androidx.compose.ui.text.TextStyle,
    maxLines: Int
) {
    val trackTitle = playbackState.currentTrackTitle?.takeIf { it.isNotBlank() }
    val trackArtist = playbackState.currentArtist?.takeIf { it.isNotBlank() }
    Text(
        text = trackTitle ?: playbackState.stationName.ifBlank { "SceneRadio" },
        style = titleStyle,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
    if (trackArtist != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = trackArtist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (playbackState.stationName.isNotBlank() && trackTitle != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = playbackState.stationName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Volledig scherm: de hoes zo groot mogelijk op zwart, met artiest en titel. Een tik ergens sluit hem weer.
@Composable
private fun FullScreenCover(cover: Bitmap, playbackState: PlaybackStateData, onClose: () -> Unit) {
    val image = remember(cover) { cover.asImageBitmap() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        ) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = tr("Hoes van het nummer", "Track cover"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) { FullScreenTexts(playbackState, TextAlign.Start) }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = tr("Hoes van het nummer", "Track cover"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    FullScreenTexts(playbackState, TextAlign.Center)
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = tr("Sluiten", "Close"), tint = Color.White)
            }
            Text(
                text = tr("Tik ergens om te sluiten", "Tap anywhere to close"),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7C8AA3),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}

// Status & formaat-badge ("LIVE RADIO • MP3")
@Composable
private fun StatusBadge(playbackState: PlaybackStateData, modifier: Modifier = Modifier) {
    val isPlaying = playbackState.isPlaying
    val isBuffering = playbackState.isBuffering
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isBuffering -> Color(0xFFF59E0B)
                            isPlaying -> Color(0xFF10B981)
                            else -> Color(0xFF94A3B8)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isBuffering -> tr("BUFFEREN…", "BUFFERING…")
                    isPlaying -> "LIVE RADIO"
                    playbackState.stationName.isNotBlank() -> tr("GEPAUZEERD", "PAUSED")
                    else -> tr("SELECTEER EEN STATION", "SELECT A STATION")
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (playbackState.format.isNotBlank() && playbackState.stationName.isNotBlank()) {
                Text(
                    text = " • ${playbackState.format}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Zenderlogo (of beginletters-tegel) met een zachte gloed eromheen
@Composable
private fun LogoWithHalo(
    playbackState: PlaybackStateData,
    haloSize: Dp,
    logoSize: Dp,
    pulseScale: Float
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(haloSize)
            .scale(pulseScale)
    ) {
        Box(
            modifier = Modifier
                .size(haloSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )
        StationLogo(
            logoUrl = playbackState.logoUrl,
            stationName = playbackState.stationName,
            size = logoSize
        )
    }
}

// Zendernaam met daaronder artiest en titel (ICY/ID3-metadata), op de plek van "Live Radio Uitzending"
@Composable
private fun NowPlaying(
    playbackState: PlaybackStateData,
    textAlign: TextAlign,
    nameStyle: androidx.compose.ui.text.TextStyle,
    maxLines: Int
) {
    Text(
        text = playbackState.stationName.ifBlank { "SceneRadio" },
        style = nameStyle,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )

    Spacer(modifier = Modifier.height(6.dp))

    val trackTitle = playbackState.currentTrackTitle?.takeIf { it.isNotBlank() }
    val trackArtist = playbackState.currentArtist?.takeIf { it.isNotBlank() }

    if (trackTitle != null || trackArtist != null) {
        if (trackArtist != null) {
            Text(
                text = trackArtist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trackTitle != null) {
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Text(
            text = if (playbackState.stationName.isNotBlank()) tr("Live Radio Uitzending", "Live Radio Broadcast") else tr("Geen zender actief", "No station active"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Foutmelding en Opnieuw-knop bij een dode stream
@Composable
private fun ErrorBox(playbackState: PlaybackStateData, onRetry: () -> Unit) {
    AnimatedVisibility(visible = playbackState.errorMessage != null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playbackState.errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(tr("Opnieuw", "Retry"))
                }
            }
        }
    }
}

// Vorig station, grote afspeelknop, volgend station
@Composable
private fun TransportControls(
    viewModel: RadioViewModel,
    isPlaying: Boolean,
    isBuffering: Boolean,
    arrangement: Arrangement.Horizontal
) {
    Row(
        modifier = if (arrangement == Arrangement.SpaceEvenly) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = { viewModel.playPreviousStation() },
            modifier = Modifier.size(54.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = tr("Vorig station", "Previous station"),
                modifier = Modifier.size(28.dp)
            )
        }

        Button(
            onClick = { viewModel.togglePlayPause() },
            shape = CircleShape,
            modifier = Modifier.size(76.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) tr("Pauze", "Pause") else tr("Afspelen", "Play"),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        FilledTonalIconButton(
            onClick = { viewModel.playNextStation() },
            modifier = Modifier.size(54.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = tr("Volgend station", "Next station"),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// De tekst bij de hoes op volledig scherm: titel, artiest en zender.
@Composable
private fun FullScreenTexts(playbackState: PlaybackStateData, align: TextAlign) {
    val trackTitle = playbackState.currentTrackTitle?.takeIf { it.isNotBlank() }
    val trackArtist = playbackState.currentArtist?.takeIf { it.isNotBlank() }
    if (trackTitle != null) {
        Text(
            text = trackTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (trackArtist != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = trackArtist,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (playbackState.stationName.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = playbackState.stationName + " · LIVE",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB0BCCF),
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
