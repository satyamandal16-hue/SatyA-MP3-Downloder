package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.LinkProbeResult
import com.example.data.model.MediaCategory
import com.example.data.prefs.EnginePreferences
import com.example.data.prefs.EngineSettings
import com.example.download.AudioExtractor
import com.example.download.LinkInspector
import com.example.download.MultiSegmentDownloadEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val preferences: EnginePreferences,
    private val inspector: LinkInspector,
    private val engine: MultiSegmentDownloadEngine
) {
    val allDownloads: Flow<List<DownloadEntity>> = dao.observeAllDownloads()
    val engineSettings: Flow<EngineSettings> = preferences.settingsFlow

    fun observeDownload(id: Long): Flow<DownloadEntity?> = dao.observeDownloadById(id)
    fun observeSegments(downloadId: Long): Flow<List<DownloadSegmentEntity>> = dao.observeSegments(downloadId)

    suspend fun probeUrl(url: String): LinkProbeResult = inspector.inspect(url)

    suspend fun enqueueDownload(
        url: String,
        fileName: String,
        mimeType: String,
        category: MediaCategory,
        qualityLabel: String,
        totalBytes: Long,
        supportsRange: Boolean,
        threadCount: Int,
        extractAudioTrack: Boolean = false
    ): Long {
        val cleanUrl = LinkInspector.normalizeUrl(url)
        val safeName = LinkInspector.sanitizeFileName(fileName)
        val entity = DownloadEntity(
            url = cleanUrl,
            fileName = safeName,
            mimeType = mimeType.ifBlank { LinkInspector.guessMimeFromName(safeName) },
            category = category,
            qualityLabel = qualityLabel,
            totalBytes = totalBytes,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            threadCount = threadCount.coerceIn(1, 16),
            supportsRange = supportsRange,
            extractAudioTrack = extractAudioTrack
        )
        val id = dao.insertDownload(entity)
        engine.startOrResume(id)
        return id
    }

    suspend fun enqueueQuickUrl(url: String, preferredCategory: MediaCategory? = null): Long {
        val settings = preferences.settingsFlow.first()
        val probe = inspector.inspect(url)
        val category = preferredCategory ?: probe.category
        val qualityLabel = when (category) {
            MediaCategory.HD_AUDIO -> "320 kbps HD MP3"
            MediaCategory.HD_VIDEO -> "1080p HD Video"
            else -> "Multi-Thread"
        }
        return enqueueDownload(
            url = probe.resolvedUrl.ifBlank { url },
            fileName = probe.fileName,
            mimeType = probe.mimeType,
            category = category,
            qualityLabel = qualityLabel,
            totalBytes = probe.contentLength,
            supportsRange = probe.supportsRange,
            threadCount = settings.defaultThreads,
            extractAudioTrack = settings.autoExtractMp3FromVideo && category == MediaCategory.HD_VIDEO
        )
    }

    fun pauseDownload(id: Long) = engine.pause(id)
    fun resumeDownload(id: Long) = engine.startOrResume(id)
    fun cancelDownload(id: Long) = engine.cancelAndRemoveParts(id)

    suspend fun deleteDownload(item: DownloadEntity) {
        engine.cancelAndRemoveParts(item.id)
        item.localFilePath?.let { path -> runCatching { File(path).delete() } }
        item.localUri?.let { uriStr ->
            if (uriStr.startsWith("content://") && !uriStr.contains(".fileprovider")) {
                runCatching { context.contentResolver.delete(Uri.parse(uriStr), null, null) }
            }
        }
        dao.deleteDownloadById(item.id)
    }

    suspend fun clearCompleted() {
        dao.clearCompleted()
    }

    suspend fun extractAudioFromCompletedVideo(item: DownloadEntity): Result<Unit> {
        val path = item.localFilePath ?: return Result.failure(IllegalStateException("Video file not available locally"))
        val file = File(path)
        if (!file.exists()) return Result.failure(IllegalStateException("Source video missing"))

        return AudioExtractor.extractAudioFromVideo(context, file, item.fileName).map { (saved, size) ->
            val audioName = "${item.fileName.substringBeforeLast('.')}_HD.m4a"
            dao.insertDownload(
                DownloadEntity(
                    url = item.url,
                    fileName = audioName,
                    mimeType = "audio/mp4",
                    category = MediaCategory.HD_AUDIO,
                    qualityLabel = "Extracted HD Audio",
                    totalBytes = size,
                    downloadedBytes = size,
                    status = DownloadStatus.COMPLETED,
                    threadCount = 1,
                    supportsRange = false,
                    localUri = saved.uriString,
                    localFilePath = saved.localFilePath,
                    completedAt = System.currentTimeMillis()
                )
            )
            Unit
        }
    }

    suspend fun setDefaultThreads(threads: Int) = preferences.setDefaultThreads(threads)
    suspend fun setMaxConcurrent(count: Int) = preferences.setMaxConcurrent(count)
    suspend fun setWifiOnly(enabled: Boolean) = preferences.setWifiOnly(enabled)
    suspend fun setSpeedLimitKbps(kbps: Int) = preferences.setSpeedLimitKbps(kbps)
    suspend fun setAutoExtractMp3(enabled: Boolean) = preferences.setAutoExtractMp3(enabled)
    suspend fun setDarkTheme(enabled: Boolean) = preferences.setDarkTheme(enabled)
}
