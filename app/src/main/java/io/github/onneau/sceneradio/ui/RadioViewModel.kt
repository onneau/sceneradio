package io.github.onneau.sceneradio.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.onneau.sceneradio.AppVersion
import io.github.onneau.sceneradio.SceneRadioApp
import io.github.onneau.sceneradio.data.DefaultPlaylistSource
import io.github.onneau.sceneradio.data.api.RadioApiService
import io.github.onneau.sceneradio.data.cover.CoverArt
import io.github.onneau.sceneradio.data.export.DownloadsSaver
import io.github.onneau.sceneradio.data.export.M3uWriter
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.data.api.RadioBrowserStationItem
import io.github.onneau.sceneradio.data.local.DefaultPlaylistPrefs
import io.github.onneau.sceneradio.data.local.UpdatePrefs
import io.github.onneau.sceneradio.data.model.ALL_GENRES
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.data.model.genres
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.VisualizerSettings
import io.github.onneau.sceneradio.playback.PlaybackStateData
import io.github.onneau.sceneradio.playback.RadioPlaybackService
import io.github.onneau.sceneradio.playback.toMediaItem
import io.github.onneau.sceneradio.update.AppUpdater
import io.github.onneau.sceneradio.update.UpdateDownload
import io.github.onneau.sceneradio.update.UpdateInfo
import io.github.onneau.sceneradio.update.VersionNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import io.github.onneau.sceneradio.i18n.tr

// Manieren om de zenderlijst te sorteren.
enum class StationSort(val key: String, private val nl: String, private val en: String) {
    LIST_ORDER("list", "Lijstvolgorde", "List order"),
    NAME_ASC("name_asc", "Naam A–Z", "Name A–Z"),
    NAME_DESC("name_desc", "Naam Z–A", "Name Z–A"),
    GENRE("genre", "Genre", "Genre"),
    FAVORITES_FIRST("favorites", "Favorieten eerst", "Favorites first");

    /** De naam in de taal van de app; leest de taalkeuze op het moment dat hij getoond wordt. */
    val label: String get() = tr(nl, en)

    companion object {
        fun fromKey(key: String?): StationSort = values().firstOrNull { it.key == key } ?: LIST_ORDER
    }
}

private fun sortStations(list: List<RadioStation>, sort: StationSort): List<RadioStation> = when (sort) {
    StationSort.LIST_ORDER -> list
    StationSort.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    StationSort.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
    StationSort.GENRE -> list.sortedWith(
        compareBy<RadioStation, String>(String.CASE_INSENSITIVE_ORDER) { it.genres().firstOrNull() ?: "\uFFFF" }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    )
    StationSort.FAVORITES_FIRST -> list.sortedWith(
        compareByDescending<RadioStation> { it.isFavorite }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    )
}

private fun matchesQuery(station: RadioStation, query: String): Boolean =
    query.isBlank() ||
        station.name.contains(query, ignoreCase = true) ||
        (station.groupTitle?.contains(query, ignoreCase = true) == true)

// Een nieuwe versie van de standaard zenderlijst die klaarstaat om te installeren.
data class DefaultPlaylistUpdate(
    val sha: String,
    // Pad + versie: verhuist de lijst naar een andere plek in de repo, dan geldt dat als een nieuwe versie.
    val key: String,
    val content: String,
    val isFirstImport: Boolean
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        // Niet vaker dan eens per 6 uur automatisch controleren.
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
        const val TAG = "SceneRadioPlaylist"

        // Updates van de app: automatisch hooguit eens per ~dag controleren (20 uur, zodat dagelijks gebruik elke dag een kans krijgt).
        const val UPDATE_CHECK_INTERVAL_MS = 20L * 60 * 60 * 1000
        const val UPDATE_TAG = "SceneRadioUpdate"
    }

    private val repository = (application as SceneRadioApp).repository
    private val playback = (application as SceneRadioApp).playback

    val allStations: StateFlow<List<RadioStation>> = repository.allStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledStations: StateFlow<List<RadioStation>> = repository.enabledStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteStations: StateFlow<List<RadioStation>> = repository.favoriteStations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState: StateFlow<PlaybackStateData> = RadioPlaybackService.playbackState

    val searchQuery = MutableStateFlow("")
    val selectedGroup = MutableStateFlow<String?>(null)
    val selectedPlaylistId = MutableStateFlow<Long?>(null)
    val sortOrder = MutableStateFlow(StationSort.LIST_ORDER)

    // Dynamisch gefilterde en gesorteerde zenderlijst voor de Stations pagina
    val filteredStations: StateFlow<List<RadioStation>> = combine(
        allStations,
        searchQuery,
        selectedGroup,
        selectedPlaylistId,
        sortOrder
    ) { stations, query, group, playlistId, sort ->
        val filtered = stations.filter { st ->
            // Een zender kan meerdere genres hebben ("Pop;Dance") en staat onder elk genre.
            val matchesGroup = group == null || group == ALL_GENRES ||
                st.genres().any { it.equals(group, ignoreCase = true) }
            val matchesPlaylist = playlistId == null || st.playlistId == playlistId
            matchesQuery(st, query) && matchesGroup && matchesPlaylist
        }
        sortStations(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Het tabblad Favorieten: alleen favorieten, met dezelfde zoekopdracht en sortering.
    val filteredFavorites: StateFlow<List<RadioStation>> = combine(
        allStations,
        searchQuery,
        sortOrder
    ) { stations, query, sort ->
        sortStations(stations.filter { it.isFavorite && matchesQuery(it, query) }, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(sort: StationSort) {
        sortOrder.value = sort
        viewModelScope.launch { repository.preferences.setStationSort(sort.key) }
    }

    /** Na het terugzetten van een back-up: de sortering opnieuw uit de instellingen lezen. */
    fun reloadSortOrder() {
        viewModelScope.launch {
            sortOrder.value = StationSort.fromKey(repository.preferences.stationSortFlow.first())
        }
    }

    val themeMode: StateFlow<String> = repository.preferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    // Visualizer-instellingen (Instellingen > Weergave > Visualizer). De waarde in het geheugen geldt
    // meteen, zodat het voorbeeld live meebeweegt; het opslaan volgt kort nadat je klaar bent met schuiven.
    private val _visualizerSettings = MutableStateFlow(VisualizerSettings())
    val visualizerSettings: StateFlow<VisualizerSettings> = _visualizerSettings.asStateFlow()
    private var visualizerSaveJob: Job? = null

    init {
        viewModelScope.launch {
            _visualizerSettings.value = repository.preferences.getVisualizerSettings()
        }
    }

    fun updateVisualizerSettings(change: (VisualizerSettings) -> VisualizerSettings) {
        val next = change(_visualizerSettings.value).coerced()
        _visualizerSettings.value = next
        visualizerSaveJob?.cancel()
        visualizerSaveJob = viewModelScope.launch {
            delay(400)
            repository.preferences.setVisualizerSettings(next)
        }
    }

    // Alles terug naar standaard, behalve de slogans (dat zijn teksten van jou, geen instellingen; de knop
    // "Standaardslogans herstellen" op het scherm Slogans zet die terug) en "Hoes in de speler" (dat hoort niet bij de visualizer).
    // De positie van de hoes en de foto van de artiest gaan wel terug naar standaard.
    fun resetVisualizerSettings() = updateVisualizerSettings {
        VisualizerSettings(slogans = it.slogans, coverInPlayer = it.coverInPlayer)
    }

    private data class CoverRequest(val artist: String, val title: String, val withPhoto: Boolean)

    // De hoes van het nummer dat nu speelt, voor in de visualizer en in de speler (alleen als een van die twee in de instellingen aan staat).
    // Is er geen hoes (ook niet met artiest en titel omgewisseld), dan met "Foto van de artiest" aan een foto van de artiest.
    // Pas na even rust zoeken, zodat een stream die snel wisselt of flikkert niets onnodigs opvraagt; terwijl er
    // gezocht wordt (of niets is gevonden) is er geen hoes.
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val coverArt: StateFlow<Bitmap?> = combine(
        playbackState.map { it.currentArtist?.trim().orEmpty() to it.currentTrackTitle?.trim().orEmpty() }
            .distinctUntilChanged(),
        visualizerSettings.map { (it.showCover || it.coverInPlayer) to it.artistPhoto }.distinctUntilChanged()
    ) { track, wanted ->
        if (wanted.first && track.first.isNotEmpty() && track.second.isNotEmpty()) {
            CoverRequest(track.first, track.second, wanted.second)
        } else {
            null
        }
    }
        .distinctUntilChanged()
        .debounce(1200L)
        .transformLatest<CoverRequest?, Bitmap?> { request ->
            emit(null)
            if (request != null) {
                emit(CoverArt.findArtwork(getApplication<Application>(), request.artist, request.title, request.withPhoto))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Grote bediening (voor een auto-radio): zonder eigen keuze staat hij aan op een apparaat dat zich als auto meldt.
    private fun deviceIsCar(): Boolean {
        val app = getApplication<Application>()
        val carMode = (app.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_CAR
        return carMode || app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    val largeControls: StateFlow<Boolean> = repository.preferences.largeControlsFlow
        .map { it ?: deviceIsCar() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, deviceIsCar())

    fun setLargeControls(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setLargeControls(enabled) }
    }

    // Radio Browser Search state
    val radioBrowserSearchResults = MutableStateFlow<List<RadioBrowserStationItem>>(emptyList())
    val isSearchingRadioBrowser = MutableStateFlow(false)
    val radioBrowserError = MutableStateFlow<String?>(null)

    // Import status
    val isImporting = MutableStateFlow(false)
    val importMessage = MutableStateFlow<String?>(null)

    // Standaard zenderlijst (GitHub)
    val defaultPlaylistUpdate = MutableStateFlow<DefaultPlaylistUpdate?>(null)
    val defaultPlaylistStatus = MutableStateFlow<String?>(null)
    val isCheckingPlaylist = MutableStateFlow(false)
    val isApplyingPlaylist = MutableStateFlow(false)
    val defaultPlaylistPrefs: StateFlow<DefaultPlaylistPrefs> = repository.preferences.defaultPlaylistPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultPlaylistPrefs())

    // App-updates (GitHub Releases)
    val appUpdate = MutableStateFlow<UpdateInfo?>(null)
    val updateStatus = MutableStateFlow<String?>(null)
    val updateError = MutableStateFlow<String?>(null)
    val isCheckingUpdate = MutableStateFlow(false)
    val showUpdateDialog = MutableStateFlow(false)
    val updateDownload = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    // Bevestiging nadat de update in Downloads is gezet (voor als het installatiescherm niet opent, zoals op een auto-radio)
    val updateSavedMessage = MutableStateFlow<String?>(null)
    // "Alleen downloaden": de update gaat na het downloaden naar Downloads, zonder dat het installatiescherm opent
    val updateSaveOnly = MutableStateFlow(false)
    val updatePrefs: StateFlow<UpdatePrefs> = repository.preferences.updatePrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdatePrefs())
    private var updateJob: Job? = null

    private var hasAutoPlayed = false

    // Zenders waarvoor al een logo is opgezocht (één poging per zender per sessie).
    private val logoLookupAttempted = mutableSetOf<Long>()

    init {
        // Verbinding met de afspeelservice opzetten (start/bindt de service).
        playback.connect()

        // Eerder gekozen sortering terugzetten.
        viewModelScope.launch {
            sortOrder.value = StationSort.fromKey(repository.preferences.stationSortFlow.first())
        }

        // Kijken of er een nieuwere standaard zenderlijst is (maximaal eens per 6 uur).
        viewModelScope.launch {
            delay(3000)
            checkForDefaultPlaylistUpdate(force = false)
        }

        // Kijken of er een nieuwere versie van de app is (hooguit eens per dag, en alleen als dat aan staat).
        viewModelScope.launch {
            delay(6000)
            withContext(Dispatchers.IO) { AppUpdater.clearDownloads(getApplication<Application>()) }
            checkForAppUpdate(force = false)
        }

        // Automatisch afspelen ALLEEN als LAST_STATION_ID al in DataStore staat,
        // en alleen als er nog niets geladen is (bijv. app heropend terwijl de radio speelt).
        viewModelScope.launch {
            combine(repository.lastStationIdFlow, enabledStations) { lastId, stations ->
                Pair(lastId, stations)
            }.collect { (lastId, stations) ->
                if (!hasAutoPlayed && lastId != null && stations.isNotEmpty()) {
                    if (playbackState.value.currentStationId != null) {
                        hasAutoPlayed = true
                        return@collect
                    }
                    val stationToPlay = stations.find { it.id == lastId }
                    if (stationToPlay != null) {
                        hasAutoPlayed = true
                        playStation(stationToPlay)
                    }
                }
            }
        }

        // Automatische logo-verrijking op de achtergrond voor zenders zonder logo:
        // elke zender wordt maar één keer geprobeerd en er zit tijd tussen de verzoeken.
        viewModelScope.launch(Dispatchers.IO) {
            allStations
                .map { list ->
                    // Voor de standaard zenderlijst is de repository (map logos/) de enige bron voor
                    // logo's; daar zoekt de app niet zelf naar. Alleen eigen imports krijgen een zoekactie.
                    val defaultId = repository.defaultPlaylistId()
                    list.filter { it.playlistId != defaultId && needsLogo(it.logoUrl) && it.id !in logoLookupAttempted }
                }
                .filter { it.isNotEmpty() }
                .collect { batch ->
                    for (st in batch) {
                        logoLookupAttempted.add(st.id)
                        val logo = RadioApiService.findLogoForStation(st.name, st.streamUrl)
                        if (!logo.isNullOrBlank()) {
                            repository.updateStationLogo(st.id, logo)
                        }
                        delay(500)
                    }
                }
        }
    }

    fun playStation(station: RadioStation) {
        // Het onthouden van de laatste zender gebeurt in de service (ook voor Android Auto).
        playback.play(station.toMediaItem())
    }

    fun togglePlayPause() {
        playback.togglePlayPause()
    }

    fun retryPlayback() {
        playback.retry()
    }

    fun playNextStation() {
        val list = enabledStations.value
        if (list.isEmpty()) return
        val currentId = playbackState.value.currentStationId
        val currentIndex = list.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex != -1 && currentIndex + 1 < list.size) currentIndex + 1 else 0
        playStation(list[nextIndex])
    }

    fun playPreviousStation() {
        val list = enabledStations.value
        if (list.isEmpty()) return
        val currentId = playbackState.value.currentStationId
        val currentIndex = list.indexOfFirst { it.id == currentId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playStation(list[prevIndex])
    }

    fun setStationEnabled(stationId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setStationEnabled(stationId, enabled)
        }
    }

    fun toggleStationVisibility(station: RadioStation) {
        setStationEnabled(station.id, !station.isEnabled)
    }

    fun setStationFavorite(stationId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setStationFavorite(stationId, isFavorite)
        }
    }

    fun toggleStationFavorite(station: RadioStation) {
        setStationFavorite(station.id, !station.isFavorite)
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.preferences.setThemeMode(mode)
        }
    }

    fun setThemeModeSetting(mode: String) {
        setThemeMode(mode)
    }

    suspend fun checkForDefaultPlaylistUpdate(force: Boolean) {
        val now = System.currentTimeMillis()
        val prefs = repository.preferences.getDefaultPlaylistPrefs()
        if (!force && now - prefs.checkedAt < CHECK_INTERVAL_MS) {
            val minutes = (now - prefs.checkedAt) / 60_000
            Log.d(TAG, "Controle overgeslagen: $minutes min geleden al gecontroleerd (cache-hit, lokale lijst blijft in gebruik)")
            return
        }
        if (isCheckingPlaylist.value) return

        Log.i(TAG, "Controle gestart (handmatig=$force)")
        AppLog.v(LogCategory.NETWERK, "zenderlijst op GitHub controleren (${if (force) "handmatig" else "automatisch"})")
        isCheckingPlaylist.value = true
        if (force) defaultPlaylistStatus.value = tr("Bezig met controleren…", "Checking…")
        val result = RadioApiService.fetchDefaultPlaylist()
        isCheckingPlaylist.value = false

        result.onSuccess { file ->
            repository.preferences.setDefaultPlaylistChecked(now)
            val hasPlaylist = repository.hasDefaultPlaylist()
            val key = "${DefaultPlaylistSource.PATH}:${file.sha}"
            Log.i(TAG, "GitHub-versie ${file.sha.take(8)}, lokaal ${prefs.sha.take(8).ifEmpty { "geen" }}, lijst aanwezig=$hasPlaylist")
            AppLog.v(LogCategory.NETWERK, "zenderlijst: GitHub ${file.sha.take(8)}, lokaal ${prefs.sha.take(8).ifEmpty { "geen" }}")
            when {
                key == prefs.sha && hasPlaylist -> {
                    defaultPlaylistUpdate.value = null
                    defaultPlaylistStatus.value = tr("De zenderlijst is actueel.", "The station list is up to date.")
                }
                !force && key == prefs.dismissedSha -> {
                    // Deze versie is eerder genegeerd; niet opnieuw opdringen.
                }
                else -> {
                    defaultPlaylistUpdate.value = DefaultPlaylistUpdate(
                        sha = file.sha,
                        key = key,
                        content = file.text,
                        isFirstImport = prefs.sha.isBlank() || !hasPlaylist
                    )
                    defaultPlaylistStatus.value = tr("Er staat een nieuwe zenderlijst klaar.", "A new station list is available.")
                }
            }
        }.onFailure { error ->
            // Bij een mislukte controle blijft de laatst gedownloade lijst gewoon in gebruik.
            Log.w(TAG, "Controle mislukt, laatste lokale lijst blijft in gebruik: ${error.message}")
            AppLog.v(LogCategory.NETWERK, "zenderlijst controleren mislukt: ${error.message ?: error.javaClass.simpleName}")
            if (force) {
                defaultPlaylistStatus.value = error.message ?: tr("Controleren is mislukt.", "Checking failed.")
            }
        }
    }

    /** Haalt de nieuwste standaard zenderlijst op en past hem meteen toe (de knop "Controleren en bijwerken"). */
    fun refreshDefaultPlaylistAndApply() {
        viewModelScope.launch { checkAndApplyDefaultPlaylist() }
    }

    /** Zoals [refreshDefaultPlaylistAndApply], maar wacht tot het klaar is. */
    suspend fun checkAndApplyDefaultPlaylist() {
        checkForDefaultPlaylistUpdate(force = true)
        if (defaultPlaylistUpdate.value != null) applyDefaultPlaylistUpdateAndWait()
    }

    fun applyDefaultPlaylistUpdate() {
        viewModelScope.launch { applyDefaultPlaylistUpdateAndWait() }
    }

    private suspend fun applyDefaultPlaylistUpdateAndWait() {
        val update = defaultPlaylistUpdate.value ?: return
        if (isApplyingPlaylist.value) return
        isApplyingPlaylist.value = true
        defaultPlaylistStatus.value = tr("Bezig met bijwerken…", "Updating…")
        try {
            val result = withContext(Dispatchers.IO) {
                repository.applyDefaultPlaylist(update.content, update.sha)
            }
            Log.i(TAG, "Bijgewerkt naar ${update.sha.take(8)}: totaal=${result.total}, nieuw=${result.added}, gewijzigd=${result.updated}, verwijderd=${result.removed}")
            if (result.total == 0) {
                defaultPlaylistStatus.value = tr("Geen zenders gevonden in de lijst; er is niets gewijzigd.", "No stations found in the list; nothing was changed.")
            } else {
                repository.preferences.setDefaultPlaylistApplied(update.key, System.currentTimeMillis())
                defaultPlaylistUpdate.value = null
                defaultPlaylistStatus.value = if (update.isFirstImport) {
                    tr("${result.total} zenders toegevoegd.", "${result.total} stations added.")
                } else {
                    tr("Bijgewerkt: ${result.added} nieuw, ${result.updated} gewijzigd, ${result.removed} verwijderd.", "Updated: ${result.added} new, ${result.updated} changed, ${result.removed} removed.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bijwerken mislukt", e)
            defaultPlaylistStatus.value = tr("Bijwerken mislukt: ${e.message ?: "onbekende fout"}", "Update failed: ${e.message ?: "unknown error"}")
        } finally {
            isApplyingPlaylist.value = false
        }
    }

    fun dismissDefaultPlaylistUpdate() {
        val update = defaultPlaylistUpdate.value ?: return
        viewModelScope.launch {
            repository.preferences.setDefaultPlaylistDismissed(update.key)
            defaultPlaylistUpdate.value = null
        }
    }

    // App-updates
    suspend fun checkForAppUpdate(force: Boolean) {
        val prefs = repository.preferences.getUpdatePrefs()
        if (!force && !prefs.autoCheck) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.checkedAt < UPDATE_CHECK_INTERVAL_MS) return
        if (isCheckingUpdate.value) return
        // Een download die bezig is of klaarstaat, laten we met rust.
        if (updateDownload.value !is UpdateDownload.Idle) return

        isCheckingUpdate.value = true
        updateError.value = null
        if (force) updateStatus.value = tr("Bezig met controleren…", "Checking…")
        AppLog.i(LogCategory.UPDATE, if (force) "handmatig zoeken naar een nieuwe versie" else "automatisch zoeken naar een nieuwe versie")
        val result = AppUpdater.fetchLatest()
        isCheckingUpdate.value = false

        result.onSuccess { info ->
            repository.preferences.setUpdateChecked(now)
            if (info != null && VersionNumber.compare(info.version, AppVersion.NUMBER) > 0) {
                Log.i(UPDATE_TAG, "Nieuwe versie ${info.version} gevonden (jij hebt ${AppVersion.NUMBER})")
                AppLog.i(LogCategory.UPDATE, "nieuwe versie ${info.version} gevonden (deze app is ${AppVersion.NUMBER})")
                appUpdate.value = info
                updateStatus.value = tr("Update beschikbaar: versie ${info.version}", "Update available: version ${info.version}")
                // Automatisch alleen laten zien als je deze versie nog niet met "Later" hebt weggedrukt.
                if (!force && info.version != prefs.dismissedVersion) showUpdateDialog.value = true
            } else {
                appUpdate.value = null
                updateStatus.value = tr("Je hebt de nieuwste versie.", "You have the latest version.")
                AppLog.i(
                    LogCategory.UPDATE,
                    "geen nieuwe versie gevonden (nieuwste op GitHub: ${info?.version ?: "geen release met een APK"}, deze app: ${AppVersion.NUMBER})"
                )
            }
        }.onFailure { error ->
            Log.w(UPDATE_TAG, "Update-controle mislukt: ${error.message}")
            AppLog.i(LogCategory.UPDATE, "zoeken naar een nieuwe versie mislukt: ${error.message ?: error.javaClass.simpleName}")
            updateStatus.value = tr("Controleren mislukt: ${error.message ?: "onbekende fout"}", "Checking failed: ${error.message ?: "unknown error"}")
        }
    }

    fun checkAppUpdateNow() {
        viewModelScope.launch { checkForAppUpdate(force = true) }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setUpdateAutoCheck(enabled) }
    }

    fun openUpdateDialog() {
        updateError.value = null
        showUpdateDialog.value = true
    }

    /** "Later": het overzicht verdwijnt en komt voor deze versie niet vanzelf terug. Instellingen blijft de update tonen. */
    fun dismissUpdateDialog() {
        showUpdateDialog.value = false
        updateSavedMessage.value = null
        val info = appUpdate.value ?: return
        viewModelScope.launch { repository.preferences.setUpdateDismissed(info.version) }
    }

    /** "Alleen downloaden": haalt de update op en zet hem in Downloads, zonder het installatiescherm te openen. */
    fun downloadUpdateOnly() {
        if (appUpdate.value == null || updateDownload.value !is UpdateDownload.Idle) return
        openUpdateDialog()   // zodat de voortgang en de uitkomst te zien zijn
        startUpdateDownload(saveOnly = true)
    }

    fun startUpdateDownload(saveOnly: Boolean = false) {
        val info = appUpdate.value ?: return
        if (updateDownload.value !is UpdateDownload.Idle) return
        updateError.value = null
        updateSavedMessage.value = null
        updateSaveOnly.value = saveOnly
        val app = getApplication<Application>()
        updateJob = viewModelScope.launch {
            updateDownload.value = UpdateDownload.Downloading(0L, info.sizeBytes)
            AppLog.i(LogCategory.UPDATE, "download van versie ${info.version} gestart (${info.sizeBytes / (1024 * 1024)} MB)")
            val result = AppUpdater.download(app, info) { done, total ->
                if (isActive) updateDownload.value = UpdateDownload.Downloading(done, total)
            }
            result.onSuccess { file ->
                updateDownload.value = UpdateDownload.Verifying
                val problem = withContext(Dispatchers.IO) { AppUpdater.verify(app, file) }
                if (problem != null) {
                    file.delete()
                    updateDownload.value = UpdateDownload.Idle
                    updateError.value = problem
                    AppLog.i(LogCategory.UPDATE, "controle van de download afgekeurd: $problem")
                } else {
                    updateDownload.value = UpdateDownload.Ready(file, info.version)
                    AppLog.i(LogCategory.UPDATE, "versie ${info.version} gedownload en gecontroleerd; klaar om te installeren")
                    // "Alleen downloaden": meteen naar Downloads, het installatiescherm blijft dicht.
                    if (saveOnly) saveUpdateToDownloads()
                }
            }.onFailure { error ->
                Log.w(UPDATE_TAG, "Downloaden mislukt: ${error.message}")
                AppLog.i(LogCategory.UPDATE, "downloaden mislukt: ${error.message ?: error.javaClass.simpleName}")
                updateDownload.value = UpdateDownload.Idle
                updateError.value = error.message ?: tr("Downloaden mislukt.", "Download failed.")
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
        updateJob = null
        updateDownload.value = UpdateDownload.Idle
        updateSavedMessage.value = null
        updateSaveOnly.value = false
    }

    /**
     * Zet de gedownloade en gecontroleerde update in de map Downloads, voor als Android het installatiescherm niet
     * opent (bijvoorbeeld op een auto-radio): je opent het bestand dan met de bestandsbeheerder van het apparaat.
     */
    fun saveUpdateToDownloads() {
        val ready = updateDownload.value as? UpdateDownload.Ready ?: return
        updateError.value = null
        AppLog.i(LogCategory.DOWNLOADS, "\"Opslaan in Downloads\" gekozen voor de update naar versie ${ready.version}")
        viewModelScope.launch {
            val name = "sceneradio-${ready.version}.apk"
            val result = withContext(Dispatchers.IO) {
                DownloadsSaver.saveFile(
                    getApplication<Application>(), ready.file, name, "application/vnd.android.package-archive"
                )
            }
            result.onSuccess { saved ->
                updateSavedMessage.value = tr("✓ Opgeslagen in ${saved.place} als ${saved.name}. ", "✓ Saved in ${saved.place} as ${saved.name}. ") +
                    tr("Open het met de bestandsbeheerder van je apparaat en kies Installeren.", "Open it with your device's file manager and choose Install.")
            }.onFailure { error ->
                Log.w(UPDATE_TAG, "Opslaan in Downloads mislukt: ${error.message}")
                updateError.value = tr("Opslaan in Downloads mislukt: ${error.message ?: "onbekende fout"}", "Saving to Downloads failed: ${error.message ?: "unknown error"}")
            }
        }
    }

    /**
     * Het installatiescherm van Android is aangevraagd. Het updatevenster blijft staan: op een auto-radio verschijnt
     * dat scherm soms niet, en dan is "Opslaan in Downloads" in dit venster de weg om de update toch te installeren.
     * De gedownloade update blijft klaarstaan.
     */
    fun onInstallLaunched() {
        AppLog.i(LogCategory.UPDATE, "installatiescherm van Android aangevraagd")
    }

    fun reportUpdateError(message: String) {
        updateError.value = message
    }

    // M3U downloaden: Favorieten (playlistId = null) of een hele lijst (de standaardlijst of een geïmporteerde lijst)
    val exportMessage = MutableStateFlow<String?>(null)

    private fun stationsForExport(playlistId: Long?): List<RadioStation> =
        if (playlistId == null) favoriteStations.value
        else allStations.value.filter { it.playlistId == playlistId }

    /** Zet de gekozen lijst als .m3u-bestand in Downloads. */
    fun saveM3uToDownloads(playlistId: Long?, label: String) {
        viewModelScope.launch {
            val stations = stationsForExport(playlistId)
            if (stations.isEmpty()) {
                exportMessage.value = tr("Deze lijst heeft geen zenders om te downloaden.", "This list has no stations to download.")
                return@launch
            }
            val text = M3uWriter.write(stations)
            val name = M3uWriter.fileName(label)
            val result = withContext(Dispatchers.IO) {
                DownloadsSaver.saveText(getApplication<Application>(), name, "audio/x-mpegurl", text)
            }
            exportMessage.value = result.fold(
                onSuccess = { tr("✓ Opgeslagen in ${it.place} als ${it.name} (${stations.size} zenders)", "✓ Saved in ${it.place} as ${it.name} (${stations.size} stations)") },
                onFailure = { tr("Opslaan mislukt: ${it.message ?: "onbekende fout"}", "Saving failed: ${it.message ?: "unknown error"}") }
            )
        }
    }

    /** Maakt het deelmenu van Android voor de gekozen lijst; null als de lijst leeg is of het bestand niet kan worden gemaakt. */
    suspend fun m3uShareIntent(playlistId: Long?, label: String): Intent? {
        val stations = stationsForExport(playlistId)
        if (stations.isEmpty()) {
            exportMessage.value = tr("Deze lijst heeft geen zenders om te delen.", "This list has no stations to share.")
            return null
        }
        val app = getApplication<Application>()
        return withContext(Dispatchers.IO) {
            try {
                val folder = File(app.cacheDir, "exports").apply { mkdirs() }
                val file = File(folder, M3uWriter.fileName(label))
                file.writeText(M3uWriter.write(stations), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/x-mpegurl"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Intent.createChooser(send, tr("Zenderlijst delen", "Share station list")).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            } catch (e: Exception) {
                exportMessage.value = tr("Delen mislukt: ${e.message ?: "onbekende fout"}", "Sharing failed: ${e.message ?: "unknown error"}")
                null
            }
        }
    }

    // Playlists beheer
    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.clearPlaylistStations(playlistId)
        }
    }

    fun clearPlaylist(playlist: Playlist) {
        clearPlaylist(playlist.id)
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        deletePlaylist(playlist.id)
    }

    // Een logo ontbreekt, of is een relatief pad uit een M3U dat niet kan laden (bijv. "logos/x.jpg").
    private fun needsLogo(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return !(lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("file://") || lower.startsWith("content://"))
    }

    private fun importResultMessage(count: Int): String =
        if (count == 0) {
            tr("Geen zenders gevonden in deze afspeellijst.", "No stations found in this playlist.")
        } else {
            tr("$count zenders succesvol geïmporteerd!", "$count stations imported successfully!")
        }

    // M3U Import via URL of tekst
    fun importM3uFromUrl(url: String, playlistName: String) {
        viewModelScope.launch {
            isImporting.value = true
            importMessage.value = tr("Bezig met ophalen van afspeellijst…", "Fetching playlist…")
            val fetchResult = RadioApiService.fetchUrlContent(url)
            if (fetchResult.isSuccess) {
                val content = fetchResult.getOrNull() ?: ""
                val count = repository.importM3uContent(content, playlistName, url)
                importMessage.value = importResultMessage(count)
            } else {
                importMessage.value = fetchResult.exceptionOrNull()?.message ?: tr("Fout bij downloaden", "Download error")
            }
            isImporting.value = false
        }
    }

    fun importM3uContent(content: String, playlistName: String, isLocal: Boolean = false) {
        viewModelScope.launch {
            isImporting.value = true
            val count = repository.importM3uContent(content, playlistName, sourceUrl = if (isLocal) "local" else null)
            importMessage.value = importResultMessage(count)
            isImporting.value = false
        }
    }

    // Radio-Browser Zoeken & Import
    fun searchRadioBrowser(term: String) {
        if (term.isBlank()) return
        viewModelScope.launch {
            isSearchingRadioBrowser.value = true
            radioBrowserError.value = null
            val result = RadioApiService.searchStationsByName(term)
            if (result.isSuccess) {
                radioBrowserSearchResults.value = result.getOrNull() ?: emptyList()
            } else {
                radioBrowserError.value = result.exceptionOrNull()?.message
            }
            isSearchingRadioBrowser.value = false
        }
    }

    fun importFromRadioBrowser(item: RadioBrowserStationItem) {
        viewModelScope.launch {
            val resolvedUrl = RadioApiService.registerClickAndResolveUrl(item.stationuuid) ?: item.urlResolved ?: item.url
            val updated = item.copy(urlResolved = resolvedUrl)
            val st = repository.importRadioBrowserStation(updated)
            playStation(st)
        }
    }
}
