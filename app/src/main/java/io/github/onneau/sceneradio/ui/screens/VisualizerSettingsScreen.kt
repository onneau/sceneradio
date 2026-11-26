package io.github.onneau.sceneradio.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.onneau.sceneradio.data.model.CopperMode
import io.github.onneau.sceneradio.data.model.CoverPosition
import io.github.onneau.sceneradio.data.model.CoverSize
import io.github.onneau.sceneradio.data.model.VisualizerSettings
import io.github.onneau.sceneradio.ui.RadioViewModel
import io.github.onneau.sceneradio.ui.visualizer.AmigaVisualizer
import io.github.onneau.sceneradio.ui.visualizer.shiftHue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import io.github.onneau.sceneradio.i18n.tr

private val CHANGED_COLOR = Color(0xFFFFB300)   // een aangepaste waarde springt eruit

private val RAINBOW = listOf(
    Color(0xFFFF4D4D), Color(0xFFFFB84D), Color(0xFFF5F54D), Color(0xFF4DFF6A), Color(0xFF4DFFE6),
    Color(0xFF4D8BFF), Color(0xFFB04DFF), Color(0xFFFF4DC4), Color(0xFFFF4D4D)
)

/**
 * Instellingen > Weergave > Visualizer: snelheid, grootte, kleuren, equalizer, slogan en dimmen van het liggende scherm
 * met sliders, met bovenaan een live voorbeeld. De lijst met slogans staat een laag dieper (SloganSettingsScreen).
 * Een streepje op de baan is de standaardwaarde; een aangepaste waarde is geel.
 */
@Composable
fun VisualizerSettingsScreen(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSlogans: () -> Unit = {}
) {
    val settings by viewModel.visualizerSettings.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val cover by viewModel.coverArt.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Speelt er iets, dan toont het voorbeeld de echte zender en het echte nummer; anders een voorbeeld.
    val hasTrack = !playbackState.currentArtist.isNullOrBlank() || !playbackState.currentTrackTitle.isNullOrBlank()
    val previewStation = playbackState.stationName.ifBlank { "Radio 538" }
    val previewArtist = if (hasTrack) playbackState.currentArtist else "Daft Punk"
    val previewTitle = if (hasTrack) playbackState.currentTrackTitle else "One More Time"

    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Terug", "Back"))
            }
            Text(
                text = "Visualizer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
    val preview: @Composable (Modifier) -> Unit = { previewModifier ->
        Box(
            previewModifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            AmigaVisualizer(
                artist = previewArtist,
                title = previewTitle,
                stationName = previewStation,
                isPlaying = true,
                settings = settings,
                // Alleen bij een echt nummer: het voorbeeldnummer heeft geen hoes.
                cover = if (hasTrack) cover else null
            )
        }
    }
    val update: ((VisualizerSettings) -> VisualizerSettings) -> Unit = { change ->
        viewModel.updateVisualizerSettings(change)
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                header()
                Spacer(modifier = Modifier.height(8.dp))
                preview(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                SliderSections(settings, update, onOpenSlogans) { viewModel.resetVisualizerSettings() }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            header()
            preview(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                SliderSections(settings, update, onOpenSlogans) { viewModel.resetVisualizerSettings() }
            }
        }
    }
}

@Composable
private fun SliderSections(
    settings: VisualizerSettings,
    update: ((VisualizerSettings) -> VisualizerSettings) -> Unit,
    onOpenSlogans: () -> Unit,
    onReset: () -> Unit
) {
    val d = remember { VisualizerSettings() }

    SectionTitle(tr("Snelheid", "Speed"))
    SliderRow(
        label = tr("Scroller-golf", "Scroller wave"), valueText = oneDecimal(settings.waveSpeed),
        value = settings.waveSpeed, range = VisualizerSettings.SPEED_RANGE, default = d.waveSpeed
    ) { v -> update { it.copy(waveSpeed = roundTo(v, 0.1f)) } }
    SliderRow(
        label = tr("Koperbalken", "Copper bars"), valueText = oneDecimal(settings.copperSpeed),
        value = settings.copperSpeed, range = VisualizerSettings.SPEED_RANGE, default = d.copperSpeed
    ) { v -> update { it.copy(copperSpeed = roundTo(v, 0.1f)) } }
    SliderRow(
        label = tr("Zendernaam (hellen en zwieren)", "Station name (tilt and sway)"), valueText = oneDecimal(settings.hazeSpeed),
        value = settings.hazeSpeed, range = VisualizerSettings.SPEED_RANGE, default = d.hazeSpeed
    ) { v -> update { it.copy(hazeSpeed = roundTo(v, 0.1f)) } }
    SliderRow(
        label = tr("Sterren", "Stars"), valueText = oneDecimal(settings.starSpeed) + "×",
        value = settings.starSpeed, range = VisualizerSettings.STAR_SPEED_RANGE, default = d.starSpeed
    ) { v -> update { it.copy(starSpeed = roundTo(v, 0.1f)) } }

    SectionTitle(tr("Grootte", "Size"))
    SliderRow(
        label = tr("Zendernaam", "Station name"), valueText = oneDecimal(settings.titleScale) + "×",
        value = settings.titleScale, range = VisualizerSettings.TITLE_SCALE_RANGE, default = d.titleScale
    ) { v -> update { it.copy(titleScale = roundTo(v, 0.05f)) } }
    SliderRow(
        label = tr("Scroller (deel van de naam)", "Scroller (part of the name)"), valueText = "${(settings.scrollerRatio * 100).roundToInt()} %",
        value = settings.scrollerRatio, range = VisualizerSettings.SCROLLER_RATIO_RANGE, default = d.scrollerRatio
    ) { v -> update { it.copy(scrollerRatio = roundTo(v, 0.05f)) } }
    SliderRow(
        label = "Slogan", valueText = "${settings.sloganPx} px",
        value = settings.sloganPx.toFloat(),
        range = VisualizerSettings.SLOGAN_PX_RANGE.first.toFloat()..VisualizerSettings.SLOGAN_PX_RANGE.last.toFloat(),
        default = d.sloganPx.toFloat(),
        steps = VisualizerSettings.SLOGAN_PX_RANGE.last - VisualizerSettings.SLOGAN_PX_RANGE.first - 1
    ) { v -> update { it.copy(sloganPx = v.roundToInt()) } }
    SliderRow(
        label = tr("Zendernaam-uitslag", "Station name swing"), valueText = oneDecimal(settings.hazeAmplitude) + " px",
        value = settings.hazeAmplitude, range = VisualizerSettings.HAZE_AMPLITUDE_RANGE, default = d.hazeAmplitude
    ) { v -> update { it.copy(hazeAmplitude = roundTo(v, 0.1f)) } }

    SectionTitle(tr("Kleuren", "Colors"))
    HueRow(tr("Koperbalken", "Copper bars"), settings.copperHue, Color(0xFFFFC22E)) { h -> update { it.copy(copperHue = h) } }
    Text(
        text = tr("Kleur van de koperbalken laten veranderen", "Let the color of the copper bars change"),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = settings.copperMode == CopperMode.OFF,
            onClick = { update { it.copy(copperMode = CopperMode.OFF) } },
            label = { Text(tr("Uit", "Off")) },
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = settings.copperMode == CopperMode.SUBTLE,
            onClick = { update { it.copy(copperMode = CopperMode.SUBTLE) } },
            label = { Text(tr("Subtiel", "Subtle")) },
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = settings.copperMode == CopperMode.ALL,
            onClick = { update { it.copy(copperMode = CopperMode.ALL) } },
            label = { Text(tr("Alle kleuren", "All colors")) },
            shape = RoundedCornerShape(12.dp)
        )
    }
    when (settings.copperMode) {
        CopperMode.OFF -> Text(
            text = tr("De koperbalken houden de gekozen tint.", "The copper bars keep the chosen hue."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        CopperMode.SUBTLE -> {
            Text(
                text = tr("De kleur schuift rustig heen en weer rond de gekozen tint.", "The color slowly drifts back and forth around the chosen hue."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            SliderRow(
                label = tr("Bereik", "Range"), valueText = "±${settings.copperDriftRange.roundToInt()}°",
                value = settings.copperDriftRange, range = VisualizerSettings.COPPER_DRIFT_RANGE_RANGE,
                default = d.copperDriftRange
            ) { v -> update { it.copy(copperDriftRange = v.roundToInt().toFloat()) } }
            SliderRow(
                label = tr("Een rondje duurt", "One round takes"), valueText = "${settings.copperDriftSeconds} s",
                value = settings.copperDriftSeconds.toFloat(),
                range = VisualizerSettings.COPPER_DRIFT_SECONDS_RANGE.first.toFloat()..VisualizerSettings.COPPER_DRIFT_SECONDS_RANGE.last.toFloat(),
                default = d.copperDriftSeconds.toFloat(),
                steps = (VisualizerSettings.COPPER_DRIFT_SECONDS_RANGE.last - VisualizerSettings.COPPER_DRIFT_SECONDS_RANGE.first) / 5 - 1
            ) { v -> update { it.copy(copperDriftSeconds = (v / 5f).roundToInt() * 5) } }
        }
        CopperMode.ALL -> {
            Text(
                text = tr("De kleur loopt langzaam door de hele regenboog, vanaf de gekozen tint.", "The color slowly runs through the whole rainbow, starting from the chosen hue."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(RAINBOW))
            )
            SliderRow(
                label = tr("Een rondje duurt", "One round takes"), valueText = formatDuration(settings.copperCycleSeconds),
                value = settings.copperCycleSeconds.toFloat(),
                range = VisualizerSettings.COPPER_CYCLE_SECONDS_RANGE.first.toFloat()..VisualizerSettings.COPPER_CYCLE_SECONDS_RANGE.last.toFloat(),
                default = d.copperCycleSeconds.toFloat(),
                steps = (VisualizerSettings.COPPER_CYCLE_SECONDS_RANGE.last - VisualizerSettings.COPPER_CYCLE_SECONDS_RANGE.first) / 5 - 1
            ) { v -> update { it.copy(copperCycleSeconds = (v / 5f).roundToInt() * 5) } }
            Text(
                text = tr("Instelbaar van 20 seconden tot 5 minuten.", "Adjustable from 20 seconds to 5 minutes."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HueRow("Equalizer", settings.equalizerHue, Color(0xFFF2475A)) { h -> update { it.copy(equalizerHue = h) } }
    HueRow(tr("Achtergrond en sterren", "Background and stars"), settings.backgroundHue, Color(0xFF3377FF)) { h ->
        update { it.copy(backgroundHue = h) }
    }
    Text(
        text = tr("De lettertypes (zendernaam, scroller, slogan) behouden hun eigen Amiga-kleuren.", "The fonts (station name, scroller, slogan) keep their own Amiga colors."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    SectionTitle(tr("Sterren", "Stars"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("Vonkelende sterren", "Sparkling stars"), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tr("De sterren flikkeren zacht en af en toe fonkelt er een met een kruisje.", "The stars twinkle softly and now and then one sparkles with a little cross."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = settings.sparklingStars,
            onCheckedChange = { on -> update { it.copy(sparklingStars = on) } }
        )
    }

    SectionTitle(tr("Hoes van het nummer", "Track cover"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("Hoes van het nummer tonen", "Show track cover"), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tr("Een hoesje in Amiga-stijl in de visualizer, en de hoes bij Android Auto en op het vergrendelscherm, zolang er een nummer met artiest en titel speelt.", "A cover in Amiga style in the visualizer, and the cover on Android Auto and on the lock screen, as long as a track with artist and title is playing."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = settings.showCover,
            onCheckedChange = { on -> update { it.copy(showCover = on) } }
        )
    }
    Text(
        text = tr("Alleen artiest en titel gaan naar de hoezen-dienst (MusicBrainz en Cover Art Archive). Hoezen blijven in een cache op je apparaat. Zonder hoes toont de app gewoon de balken. Staat standaard uit.", "Only artist and title go to the cover service (MusicBrainz and Cover Art Archive). Covers are kept in a cache on your device. Without a cover the app simply shows the bars. Off by default."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Text(
        text = tr("Grootte van de hoes in de visualizer", "Cover size in the visualizer"),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(CoverSize.SMALL to tr("Klein", "Small"), CoverSize.MEDIUM to tr("Middel", "Medium"), CoverSize.LARGE to tr("Groot", "Large")).forEach { (size, label) ->
            FilterChip(
                selected = settings.coverSize == size,
                onClick = { update { it.copy(coverSize = size) } },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
    Text(
        text = tr("Klein: midden in de koperbalken (zoals voorheen). Middel: 1,4 × zo groot, ook in de koperbalken. Groot: 1,9 × zo groot, links onder het spiegelbeeld van de scroller; maak je de scroller of de zendernaam groter, dan wordt Groot vanzelf wat kleiner.", "Small: in the middle of the copper bars (as before). Medium: 1.4 × as big, also in the copper bars. Large: 1.9 × as big, bottom left below the mirror image of the scroller; if you make the scroller or the station name bigger, Large automatically gets a bit smaller."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Text(
        text = tr("Positie van de hoes", "Cover position"),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            CoverPosition.DEFAULT to tr("Standaard", "Default"),
            CoverPosition.LEFT to tr("Links", "Left"),
            CoverPosition.CENTER to tr("Midden", "Center"),
            CoverPosition.RIGHT to tr("Rechts", "Right")
        ).forEach { (position, label) ->
            FilterChip(
                selected = settings.coverPosition == position,
                onClick = { update { it.copy(coverPosition = position) } },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
    Text(
        text = tr("Geldt voor de hoes in de visualizer én in de speler. Standaard is zoals voorheen: in de visualizer Klein en Middel in het midden en Groot links; in de speler staand in het midden en liggend links. Staand wordt de hoes bij Links of Rechts wat kleiner.", "Applies to the cover in the visualizer and in the player. Default is as before: in the visualizer Small and Medium in the center and Large on the left; in the player centered in portrait and on the left in landscape. In portrait the cover gets a bit smaller with Left or Right."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("Foto van de artiest als er geen hoes is", "Artist photo when there is no cover"), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tr("Is er geen hoes (ook niet met artiest en titel omgewisseld), dan toont de app overal waar de hoes verschijnt (visualizer, speler, vergrendelscherm en Android Auto, elk volgens zijn eigen schakelaar) een foto van de artiest. De foto komt uit Wikimedia Commons; alleen bij een precies passende naam.", "If there is no cover (not even with artist and title swapped), the app shows a photo of the artist everywhere the cover appears (visualizer, player, lock screen and Android Auto, each according to its own switch). The photo comes from Wikimedia Commons; only for an exactly matching name."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = settings.artistPhoto,
            onCheckedChange = { on -> update { it.copy(artistPhoto = on) } }
        )
    }
    Text(
        text = tr("Alleen de naam van de artiest gaat naar MusicBrainz, Wikidata en Wikimedia Commons. Foto's blijven in een cache op je apparaat. De foto's zijn van hun makers; zie Instellingen > Over SceneRadio. Staat standaard uit.", "Only the artist's name goes to MusicBrainz, Wikidata and Wikimedia Commons. Photos are kept in a cache on your device. The photos belong to their creators; see Settings > About SceneRadio. Off by default."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    SectionTitle("Equalizer")
    SliderRow(
        label = tr("Gevoeligheid", "Sensitivity"), valueText = "${(settings.equalizerSensitivity * 100).roundToInt()} %",
        value = settings.equalizerSensitivity, range = VisualizerSettings.EQ_SENSITIVITY_RANGE,
        default = d.equalizerSensitivity
    ) { v -> update { it.copy(equalizerSensitivity = roundTo(v, 0.05f)) } }
    Text(
        text = tr("Lager = rustiger, alleen de pieken komen hoog. Speel muziek af: het voorbeeld hierboven laat dan de echte balken zien.", "Lower = calmer, only the peaks go high. Play music: the preview above then shows the real bars."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SectionTitle("Slogan")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !settings.sloganColorFixed,
            onClick = { update { it.copy(sloganColorFixed = false) } },
            label = { Text(tr("Kleurencyclus", "Color cycle")) },
            shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
            selected = settings.sloganColorFixed,
            onClick = { update { it.copy(sloganColorFixed = true) } },
            label = { Text(tr("Vaste kleur", "Fixed color")) },
            shape = RoundedCornerShape(12.dp)
        )
    }
    if (settings.sloganColorFixed) {
        FixedHueRow(tr("Kleur van de slogan", "Slogan color"), settings.sloganHue) { h -> update { it.copy(sloganHue = h) } }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenSlogans)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Slogans", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when (settings.slogans.size) {
                        0 -> tr("Geen slogans: er wordt niets getoond", "No slogans: nothing is shown")
                        1 -> tr("1 slogan · aanpassen", "1 slogan · edit")
                        else -> tr("${settings.slogans.size} slogans · aanpassen", "${settings.slogans.size} slogans · edit")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }

    SectionTitle(tr("Dimmen", "Dimming"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr("Visualizer dimmen", "Dim visualizer"), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tr("Na een tijdje zonder aanraking wordt het beeld minder fel, tegen inbranden en om de ogen te sparen.", "After a while without touching, the picture becomes less bright, to prevent burn-in and to spare your eyes."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = settings.dimEnabled,
            onCheckedChange = { on -> update { it.copy(dimEnabled = on) } }
        )
    }
    if (settings.dimEnabled) {
        SliderRow(
            label = tr("Dimmen na", "Dim after"), valueText = "${settings.dimDelaySec} s",
            value = settings.dimDelaySec.toFloat(),
            range = VisualizerSettings.DIM_DELAY_RANGE.first.toFloat()..VisualizerSettings.DIM_DELAY_RANGE.last.toFloat(),
            default = d.dimDelaySec.toFloat(),
            steps = (VisualizerSettings.DIM_DELAY_RANGE.last - VisualizerSettings.DIM_DELAY_RANGE.first) / 5 - 1
        ) { v -> update { it.copy(dimDelaySec = (v / 5f).roundToInt() * 5) } }
        SliderRow(
            label = tr("Helderheid na dimmen", "Brightness after dimming"), valueText = "${(settings.dimLevel * 100).roundToInt()} %",
            value = settings.dimLevel, range = VisualizerSettings.DIM_LEVEL_RANGE, default = d.dimLevel
        ) { v -> update { it.copy(dimLevel = roundTo(v, 0.05f)) } }
        Text(
            text = tr("De tijd telt vanaf het moment dat de visualizer het hele scherm vult. Het menu verdwijnt na 7 seconden, dus het dimmen begint ongeveer 7 seconden later dan ingesteld, gerekend vanaf je laatste aanraking.", "The time counts from the moment the visualizer fills the whole screen. The menu disappears after 7 seconds, so dimming starts about 7 seconds later than set, counted from your last touch."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Alles terug naar standaard", "Reset everything to default"))
    }
    Text(
        text = tr("Je slogans blijven staan.", "Your slogans are kept."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    val changed = abs(value - default) > 0.001f
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (changed) CHANGED_COLOR else MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Streepje op de baan = de standaardwaarde.
                .drawBehind {
                    val inset = 10.dp.toPx()
                    val fraction = (default - range.start) / (range.endInclusive - range.start)
                    val x = inset + (size.width - 2 * inset) * fraction
                    drawLine(
                        color = tickColor,
                        start = Offset(x, size.height / 2 - 7.dp.toPx()),
                        end = Offset(x, size.height / 2 + 7.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
        ) {
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                steps = steps
            )
        }
    }
}

@Composable
private fun HueRow(
    label: String,
    hue: Float,
    baseColor: Color,
    onChange: (Float) -> Unit
) {
    val changed = abs(hue) > 0.5f
    val swatch = Color(shiftHue(baseColor.toArgb(), hue))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(swatch)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (changed) "${if (hue > 0) "+" else ""}${hue.roundToInt()}°" else tr("Standaard", "Default"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (changed) CHANGED_COLOR else MaterialTheme.colorScheme.primary
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Regenboogbaan achter een slider zonder eigen baan: de knop wijst de gedraaide tint aan.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(RAINBOW))
            )
            Slider(
                value = hue,
                onValueChange = { onChange(it.roundToInt().toFloat()) },
                valueRange = VisualizerSettings.HUE_RANGE,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

// Vaste kleur van de slogan: een regenboogbaan van 0 tot 360 graden (de tint zelf, niet een draaiing).
@Composable
private fun FixedHueRow(
    label: String,
    hue: Float,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.hsv(hue.coerceIn(0f, 360f), 0.85f, 1f))
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(RAINBOW))
            )
            Slider(
                value = hue,
                onValueChange = { onChange(it.roundToInt().toFloat()) },
                valueRange = VisualizerSettings.SLOGAN_HUE_RANGE,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

// 45 -> "45 s", 60 -> "1 min", 90 -> "1 min 30 s"
private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val sec = seconds % 60
    return when {
        m == 0 -> "$sec s"
        sec == 0 -> "$m min"
        else -> "$m min $sec s"
    }
}

private fun oneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun roundTo(value: Float, step: Float): Float = (value / step).roundToInt() * step
