package io.github.onneau.sceneradio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.onneau.sceneradio.data.model.Playlist
import io.github.onneau.sceneradio.data.model.RadioStation

@Database(entities = [RadioStation::class, Playlist::class], version = 1, exportSchema = false)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun getInstance(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "sceneradio_radio.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
