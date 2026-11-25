package io.github.onneau.sceneradio.data.repository

import android.content.Context
import io.github.onneau.sceneradio.data.DefaultPlaylistSource
import io.github.onneau.sceneradio.data.api.RadioBrowserStationItem
import io.github.onneau.sceneradio.data.local.RadioDatabase
import io.github.onneau.sceneradio.data.local.RadioPreferences
import io.github.onneau.sceneradio.data.manage.LocalLogos
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.data.model.RadioStation
import io.github.onneau.sceneradio.data.parser.M3uParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// Zenders die je zelf in de app hebt toegevoegd (kolom "format"). Een update van de standaard zenderlijst laat
// ze staan, tot ze zelf in de lijst opduiken.
const val LOCAL_STATION_MARK = "eigen"

// Uitkomst van het bijwerken van de standaard zenderlijst.
data class DefaultPlaylistResult(
    val total: Int,
    val added: Int,
    val updated: Int,
    val removed: Int
)

class RadioRepository(private val context: Context) {

    private companion object {
        // Vaste bron-aanduiding voor de eigen afspeellijst met Radio Browser-zenders.
        const val RADIO_BROWSER_SOURCE = "radio-browser"
    }

    private val db = RadioDatabase.getInstance(context)
    private val stationDao = db.stationDao()
    private val playlistDao = db.playlistDao()
    val preferences = RadioPreferences(context)

    val allStations: Flow<List<RadioStation>> = stationDao.getAllStations()
    val enabledStations: Flow<List<RadioStation>> = stationDao.getEnabledStations()
    val favoriteStations: Flow<List<RadioStation>> = stationDao.getFavoriteStations()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val lastStationIdFlow: Flow<Long?> = preferences.lastStationIdFlow

    suspend fun getStationById(id: Long): RadioStation? {
        return stationDao.getStationById(id)
    }

    suspend fun setStationEnabled(stationId: Long, enabled: Boolean) {
        stationDao.setStationEnabled(stationId, enabled)
    }

    suspend fun setStationFavorite(stationId: Long, isFavorite: Boolean) {
        stationDao.setStationFavorite(stationId, isFavorite)
    }

    suspend fun updateStationLogo(stationId: Long, logoUrl: String) {
        stationDao.updateStationLogo(stationId, logoUrl)
    }

    suspend fun setLastStationId(stationId: Long) {
        preferences.setLastStationId(stationId)
    }

    suspend fun clearLastStationId() {
        preferences.clearLastStationId()
    }

    suspend fun lastStationId(): Long? = lastStationIdFlow.first()

    suspend fun recentStationIds(): List<Long> = preferences.recentStationIds()

    suspend fun addRecentStation(stationId: Long) {
        preferences.addRecentStation(stationId)
    }

    // Afspeellijsten beheren
    suspend fun createPlaylist(name: String, sourceUrl: String? = null): Long {
        val pl = Playlist(name = name, sourceUrl = sourceUrl, stationCount = 0)
        return playlistDao.insertPlaylist(pl)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val pl = playlistDao.getPlaylistById(playlistId)
        if (pl != null) {
            playlistDao.updatePlaylist(pl.copy(name = newName))
        }
    }

    suspend fun clearPlaylistStations(playlistId: Long) {
        stationDao.deleteStationsByPlaylist(playlistId)
        val pl = playlistDao.getPlaylistById(playlistId)
        if (pl != null) {
            playlistDao.updatePlaylist(pl.copy(stationCount = 0))
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        stationDao.deleteStationsByPlaylist(playlistId)
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun importM3uContent(content: String, playlistName: String, sourceUrl: String? = null): Int {
        // Eerst parsen: een lijst zonder zenders mag geen lege afspeellijst achterlaten.
        val parsed = M3uParser.parse(content)
        if (parsed.isEmpty()) return 0

        val playlistId = createPlaylist(playlistName, sourceUrl)
        val stations = parsed.map {
            it.copy(playlistId = playlistId, logoUrl = resolveLogoUrl(it.logoUrl))
        }
        stationDao.insertStations(stations)

        val pl = playlistDao.getPlaylistById(playlistId)
        if (pl != null) {
            playlistDao.updatePlaylist(pl.copy(stationCount = stations.size))
        }
        return stations.size
    }

    private val assetFolderCache = mutableMapOf<String, Set<String>>()

    // Een logo in een M3U kan een volledige URL zijn (blijft zoals het is) of een relatief pad zoals
    // "logos/npo-radio-1.jpg". Dat laatste werkt alleen als het bestand in de app zit (assets);
    // anders geven we null terug, zodat het logo automatisch via Radio Browser gezocht wordt.
    private fun resolveLogoUrl(raw: String?): String? {
        val logo = raw?.trim()
        if (logo.isNullOrEmpty()) return null

        val lower = logo.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("file://") || lower.startsWith("content://") || lower.startsWith("data:")
        ) {
            return logo
        }

        val path = logo.removePrefix("./").trimStart('/').substringBefore('?')
        val folder = path.substringBeforeLast('/', "")
        val file = path.substringAfterLast('/')
        val files = assetFolderCache.getOrPut(folder) {
            try {
                context.assets.list(folder)?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        return if (file in files) "file:///android_asset/$path" else null
    }

    suspend fun defaultPlaylistId(): Long? =
        playlistDao.getPlaylistBySource(DefaultPlaylistSource.SOURCE_ID)?.id

    suspend fun hasDefaultPlaylist(): Boolean =
        playlistDao.getPlaylistBySource(DefaultPlaylistSource.SOURCE_ID) != null

    private fun normalizeStreamUrl(url: String): String =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')

    /**
     * Werkt de standaard zenderlijst bij: bestaande zenders (herkend aan stream-URL of naam) blijven
     * dezelfde, met hun favoriet- en verborgen-instelling. Nieuwe zenders komen erbij; zenders die niet
     * meer in de lijst staan verdwijnen, behalve favorieten. Een lijst zonder zenders verandert niets.
     */
    suspend fun applyDefaultPlaylist(content: String, version: String): DefaultPlaylistResult {
        val parsed = M3uParser.parse(content)
        if (parsed.isEmpty()) return DefaultPlaylistResult(0, 0, 0, 0)

        val playlistId = playlistDao.getPlaylistBySource(DefaultPlaylistSource.SOURCE_ID)?.id
            ?: playlistDao.insertPlaylist(
                Playlist(
                    name = DefaultPlaylistSource.PLAYLIST_NAME,
                    sourceUrl = DefaultPlaylistSource.SOURCE_ID
                )
            )

        val existing = stationDao.getStationsByPlaylistOnce(playlistId)
        val byUrl = existing.associateBy { normalizeStreamUrl(it.streamUrl) }
        val byName = existing.associateBy { it.name.trim().lowercase() }

        val matchedIds = mutableSetOf<Long>()
        val toInsert = mutableListOf<RadioStation>()
        var updated = 0

        for (incoming in parsed) {
            val logo = DefaultPlaylistSource.resolveLogo(incoming.logoUrl, version)
            val match = (byUrl[normalizeStreamUrl(incoming.streamUrl)] ?: byName[incoming.name.trim().lowercase()])
                ?.takeIf { it.id !in matchedIds }

            if (match != null) {
                matchedIds.add(match.id)
                val merged = match.copy(
                    name = incoming.name,
                    streamUrl = incoming.streamUrl,
                    groupTitle = incoming.groupTitle,
                    // Een logo dat je zelf op dit toestel hebt gekozen blijft staan. Anders: geen logo in de
                    // nieuwe lijst betekent dat het eerder gevonden logo blijft staan.
                    logoUrl = if (LocalLogos.isLocal(match.logoUrl)) match.logoUrl else (logo ?: match.logoUrl),
                    orderIndex = incoming.orderIndex,
                    // Staat de zender nu in de lijst, dan is hij niet langer alleen van dit toestel.
                    format = match.format.takeUnless { it == LOCAL_STATION_MARK }
                )
                if (merged != match) {
                    stationDao.updateStation(merged)
                    updated++
                }
            } else {
                toInsert.add(incoming.copy(playlistId = playlistId, logoUrl = logo))
            }
        }

        if (toInsert.isNotEmpty()) stationDao.insertStations(toInsert)

        val stale = existing.filter { it.id !in matchedIds && !it.isFavorite && it.format != LOCAL_STATION_MARK }
        stale.forEach { stationDao.deleteStation(it) }

        val total = stationDao.getStationsByPlaylistOnce(playlistId).size
        playlistDao.getPlaylistById(playlistId)?.let {
            playlistDao.updatePlaylist(it.copy(stationCount = total))
        }
        return DefaultPlaylistResult(total, toInsert.size, updated, stale.size)
    }

    private suspend fun ensureDefaultPlaylistId(): Long =
        playlistDao.getPlaylistBySource(DefaultPlaylistSource.SOURCE_ID)?.id
            ?: playlistDao.insertPlaylist(
                Playlist(
                    name = DefaultPlaylistSource.PLAYLIST_NAME,
                    sourceUrl = DefaultPlaylistSource.SOURCE_ID
                )
            )

    /** Stream-URL en genres van een bestaande zender aanpassen (de naam blijft zoals hij is). */
    suspend fun updateStationDetails(stationId: Long, streamUrl: String, groupTitle: String) {
        val station = stationDao.getStationById(stationId) ?: return
        stationDao.updateStation(station.copy(streamUrl = streamUrl, groupTitle = groupTitle))
    }

    /**
     * Voegt een zender toe aan de standaard zenderlijst, onderaan. Hij is gemarkeerd als "eigen", zodat een
     * update van de lijst hem niet weghaalt (tenzij de zender later zelf in de lijst komt). Geeft het id terug.
     */
    suspend fun addLocalStation(name: String, streamUrl: String, groupTitle: String): Long {
        val playlistId = ensureDefaultPlaylistId()
        val existing = stationDao.getStationsByPlaylistOnce(playlistId)
        val next = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val id = stationDao.insertStation(
            RadioStation(
                playlistId = playlistId,
                name = name,
                streamUrl = streamUrl,
                groupTitle = groupTitle,
                isEnabled = true,
                orderIndex = next,
                format = LOCAL_STATION_MARK
            )
        )
        playlistDao.getPlaylistById(playlistId)?.let {
            playlistDao.updatePlaylist(it.copy(stationCount = existing.size + 1))
        }
        return id
    }

    // Radio Browser-zenders krijgen een eigen afspeellijst, zodat het id niet botst met een M3U-lijst.
    private suspend fun getOrCreateRadioBrowserPlaylistId(): Long {
        val existing = playlistDao.getPlaylistBySource(RADIO_BROWSER_SOURCE)
        if (existing != null) return existing.id
        return playlistDao.insertPlaylist(
            Playlist(name = "Radio Browser", sourceUrl = RADIO_BROWSER_SOURCE)
        )
    }

    suspend fun importRadioBrowserStation(rbStation: RadioBrowserStationItem): RadioStation {
        val resolvedUrl = rbStation.urlResolved ?: rbStation.url
        val playlistId = getOrCreateRadioBrowserPlaylistId()
        val station = RadioStation(
            playlistId = playlistId,
            name = rbStation.name.trim(),
            streamUrl = resolvedUrl,
            logoUrl = rbStation.favicon?.takeIf { it.isNotBlank() },
            groupTitle = rbStation.tags?.split(",")?.firstOrNull()?.trim() ?: "Radio",
            isEnabled = true,
            isFavorite = false,
            bitrate = rbStation.bitrate
        )
        val insertedId = stationDao.insertStation(station)

        val pl = playlistDao.getPlaylistById(playlistId)
        if (pl != null) {
            playlistDao.updatePlaylist(pl.copy(stationCount = pl.stationCount + 1))
        }
        return station.copy(id = insertedId)
    }
}
