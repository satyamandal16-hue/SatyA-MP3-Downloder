package com.example.data.model

enum class MediaCategory(val displayName: String, val folderName: String) {
    HD_AUDIO("HD MP3 Audio", "Music/SatyA"),
    HD_VIDEO("HD Video", "Movies/SatyA"),
    DOCUMENT("Document", "Downloads/SatyA"),
    ARCHIVE("Archive", "Downloads/SatyA"),
    OTHER("File", "Downloads/SatyA")
}

enum class DownloadStatus {
    QUEUED,
    INSPECTING,
    DOWNLOADING,
    MERGING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AudioPreset(val label: String, val detail: String, val extension: String) {
    MP3_320("320 kbps Studio MP3", "Max clarity audio stream", "mp3"),
    MP3_192("192 kbps Balanced MP3", "Fast high-definition song", "mp3"),
    M4A_EXTRACT("Extract HD Audio", "Strip audio track from video", "m4a")
}

enum class VideoPreset(val label: String, val detail: String) {
    UHD_4K("4K Ultra HD", "2160p • Maximum detail"),
    FHD_1080P("1080p Full HD", "Crisp 60fps cinema"),
    HD_720P("720p HD", "Balanced speed & size"),
    SOURCE_ORIGINAL("Source Original", "Native server bitrate")
}

data class DetectedMediaLink(
    val url: String,
    val title: String,
    val category: MediaCategory,
    val extension: String,
    val qualityBadge: String,
    val contentLength: Long = -1L,
    val mimeType: String = ""
)

data class LinkProbeResult(
    val originalUrl: String,
    val resolvedUrl: String,
    val fileName: String,
    val mimeType: String,
    val contentLength: Long,
    val supportsRange: Boolean,
    val category: MediaCategory,
    val isHtmlPage: Boolean = false,
    val sniffedLinks: List<DetectedMediaLink> = emptyList(),
    val error: String? = null
)

data class SegmentProgress(
    val segmentIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val isComplete: Boolean
) {
    val totalBytes: Long get() = (endByte - startByte + 1L).coerceAtLeast(1L)
    val fraction: Float get() = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}
