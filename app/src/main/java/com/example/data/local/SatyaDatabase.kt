package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaCategory

class SatyaTypeConverters {
    @TypeConverter
    fun fromCategory(value: MediaCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): MediaCategory =
        runCatching { MediaCategory.valueOf(value) }.getOrDefault(MediaCategory.OTHER)

    @TypeConverter
    fun fromStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)
}

@Database(
    entities = [DownloadEntity::class, DownloadSegmentEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SatyaTypeConverters::class)
abstract class SatyaDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: SatyaDatabase? = null

        fun getInstance(context: Context): SatyaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SatyaDatabase::class.java,
                    "satya_downloads.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
