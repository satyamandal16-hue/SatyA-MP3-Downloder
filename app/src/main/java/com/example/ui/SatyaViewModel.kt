package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SatyaApplication
import com.example.data.local.DownloadEntity
import com.example.data.model.AudioPreset
import com.example.data.model.DetectedMediaLink
import com.example.data.model.DownloadStatus
import com.example.data.model.LinkProbeResult
import com.example.data.model.MediaCategory
import com.example.data.model.SegmentProgress
import com.example.data.model.VideoPreset
import com.example.data.prefs.EngineSettings
import com.example.download.LanBridgeState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class QueueFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    HD_MP3("HD MP3"),
    HD_VIDEO("HD Video"),
    COMPLETED("Done")
}

sealed interface ProbeUiState {
    data object Idle : ProbeUiState
    data object Loading : ProbeUiState
    data class Ready(val result: LinkProbeResult) : ProbeUiState
}

class SatyaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SatyaApplication
    private val repository = app.repository
    private val engine = app.engine
    private val lanBridge = app.lanBridge

    val settings: StateFlow<EngineSettings> = repository.engineSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineSettings())

    private val _selectedFilter = MutableStateFlow(QueueFilter.ALL)
    val selectedFilter: StateFlow<QueueFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allDownloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDownloads: StateFlow<List<DownloadEntity>> = combine(
        allDownloads,
        _selectedFilter,
        _searchQuery
    ) { items, filter, query ->
        items.filter { item ->
            val matchesFilter = when (filter) {
                QueueFilter.ALL -> true
                QueueFilter.ACTIVE -> item.status in setOf(
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.QUEUED,
                    DownloadStatus.INSPECTING,
                    DownloadStatus.MERGING,
                    DownloadStatus.PAUSED
                )
                QueueFilter.HD_MP3 -> item.category == MediaCategory.HD_AUDIO
                QueueFilter.HD_VIDEO -> item.category == MediaCategory.HD_VIDEO
                QueueFilter.COMPLETED -> item.status == DownloadStatus.COMPLETED
            }
            val matchesQuery = query.isBlank() ||
                item.fileName.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aggregateSpeed: StateFlow<Long> = engine.totalAggregateSpeed
    val speedHistory: StateFlow<List<Float>> = engine.liveSpeedHistory
    val liveSegments: StateFlow<Map<Long, List<SegmentProgress>>> = engine.liveSegments
    val lanBridgeState: StateFlow<LanBridgeState> = lanBridge.state

    private val _selectedDownloadId = MutableStateFlow<Long?>(null)
    val selectedDownloadId: StateFlow<Long?> = _selectedDownloadId.asStateFlow()

    private val _probeState = MutableStateFlow<ProbeUiState>(ProbeUiState.Idle)
    val probeState: StateFlow<ProbeUiState> = _probeState.asStateFlow()

    private val _showNewDownloadSheet = MutableStateFlow(false)
    val showNewDownloadSheet: StateFlow<Boolean> = _showNewDownloadSheet.asStateFlow()

    private val _prefillUrl = MutableStateFlow("")
    val prefillUrl: StateFlow<String> = _prefillUrl.asStateFlow()

    private val _prefillCategory = MutableStateFlow<MediaCategory?>(null)
    val prefillCategory: StateFlow<MediaCategory?> = _prefillCategory.asStateFlow()

    private val _sniffedBrowserLinks = MutableStateFlow<List<DetectedMediaLink>>(emptyList())
    val sniffedBrowserLinks: StateFlow<List<DetectedMediaLink>> = _sniffedBrowserLinks.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    fun setFilter(filter: QueueFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectDownload(id: Long?) {
        _selectedDownloadId.value = id
    }

    fun openNewDownloadSheet(url: String = "", preferredCategory: MediaCategory? = null) {
        _prefillUrl.value = url
        _prefillCategory.value = preferredCategory
        _probeState.value = ProbeUiState.Idle
        _showNewDownloadSheet.value = true
        if (url.isNotBlank()) {
            inspectUrl(url)
        }
    }

    fun closeNewDownloadSheet() {
        _showNewDownloadSheet.value = false
        _probeState.value = ProbeUiState.Idle
    }

    fun inspectUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _probeState.value = ProbeUiState.Loading
            val result = repository.probeUrl(url)
            _probeState.value = ProbeUiState.Ready(result)
        }
    }

    fun startDownload(
        url: String,
        fileName: String,
        mimeType: String,
        category: MediaCategory,
        qualityLabel: String,
        totalBytes: Long,
        supportsRange: Boolean,
        threadCount: Int,
        extractAudioOnly: Boolean
    ) {
        viewModelScope.launch {
            val id = repository.enqueueDownload(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                category = category,
                qualityLabel = qualityLabel,
                totalBytes = totalBytes,
                supportsRange = supportsRange,
                threadCount = threadCount,
                extractAudioTrack = extractAudioOnly
            )
            _selectedDownloadId.value = id
            _showNewDownloadSheet.value = false
            _probeState.value = ProbeUiState.Idle
            _snackbarEvents.emit("Queued $fileName ($threadCount threads)")
        }
    }

    fun startQuickAudioDownload(url: String, preset: AudioPreset, fileNameOverride: String? = null) {
        viewModelScope.launch {
            val probe = repository.probeUrl(url)
            val baseName = (fileNameOverride ?: probe.fileName).substringBeforeLast('.')
            val finalExt = if (preset == AudioPreset.M4A_EXTRACT) "mp4" else probe.fileName.substringAfterLast('.', preset.extension)
            val extract = preset == AudioPreset.M4A_EXTRACT || probe.category == MediaCategory.HD_VIDEO
            val targetCategory = if (extract) MediaCategory.HD_VIDEO else MediaCategory.HD_AUDIO
            val displayFileName = if (extract) {
                "${baseName}.mp4"
            } else {
                val cleanExt = if (finalExt.lowercase() in setOf("mp3", "m4a", "flac", "aac", "ogg", "wav")) finalExt else preset.extension
                "$baseName.$cleanExt"
            }
            val id = repository.enqueueDownload(
                url = probe.resolvedUrl.ifBlank { url },
                fileName = displayFileName,
                mimeType = if (extract) "video/mp4" else probe.mimeType.ifBlank { "audio/mpeg" },
                category = targetCategory,
                qualityLabel = preset.label,
                totalBytes = probe.contentLength,
                supportsRange = probe.supportsRange,
                threadCount = settings.value.defaultThreads,
                extractAudioTrack = extract
            )
            _selectedDownloadId.value = id
            _showNewDownloadSheet.value = false
            _snackbarEvents.emit("Downloading ${preset.label}")
        }
    }

    fun startQuickVideoDownload(url: String, preset: VideoPreset, fileNameOverride: String? = null) {
        viewModelScope.launch {
            val probe = repository.probeUrl(url)
            val baseName = (fileNameOverride ?: probe.fileName).substringBeforeLast('.')
            val ext = probe.fileName.substringAfterLast('.', "mp4").let {
                if (it.lowercase() in setOf("mp4", "mkv", "webm", "mov")) it else "mp4"
            }
            val id = repository.enqueueDownload(
                url = probe.resolvedUrl.ifBlank { url },
                fileName = "$baseName.$ext",
                mimeType = probe.mimeType.ifBlank { "video/mp4" },
                category = MediaCategory.HD_VIDEO,
                qualityLabel = preset.label,
                totalBytes = probe.contentLength,
                supportsRange = probe.supportsRange,
                threadCount = settings.value.defaultThreads,
                extractAudioTrack = settings.value.autoExtractMp3FromVideo
            )
            _selectedDownloadId.value = id
            _showNewDownloadSheet.value = false
            _snackbarEvents.emit("Downloading ${preset.label}")
        }
    }

    fun enqueueDetectedLink(link: DetectedMediaLink) {
        viewModelScope.launch {
            val id = repository.enqueueQuickUrl(link.url, link.category)
            _selectedDownloadId.value = id
            _snackbarEvents.emit("Started ${link.title}")
        }
    }

    fun pause(id: Long) = repository.pauseDownload(id)
    fun resume(id: Long) = repository.resumeDownload(id)
    fun cancel(id: Long) = repository.cancelDownload(id)

    fun delete(item: DownloadEntity) {
        viewModelScope.launch {
            if (_selectedDownloadId.value == item.id) _selectedDownloadId.value = null
            repository.deleteDownload(item)
            _snackbarEvents.emit("Removed ${item.fileName}")
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompleted()
            _snackbarEvents.emit("Cleared completed history")
        }
    }

    fun extractAudioFromVideo(item: DownloadEntity) {
        viewModelScope.launch {
            _snackbarEvents.emit("Extracting HD audio track...")
            repository.extractAudioFromCompletedVideo(item)
                .onSuccess { _snackbarEvents.emit("Saved HD audio to Music/SatyA") }
                .onFailure { err -> _snackbarEvents.emit(err.message ?: "Extraction failed") }
        }
    }

    fun addSniffedBrowserLink(link: DetectedMediaLink) {
        val current = _sniffedBrowserLinks.value
        if (current.none { it.url == link.url }) {
            _sniffedBrowserLinks.value = (listOf(link) + current).take(30)
        }
    }

    fun clearSniffedBrowserLinks() {
        _sniffedBrowserLinks.value = emptyList()
    }

    fun toggleLanBridge(enable: Boolean) {
        if (enable) {
            lanBridge.start(settings.value.pcBridgePort)
        } else {
            lanBridge.stop()
        }
    }

    fun setDefaultThreads(threads: Int) = viewModelScope.launch { repository.setDefaultThreads(threads) }
    fun setMaxConcurrent(count: Int) = viewModelScope.launch { repository.setMaxConcurrent(count) }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { repository.setWifiOnly(enabled) }
    fun setSpeedLimitKbps(kbps: Int) = viewModelScope.launch { repository.setSpeedLimitKbps(kbps) }
    fun setAutoExtractMp3(enabled: Boolean) = viewModelScope.launch { repository.setAutoExtractMp3(enabled) }
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { repository.setDarkTheme(enabled) }
}
