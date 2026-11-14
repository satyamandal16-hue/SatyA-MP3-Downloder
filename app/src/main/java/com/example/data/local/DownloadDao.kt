package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeDownloadById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun getDownloadsByStatus(statuses: List<DownloadStatus>): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String? = null)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, speedBytesPerSec = :speed, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedBytes: Long, speed: Long, totalBytes: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    // Segment queries
    @Query("SELECT * FROM download_segments WHERE downloadId = :downloadId ORDER BY segmentIndex ASC")
    fun observeSegments(downloadId: Long): Flow<List<DownloadSegmentEntity>>

    @Query("SELECT * FROM download_segments WHERE downloadId = :downloadId ORDER BY segmentIndex ASC")
    suspend fun getSegments(downloadId: Long): List<DownloadSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<DownloadSegmentEntity>)

    @Query("UPDATE download_segments SET downloadedBytes = :downloadedBytes, isComplete = :isComplete WHERE downloadId = :downloadId AND segmentIndex = :segmentIndex")
    suspend fun updateSegmentProgress(downloadId: Long, segmentIndex: Int, downloadedBytes: Long, isComplete: Boolean)

    @Query("DELETE FROM download_segments WHERE downloadId = :downloadId")
    suspend fun deleteSegmentsForDownload(downloadId: Long)
}
