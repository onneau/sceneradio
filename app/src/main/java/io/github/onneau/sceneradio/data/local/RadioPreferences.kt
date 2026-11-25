package io.github.onneau.sceneradio.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.onneau.sceneradio.data.model.CopperMode
import io.github.onneau.sceneradio.data.model.CoverPosition
import io.github.onneau.sceneradio.data.model.CoverSize
import io.github.onneau.sceneradio.data.model.VisualizerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Status van de standaard zenderlijst (versie, tijdstip van controle en update, genegeerde versie).
data class DefaultPlaylistPrefs(
    val sha: String = "",
    val dismissedSha: String = "",
    val checkedAt: Long = 0L,
    val updatedAt: Long = 0L
)

// Instellingen voor app-updates: automatisch controleren, wanneer voor het laatst, welke versie "Later" kreeg.
data class UpdatePrefs(
    val autoCheck: Boolean = true,
    val checkedAt: Long = 0L,
    val dismissedVersion: String = ""
)

val Context.radioDataStore by preferencesDataStore(name = "sceneradio_preferences")

class RadioPreferences(private val context: Context) {

    companion object {
        val KEY_LAST_STATION_ID = longPreferencesKey("last_station_id")
        val KEY_RECENT_STATIONS = stringPreferencesKey("recent_stations") // id's, komma-gescheiden, nieuwste eerst
        const val MAX_RECENT_STATIONS = 10
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // "system", "dark", "light"
        val KEY_STATION_SORT = stringPreferencesKey("station_sort")
        val KEY_DEFAULT_PLAYLIST_SHA = stringPreferencesKey("default_playlist_sha")
        val KEY_DEFAULT_PLAYLIST_DISMISSED = stringPreferencesKey("default_playlist_dismissed_sha")
        val KEY_DEFAULT_PLAYLIST_CHECKED_AT = longPreferencesKey("default_playlist_checked_at")
        val KEY_DEFAULT_PLAYLIST_UPDATED_AT = longPreferencesKey("default_playlist_updated_at")

        // App-updates
        val KEY_UPDATE_AUTO = booleanPreferencesKey("update_auto_check")
        val KEY_UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
        val KEY_UPDATE_DISMISSED = stringPreferencesKey("update_dismissed_version")

        // Visualizer-instellingen
        val KEY_VIS_WAVE_SPEED = floatPreferencesKey("vis_wave_speed")
        val KEY_VIS_COPPER_SPEED = floatPreferencesKey("vis_copper_speed")
        val KEY_VIS_HAZE_SPEED = floatPreferencesKey("vis_haze_speed")
        val KEY_VIS_STAR_SPEED = floatPreferencesKey("vis_star_speed")
        val KEY_VIS_TITLE_SCALE = floatPreferencesKey("vis_title_scale")
        val KEY_VIS_SCROLLER_RATIO = floatPreferencesKey("vis_scroller_ratio")
        val KEY_VIS_SLOGAN_PX = intPreferencesKey("vis_slogan_px")
        val KEY_VIS_HAZE_AMPLITUDE = floatPreferencesKey("vis_haze_amplitude")
        val KEY_VIS_COPPER_HUE = floatPreferencesKey("vis_copper_hue")
        val KEY_VIS_EQUALIZER_HUE = floatPreferencesKey("vis_equalizer_hue")
        val KEY_VIS_BACKGROUND_HUE = floatPreferencesKey("vis_background_hue")
        val KEY_VIS_SLOGAN_FIXED = booleanPreferencesKey("vis_slogan_fixed")
        val KEY_VIS_SLOGAN_HUE = floatPreferencesKey("vis_slogan_hue")
        val KEY_VIS_SLOGANS = stringPreferencesKey("vis_slogans") // één slogan per regel; ontbreekt = de standaardslogans
        val KEY_VIS_SLOGANS_VERSION = intPreferencesKey("vis_slogans_version") // ontbreekt = 1; 2 = met de tijd-slogan (1.25)
        val KEY_VIS_OWN_SLOGANS = stringPreferencesKey("vis_own_slogans")         // versie 1.17, alleen nog om te omzetten
        val KEY_VIS_ONLY_OWN_SLOGANS = booleanPreferencesKey("vis_only_own_slogans") // versie 1.17, idem
        val KEY_VIS_COPPER_DRIFT = booleanPreferencesKey("vis_copper_drift")  // versie 1.18 (aan/uit), alleen nog om te omzetten naar de stand
        val KEY_VIS_COPPER_MODE = stringPreferencesKey("vis_copper_mode")      // "off", "subtle" of "all"
        val KEY_VIS_COPPER_CYCLE_SECONDS = intPreferencesKey("vis_copper_cycle_seconds")
        val KEY_VIS_SPARKLE = booleanPreferencesKey("vis_sparkle")
        val KEY_VIS_COVER = booleanPreferencesKey("vis_cover")
        val KEY_VIS_COVER_SIZE = stringPreferencesKey("vis_cover_size")      // "small", "medium" of "large"
        val KEY_VIS_COVER_PLAYER = booleanPreferencesKey("vis_cover_player") // hoes groot in de speler
        val KEY_VIS_COVER_POSITION = stringPreferencesKey("vis_cover_position") // "default", "left", "center" of "right"
        val KEY_VIS_ARTIST_PHOTO = booleanPreferencesKey("vis_artist_photo")    // foto van de artiest als er geen hoes is
        val KEY_VIS_COPPER_DRIFT_RANGE = floatPreferencesKey("vis_copper_drift_range")
        val KEY_VIS_COPPER_DRIFT_SECONDS = intPreferencesKey("vis_copper_drift_seconds")
        val KEY_VIS_EQ_SENSITIVITY = floatPreferencesKey("vis_eq_sensitivity")
        val KEY_VIS_DIM_ENABLED = booleanPreferencesKey("vis_dim_enabled")
        val KEY_VIS_DIM_DELAY = intPreferencesKey("vis_dim_delay")
        val KEY_VIS_DIM_LEVEL = floatPreferencesKey("vis_dim_level")

        // Grote bediening (auto-radio): niet ingesteld = automatisch (aan als het apparaat zich als auto meldt)
        val KEY_LARGE_CONTROLS = booleanPreferencesKey("large_controls")
    }

    val lastStationIdFlow: Flow<Long?> = context.radioDataStore.data.map { preferences ->
        if (preferences.contains(KEY_LAST_STATION_ID)) {
            preferences[KEY_LAST_STATION_ID]
        } else {
            null
        }
    }

    suspend fun setLastStationId(stationId: Long) {
        context.radioDataStore.edit { preferences ->
            preferences[KEY_LAST_STATION_ID] = stationId
        }
    }

    /** De laatst beluisterde zenders (nieuwste eerst), voor het tabblad Recent in Android Auto. */
    suspend fun recentStationIds(): List<Long> =
        context.radioDataStore.data.first()[KEY_RECENT_STATIONS].toIdList()

    suspend fun addRecentStation(stationId: Long) {
        context.radioDataStore.edit { preferences ->
            val updated = (listOf(stationId) + preferences[KEY_RECENT_STATIONS].toIdList().filter { it != stationId })
                .take(MAX_RECENT_STATIONS)
            preferences[KEY_RECENT_STATIONS] = updated.joinToString(",")
        }
    }

    private fun String?.toIdList(): List<Long> =
        this.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    suspend fun clearLastStationId() {
        context.radioDataStore.edit { preferences ->
            preferences.remove(KEY_LAST_STATION_ID)
        }
    }

    val themeModeFlow: Flow<String> = context.radioDataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.radioDataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }

    val stationSortFlow: Flow<String> = context.radioDataStore.data.map { preferences ->
        preferences[KEY_STATION_SORT] ?: "list"
    }

    suspend fun setStationSort(key: String) {
        context.radioDataStore.edit { preferences ->
            preferences[KEY_STATION_SORT] = key
        }
    }

    val defaultPlaylistPrefsFlow: Flow<DefaultPlaylistPrefs> = context.radioDataStore.data.map { preferences ->
        DefaultPlaylistPrefs(
            sha = preferences[KEY_DEFAULT_PLAYLIST_SHA] ?: "",
            dismissedSha = preferences[KEY_DEFAULT_PLAYLIST_DISMISSED] ?: "",
            checkedAt = preferences[KEY_DEFAULT_PLAYLIST_CHECKED_AT] ?: 0L,
            updatedAt = preferences[KEY_DEFAULT_PLAYLIST_UPDATED_AT] ?: 0L
        )
    }

    suspend fun getDefaultPlaylistPrefs(): DefaultPlaylistPrefs = defaultPlaylistPrefsFlow.first()

    suspend fun setDefaultPlaylistChecked(at: Long) {
        context.radioDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_PLAYLIST_CHECKED_AT] = at
        }
    }

    suspend fun setDefaultPlaylistApplied(sha: String, at: Long) {
        context.radioDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_PLAYLIST_SHA] = sha
            preferences[KEY_DEFAULT_PLAYLIST_UPDATED_AT] = at
            preferences.remove(KEY_DEFAULT_PLAYLIST_DISMISSED)
        }
    }

    suspend fun setDefaultPlaylistDismissed(sha: String) {
        context.radioDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_PLAYLIST_DISMISSED] = sha
        }
    }

    val updatePrefsFlow: Flow<UpdatePrefs> = context.radioDataStore.data.map { preferences ->
        UpdatePrefs(
            autoCheck = preferences[KEY_UPDATE_AUTO] ?: true,
            checkedAt = preferences[KEY_UPDATE_CHECKED_AT] ?: 0L,
            dismissedVersion = preferences[KEY_UPDATE_DISMISSED] ?: ""
        )
    }

    suspend fun getUpdatePrefs(): UpdatePrefs = updatePrefsFlow.first()

    suspend fun setUpdateAutoCheck(enabled: Boolean) {
        context.radioDataStore.edit { preferences -> preferences[KEY_UPDATE_AUTO] = enabled }
    }

    suspend fun setUpdateChecked(at: Long) {
        context.radioDataStore.edit { preferences -> preferences[KEY_UPDATE_CHECKED_AT] = at }
    }

    suspend fun setUpdateDismissed(version: String) {
        context.radioDataStore.edit { preferences -> preferences[KEY_UPDATE_DISMISSED] = version }
    }

    // Grote bediening: null = de gebruiker heeft niets gekozen, dan beslist het apparaat zelf (auto-radio = aan).
    val largeControlsFlow: Flow<Boolean?> = context.radioDataStore.data.map { preferences ->
        if (preferences.contains(KEY_LARGE_CONTROLS)) preferences[KEY_LARGE_CONTROLS] else null
    }

    suspend fun setLargeControls(enabled: Boolean) {
        context.radioDataStore.edit { preferences -> preferences[KEY_LARGE_CONTROLS] = enabled }
    }

    val visualizerSettingsFlow: Flow<VisualizerSettings> = context.radioDataStore.data.map { p ->
        val d = VisualizerSettings()
        VisualizerSettings(
            waveSpeed = p[KEY_VIS_WAVE_SPEED] ?: d.waveSpeed,
            copperSpeed = p[KEY_VIS_COPPER_SPEED] ?: d.copperSpeed,
            hazeSpeed = p[KEY_VIS_HAZE_SPEED] ?: d.hazeSpeed,
            starSpeed = p[KEY_VIS_STAR_SPEED] ?: d.starSpeed,
            titleScale = p[KEY_VIS_TITLE_SCALE] ?: d.titleScale,
            scrollerRatio = p[KEY_VIS_SCROLLER_RATIO] ?: d.scrollerRatio,
            sloganPx = p[KEY_VIS_SLOGAN_PX] ?: d.sloganPx,
            hazeAmplitude = p[KEY_VIS_HAZE_AMPLITUDE] ?: d.hazeAmplitude,
            copperHue = p[KEY_VIS_COPPER_HUE] ?: d.copperHue,
            equalizerHue = p[KEY_VIS_EQUALIZER_HUE] ?: d.equalizerHue,
            backgroundHue = p[KEY_VIS_BACKGROUND_HUE] ?: d.backgroundHue,
            sloganColorFixed = p[KEY_VIS_SLOGAN_FIXED] ?: d.sloganColorFixed,
            sloganHue = p[KEY_VIS_SLOGAN_HUE] ?: d.sloganHue,
            slogans = p[KEY_VIS_SLOGANS]?.lines()?.let { VisualizerSettings.upgradeSlogans(it, p[KEY_VIS_SLOGANS_VERSION] ?: 1) }
                ?: VisualizerSettings.migrateSlogans(
                    own = p[KEY_VIS_OWN_SLOGANS]?.lines().orEmpty(),
                    onlyOwn = p[KEY_VIS_ONLY_OWN_SLOGANS] ?: false
                ),
            // Versie 1.18 had alleen een schakelaar: aan werd "subtiel", uit werd "uit".
            copperMode = CopperMode.fromKey(p[KEY_VIS_COPPER_MODE])
                ?: if (p[KEY_VIS_COPPER_DRIFT] == true) CopperMode.SUBTLE else d.copperMode,
            copperDriftRange = p[KEY_VIS_COPPER_DRIFT_RANGE] ?: d.copperDriftRange,
            copperDriftSeconds = p[KEY_VIS_COPPER_DRIFT_SECONDS] ?: d.copperDriftSeconds,
            copperCycleSeconds = p[KEY_VIS_COPPER_CYCLE_SECONDS] ?: d.copperCycleSeconds,
            sparklingStars = p[KEY_VIS_SPARKLE] ?: d.sparklingStars,
            showCover = p[KEY_VIS_COVER] ?: d.showCover,
            coverSize = CoverSize.fromKey(p[KEY_VIS_COVER_SIZE]) ?: d.coverSize,
            coverPosition = CoverPosition.fromKey(p[KEY_VIS_COVER_POSITION]) ?: d.coverPosition,
            artistPhoto = p[KEY_VIS_ARTIST_PHOTO] ?: d.artistPhoto,
            coverInPlayer = p[KEY_VIS_COVER_PLAYER] ?: d.coverInPlayer,
            equalizerSensitivity = p[KEY_VIS_EQ_SENSITIVITY] ?: d.equalizerSensitivity,
            dimEnabled = p[KEY_VIS_DIM_ENABLED] ?: d.dimEnabled,
            dimDelaySec = p[KEY_VIS_DIM_DELAY] ?: d.dimDelaySec,
            dimLevel = p[KEY_VIS_DIM_LEVEL] ?: d.dimLevel
        ).coerced()
    }

    suspend fun getVisualizerSettings(): VisualizerSettings = visualizerSettingsFlow.first()

    suspend fun setVisualizerSettings(settings: VisualizerSettings) {
        context.radioDataStore.edit { p ->
            p[KEY_VIS_WAVE_SPEED] = settings.waveSpeed
            p[KEY_VIS_COPPER_SPEED] = settings.copperSpeed
            p[KEY_VIS_HAZE_SPEED] = settings.hazeSpeed
            p[KEY_VIS_STAR_SPEED] = settings.starSpeed
            p[KEY_VIS_TITLE_SCALE] = settings.titleScale
            p[KEY_VIS_SCROLLER_RATIO] = settings.scrollerRatio
            p[KEY_VIS_SLOGAN_PX] = settings.sloganPx
            p[KEY_VIS_HAZE_AMPLITUDE] = settings.hazeAmplitude
            p[KEY_VIS_COPPER_HUE] = settings.copperHue
            p[KEY_VIS_EQUALIZER_HUE] = settings.equalizerHue
            p[KEY_VIS_BACKGROUND_HUE] = settings.backgroundHue
            p[KEY_VIS_SLOGAN_FIXED] = settings.sloganColorFixed
            p[KEY_VIS_SLOGAN_HUE] = settings.sloganHue
            p[KEY_VIS_SLOGANS] = settings.slogans.joinToString("\n")
            p[KEY_VIS_SLOGANS_VERSION] = VisualizerSettings.SLOGANS_VERSION
            p[KEY_VIS_COPPER_MODE] = settings.copperMode.key
            p[KEY_VIS_COPPER_DRIFT_RANGE] = settings.copperDriftRange
            p[KEY_VIS_COPPER_DRIFT_SECONDS] = settings.copperDriftSeconds
            p[KEY_VIS_COPPER_CYCLE_SECONDS] = settings.copperCycleSeconds
            p[KEY_VIS_SPARKLE] = settings.sparklingStars
            p[KEY_VIS_COVER] = settings.showCover
            p[KEY_VIS_COVER_SIZE] = settings.coverSize.key
            p[KEY_VIS_COVER_PLAYER] = settings.coverInPlayer
            p[KEY_VIS_COVER_POSITION] = settings.coverPosition.key
            p[KEY_VIS_ARTIST_PHOTO] = settings.artistPhoto
            p[KEY_VIS_EQ_SENSITIVITY] = settings.equalizerSensitivity
            p[KEY_VIS_DIM_ENABLED] = settings.dimEnabled
            p[KEY_VIS_DIM_DELAY] = settings.dimDelaySec
            p[KEY_VIS_DIM_LEVEL] = settings.dimLevel
        }
    }
}
