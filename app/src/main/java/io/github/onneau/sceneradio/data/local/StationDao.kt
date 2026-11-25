package io.github.onneau.sceneradio.data.local

import androidx.room.*
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.data.model.RadioStation
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY orderIndex ASC, id ASC")
    fun getAllStations(): Flow<List<RadioStation>>

    @Query("SELECT * FROM stations WHERE isEnabled = 1 ORDER BY orderIndex ASC, id ASC")
    fun getEnabledStations(): Flow<List<RadioStation>>

    @Query("SELECT * FROM stations WHERE isEnabled = 1 ORDER BY orderIndex ASC, id ASC")
    fun getEnabledStationsSync(): List<RadioStation>

    @Query("SELECT * FROM stations WHERE isEnabled = 1 AND isFavorite = 1 ORDER BY orderIndex ASC, id ASC")
    fun getFavoriteStations(): Flow<List<RadioStation>>

    @Query("SELECT * FROM stations WHERE isEnabled = 1 AND isFavorite = 1 ORDER BY orderIndex ASC, id ASC")
    fun getFavoriteStationsSync(): List<RadioStation>

    @Query("SELECT * FROM stations WHERE playlistId = :playlistId ORDER BY orderIndex ASC, id ASC")
    fun getStationsByPlaylist(playlistId: Long): Flow<List<RadioStation>>

    @Query("SELECT * FROM stations WHERE playlistId = :playlistId ORDER BY orderIndex ASC, id ASC")
    suspend fun getStationsByPlaylistOnce(playlistId: Long): List<RadioStation>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: Long): RadioStation?

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    fun getStationByIdSync(id: Long): RadioStation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RadioStation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RadioStation): Long

    @Update
    suspend fun updateStation(station: RadioStation)

    @Query("UPDATE stations SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setStationEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE stations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setStationFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE stations SET logoUrl = :logoUrl WHERE id = :id")
    suspend fun updateStationLogo(id: Long, logoUrl: String)

    @Query("UPDATE stations SET orderIndex = :newOrder WHERE id = :id")
    suspend fun updateStationOrder(id: Long, newOrder: Int)

    @Delete
    suspend fun deleteStation(station: RadioStation)

    @Query("DELETE FROM stations WHERE playlistId = :playlistId")
    suspend fun deleteStationsByPlaylist(playlistId: Long)

    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Query("SELECT * FROM playlists WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getPlaylistBySource(sourceUrl: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}
