package io.github.onneau.sceneradio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class RadioStation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long = 1,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0,
    val bitrate: Int? = null,
    val format: String? = null,
    val isFavorite: Boolean = false
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sourceUrl: String? = null,
    val isLocalFile: Boolean = false,
    val stationCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
