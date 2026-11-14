package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.AudioPreset
import com.example.data.model.DetectedMediaLink
import com.example.data.model.MediaCategory
import com.example.data.model.VideoPreset
import com.example.download.LinkInspector
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald

private data class QuickBookmark(val label: String, val url: String)

private val QUICK_BOOKMARKS = listOf(
    QuickBookmark("IDM Official", "https://www.internetdownloadmanager.com/"),
    QuickBookmark("Open MP3 Audio", "https://archive.org/details/audio_music"),
    QuickBookmark("Open HD Videos", "https://archive.org/details/opensource_movies"),
    QuickBookmark("Wikimedia Media", "https://commons.wikimedia.org/wiki/Main_Page")
)

private class SatyaMediaBridge(
    private val onLinkDetected: (String, String) -> Unit
) {
    @JavascriptInterface
    fun reportMedia(url: String, kind: String) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            onLinkDetected(url, kind)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GrabberScreen(
    sniffedLinks: List<DetectedMediaLink>,
    onLinkSniffed: (DetectedMediaLink) -> Unit,
    onClearSniffed: () -> Unit,
    onQuickAudioDownload: (String, AudioPreset, String?) -> Unit,
    onQuickVideoDownload: (String, VideoPreset, String?) -> Unit,
    onDirectFileDownload: (String, MediaCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentUrl by remember { mutableStateOf("https://www.internetdownloadmanager.com/") }
    var addressBarText by remember { mutableStateOf(currentUrl) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showSniffedSheet by remember { mutableStateOf(false) }

    val snifferJs = remember {
        """
        (function() {
            var tags = document.querySelectorAll('video, audio, source, a');
            tags.forEach(function(el) {
                var src = el.currentSrc || el.src || el.href || '';
                if (!src) return;
                var lower = src.toLowerCase();
                if (lower.match(/\.(mp3|m4a|flac|aac|ogg|wav)(\?|$)/) || el.tagName === 'AUDIO') {
                    SatyaBridge.reportMedia(src, 'AUDIO');
                } else if (lower.match(/\.(mp4|mkv|webm|mov|m4v)(\?|$)/) || el.tagName === 'VIDEO') {
                    SatyaBridge.reportMedia(src, 'VIDEO');
                } else if (lower.match(/\.(zip|rar|7z|pdf|exe|dmg)(\?|$)/)) {
                    SatyaBridge.reportMedia(src, 'FILE');
                }
            });
        })();
        """.trimIndent()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Browser Navigation Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("browser_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("browser_forward_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        OutlinedTextField(
                            value = addressBarText,
                            onValueChange = { addressBarText = it },
                            singleLine = true,
                            placeholder = { Text("Enter URL or search site...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val normalized = LinkInspector.normalizeUrl(addressBarText)
                                    currentUrl = normalized
                                    addressBarText = normalized
                                    onClearSniffed()
                                    webViewRef?.loadUrl(normalized)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browser_url_input")
                        )
                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("browser_reload_btn")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload page")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_BOOKMARKS.forEach { bm ->
                            FilterChip(
                                selected = currentUrl.startsWith(bm.url),
                                onClick = {
                                    currentUrl = bm.url
                                    addressBarText = bm.url
                                    onClearSniffed()
                                    webViewRef?.loadUrl(bm.url)
                                },
                                label = { Text(bm.label) },
                                modifier = Modifier.testTag("bookmark_${bm.label.replace(" ", "_")}")
                            )
                        }
                    }
                }
            }

            if (pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    color = ElectricCyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        addJavascriptInterface(
                            SatyaMediaBridge { detectedUrl, kind ->
                                val name = LinkInspector.extractFileName(detectedUrl, null, "")
                                val cat = when (kind) {
                                    "AUDIO" -> MediaCategory.HD_AUDIO
                                    "VIDEO" -> MediaCategory.HD_VIDEO
                                    else -> LinkInspector.classifyMedia(name, "")
                                }
                                val badge = when (cat) {
                                    MediaCategory.HD_AUDIO -> "HD MP3"
                                    MediaCategory.HD_VIDEO -> "HD VIDEO"
                                    else -> "FILE"
                                }
                                onLinkSniffed(
                                    DetectedMediaLink(
                                        url = detectedUrl,
                                        title = name,
                                        category = cat,
                                        extension = name.substringAfterLast('.', ""),
                                        qualityBadge = badge
                                    )
                                )
                            },
                            "SatyaBridge"
                        )

                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                            val name = LinkInspector.extractFileName(url, contentDisposition, mimeType)
                            val cat = LinkInspector.classifyMedia(name, mimeType)
                            onLinkSniffed(
                                DetectedMediaLink(
                                    url = url,
                                    title = name,
                                    category = cat,
                                    extension = name.substringAfterLast('.', ""),
                                    qualityBadge = if (cat == MediaCategory.HD_AUDIO) "HD MP3" else "DIRECT",
                                    mimeType = mimeType
                                )
                            )
                            showSniffedSheet = true
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                url?.let {
                                    currentUrl = it
                                    addressBarText = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript(snifferJs, null)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString()
                                if (reqUrl != null && LinkInspector.isInterestingMediaOrFileUrl(reqUrl)) {
                                    val name = LinkInspector.extractFileName(reqUrl, null, "")
                                    val cat = LinkInspector.classifyMedia(name, "")
                                    onLinkSniffed(
                                        DetectedMediaLink(
                                            url = reqUrl,
                                            title = name,
                                            category = cat,
                                            extension = name.substringAfterLast('.', ""),
                                            qualityBadge = when (cat) {
                                                MediaCategory.HD_AUDIO -> "HD MP3"
                                                MediaCategory.HD_VIDEO -> "HD VIDEO"
                                                else -> "FILE"
                                            }
                                        )
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // Floating IDM Media Grabber Badge
        FloatingActionButton(
            onClick = {
                webViewRef?.evaluateJavascript(snifferJs, null)
                showSniffedSheet = true
            },
            containerColor = if (sniffedLinks.isNotEmpty()) SonicEmerald else ElectricCyan,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .testTag("grab_media_fab")
        ) {
            BadgedBox(
                badge = {
                    if (sniffedLinks.isNotEmpty()) {
                        Badge { Text("${sniffedLinks.size}") }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Download detected media"
                )
            }
        }
    }

    if (showSniffedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSniffedSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Captured Media & Files (${sniffedLinks.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = { webViewRef?.evaluateJavascript(snifferJs, null) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescan page")
                    }
                }

                if (sniffedLinks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TravelExplore,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No audio or video stream captured on this view yet.",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap play on an audio/video player on the page or click a direct file link—SatyA intercepts MP3, M4A, MP4, WebM, PDF, and ZIP streams automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    onDirectFileDownload(currentUrl, null)
                                    showSniffedSheet = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Inspect Current Page URL")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sniffedLinks, key = { it.url }) { link ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = link.qualityBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (link.category == MediaCategory.HD_AUDIO) SonicEmerald else NeonAmber
                                        )
                                        Text(
                                            text = link.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SonicEmerald.copy(alpha = 0.16f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                                .clickable {
                                                    onQuickAudioDownload(link.url, AudioPreset.MP3_320, link.title)
                                                    showSniffedSheet = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.AudioFile,
                                                    contentDescription = null,
                                                    tint = SonicEmerald,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("HD MP3", style = MaterialTheme.typography.labelLarge, color = SonicEmerald)
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = NeonAmber.copy(alpha = 0.16f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                                .clickable {
                                                    onQuickVideoDownload(link.url, VideoPreset.FHD_1080P, link.title)
                                                    showSniffedSheet = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.Movie,
                                                    contentDescription = null,
                                                    tint = NeonAmber,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("HD Video", style = MaterialTheme.typography.labelLarge, color = NeonAmber)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
