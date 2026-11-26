package io.github.onneau.sceneradio.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import io.github.onneau.sceneradio.AppVersion
import io.github.onneau.sceneradio.data.DefaultPlaylistSource
import io.github.onneau.sceneradio.data.export.DownloadsSaver
import io.github.onneau.sceneradio.data.export.SavedFile
import io.github.onneau.sceneradio.data.local.RadioDatabase
import io.github.onneau.sceneradio.data.local.RadioPreferences
import io.github.onneau.sceneradio.data.local.radioDataStore
import io.github.onneau.sceneradio.data.log.AppLog
import io.github.onneau.sceneradio.data.log.LogCategory
import io.github.onneau.sceneradio.data.manage.LocalLogos
import io.github.onneau.sceneradio.data.manage.LogoTools
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.model.VisualizerSettings
import io.github.onneau.sceneradio.data.repository.LOCAL_STATION_MARK
import io.github.onneau.sceneradio.i18n.Lang
import io.github.onneau.sceneradio.i18n.LanguageSetting
import io.github.onneau.sceneradio.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Waar een back-up uit bestaat. De gebruiker kiest per onderdeel of het erin komt. */
enum class BackupPart { SETTINGS, FAVORITES, OWN_STATIONS, OWN_LOGOS, PLAYLISTS }

/** Wat er op dit toestel te bewaren valt, voor het scherm Back-up. */
data class BackupCounts(
    val favorites: Int,
    val hidden: Int,
    val own: Int,
    val logos: Int,
    val logoBytes: Long,
    val playlists: Int
)

/** Eén zender in een back-up. De zender wordt herkend aan zijn stream-adres, niet aan het interne nummer. */
data class BackupStation(
    val name: String,
    val url: String,
    val group: String?,
    val logo: String?,
    val favorite: Boolean,
    val hidden: Boolean,
    val own: Boolean,
    val order: Int
)

data class BackupPlaylist(
    val name: String,
    val source: String?,
    val local: Boolean,
    val stations: List<BackupStation>
)

/** Een gelezen en gecontroleerde back-up. Er is nog niets mee veranderd op dit toestel. */
class ParsedBackup(
    val app: String,
    val appVersion: String,
    val createdAt: Long,
    val prefs: Map<String, Any>,
    val language: String?,
    val stations: List<BackupStation>,
    val playlists: List<BackupPlaylist>,
    val logos: Map<String, ByteArray>
) {
    val hasSettings: Boolean get() = prefs.isNotEmpty() || language != null
    val favorites: Int get() = stations.count { it.favorite } + playlists.sumOf { pl -> pl.stations.count { it.favorite } }
    val hidden: Int get() = stations.count { it.hidden } + playlists.sumOf { pl -> pl.stations.count { it.hidden } }
    val own: Int get() = stations.count { it.own }
    val playlistStations: Int get() = playlists.sumOf { it.stations.size }
}

/** Uitkomst van het terugzetten. */
data class RestoreResult(
    val settings: Boolean,
    val addedStations: Int,
    val addedPlaylists: Int,
    val logos: Int,
    val logosSkipped: Int
)

/** Een fout met een melding die zo aan de gebruiker getoond kan worden. */
class BackupException(message: String) : Exception(message)

/**
 * In-app back-up van de instellingen en de persoonlijke wijzigingen, als één JSON-bestand.
 *
 * - Nooit in de back-up: tokens (die heeft de app niet eens in deze instellingen), de standaard zenderlijst zelf,
 *   hoezen en foto's (cache) en het logboek.
 * - Terugzetten voegt toe en overschrijft de instellingen; er wordt geen zender verwijderd. Het bestand wordt eerst
 *   helemaal gecontroleerd, daarna gaan de wijzigingen in de database in één transactie (alles of niets).
 * - Er komt niets van buiten het bestand in de app: alleen bekende instellingen met het juiste type, alleen
 *   http- en https-adressen en logo's alleen als ze een echt plaatje zijn.
 */
class BackupManager(context: Context) {

    private val context = context.applicationContext
    private val db = RadioDatabase.getInstance(this.context)
    private val stationDao = db.stationDao()
    private val playlistDao = db.playlistDao()
    private val prefs = RadioPreferences(this.context)

    companion object {
        const val APP_ID = "sceneradio"
        private const val FORMAT = "radio-backup"
        private const val FORMAT_VERSION = 1
        private const val MIME = "application/json"
        private const val FILE_PREFIX = "sceneradio-backup-"

        const val MAX_BYTES = 10 * 1024 * 1024
        private const val MAX_STATIONS = 20_000
        private const val MAX_PLAYLISTS = 200
        private const val MAX_LOGOS = 2_000
        private const val MAX_LOGO_BYTES = 600 * 1024
        private const val MAX_TEXT = 20_000

        private val URL_PATTERN = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

        // Welke instellingen mee gaan. Alleen deze lijst wordt gelezen en teruggezet; alles wat er niet in staat
        // (tokens, tijdstippen van controles, nummers van zenders) blijft buiten de back-up.
        private val BOOL_KEYS = listOf(
            RadioPreferences.KEY_UPDATE_AUTO, RadioPreferences.KEY_LARGE_CONTROLS,
            RadioPreferences.KEY_VIS_SLOGAN_FIXED, RadioPreferences.KEY_VIS_SPARKLE, RadioPreferences.KEY_VIS_COVER,
            RadioPreferences.KEY_VIS_COVER_PLAYER, RadioPreferences.KEY_VIS_ARTIST_PHOTO, RadioPreferences.KEY_VIS_DIM_ENABLED
        )
        private val FLOAT_KEYS = listOf(
            RadioPreferences.KEY_VIS_WAVE_SPEED, RadioPreferences.KEY_VIS_COPPER_SPEED, RadioPreferences.KEY_VIS_HAZE_SPEED,
            RadioPreferences.KEY_VIS_STAR_SPEED, RadioPreferences.KEY_VIS_TITLE_SCALE, RadioPreferences.KEY_VIS_SCROLLER_RATIO,
            RadioPreferences.KEY_VIS_HAZE_AMPLITUDE, RadioPreferences.KEY_VIS_COPPER_HUE, RadioPreferences.KEY_VIS_EQUALIZER_HUE,
            RadioPreferences.KEY_VIS_BACKGROUND_HUE, RadioPreferences.KEY_VIS_SLOGAN_HUE, RadioPreferences.KEY_VIS_COPPER_DRIFT_RANGE,
            RadioPreferences.KEY_VIS_EQ_SENSITIVITY, RadioPreferences.KEY_VIS_DIM_LEVEL
        )
        private val INT_KEYS = listOf(
            RadioPreferences.KEY_VIS_SLOGAN_PX, RadioPreferences.KEY_VIS_SLOGANS_VERSION,
            RadioPreferences.KEY_VIS_COPPER_CYCLE_SECONDS, RadioPreferences.KEY_VIS_COPPER_DRIFT_SECONDS,
            RadioPreferences.KEY_VIS_DIM_DELAY
        )
        private val STRING_KEYS = listOf(
            RadioPreferences.KEY_THEME_MODE, RadioPreferences.KEY_STATION_SORT, RadioPreferences.KEY_VIS_SLOGANS,
            RadioPreferences.KEY_VIS_COPPER_MODE, RadioPreferences.KEY_VIS_COVER_SIZE, RadioPreferences.KEY_VIS_COVER_POSITION
        )

        private fun norm(url: String): String =
            url.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')

        /** De bestandsnaam van een back-up van vandaag. */
        fun fileName(): String =
            FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

        /** Leest een gekozen bestand, maar nooit meer dan [MAX_BYTES]. */
        fun readLimited(stream: InputStream): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) {
                    throw BackupException(tr("Dit bestand is te groot voor een back-up (maximaal 10 MB).", "This file is too large for a backup (maximum 10 MB)."))
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tellen: wat staat er op dit toestel
    // ---------------------------------------------------------------------------------------------

    suspend fun counts(): BackupCounts = withContext(Dispatchers.IO) {
        val stations = stationDao.getAllStationsOnce()
        val local = stations.filter { LocalLogos.isLocal(it.logoUrl) }
        BackupCounts(
            favorites = stations.count { it.isFavorite },
            hidden = stations.count { !it.isEnabled },
            own = stations.count { it.format == LOCAL_STATION_MARK },
            logos = local.size,
            logoBytes = local.sumOf { localLogoFile(it.logoUrl)?.length() ?: 0L },
            playlists = playlistDao.getAllPlaylistsOnce().count { it.sourceUrl != DefaultPlaylistSource.SOURCE_ID }
        )
    }

    /** Of de standaard zenderlijst al op dit toestel staat (anders kunnen verborgen zenders niet worden teruggezet). */
    suspend fun defaultListPresent(): Boolean = withContext(Dispatchers.IO) {
        val id = playlistDao.getPlaylistBySource(DefaultPlaylistSource.SOURCE_ID)?.id ?: return@withContext false
        stationDao.getStationsByPlaylistOnce(id).isNotEmpty()
    }

    // ---------------------------------------------------------------------------------------------
    // Maken
    // ---------------------------------------------------------------------------------------------

    /** Het bestand van een logo dat je zelf koos, alleen als het echt in de logomap van de app staat. */
    private fun localLogoFile(url: String?): File? {
        if (url == null || !LocalLogos.isLocal(url)) return null
        return try {
            val file = File(requireNotNull(Uri.parse(url).path)).canonicalFile
            val dir = File(context.filesDir, "logos").canonicalFile
            if (file.parentFile == dir && file.isFile) file else null
        } catch (e: Exception) {
            null
        }
    }

    private fun station(
        s: RadioStation,
        parts: Set<BackupPart>,
        logos: MutableMap<String, ByteArray>
    ): JSONObject? {
        val favorite = BackupPart.FAVORITES in parts && s.isFavorite
        val hidden = BackupPart.FAVORITES in parts && !s.isEnabled
        val own = BackupPart.OWN_STATIONS in parts && s.format == LOCAL_STATION_MARK
        var hasLogo = false
        if (BackupPart.OWN_LOGOS in parts) {
            val file = localLogoFile(s.logoUrl)
            if (file != null && file.length() in 1..MAX_LOGO_BYTES.toLong()) {
                logos[norm(s.streamUrl)] = file.readBytes()
                hasLogo = true
            }
        }
        return JSONObject().apply {
            put("name", s.name)
            put("url", s.streamUrl)
            s.groupTitle?.let { put("group", it) }
            if (s.logoUrl != null && URL_PATTERN.matches(s.logoUrl)) put("logo", s.logoUrl)
            if (favorite) put("favorite", true)
            if (hidden) put("hidden", true)
            if (own) put("own", true)
            put("order", s.orderIndex)
            put("flagged", favorite || hidden || own || hasLogo)
        }
    }

    suspend fun create(parts: Set<BackupPart>): String = withContext(Dispatchers.IO) {
        val allStations = stationDao.getAllStationsOnce()
        val allPlaylists = playlistDao.getAllPlaylistsOnce()
        val defaultId = allPlaylists.firstOrNull { it.sourceUrl == DefaultPlaylistSource.SOURCE_ID }?.id
        val logos = LinkedHashMap<String, ByteArray>()

        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("app", APP_ID)
        root.put("appVersion", AppVersion.NUMBER)
        root.put("createdAt", System.currentTimeMillis())

        if (BackupPart.SETTINGS in parts) root.put("settings", settingsJson())

        // De standaardlijst: alleen de zenders waar je zelf iets aan deed.
        val flagged = JSONArray()
        for (s in allStations) {
            if (s.playlistId != defaultId) continue
            val record = station(s, parts, logos) ?: continue
            if (record.optBoolean("flagged")) {
                record.remove("flagged")
                flagged.put(record)
            }
        }
        root.put("stations", flagged)

        // Geïmporteerde afspeellijsten: helemaal, met hun zenders.
        val playlists = JSONArray()
        if (BackupPart.PLAYLISTS in parts) {
            for (pl in allPlaylists) {
                if (pl.id == defaultId || pl.sourceUrl == DefaultPlaylistSource.SOURCE_ID) continue
                val list = JSONArray()
                for (s in allStations) {
                    if (s.playlistId != pl.id) continue
                    val record = station(s, parts, logos) ?: continue
                    record.remove("flagged")
                    list.put(record)
                }
                playlists.put(
                    JSONObject().apply {
                        put("name", pl.name)
                        pl.sourceUrl?.let { put("source", it) }
                        if (pl.isLocalFile) put("local", true)
                        put("stations", list)
                    }
                )
            }
        }
        root.put("playlists", playlists)

        val logoArray = JSONArray()
        for ((url, bytes) in logos) {
            logoArray.put(JSONObject().put("url", url).put("jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }
        root.put("logos", logoArray)

        root.toString(1)
    }

    private suspend fun settingsJson(): JSONObject {
        val snapshot = context.radioDataStore.data.first()
        val effective = prefs.getVisualizerSettings()
        val out = JSONObject()
        for (key in BOOL_KEYS) snapshot[key]?.let { out.put(key.name, it) }
        for (key in FLOAT_KEYS) snapshot[key]?.let { out.put(key.name, it.toDouble()) }
        for (key in INT_KEYS) snapshot[key]?.let { out.put(key.name, it) }
        for (key in STRING_KEYS) snapshot[key]?.let { out.put(key.name, it) }
        // Oudere versies bewaarden slogans en koperkleur anders. Dan gaat de stand mee die de app nu laat zien.
        if (snapshot[RadioPreferences.KEY_VIS_SLOGANS] == null) {
            out.put(RadioPreferences.KEY_VIS_SLOGANS.name, effective.slogans.joinToString("\n"))
            out.put(RadioPreferences.KEY_VIS_SLOGANS_VERSION.name, VisualizerSettings.SLOGANS_VERSION)
        }
        if (snapshot[RadioPreferences.KEY_VIS_COPPER_MODE] == null) {
            out.put(RadioPreferences.KEY_VIS_COPPER_MODE.name, effective.copperMode.key)
        }
        return JSONObject().put("language", Lang.current.key).put("prefs", out)
    }

    /** Bewaart de back-up in Downloads. */
    suspend fun saveToDownloads(json: String): Result<SavedFile> = withContext(Dispatchers.IO) {
        DownloadsSaver.saveText(context, fileName(), MIME, json)
    }

    /** Het deelmenu van Android met de back-up als bijlage (via de FileProvider, map exports in de cache). */
    suspend fun shareIntent(json: String): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            folder.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
            val file = File(folder, fileName())
            file.writeText(json, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, tr("Back-up delen", "Share backup")).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }.also { result ->
            result.onSuccess { AppLog.i(LogCategory.DOWNLOADS, "back-up aan het deelmenu gegeven") }
                .onFailure { AppLog.e(LogCategory.DOWNLOADS, "delen van de back-up mislukt", it) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lezen en controleren
    // ---------------------------------------------------------------------------------------------

    private fun notABackup() = BackupException(tr("Dit is geen back-upbestand van deze app.", "This is not a backup file from this app."))

    private fun text(o: JSONObject, key: String, max: Int): String? {
        if (!o.has(key) || o.isNull(key)) return null
        val value = o.optString(key, "").trim()
        return if (value.isEmpty() || value.length > max) null else value
    }

    private fun record(o: JSONObject): BackupStation? {
        val name = text(o, "name", 200) ?: return null
        val url = text(o, "url", 2000) ?: return null
        if (!URL_PATTERN.matches(url)) return null
        val logo = text(o, "logo", 2000)?.takeIf { URL_PATTERN.matches(it) }
        return BackupStation(
            name = name,
            url = url,
            group = text(o, "group", 200),
            logo = logo,
            favorite = o.optBoolean("favorite", false),
            hidden = o.optBoolean("hidden", false),
            own = o.optBoolean("own", false),
            order = o.optInt("order", 0)
        )
    }

    private fun records(array: JSONArray?): List<BackupStation> {
        val list = ArrayList<BackupStation>()
        if (array == null) return list
        for (i in 0 until minOf(array.length(), MAX_STATIONS)) {
            val o = array.optJSONObject(i) ?: continue
            val r = record(o) ?: continue
            list.add(r)
        }
        return list
    }

    /** Controleert het bestand helemaal. Gooit een [BackupException] met een duidelijke melding als er iets niet klopt. */
    fun parse(bytes: ByteArray): ParsedBackup {
        if (bytes.size > MAX_BYTES) {
            throw BackupException(tr("Dit bestand is te groot voor een back-up (maximaal 10 MB).", "This file is too large for a backup (maximum 10 MB)."))
        }
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw notABackup()
        }
        if (root.optString("format") != FORMAT) throw notABackup()
        val version = root.optInt("formatVersion", 0)
        if (version < 1) throw notABackup()
        if (version > FORMAT_VERSION) {
            throw BackupException(tr("Deze back-up komt uit een nieuwere versie van de app. Werk de app eerst bij.", "This backup comes from a newer version of the app. Update the app first."))
        }

        val values = HashMap<String, Any>()
        var language: String? = null
        val settings = root.optJSONObject("settings")
        if (settings != null) {
            language = text(settings, "language", 10)?.takeIf { it == "system" || it == "nl" || it == "en" }
            val p = settings.optJSONObject("prefs")
            if (p != null) {
                for (key in BOOL_KEYS) { val v = p.opt(key.name); if (v is Boolean) values[key.name] = v }
                for (key in FLOAT_KEYS) { val v = p.opt(key.name); if (v is Number && v.toDouble().isFinite()) values[key.name] = v.toFloat() }
                for (key in INT_KEYS) { val v = p.opt(key.name); if (v is Number && v.toDouble().isFinite()) values[key.name] = v.toInt() }
                for (key in STRING_KEYS) { val v = p.opt(key.name); if (v is String && v.length <= MAX_TEXT) values[key.name] = v }
            }
        }

        val stations = records(root.optJSONArray("stations"))

        val playlists = ArrayList<BackupPlaylist>()
        val playlistArray = root.optJSONArray("playlists")
        if (playlistArray != null) {
            for (i in 0 until minOf(playlistArray.length(), MAX_PLAYLISTS)) {
                val o = playlistArray.optJSONObject(i) ?: continue
                val name = text(o, "name", 200) ?: continue
                val source = text(o, "source", 2000)
                if (source == DefaultPlaylistSource.SOURCE_ID) continue
                playlists.add(BackupPlaylist(name, source, o.optBoolean("local", false), records(o.optJSONArray("stations"))))
            }
        }

        val logos = HashMap<String, ByteArray>()
        val logoArray = root.optJSONArray("logos")
        if (logoArray != null) {
            for (i in 0 until minOf(logoArray.length(), MAX_LOGOS)) {
                val o = logoArray.optJSONObject(i) ?: continue
                val url = text(o, "url", 2000) ?: continue
                val data = text(o, "jpeg", MAX_LOGO_BYTES * 2) ?: continue
                val decoded = try {
                    Base64.decode(data, Base64.DEFAULT)
                } catch (e: IllegalArgumentException) {
                    continue
                }
                if (decoded.isEmpty() || decoded.size > MAX_LOGO_BYTES) continue
                logos[norm(url)] = decoded
            }
        }

        val parsed = ParsedBackup(
            app = text(root, "app", 40) ?: "",
            appVersion = text(root, "appVersion", 40) ?: "",
            createdAt = root.optLong("createdAt", 0L),
            prefs = values,
            language = language,
            stations = stations,
            playlists = playlists,
            logos = logos
        )
        if (!parsed.hasSettings && stations.isEmpty() && playlists.isEmpty() && logos.isEmpty()) {
            throw BackupException(tr("Deze back-up bevat niets om terug te zetten.", "This backup contains nothing to restore."))
        }
        return parsed
    }

    // ---------------------------------------------------------------------------------------------
    // Terugzetten
    // ---------------------------------------------------------------------------------------------

    suspend fun restore(backup: ParsedBackup): RestoreResult = withContext(Dispatchers.IO) {
        val logoJobs = ArrayList<Pair<Long, String>>() // (id van de zender, genormaliseerd stream-adres)
        var added = 0
        var addedPlaylists = 0

        // 1. Zenders en afspeellijsten, in één transactie: lukt het niet helemaal, dan verandert er niets.
        db.withTransaction {
            if (backup.stations.isNotEmpty()) {
                val defaultId = playlistDao.getPlaylistBySource(DefaultPlaylistSource.SOURCE_ID)?.id
                    ?: playlistDao.insertPlaylist(
                        Playlist(name = DefaultPlaylistSource.PLAYLIST_NAME, sourceUrl = DefaultPlaylistSource.SOURCE_ID)
                    )
                val existing = stationDao.getStationsByPlaylistOnce(defaultId)
                val byUrl = HashMap<String, RadioStation>()
                val byName = HashMap<String, RadioStation>()
                for (s in existing) {
                    byUrl.putIfAbsent(norm(s.streamUrl), s)
                    byName.putIfAbsent(s.name.trim().lowercase(), s)
                }
                var nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
                for (rec in backup.stations) {
                    val key = norm(rec.url)
                    val current = byUrl[key] ?: byName[rec.name.trim().lowercase()]
                    if (current != null) {
                        var updated = current
                        if (rec.favorite && !current.isFavorite) updated = updated.copy(isFavorite = true)
                        if (rec.hidden && current.isEnabled) updated = updated.copy(isEnabled = false)
                        if (updated != current) stationDao.updateStation(updated)
                        if (key in backup.logos) logoJobs.add(current.id to key)
                    } else if (rec.own || rec.favorite) {
                        // Staat de zender niet (meer) in de standaardlijst, dan komt hij terug als eigen zender.
                        val id = stationDao.insertStation(
                            RadioStation(
                                playlistId = defaultId,
                                name = rec.name,
                                streamUrl = rec.url,
                                logoUrl = rec.logo,
                                groupTitle = rec.group,
                                isEnabled = !rec.hidden,
                                orderIndex = nextOrder++,
                                format = LOCAL_STATION_MARK,
                                isFavorite = rec.favorite
                            )
                        )
                        added++
                        if (key in backup.logos) logoJobs.add(id to key)
                        byUrl[key] = RadioStation(id = id, playlistId = defaultId, name = rec.name, streamUrl = rec.url)
                    }
                }
                playlistDao.getPlaylistById(defaultId)?.let {
                    playlistDao.updatePlaylist(it.copy(stationCount = stationDao.getStationsByPlaylistOnce(defaultId).size))
                }
            }

            val knownPlaylists = playlistDao.getAllPlaylistsOnce()
            for (pl in backup.playlists) {
                val found = knownPlaylists.firstOrNull { c ->
                    if (pl.source != null) c.sourceUrl == pl.source else (c.sourceUrl == null && c.name == pl.name)
                }
                val playlistId = found?.id ?: playlistDao.insertPlaylist(
                    Playlist(name = pl.name, sourceUrl = pl.source, isLocalFile = pl.local)
                ).also { addedPlaylists++ }
                val existing = stationDao.getStationsByPlaylistOnce(playlistId)
                val byUrl = HashMap<String, RadioStation>()
                for (s in existing) byUrl.putIfAbsent(norm(s.streamUrl), s)
                for (rec in pl.stations) {
                    val key = norm(rec.url)
                    val current = byUrl[key]
                    if (current != null) {
                        var updated = current
                        if (rec.favorite && !current.isFavorite) updated = updated.copy(isFavorite = true)
                        if (rec.hidden && current.isEnabled) updated = updated.copy(isEnabled = false)
                        if (updated != current) stationDao.updateStation(updated)
                        if (key in backup.logos) logoJobs.add(current.id to key)
                    } else {
                        val id = stationDao.insertStation(
                            RadioStation(
                                playlistId = playlistId,
                                name = rec.name,
                                streamUrl = rec.url,
                                logoUrl = rec.logo,
                                groupTitle = rec.group,
                                isEnabled = !rec.hidden,
                                orderIndex = rec.order,
                                isFavorite = rec.favorite
                            )
                        )
                        added++
                        if (key in backup.logos) logoJobs.add(id to key)
                        byUrl[key] = RadioStation(id = id, playlistId = playlistId, name = rec.name, streamUrl = rec.url)
                    }
                }
                playlistDao.getPlaylistById(playlistId)?.let {
                    playlistDao.updatePlaylist(it.copy(stationCount = stationDao.getStationsByPlaylistOnce(playlistId).size))
                }
            }
        }

        // 2. Eigen logo's: een plaatje dat niet te lezen is, wordt overgeslagen.
        var logosSaved = 0
        var logosSkipped = 0
        for ((id, key) in logoJobs) {
            val raw = backup.logos[key] ?: continue
            try {
                val jpeg = LogoTools.toJpeg(raw)
                stationDao.updateStationLogo(id, LocalLogos.save(context, id, jpeg))
                logosSaved++
            } catch (e: Exception) {
                logosSkipped++
            }
        }

        // 3. Instellingen en taal.
        if (backup.prefs.isNotEmpty()) {
            context.radioDataStore.edit { e ->
                for (key in BOOL_KEYS) (backup.prefs[key.name] as? Boolean)?.let { e[key] = it }
                for (key in FLOAT_KEYS) (backup.prefs[key.name] as? Float)?.let { e[key] = it }
                for (key in INT_KEYS) (backup.prefs[key.name] as? Int)?.let { e[key] = it }
                for (key in STRING_KEYS) (backup.prefs[key.name] as? String)?.let { e[key] = it }
            }
        }
        backup.language?.let { withContext(Dispatchers.Main) { Lang.set(context, LanguageSetting.fromKey(it)) } }

        BackupInfo.markRestored(context)
        AppLog.i(
            LogCategory.DOWNLOADS,
            "back-up teruggezet: $added zenders erbij, $addedPlaylists afspeellijsten erbij, $logosSaved logo's, $logosSkipped logo's overgeslagen"
        )
        RestoreResult(backup.hasSettings, added, addedPlaylists, logosSaved, logosSkipped)
    }
}

/** Wanneer er voor het laatst een back-up is gemaakt, en of de kaart in Instellingen al is bekeken. */
object BackupInfo {
    private const val PREFS = "backup"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastMadeAt(context: Context): Long = prefs(context).getLong("last_made", 0L)

    fun markMade(context: Context) {
        prefs(context).edit().putLong("last_made", System.currentTimeMillis()).apply()
    }

    fun markRestored(context: Context) {
        prefs(context).edit().putLong("last_restored", System.currentTimeMillis()).apply()
    }

    fun seen(context: Context): Boolean = prefs(context).getBoolean("seen", false)

    fun markSeen(context: Context) {
        prefs(context).edit().putBoolean("seen", true).apply()
    }
}
