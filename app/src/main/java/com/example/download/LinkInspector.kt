package com.example.download

import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.data.model.DetectedMediaLink
import com.example.data.model.LinkProbeResult
import com.example.data.model.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LinkInspector(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    suspend fun inspect(rawUrl: String): LinkProbeResult = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(rawUrl)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return@withContext LinkProbeResult(
                originalUrl = rawUrl,
                resolvedUrl = normalized,
                fileName = "download.bin",
                mimeType = "application/octet-stream",
                contentLength = -1L,
                supportsRange = false,
                category = MediaCategory.OTHER,
                error = "Please enter a valid http:// or https:// URL"
            )
        }

        try {
            // Try HEAD first
            val headRequest = Request.Builder()
                .url(normalized)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()

            var resolvedUrl = normalized
            var mimeType = ""
            var contentLength = -1L
            var acceptRanges = false
            var contentDisposition: String? = null

            val headSuccess = runCatching {
                client.newCall(headRequest).execute().use { resp ->
                    resolvedUrl = resp.request.url.toString()
                    mimeType = resp.header("Content-Type")?.substringBefore(";")?.trim().orEmpty()
                    contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    acceptRanges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                    contentDisposition = resp.header("Content-Disposition")
                    resp.isSuccessful
                }
            }.getOrDefault(false)

            // Fallback to ranged GET if HEAD failed or didn't report length/type
            if (!headSuccess || mimeType.isBlank() || contentLength <= 0L) {
                val rangeReq = Request.Builder()
                    .url(normalized)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=0-0")
                    .build()

                client.newCall(rangeReq).execute().use { resp ->
                    resolvedUrl = resp.request.url.toString()
                    if (mimeType.isBlank()) {
                        mimeType = resp.header("Content-Type")?.substringBefore(";")?.trim().orEmpty()
                    }
                    contentDisposition = contentDisposition ?: resp.header("Content-Disposition")
                    val contentRange = resp.header("Content-Range")
                    if (resp.code == 206 || contentRange != null) {
                        acceptRanges = true
                        val totalFromRange = contentRange?.substringAfter("/")?.toLongOrNull()
                        if (totalFromRange != null && totalFromRange > 0) {
                            contentLength = totalFromRange
                        }
                    } else if (contentLength <= 0L) {
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                    if (!acceptRanges) {
                        acceptRanges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true
                    }
                }
            }

            val isHtml = mimeType.contains("text/html", ignoreCase = true) ||
                mimeType.contains("application/xhtml", ignoreCase = true)

            if (isHtml) {
                val sniffed = fetchAndSniffHtml(resolvedUrl)
                val pageName = extractFileName(resolvedUrl, contentDisposition, "text/html")
                return@withContext LinkProbeResult(
                    originalUrl = rawUrl,
                    resolvedUrl = resolvedUrl,
                    fileName = pageName,
                    mimeType = mimeType,
                    contentLength = contentLength,
                    supportsRange = false,
                    category = MediaCategory.DOCUMENT,
                    isHtmlPage = true,
                    sniffedLinks = sniffed
                )
            }

            val fileName = extractFileName(resolvedUrl, contentDisposition, mimeType)
            val category = classifyMedia(fileName, mimeType)
            LinkProbeResult(
                originalUrl = rawUrl,
                resolvedUrl = resolvedUrl,
                fileName = fileName,
                mimeType = mimeType.ifBlank { guessMimeFromName(fileName) },
                contentLength = contentLength,
                supportsRange = acceptRanges && contentLength > 0L,
                category = category,
                isHtmlPage = false
            )
        } catch (e: Exception) {
            val fallbackName = extractFileName(normalized, null, "")
            LinkProbeResult(
                originalUrl = rawUrl,
                resolvedUrl = normalized,
                fileName = fallbackName,
                mimeType = guessMimeFromName(fallbackName),
                contentLength = -1L,
                supportsRange = false,
                category = classifyMedia(fallbackName, ""),
                error = e.message ?: "Unable to inspect URL"
            )
        }
    }

    private fun fetchAndSniffHtml(pageUrl: String): List<DetectedMediaLink> {
        val req = Request.Builder()
            .url(pageUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        val html = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bytes = resp.body?.byteStream()?.use { stream ->
                    val buf = ByteArray(512 * 1024)
                    var total = 0
                    while (total < buf.size) {
                        val read = stream.read(buf, total, buf.size - total)
                        if (read <= 0) break
                        total += read
                    }
                    String(buf, 0, total, Charsets.UTF_8)
                } ?: ""
                bytes
            }
        }.getOrDefault("")

        if (html.isBlank()) return emptyList()

        val foundUrls = LinkedHashSet<String>()
        val attrPattern = Pattern.compile(
            "(?:href|src|data-src|content)\\s*=\\s*[\"']([^\"'#>\\s]+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = attrPattern.matcher(html)
        val baseUri = Uri.parse(pageUrl)

        while (matcher.find()) {
            val rawLink = matcher.group(1)?.trim() ?: continue
            val resolved = resolveUrl(baseUri, rawLink) ?: continue
            if (isInterestingMediaOrFileUrl(resolved)) {
                foundUrls.add(resolved)
            }
        }

        // Also match raw direct media URLs embedded in JS/JSON blocks
        val directPattern = Pattern.compile(
            "https?://[^\\s\"'<>()]+\\.(?:mp3|m4a|flac|aac|ogg|wav|mp4|mkv|webm|mov|zip|rar|7z|pdf|exe|dmg)(?:\\?[^\\s\"'<>()]*)?",
            Pattern.CASE_INSENSITIVE
        )
        val directMatcher = directPattern.matcher(html)
        while (directMatcher.find()) {
            foundUrls.add(directMatcher.group())
        }

        return foundUrls.take(40).map { url ->
            val name = extractFileName(url, null, "")
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val cat = classifyMedia(name, guessMimeFromName(name))
            val badge = when (cat) {
                MediaCategory.HD_AUDIO -> if (ext == "flac" || ext == "wav") "LOSSLESS" else "HD MP3"
                MediaCategory.HD_VIDEO -> when {
                    url.contains("2160") || url.contains("4k", ignoreCase = true) -> "4K UHD"
                    url.contains("1080") -> "1080p HD"
                    url.contains("720") -> "720p HD"
                    else -> "HD VIDEO"
                }
                MediaCategory.ARCHIVE -> ext.uppercase(Locale.ROOT).ifBlank { "ARCHIVE" }
                MediaCategory.DOCUMENT -> ext.uppercase(Locale.ROOT).ifBlank { "DOC" }
                MediaCategory.OTHER -> ext.uppercase(Locale.ROOT).ifBlank { "FILE" }
            }
            DetectedMediaLink(
                url = url,
                title = name,
                category = cat,
                extension = ext,
                qualityBadge = badge,
                mimeType = guessMimeFromName(name)
            )
        }
    }

    private fun resolveUrl(base: Uri, candidate: String): String? {
        if (candidate.startsWith("javascript:", true) ||
            candidate.startsWith("mailto:", true) ||
            candidate.startsWith("data:", true)
        ) return null
        return runCatching {
            when {
                candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
                candidate.startsWith("//") -> "${base.scheme ?: "https"}:$candidate"
                candidate.startsWith("/") -> "${base.scheme}://${base.authority}$candidate"
                else -> {
                    val pathPrefix = base.path?.substringBeforeLast('/', "") ?: ""
                    "${base.scheme}://${base.authority}$pathPrefix/$candidate"
                }
            }
        }.getOrNull()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) SatyA/1.0 Mobile Safari/537.36"

        private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma")
        private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts")
        private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")
        private val DOC_EXTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "txt", "csv")
        private val BINARY_EXTS = setOf("exe", "msi", "dmg", "deb", "apk")

        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return ""
            return if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                "https://$trimmed"
            } else {
                trimmed
            }
        }

        fun isInterestingMediaOrFileUrl(url: String): Boolean {
            val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
            val ext = clean.substringAfterLast('.', "")
            return ext in AUDIO_EXTS || ext in VIDEO_EXTS || ext in ARCHIVE_EXTS || ext in DOC_EXTS || ext in BINARY_EXTS
        }

        fun classifyMedia(fileName: String, mimeType: String): MediaCategory {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = mimeType.lowercase(Locale.ROOT)
            return when {
                mime.startsWith("audio/") || ext in AUDIO_EXTS -> MediaCategory.HD_AUDIO
                mime.startsWith("video/") || ext in VIDEO_EXTS -> MediaCategory.HD_VIDEO
                ext in ARCHIVE_EXTS || mime.contains("zip") || mime.contains("compressed") -> MediaCategory.ARCHIVE
                ext in DOC_EXTS || mime.contains("pdf") || mime.startsWith("text/") -> MediaCategory.DOCUMENT
                else -> MediaCategory.OTHER
            }
        }

        fun guessMimeFromName(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: when (ext) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "flac" -> "audio/flac"
                    "mp4" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "webm" -> "video/webm"
                    "zip" -> "application/zip"
                    "pdf" -> "application/pdf"
                    else -> "application/octet-stream"
                }
        }

        fun extractFileName(url: String, contentDisposition: String?, mimeType: String): String {
            // Check Content-Disposition filename
            if (!contentDisposition.isNullOrBlank()) {
                val utf8Matcher = Pattern.compile("filename\\*=UTF-8''([^;\\r\\n]+)", Pattern.CASE_INSENSITIVE)
                    .matcher(contentDisposition)
                if (utf8Matcher.find()) {
                    val decoded = runCatching { URLDecoder.decode(utf8Matcher.group(1), "UTF-8") }.getOrNull()
                    if (!decoded.isNullOrBlank()) return sanitizeFileName(decoded)
                }
                val stdMatcher = Pattern.compile("filename=\"?([^\";\\r\\n]+)\"?", Pattern.CASE_INSENSITIVE)
                    .matcher(contentDisposition)
                if (stdMatcher.find()) {
                    val raw = stdMatcher.group(1)?.trim()
                    if (!raw.isNullOrBlank()) return sanitizeFileName(raw)
                }
            }

            // Fallback to URL path segment
            val path = Uri.parse(url).lastPathSegment
            if (!path.isNullOrBlank() && path.contains('.')) {
                return sanitizeFileName(path)
            }

            val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: when {
                    mimeType.contains("mpeg") -> "mp3"
                    mimeType.contains("mp4") -> "mp4"
                    mimeType.contains("audio") -> "mp3"
                    mimeType.contains("video") -> "mp4"
                    mimeType.contains("pdf") -> "pdf"
                    else -> "bin"
                }
            val host = Uri.parse(url).host?.replace(".", "_") ?: "satya"
            return sanitizeFileName("${host}_${System.currentTimeMillis() % 10000}.$extFromMime")
        }

        fun sanitizeFileName(name: String): String {
            return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "satya_download.bin" }
        }
    }
}
