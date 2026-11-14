package com.example.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaCategory
import com.example.data.model.SegmentProgress
import com.example.data.prefs.EnginePreferences
import com.example.data.prefs.EngineSettings
import com.example.ui.components.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MultiSegmentDownloadEngine(
    private val context: Context,
    private val dao: DownloadDao,
    private val preferences: EnginePreferences,
    private val inspector: LinkInspector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedOrCancelled = ConcurrentHashMap.newKeySet<Long>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Live telemetry maps for smooth 60fps Compose updates without hammering Room
    private val _liveSpeedHistory = MutableStateFlow<List<Float>>(List(24) { 0f })
    val liveSpeedHistory: StateFlow<List<Float>> = _liveSpeedHistory.asStateFlow()

    private val _totalAggregateSpeed = MutableStateFlow(0L)
    val totalAggregateSpeed: StateFlow<Long> = _totalAggregateSpeed.asStateFlow()

    private val _liveSegments = MutableStateFlow<Map<Long, List<SegmentProgress>>>(emptyMap())
    val liveSegments: StateFlow<Map<Long, List<SegmentProgress>>> = _liveSegments.asStateFlow()

    private val activeSpeeds = ConcurrentHashMap<Long, Long>()

    // Simple token bucket for speed limiting
    private val bytesInCurrentWindow = AtomicLong(0L)
    @Volatile
    private var windowStartMs = System.currentTimeMillis()

    init {
        scope.launch {
            // Reset any orphaned DOWNLOADING rows from prior process kill to PAUSED
            val stuck = dao.getDownloadsByStatus(
                listOf(DownloadStatus.DOWNLOADING, DownloadStatus.INSPECTING, DownloadStatus.MERGING)
            )
            stuck.forEach { item ->
                dao.updateStatus(item.id, DownloadStatus.PAUSED)
            }
        }

        // Periodic aggregate speedometer & notification updater
        scope.launch {
            while (isActive) {
                delay(750)
                val sumSpeed = activeSpeeds.values.sum()
                _totalAggregateSpeed.value = sumSpeed
                _liveSpeedHistory.update { prev ->
                    (prev.drop(1) + (sumSpeed / 1024f)).takeLast(24)
                }
                if (activeJobs.isNotEmpty()) {
                    DownloadForegroundService.startOrUpdate(
                        context = context,
                        activeCount = activeJobs.size,
                        speedText = Formatters.formatSpeed(sumSpeed),
                        title = "SatyA Accelerated Transfer"
                    )
                }
            }
        }
    }

    fun startOrResume(downloadId: Long) {
        if (activeJobs.containsKey(downloadId)) return
        pausedOrCancelled.remove(downloadId)

        val job = scope.launch {
            try {
                val settings = preferences.settingsFlow.first()
                if (settings.wifiOnly && !isOnWifi()) {
                    dao.updateStatus(downloadId, DownloadStatus.PAUSED, "Waiting for Wi-Fi connection")
                    return@launch
                }

                // Respect max concurrent limit by waiting in QUEUED state
                while (activeJobs.size > settings.maxConcurrentDownloads) {
                    dao.updateStatus(downloadId, DownloadStatus.QUEUED)
                    delay(800)
                    if (pausedOrCancelled.contains(downloadId)) return@launch
                }

                var entity = dao.getDownloadById(downloadId) ?: return@launch

                // Probe if metadata missing
                if (entity.totalBytes <= 0L) {
                    dao.updateStatus(downloadId, DownloadStatus.INSPECTING)
                    val probe = inspector.inspect(entity.url)
                    if (probe.error == null && !probe.isHtmlPage) {
                        val updated = entity.copy(
                            url = probe.resolvedUrl,
                            totalBytes = probe.contentLength,
                            supportsRange = probe.supportsRange,
                            mimeType = probe.mimeType.ifBlank { entity.mimeType }
                        )
                        dao.updateDownload(updated)
                        entity = updated
                    }
                }

                dao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

                val partDir = File(context.filesDir, "satya_parts/$downloadId").apply { mkdirs() }
                val effectiveThreads = if (entity.supportsRange && entity.totalBytes >= 512 * 1024L) {
                    entity.threadCount.coerceIn(1, 16)
                } else {
                    1
                }

                var segments = dao.getSegments(downloadId)
                if (segments.isEmpty()) {
                    segments = buildSegments(downloadId, entity.totalBytes, effectiveThreads)
                    dao.insertSegments(segments)
                }

                val mergedTempFile = File(partDir, "merged_${entity.fileName}")

                if (effectiveThreads > 1 && entity.totalBytes > 0L) {
                    runMultiSegmentDownload(entity, segments, partDir, settings)
                    if (pausedOrCancelled.contains(downloadId)) return@launch

                    dao.updateStatus(downloadId, DownloadStatus.MERGING)
                    mergeSegments(segments, partDir, mergedTempFile)
                } else {
                    runSingleStreamDownload(entity, mergedTempFile, settings)
                    if (pausedOrCancelled.contains(downloadId)) return@launch
                }

                // Save to MediaStore public folder
                val saved = MediaStoreSaver.saveToPublicStorage(
                    context = context,
                    sourceFile = mergedTempFile,
                    fileName = entity.fileName,
                    mimeType = entity.mimeType,
                    category = entity.category
                )

                val finalSize = saved.localFilePath?.let { File(it).length() }
                    ?: entity.totalBytes.coerceAtLeast(entity.downloadedBytes)

                dao.updateDownload(
                    entity.copy(
                        status = DownloadStatus.COMPLETED,
                        totalBytes = finalSize,
                        downloadedBytes = finalSize,
                        speedBytesPerSec = 0L,
                        localUri = saved.uriString,
                        localFilePath = saved.localFilePath,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                )

                // If user asked to extract HD audio track from this video
                if (entity.extractAudioTrack && entity.category == MediaCategory.HD_VIDEO && saved.localFilePath != null) {
                    val videoFile = File(saved.localFilePath)
                    if (videoFile.exists()) {
                        AudioExtractor.extractAudioFromVideo(context, videoFile, entity.fileName)
                            .onSuccess { (audioSaved, audioSize) ->
                                val audioName = "${entity.fileName.substringBeforeLast('.')}_HD.m4a"
                                dao.insertDownload(
                                    DownloadEntity(
                                        url = entity.url,
                                        fileName = audioName,
                                        mimeType = "audio/mp4",
                                        category = MediaCategory.HD_AUDIO,
                                        qualityLabel = "Extracted HD Audio",
                                        totalBytes = audioSize,
                                        downloadedBytes = audioSize,
                                        status = DownloadStatus.COMPLETED,
                                        threadCount = 1,
                                        supportsRange = false,
                                        localUri = audioSaved.uriString,
                                        localFilePath = audioSaved.localFilePath,
                                        completedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                    }
                }

                partDir.deleteRecursively()
                _liveSegments.update { it - downloadId }
            } catch (_: CancellationException) {
                // Paused or cancelled cleanly
            } catch (e: Exception) {
                if (!pausedOrCancelled.contains(downloadId)) {
                    dao.updateStatus(
                        downloadId,
                        DownloadStatus.FAILED,
                        e.message ?: "Network transfer interrupted"
                    )
                }
            } finally {
                activeJobs.remove(downloadId)
                activeSpeeds.remove(downloadId)
                if (activeJobs.isEmpty()) {
                    _totalAggregateSpeed.value = 0L
                    DownloadForegroundService.stop(context)
                }
            }
        }

        activeJobs[downloadId] = job
    }

    fun pause(downloadId: Long) {
        pausedOrCancelled.add(downloadId)
        activeJobs.remove(downloadId)?.cancel()
        activeSpeeds.remove(downloadId)
        scope.launch {
            dao.updateStatus(downloadId, DownloadStatus.PAUSED)
            if (activeJobs.isEmpty()) DownloadForegroundService.stop(context)
        }
    }

    fun cancelAndRemoveParts(downloadId: Long) {
        pausedOrCancelled.add(downloadId)
        activeJobs.remove(downloadId)?.cancel()
        activeSpeeds.remove(downloadId)
        scope.launch {
            dao.updateStatus(downloadId, DownloadStatus.CANCELLED)
            File(context.filesDir, "satya_parts/$downloadId").deleteRecursively()
            _liveSegments.update { it - downloadId }
            if (activeJobs.isEmpty()) DownloadForegroundService.stop(context)
        }
    }

    private suspend fun runMultiSegmentDownload(
        entity: DownloadEntity,
        initialSegments: List<DownloadSegmentEntity>,
        partDir: File,
        settings: EngineSettings
    ) = coroutineScope {
        val segmentStates = ConcurrentHashMap<Int, SegmentProgress>()
        initialSegments.forEach { seg ->
            val partFile = File(partDir, "part_${seg.segmentIndex}")
            val diskBytes = if (partFile.exists()) partFile.length() else 0L
            val segLen = (seg.endByte - seg.startByte + 1L).coerceAtLeast(1L)
            val actualBytes = minOf(diskBytes, segLen)
            segmentStates[seg.segmentIndex] = SegmentProgress(
                segmentIndex = seg.segmentIndex,
                startByte = seg.startByte,
                endByte = seg.endByte,
                downloadedBytes = actualBytes,
                isComplete = seg.isComplete && actualBytes >= segLen
            )
        }

        val monitorJob = launch {
            var lastBytes = segmentStates.values.sumOf { it.downloadedBytes }
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                delay(300)
                val now = System.currentTimeMillis()
                val currentTotal = segmentStates.values.sumOf { it.downloadedBytes }
                val elapsedMs = (now - lastTick).coerceAtLeast(1L)
                val speed = ((currentTotal - lastBytes).coerceAtLeast(0L) * 1000L) / elapsedMs
                lastBytes = currentTotal
                lastTick = now
                activeSpeeds[entity.id] = speed

                val sorted = segmentStates.values.sortedBy { it.segmentIndex }
                _liveSegments.update { it + (entity.id to sorted) }
                dao.updateProgress(entity.id, currentTotal, speed, entity.totalBytes)
                sorted.forEach { s ->
                    dao.updateSegmentProgress(entity.id, s.segmentIndex, s.downloadedBytes, s.isComplete)
                }
            }
        }

        try {
            val tasks = initialSegments.map { seg ->
                async(Dispatchers.IO) {
                    val state = segmentStates[seg.segmentIndex]!!
                    if (state.isComplete) return@async

                    val partFile = File(partDir, "part_${seg.segmentIndex}")
                    val resumeFrom = seg.startByte + state.downloadedBytes
                    if (resumeFrom > seg.endByte) {
                        segmentStates[seg.segmentIndex] = state.copy(isComplete = true)
                        return@async
                    }

                    val req = Request.Builder()
                        .url(entity.url)
                        .header("User-Agent", LinkInspector.USER_AGENT)
                        .header("Range", "bytes=$resumeFrom-${seg.endByte}")
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code != 206) {
                            throw IllegalStateException("HTTP ${resp.code} on segment ${seg.segmentIndex}")
                        }
                        val body = resp.body ?: throw IllegalStateException("Empty response body")
                        RandomAccessFile(partFile, "rw").use { raf ->
                            raf.seek(state.downloadedBytes)
                            val buf = ByteArray(16 * 1024)
                            var segDownloaded = state.downloadedBytes
                            val maxExpected = seg.endByte - seg.startByte + 1L
                            body.byteStream().use { input ->
                                while (isActive && !pausedOrCancelled.contains(entity.id)) {
                                    val read = input.read(buf)
                                    if (read == -1) break
                                    val remainingAllowed = (maxExpected - segDownloaded).toInt().coerceAtLeast(0)
                                    val toWrite = minOf(read, remainingAllowed)
                                    if (toWrite <= 0) break
                                    raf.write(buf, 0, toWrite)
                                    segDownloaded += toWrite
                                    segmentStates[seg.segmentIndex] = state.copy(
                                        downloadedBytes = segDownloaded,
                                        isComplete = segDownloaded >= maxExpected
                                    )
                                    throttleIfNeeded(toWrite, settings.speedLimitKbps)
                                }
                            }
                            val finished = segDownloaded >= maxExpected
                            segmentStates[seg.segmentIndex] = state.copy(
                                downloadedBytes = segDownloaded,
                                isComplete = finished
                            )
                            dao.updateSegmentProgress(entity.id, seg.segmentIndex, segDownloaded, finished)
                        }
                    }
                }
            }
            tasks.awaitAll()
            val finalSum = segmentStates.values.sumOf { it.downloadedBytes }
            dao.updateProgress(entity.id, finalSum, 0L, entity.totalBytes)
        } finally {
            monitorJob.cancel()
        }
    }

    private suspend fun runSingleStreamDownload(
        entity: DownloadEntity,
        destFile: File,
        settings: EngineSettings
    ) = coroutineScope {
        val existingBytes = if (entity.supportsRange && destFile.exists()) destFile.length() else 0L
        val reqBuilder = Request.Builder()
            .url(entity.url)
            .header("User-Agent", LinkInspector.USER_AGENT)
        if (existingBytes > 0L && entity.supportsRange) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code} ${resp.message}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty body")
            val append = resp.code == 206 && existingBytes > 0L
            val knownTotal = when {
                entity.totalBytes > 0L -> entity.totalBytes
                body.contentLength() > 0L -> if (append) existingBytes + body.contentLength() else body.contentLength()
                else -> -1L
            }

            FileOutputStream(destFile, append).use { out ->
                val buf = ByteArray(16 * 1024)
                var downloaded = if (append) existingBytes else 0L
                var lastReportedBytes = downloaded
                var lastTick = System.currentTimeMillis()

                body.byteStream().use { input ->
                    while (isActive && !pausedOrCancelled.contains(entity.id)) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        downloaded += read
                        throttleIfNeeded(read, settings.speedLimitKbps)

                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 300) {
                            val speed = ((downloaded - lastReportedBytes).coerceAtLeast(0L) * 1000L) / (now - lastTick).coerceAtLeast(1L)
                            lastReportedBytes = downloaded
                            lastTick = now
                            activeSpeeds[entity.id] = speed
                            val seg = SegmentProgress(0, 0L, knownTotal.coerceAtLeast(downloaded), downloaded, false)
                            _liveSegments.update { it + (entity.id to listOf(seg)) }
                            dao.updateProgress(entity.id, downloaded, speed, knownTotal)
                        }
                    }
                }
                dao.updateProgress(entity.id, downloaded, 0L, if (knownTotal > 0) knownTotal else downloaded)
            }
        }
    }

    private fun mergeSegments(
        segments: List<DownloadSegmentEntity>,
        partDir: File,
        outputFile: File
    ) {
        if (outputFile.exists()) outputFile.delete()
        FileOutputStream(outputFile).use { out ->
            val buf = ByteArray(64 * 1024)
            segments.sortedBy { it.segmentIndex }.forEach { seg ->
                val partFile = File(partDir, "part_${seg.segmentIndex}")
                if (partFile.exists()) {
                    FileInputStream(partFile).use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                    }
                }
            }
        }
    }

    private fun buildSegments(
        downloadId: Long,
        totalBytes: Long,
        threadCount: Int
    ): List<DownloadSegmentEntity> {
        if (totalBytes <= 0L || threadCount <= 1) {
            return listOf(
                DownloadSegmentEntity(
                    downloadId = downloadId,
                    segmentIndex = 0,
                    startByte = 0L,
                    endByte = (totalBytes - 1L).coerceAtLeast(0L),
                    downloadedBytes = 0L
                )
            )
        }
        val chunkSize = totalBytes / threadCount
        return (0 until threadCount).map { idx ->
            val start = idx * chunkSize
            val end = if (idx == threadCount - 1) totalBytes - 1L else ((idx + 1) * chunkSize - 1L)
            DownloadSegmentEntity(
                downloadId = downloadId,
                segmentIndex = idx,
                startByte = start,
                endByte = end,
                downloadedBytes = 0L
            )
        }
    }

    private suspend fun throttleIfNeeded(bytesRead: Int, speedLimitKbps: Int) {
        if (speedLimitKbps <= 0) return
        val maxBytesPerSec = speedLimitKbps * 1024L
        val current = bytesInCurrentWindow.addAndGet(bytesRead.toLong())
        val now = System.currentTimeMillis()
        val elapsed = now - windowStartMs
        if (elapsed >= 1000L) {
            bytesInCurrentWindow.set(0L)
            windowStartMs = now
        } else if (current > maxBytesPerSec) {
            val sleepMs = (1000L - elapsed).coerceIn(10L, 400L)
            delay(sleepMs)
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
