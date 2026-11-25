package io.github.onneau.sceneradio.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import io.github.onneau.sceneradio.SceneRadioApp
import io.github.onneau.sceneradio.data.local.RadioDatabase
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.data.cover.CoverArt
import io.github.onneau.sceneradio.data.local.StationDao
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.genres
import io.github.onneau.sceneradio.ui.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class PlaybackStateData(
    val currentStationId: Long? = null,
    val stationName: String = "",
    val streamUrl: String = "",
    val logoUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrackTitle: String? = null,
    val currentArtist: String? = null,
    val format: String = "MP3",
    val errorMessage: String? = null
)

/**
 * Media3-service voor achtergrondafspelen en Android Auto.
 * De notificatie (en de foreground-status) wordt door Media3 zelf beheerd.
 * De UI bestuurt deze service via [PlaybackConnection] (MediaController).
 *
 * Android Auto (en de app op een auto-radio) krijgt vier tabbladen: Favorieten en Recent als raster, Genres en
 * Alle zenders als lijst. Vorige en volgende zijn vorige en volgende zender, een hartje zet de zender in of uit
 * de favorieten, de spraakopdracht "Speel Radio 538" werkt, en na het starten van de auto hervat de laatste zender.
 */
class RadioPlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer

    // De sessie en de speler mogen alleen vanaf de hoofdthread worden aangeraakt; databasewerk gaat via ioExecutor.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var coverJob: Job? = null
    // Wat het hartje in de mediabediening nu toont (null = nog niet bepaald)
    private var heartShowsFavorite: Boolean? = null
    private val toggleFavoriteCommand = SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY)

    // Room mag niet op de main thread; de sessie-callbacks draaien daar wel.
    private lateinit var ioExecutor: ListeningExecutorService
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ROOT_ID = "root"
        const val ROOT_RECENT_ID = "root_recent"
        const val FOLDER_ALL = "folder_all"
        const val FOLDER_FAVORITES = "folder_favorites"
        const val FOLDER_RECENT = "folder_recent"
        const val FOLDER_GENRES = "folder_genres"
        private const val GENRE_PREFIX = "genre_"

        private const val CMD_TOGGLE_FAVORITE = "sceneradio.toggle_favorite"

        // Weergave van de lijsten in Android Auto (1 = lijst, 2 = raster); de sleutels staan zo in de Android-documentatie.
        private const val HINT_BROWSABLE_STYLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val HINT_PLAYABLE_STYLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val STYLE_LIST = 1
        private const val STYLE_GRID = 2

        // Even wachten met zoeken naar een hoes: zenders sturen soms eerst een halve of tijdelijke titel.
        private const val COVER_LOOKUP_DELAY_MS = 1_500L

        // Het echte logo-adres van de zender, in de extra's van het spelende item (de artworkUri is daar een content://-adres, zie toPlayItem).
        private const val EXTRA_LOGO_URL = "sceneradio.logo_url"

        private val _playbackState = MutableStateFlow(PlaybackStateData())
        val playbackState = _playbackState.asStateFlow()

        private fun sameText(a: String, b: String): Boolean =
            a.filter { it.isLetterOrDigit() }.equals(b.filter { it.isLetterOrDigit() }, ignoreCase = true)

        private fun quotedValue(raw: String, key: String): String? =
            Regex("$key=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(raw)
                ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        /**
         * Zet de ruwe stream-titel om naar (titel, artiest).
         * Ondersteunt "Artiest - Titel" en het sleutel="waarde"-formaat dat sommige
         * streams gebruiken (title="..." artist="..."; song_spot="T" is reclame/praatje).
         */
        private fun parseStreamTitle(raw: String): Pair<String?, String?> {
            val text = raw.trim()
            if (text.isEmpty() || text == "-") return Pair(null, null)

            if (text.contains("title=\"", ignoreCase = true) || text.contains("song_spot=", ignoreCase = true)) {
                if (quotedValue(text, "song_spot").equals("T", ignoreCase = true)) return Pair(null, null)
                val title = quotedValue(text, "title") ?: quotedValue(text, "text")
                val artist = quotedValue(text, "artist")
                return Pair(title, artist)
            }

            val separator = text.indexOf(" - ")
            if (separator > 0 && separator + 3 < text.length) {
                return Pair(text.substring(separator + 3).trim(), text.substring(0, separator).trim())
            }
            return Pair(text, null)
        }

        private fun detectFormat(url: String): String = when {
            url.contains(".m3u8", ignoreCase = true) -> "HLS"
            url.contains(".aac", ignoreCase = true) -> "AAC"
            else -> "MP3"
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return

            val stationId = stationIdFromMediaId(mediaItem.mediaId)
            // Een update van de metadata van het huidige station (nummerinfo) is geen nieuw station.
            if (stationId != null &&
                stationId == _playbackState.value.currentStationId &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            ) {
                return
            }

            val streamUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            // De zendernaam staat in de extra's: de titel van het item wordt met nummerinfo het nummer zelf.
            val name = (mediaItem.mediaMetadata.extras?.getString(EXTRA_STATION_NAME)
                ?: mediaItem.mediaMetadata.displayTitle ?: mediaItem.mediaMetadata.title)
                ?.toString()
                .orEmpty()

            _playbackState.value = PlaybackStateData(
                currentStationId = stationId,
                stationName = name,
                streamUrl = streamUrl,
                logoUrl = mediaItem.mediaMetadata.extras?.getString(EXTRA_LOGO_URL)
                    ?: mediaItem.mediaMetadata.artworkUri?.toString(),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                format = detectFormat(streamUrl)
            )

            AppLog.i(LogCategory.AFSPELEN, "zender gekozen: ${name.ifBlank { "(zonder naam)" }}")

            // Laatst gekozen zender (en het rijtje recente zenders) onthouden, ook als die via Android Auto is gekozen.
            coverJob?.cancel()
            if (stationId != null) {
                serviceScope.launch {
                    val repository = (application as? SceneRadioApp)?.repository ?: return@launch
                    repository.setLastStationId(stationId)
                    repository.addRecentStation(stationId)
                }
            }
            refreshHeart()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            AppLog.v(LogCategory.AFSPELEN, if (isPlaying) "speelt" else "speelt niet (gepauzeerd, buffert of gestopt)")
        }

        override fun onPlaybackStateChanged(state: Int) {
            AppLog.v(
                LogCategory.AFSPELEN,
                "afspeelstatus: " + when (state) {
                    Player.STATE_IDLE -> "inactief"
                    Player.STATE_BUFFERING -> "bufferen"
                    Player.STATE_READY -> "klaar"
                    Player.STATE_ENDED -> "afgelopen"
                    else -> "onbekend ($state)"
                }
            )
            val current = _playbackState.value
            _playbackState.value = current.copy(
                isBuffering = state == Player.STATE_BUFFERING,
                errorMessage = if (state == Player.STATE_READY) null else current.errorMessage
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val userMsg = "Stream fout: ${error.message ?: "Kan niet verbinden met de radio-stream."}"
            AppLog.i(
                LogCategory.AFSPELEN,
                "afspeelfout bij ${_playbackState.value.stationName.ifBlank { "(zonder naam)" }}: " +
                    "${error.errorCodeName}, ${error.message ?: "geen melding"}" +
                    (error.cause?.let { " (oorzaak: ${it.javaClass.simpleName})" } ?: "")
            )
            AppLog.v(LogCategory.NETWERK, "verbindingsfout met de stream: ${error.errorCodeName}")
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = userMsg
            )
        }

        // Ruwe stream-metadata (ICY bij mp3/aac, ID3 bij HLS). We gebruiken niet de samengevoegde
        // player.mediaMetadata: daarin wint de vaste titel van het item (de zendernaam).
        override fun onMetadata(metadata: Metadata) {
            Log.d("SceneRadioMeta", (0 until metadata.length()).joinToString(" | ") { metadata[it].toString() })
            val parsed = MediaMetadata.Builder().populateFromMetadata(metadata).build()
            if (parsed.title == null && parsed.artist == null) return  // bijv. alleen een StreamUrl
            applyTrackInfo(parsed.title?.toString().orEmpty(), parsed.artist?.toString())
        }
    }

    private fun applyTrackInfo(rawTitleInput: String, rawArtistInput: String?) {
        val current = _playbackState.value
        val rawTitle = rawTitleInput.trim()
        val rawArtist = rawArtistInput?.trim()?.takeIf { it.isNotBlank() && !sameText(it, current.stationName) }

        // De zendernaam zelf is geen tracktitel.
        val isStationName = rawTitle.isNotEmpty() && sameText(rawTitle, current.stationName)

        var title: String? = null
        var artist: String? = rawArtist
        if (rawTitle.isNotEmpty() && !isStationName) {
            val parsed = parseStreamTitle(rawTitle)
            title = parsed.first
            if (artist == null) artist = parsed.second
        }

        _playbackState.value = current.copy(currentTrackTitle = title, currentArtist = artist)
        refreshSessionMetadata(artist, title)
    }

    /**
     * Zet het nummer in de metadata die de mediabediening, het vergrendelscherm, de melding en Android Auto tonen:
     * het nummer is de titel, de artiest de regel eronder en de zendernaam het album (derde regel, als het scherm die
     * toont). Zonder titel (alleen een artiest) blijft de zendernaam de titel; zonder nummerinfo staan de zendernaam
     * en de groep er weer zoals bij het kiezen van de zender. Een wijziging van alleen de metadata van het huidige
     * item gebeurt in ExoPlayer ter plekke, zonder het afspelen te onderbreken.
     */
    private fun refreshSessionMetadata(artist: String?, title: String?) {
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        val station = metadata.extras?.getString(EXTRA_STATION_NAME) ?: _playbackState.value.stationName
        val group = metadata.extras?.getString(EXTRA_STATION_GROUP) ?: "Live Radio"
        val trackArtist = artist?.takeIf { it.isNotBlank() }
        val trackTitle = title?.takeIf { it.isNotBlank() }
        val lines = when {
            trackTitle != null && trackArtist != null -> SessionLines(trackTitle, trackArtist, station)
            trackTitle != null -> SessionLines(trackTitle, station, null)
            trackArtist != null -> SessionLines(station, trackArtist, null)
            else -> SessionLines(station, group, null)
        }
        if (metadata.title?.toString() == lines.title &&
            metadata.artist?.toString() == lines.artist &&
            metadata.albumTitle?.toString() == lines.album
        ) return

        // Bij een nieuw nummer hoort de hoes van het vorige er niet meer bij: terug naar het zenderlogo.
        // De regel eronder staat ook als ondertitel: sommige schermen (en de wachtrij) tonen die in plaats van de artiest.
        val updated = item.buildUpon()
            .setMediaMetadata(
                metadata.buildUpon()
                    .setTitle(lines.title)
                    .setDisplayTitle(lines.title)
                    .setArtist(lines.artist)
                    .setSubtitle(lines.artist)
                    .setAlbumTitle(lines.album)
                    .setArtworkData(null, null)
                    .setArtworkUri(item.logoArtworkUri())
                    .build()
            )
            .build()
        player.replaceMediaItem(player.currentMediaItemIndex, updated)
        refreshCover(artist, title)
    }

    // Wat Android Auto, de melding en het vergrendelscherm tonen: titel, regel eronder en (optioneel) album.
    private data class SessionLines(val title: String, val artist: String, val album: String?)

    /**
     * Met "Hoes van het nummer" aan (Instellingen > Weergave > Visualizer) zoekt de service ook een hoes voor het
     * autoscherm en de melding. Alleen artiest en titel gaan naar MusicBrainz en Cover Art Archive; zonder hoes
     * blijft het zenderlogo staan.
     */
    private fun refreshCover(artist: String?, title: String?) {
        coverJob?.cancel()
        if (artist.isNullOrBlank() || title.isNullOrBlank()) return
        val repository = (application as? SceneRadioApp)?.repository ?: return
        coverJob = serviceScope.launch {
            val settings = repository.preferences.getVisualizerSettings()
            if (!settings.showCover) return@launch
            delay(COVER_LOOKUP_DELAY_MS)
            // Een hoes, of (met "Foto van de artiest" aan) bij gebrek daaraan een foto van de artiest.
            val bitmap = CoverArt.findArtwork(applicationContext, artist, title, settings.artistPhoto)
            // Alleen het resultaat in het logboek, nooit de artiest of titel zelf.
            AppLog.v(LogCategory.COVER, if (bitmap == null) "geen hoes of foto gevonden voor dit nummer" else "hoes of foto gevonden en doorgegeven")
            if (bitmap == null) return@launch
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
            // Ook als bestand in de cache met een eigen content://-adres: het kleine scherm van Android Auto (splitscreen) laadt alleen via een adres.
            val coverUri = ArtworkProvider.saveCover(applicationContext, artist, title, bytes)
            if (coverUri == null) AppLog.i(LogCategory.AUTO, "hoes voor Android Auto opslaan mislukt")
            mainHandler.post { applyCoverArtwork(artist, title, bytes, coverUri) }
        }
    }

    private fun applyCoverArtwork(artist: String, title: String, bytes: ByteArray, coverUri: Uri?) {
        if (mediaLibrarySession == null) return
        val state = _playbackState.value
        if (state.currentArtist != artist || state.currentTrackTitle != title) return  // alweer een ander nummer
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata.buildUpon()
            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        if (coverUri != null) metadata.setArtworkUri(coverUri)
        val updated = item.buildUpon().setMediaMetadata(metadata.build()).build()
        player.replaceMediaItem(player.currentMediaItemIndex, updated)
    }

    // ---- Hartje (favoriet) in de mediabediening van telefoon en auto ----

    private fun favoriteButton(isFavorite: Boolean): CommandButton =
        CommandButton.Builder(if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (isFavorite) "Uit favorieten halen" else "Toevoegen aan favorieten")
            .setSessionCommand(toggleFavoriteCommand)
            .build()

    /** Kijkt of de huidige zender een favoriet is en zet het hartje daarnaar (gevuld of leeg). */
    private fun refreshHeart() {
        serviceScope.launch {
            val id = _playbackState.value.currentStationId
            val favorite = id != null && stationDao().getStationByIdSync(id)?.isFavorite == true
            mainHandler.post {
                val session = mediaLibrarySession ?: return@post
                if (heartShowsFavorite == favorite) return@post
                heartShowsFavorite = favorite
                session.setMediaButtonPreferences(ImmutableList.of(favoriteButton(favorite)))
            }
        }
    }

    // ---- Vorige en volgende zender ----

    /** Gaat naar de volgende (+1) of vorige (-1) zichtbare zender, in dezelfde volgorde als de lijst in de app. */
    private fun skipStation(direction: Int) {
        serviceScope.launch {
            val list = stationDao().getEnabledStationsSync()
            if (list.isEmpty()) return@launch
            val index = list.indexOfFirst { it.id == _playbackState.value.currentStationId }
            val target = if (direction > 0) {
                if (index != -1 && index + 1 < list.size) list[index + 1] else list.first()
            } else {
                if (index > 0) list[index - 1] else list.last()
            }
            mainHandler.post {
                if (mediaLibrarySession == null) return@post
                player.setMediaItem(target.toPlayItem())
                player.prepare()
                player.play()
            }
        }
    }

    /**
     * Een radio heeft geen wachtrij, maar de knoppen vorige en volgende (melding, vergrendelscherm, auto,
     * stuurwiel) moeten wel werken: die gaan naar de vorige en volgende zender.
     */
    private inner class StationPlayer(wrapped: Player) : ForwardingPlayer(wrapped) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
                .build()

        override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true

        override fun seekToNext() = skipStation(+1)
        override fun seekToPrevious() = skipStation(-1)
        override fun seekToNextMediaItem() = skipStation(+1)
        override fun seekToPreviousMediaItem() = skipStation(-1)
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.i(LogCategory.AFSPELEN, "afspeelservice gestart")
        ioExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SceneRadio/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        player = ExoPlayer.Builder(this, SpectrumRenderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(playerListener)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Zenderlogo's voor melding, vergrendelscherm en autoscherm: veel favicon-links sturen
        // door van http naar https, wat de standaard-loader niet toestaat.
        val artworkHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SceneRadio/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        // CacheBitmapLoader onthoudt het laatste logo, zodat het niet bij elke nummerwissel opnieuw
        // wordt opgehaald.
        // De logo's en hoezen van de ArtworkProvider hebben een content://-adres: DefaultDataSource laadt die zelf en gebruikt
        // voor web-adressen de HTTP-fabriek hierboven.
        val bitmapLoader = CacheBitmapLoader(
            DataSourceBitmapLoader.Builder(this)
                .setDataSourceFactory(DefaultDataSource.Factory(this, artworkHttpFactory))
                .setMaximumOutputDimension(512)
                .build()
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, StationPlayer(player), LibraryCallback())
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(bitmapLoader)
            .build()

        // Zet je (in de app of in de auto) een zender in of uit de favorieten, dan volgt het hartje.
        (application as? SceneRadioApp)?.repository?.let { repository ->
            serviceScope.launch { repository.favoriteStations.collect { refreshHeart() } }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        AppLog.i(LogCategory.AFSPELEN, "afspeelservice gestopt")
        coverJob?.cancel()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player.release()
        ioExecutor.shutdown()
        serviceScope.cancel()
        _playbackState.value = PlaybackStateData()
        super.onDestroy()
    }

    private fun stationDao(): StationDao =
        RadioDatabase.getInstance(applicationContext).stationDao()

    private fun repository() = (application as SceneRadioApp).repository

    /**
     * Een map in de catalogus. De twee extra's vertellen Android Auto hoe de inhoud wordt getoond:
     * [playableStyle] voor zenders en [browsableStyle] voor mappen daarbinnen (lijst of raster).
     */
    private fun folderItem(
        id: String,
        title: String,
        playableStyle: Int = STYLE_LIST,
        browsableStyle: Int = STYLE_LIST,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(HINT_PLAYABLE_STYLE, playableStyle)
            putInt(HINT_BROWSABLE_STYLE, browsableStyle)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    // De vier tabbladen. Favorieten en Recent zijn een raster van zenderlogo's, Genres en Alle zenders een lijst.
    private fun rootFolder(): MediaItem =
        folderItem(ROOT_ID, "SceneRadio", mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    private fun favoritesFolder() = folderItem(FOLDER_FAVORITES, "Favorieten", playableStyle = STYLE_GRID)
    private fun recentFolder() = folderItem(FOLDER_RECENT, "Recent", playableStyle = STYLE_GRID)
    private fun genresFolder() = folderItem(
        FOLDER_GENRES, "Genres", browsableStyle = STYLE_LIST, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    )
    private fun allFolder() = folderItem(FOLDER_ALL, "Alle zenders", playableStyle = STYLE_LIST)

    private fun genreFolder(genre: String) = folderItem(GENRE_PREFIX + genre, genre, playableStyle = STYLE_LIST)

    /** De map met dit id, of null als het geen map is. */
    private fun folderFor(mediaId: String): MediaItem? = when {
        mediaId == ROOT_ID || mediaId == ROOT_RECENT_ID -> rootFolder()
        mediaId == FOLDER_FAVORITES -> favoritesFolder()
        mediaId == FOLDER_RECENT -> recentFolder()
        mediaId == FOLDER_GENRES -> genresFolder()
        mediaId == FOLDER_ALL -> allFolder()
        mediaId.startsWith(GENRE_PREFIX) -> genreFolder(mediaId.removePrefix(GENRE_PREFIX))
        else -> null
    }

    /** Levert één pagina van een lijst die op de IO-thread wordt opgebouwd (Room mag niet op de main thread). */
    private fun loadItems(
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
        build: () -> List<MediaItem>
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return ioExecutor.submit(Callable<LibraryResult<ImmutableList<MediaItem>>> {
            val all = build()
            val size = all.size.toLong()
            val from = (page.toLong() * pageSize).coerceAtMost(size).toInt()
            val to = (from.toLong() + pageSize).coerceAtMost(size).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        })
    }

    private fun loadStations(
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
        query: (StationDao) -> List<RadioStation>
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        loadItems(page, pageSize, params) { query(stationDao()).map { it.toBrowseItem() } }

    /**
     * Een zender zoals hij in de speler komt (ook in Android Auto): het zenderlogo staat als content://-adres van de
     * ArtworkProvider in de artworkUri, zodat het kleine scherm van Android Auto (splitscreen) het kan laden; het echte
     * logo-adres staat in de extra's, voor de app zelf (zie onMediaItemTransition) en om het logo terug te zetten bij een nieuw nummer.
     */
    private fun RadioStation.toPlayItem(): MediaItem {
        val item = toMediaItem()
        val raw = logoUrl?.trim().orEmpty()
        val art = ArtworkProvider.contentUriFor(applicationContext, raw) ?: return item
        val extras = Bundle().apply {
            item.mediaMetadata.extras?.let { putAll(it) }   // zendernaam en groep blijven staan
            putString(EXTRA_LOGO_URL, raw)
        }
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(art).setExtras(extras).build())
            .build()
    }

    // Het zenderlogo van een spelend item als content://-adres (zonder extra's: wat er al in de artworkUri staat).
    private fun MediaItem.logoArtworkUri(): Uri? {
        val raw = mediaMetadata.extras?.getString(EXTRA_LOGO_URL) ?: return mediaMetadata.artworkUri
        return ArtworkProvider.contentUriFor(applicationContext, raw)
    }

    /**
     * Een zender zoals Android Auto hem in een lijst toont. Android Auto laadt alleen content://-plaatjes
     * (geen web-adressen); het logo gaat dus
     * via de ArtworkProvider. Voor het afspelen blijft het gewone item met het echte logo-adres staan.
     */
    private fun RadioStation.toBrowseItem(): MediaItem {
        val item = toMediaItem()
        val art = ArtworkProvider.contentUriFor(applicationContext, logoUrl) ?: return item
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(art).build())
            .build()
    }

    // De zichtbare zenders met dit genre
    private fun stationsOfGenre(dao: StationDao, genre: String): List<RadioStation> =
        dao.getEnabledStationsSync().filter { station -> station.genres().any { it.equals(genre, ignoreCase = true) } }

    private fun allGenres(dao: StationDao): List<String> =
        dao.getEnabledStationsSync()
            .flatMap { it.genres() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    // De recente zenders, nieuwste eerst; verborgen of verwijderde zenders vallen af.
    private fun recentStations(dao: StationDao): List<RadioStation> =
        runBlocking { repository().recentStationIds() }
            .mapNotNull { dao.getStationByIdSync(it) }
            .filter { it.isEnabled }

    // Waar een spraakopdracht zonder zendernaam ("Speel muziek") op uitkomt: de laatste zender, anders een favoriet.
    private fun defaultStation(dao: StationDao): RadioStation? {
        val last = runBlocking { repository().lastStationId() }
            ?.let { dao.getStationByIdSync(it) }
            ?.takeIf { it.isEnabled }
        return last ?: dao.getFavoriteStationsSync().firstOrNull() ?: dao.getEnabledStationsSync().firstOrNull()
    }

    private fun searchKey(text: String): String = text.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * Zoekt zenders bij een getypte of gesproken zoekterm: eerst op naam (de beste overeenkomst bovenaan), dan op
     * genre. "Favorieten" geeft de favorieten. Een lege zoekterm geeft alle zichtbare zenders.
     */
    private fun searchStations(query: String, dao: StationDao): List<RadioStation> {
        val key = searchKey(query)
        val all = dao.getEnabledStationsSync()
        if (key.isEmpty()) return all
        if (key.contains("favoriet") || key.contains("favorite")) return dao.getFavoriteStationsSync()

        val byName = all
            .filter {
                val name = searchKey(it.name)
                name.contains(key) || (name.length >= 3 && key.contains(name))
            }
            .sortedBy { kotlin.math.abs(searchKey(it.name).length - key.length) }
        if (byName.isNotEmpty()) return byName

        return all.filter { station ->
            station.genres().any {
                val genre = searchKey(it)
                genre.contains(key) || (genre.length >= 3 && key.contains(genre))
            }
        }
    }

    /**
     * Maakt van een verzoek van een controller (bijv. Android Auto, alleen mediaId of een zoekterm) afspeelbare items.
     * Een lege lijst betekent: niets gevonden.
     */
    private fun resolveMediaItems(item: MediaItem, dao: StationDao): List<MediaItem> {
        // Een zender uit de lijst altijd opnieuw uit de database halen: een item uit Android Auto heeft voor het
        // logo een content://-adres van de ArtworkProvider, en voor het afspelen willen we het echte logo-adres.
        val stationId = stationIdFromMediaId(item.mediaId)
        if (stationId != null) {
            val station = dao.getStationByIdSync(stationId)
            if (station != null) return listOf(station.toPlayItem())
        }

        if (item.localConfiguration != null) return listOf(item)

        // Spraakopdracht: "Speel Radio 538", "Speel favorieten", "Speel jazz" of gewoon "Speel muziek"
        val query = item.requestMetadata.searchQuery
        if (query != null || item.mediaId == MediaItem.DEFAULT_MEDIA_ID) {
            val station = if (query.isNullOrBlank()) defaultStation(dao) else searchStations(query, dao).firstOrNull()
            return listOfNotNull(station?.toPlayItem())
        }

        val requestedUri = item.requestMetadata.mediaUri
        if (requestedUri != null) {
            return listOf(item.buildUpon().setUri(requestedUri).build())
        }
        return emptyList()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // De eigen app is gewoon een speler-verbinding; elke andere (Android Auto, een auto-radio, een bluetooth-bediening) meldt zich als Auto.
            if (controller.packageName == packageName) {
                AppLog.v(LogCategory.AFSPELEN, "app verbonden met de afspeelservice")
            } else {
                AppLog.i(LogCategory.AUTO, "verbinding van ${controller.packageName}")
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(toggleFavoriteCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(ImmutableList.of(favoriteButton(heartShowsFavorite == true)))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_TOGGLE_FAVORITE) {
                val stationId = _playbackState.value.currentStationId
                if (stationId != null) {
                    serviceScope.launch {
                        val station = stationDao().getStationByIdSync(stationId) ?: return@launch
                        repository().setStationFavorite(stationId, !station.isFavorite)
                        // De lijst met favorieten verandert, en daarmee volgt het hartje vanzelf.
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Android Auto vraagt met "recent" om de zender waarmee het na het starten van de auto kan hervatten.
            val rootId = if (params?.isRecent == true) ROOT_RECENT_ID else ROOT_ID
            AppLog.v(LogCategory.AUTO, "${browser.packageName} vraagt de hoofdmap op${if (rootId == ROOT_RECENT_ID) " (om te hervatten)" else ""}")
            return Futures.immediateFuture(LibraryResult.ofItem(folderFor(rootId)!!, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val folder = folderFor(mediaId)
            if (folder != null) {
                return Futures.immediateFuture(LibraryResult.ofItem(folder, null))
            }

            val stationId = stationIdFromMediaId(mediaId)
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE)
                )

            return ioExecutor.submit(Callable {
                val station = stationDao().getStationByIdSync(stationId)
                if (station != null) {
                    LibraryResult.ofItem(station.toBrowseItem(), null)
                } else {
                    LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE)
                }
            })
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            AppLog.v(LogCategory.AUTO, "${browser.packageName} opent map $parentId (pagina $page)")
            return when {
                parentId == ROOT_ID -> Futures.immediateFuture(
                    LibraryResult.ofItemList(
                        ImmutableList.of(favoritesFolder(), recentFolder(), genresFolder(), allFolder()),
                        params
                    )
                )
                // Verborgen zenders zitten niet in de catalogus (alleen isEnabled == true).
                parentId == FOLDER_ALL -> loadStations(page, pageSize, params) { it.getEnabledStationsSync() }
                parentId == FOLDER_FAVORITES -> loadStations(page, pageSize, params) { it.getFavoriteStationsSync() }
                parentId == FOLDER_RECENT || parentId == ROOT_RECENT_ID ->
                    loadStations(page, pageSize, params) { recentStations(it) }
                parentId == FOLDER_GENRES -> loadItems(page, pageSize, params) {
                    allGenres(stationDao()).map { genreFolder(it) }
                }
                parentId.startsWith(GENRE_PREFIX) -> loadStations(page, pageSize, params) {
                    stationsOfGenre(it, parentId.removePrefix(GENRE_PREFIX))
                }
                else -> Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)
                )
            }
        }

        // Zoeken in Android Auto (het zoekveld): eerst melden hoeveel er is, daarna haalt Auto de lijst op.
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            // Alleen dat er gezocht is, niet waarnaar (dat kan een gesproken zoekopdracht zijn).
            AppLog.i(LogCategory.AUTO, "${browser.packageName} zoekt een zender")
            return ioExecutor.submit(Callable<LibraryResult<Void>> {
                val count = searchStations(query, stationDao()).size
                mainHandler.post {
                    if (mediaLibrarySession != null) {
                        session.notifySearchResultChanged(browser, query, count, params)
                    }
                }
                LibraryResult.ofVoid()
            })
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            loadStations(page, pageSize, params) { searchStations(query, it) }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return ioExecutor.submit(Callable {
                val dao = stationDao()
                mediaItems.flatMap { resolveMediaItems(it, dao) }.toMutableList()
            })
        }

        // Auto start (of een mediaknop op het stuur) terwijl er niets in de speler staat: de laatste zender hervatten.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            AppLog.i(LogCategory.AUTO, "${controller.packageName} vraagt om het hervatten van de laatste zender")
            return ioExecutor.submit(Callable<MediaSession.MediaItemsWithStartPosition> {
                val station = defaultStation(stationDao())
                    ?: throw IllegalStateException("Geen zender om te hervatten")
                MediaSession.MediaItemsWithStartPosition(listOf(station.toPlayItem()), 0, C.TIME_UNSET)
            })
        }
    }
}
