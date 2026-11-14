package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioPreset
import com.example.data.model.DetectedMediaLink
import com.example.data.model.MediaCategory
import com.example.data.model.VideoPreset
import com.example.ui.ProbeUiState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald
import kotlin.math.roundToInt

private enum class DownloadModeTab(val label: String) {
    HD_MP3("HD MP3 Song"),
    HD_VIDEO("HD Video"),
    FILE("Any File")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewDownloadSheet(
    prefillUrl: String,
    prefillCategory: MediaCategory?,
    defaultThreads: Int,
    probeState: ProbeUiState,
    onDismiss: () -> Unit,
    onInspectUrl: (String) -> Unit,
    onStartCustomDownload: (
        url: String,
        fileName: String,
        mimeType: String,
        category: MediaCategory,
        qualityLabel: String,
        totalBytes: Long,
        supportsRange: Boolean,
        threadCount: Int,
        extractAudioOnly: Boolean
    ) -> Unit,
    onEnqueueDetectedLink: (DetectedMediaLink) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var urlText by remember(prefillUrl) { mutableStateOf(prefillUrl) }
    var fileNameText by remember { mutableStateOf("") }
    var modeTab by remember(prefillCategory) {
        mutableStateOf(
            when (prefillCategory) {
                MediaCategory.HD_AUDIO -> DownloadModeTab.HD_MP3
                MediaCategory.HD_VIDEO -> DownloadModeTab.HD_VIDEO
                else -> DownloadModeTab.HD_MP3
            }
        )
    }
    var selectedAudioPreset by remember { mutableStateOf(AudioPreset.MP3_320) }
    var selectedVideoPreset by remember { mutableStateOf(VideoPreset.FHD_1080P) }
    var threadSlider by remember(defaultThreads) { mutableFloatStateOf(defaultThreads.toFloat()) }
    var alsoExtractAudio by remember { mutableStateOf(false) }

    LaunchedEffect(probeState) {
        if (probeState is ProbeUiState.Ready) {
            val res = probeState.result
            if (!res.isHtmlPage) {
                fileNameText = res.fileName
                modeTab = when (res.category) {
                    MediaCategory.HD_AUDIO -> DownloadModeTab.HD_MP3
                    MediaCategory.HD_VIDEO -> DownloadModeTab.HD_VIDEO
                    else -> DownloadModeTab.FILE
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add Accelerated Download",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Media or File URL (http / https)") },
                placeholder = { Text("https://example.com/song.mp3 or video.mp4") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                val clip = clipboard.getText()?.text?.trim().orEmpty()
                                if (clip.isNotBlank()) {
                                    urlText = clip
                                    onInspectUrl(clip)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("paste_url_btn")
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste URL")
                        }
                        IconButton(
                            onClick = { onInspectUrl(urlText) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("probe_url_btn")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Inspect URL")
                        }
                    }
                }
            )

            if (probeState is ProbeUiState.Loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Inspecting headers, byte-range support & embedded media...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (probeState is ProbeUiState.Ready) {
                val res = probeState.result
                if (res.error != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Note: ${res.error}. You can still force direct download.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (res.isHtmlPage) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricCyan.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Webpage Detected • ${res.sniffedLinks.size} Media/File Links Found",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                            if (res.sniffedLinks.isEmpty()) {
                                Text(
                                    text = "No direct MP3/MP4 files in static HTML. Open this URL in the Grabber tab to capture streaming media.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                res.sniffedLinks.take(8).forEach { link ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onEnqueueDetectedLink(link)
                                                onDismiss()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = link.qualityBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SonicEmerald
                                            )
                                            Text(
                                                text = link.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = "Download detected link",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SonicEmerald.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${res.category.displayName} • ${Formatters.formatBytes(res.contentLength)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SonicEmerald
                                )
                                Text(
                                    text = if (res.supportsRange) "Multi-Thread Resume Supported" else "Single-Stream Server",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Format / Mode Selector
            Text(
                text = "Download Option",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DownloadModeTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        DownloadModeTab.HD_MP3 -> Icons.Filled.AudioFile
                        DownloadModeTab.HD_VIDEO -> Icons.Filled.Movie
                        DownloadModeTab.FILE -> Icons.Filled.InsertDriveFile
                    }
                    FilterChip(
                        selected = modeTab == tab,
                        onClick = { modeTab = tab },
                        label = { Text(tab.label) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("mode_chip_${tab.name}")
                    )
                }
            }

            when (modeTab) {
                DownloadModeTab.HD_MP3 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioPreset.entries.forEach { preset ->
                            val selected = selectedAudioPreset == preset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) SonicEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) SonicEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAudioPreset = preset }
                                    .testTag("audio_preset_${preset.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(preset.label, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            preset.detail,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = preset.extension.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) SonicEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                DownloadModeTab.HD_VIDEO -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedVideoPreset == preset,
                                onClick = { selectedVideoPreset = preset },
                                label = { Text(preset.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonAmber.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonAmber
                                ),
                                modifier = Modifier.testTag("video_preset_${preset.name}")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Also Save HD Audio Track", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Extract M4A audio into Music/SatyA automatically",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alsoExtractAudio,
                            onCheckedChange = { alsoExtractAudio = it },
                            modifier = Modifier.testTag("extract_audio_switch")
                        )
                    }
                }
                DownloadModeTab.FILE -> {
                    // Direct file mode uses custom filename & thread count below
                }
            }

            OutlinedTextField(
                value = fileNameText,
                onValueChange = { fileNameText = it },
                label = { Text("Save As Filename (optional)") },
                placeholder = { Text("Auto-detected from server") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filename_input_field")
            )

            // IDM Multi-Thread Accelerator Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Connection Threads (IDM Engine)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${threadSlider.roundToInt()} Streams",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }
                Slider(
                    value = threadSlider,
                    onValueChange = { threadSlider = it },
                    valueRange = 1f..16f,
                    steps = 14,
                    modifier = Modifier.testTag("thread_slider")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("cancel_sheet_btn")
                ) {
                    Text("Close")
                }

                Button(
                    onClick = {
                        val cleanUrl = urlText.trim()
                        if (cleanUrl.isNotBlank()) {
                            val probed = (probeState as? ProbeUiState.Ready)?.result
                            val resolvedUrl = probed?.resolvedUrl?.takeIf { !probed.isHtmlPage } ?: cleanUrl
                            val baseName = fileNameText.trim().ifBlank {
                                probed?.fileName?.takeIf { !probed.isHtmlPage }
                                    ?: "satya_media_${System.currentTimeMillis() % 10000}"
                            }
                            val finalCategory: MediaCategory
                            val finalName: String
                            val finalQuality: String
                            val finalMime: String
                            var extractFlag = false

                            when (modeTab) {
                                DownloadModeTab.HD_MP3 -> {
                                    if (selectedAudioPreset == AudioPreset.M4A_EXTRACT ||
                                        probed?.category == MediaCategory.HD_VIDEO
                                    ) {
                                        finalCategory = MediaCategory.HD_VIDEO
                                        finalName = if (baseName.contains('.')) baseName else "$baseName.mp4"
                                        finalQuality = selectedAudioPreset.label
                                        finalMime = "video/mp4"
                                        extractFlag = true
                                    } else {
                                        finalCategory = MediaCategory.HD_AUDIO
                                        finalName = if (baseName.contains('.')) baseName else "$baseName.${selectedAudioPreset.extension}"
                                        finalQuality = selectedAudioPreset.label
                                        finalMime = probed?.mimeType?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
                                    }
                                }
                                DownloadModeTab.HD_VIDEO -> {
                                    finalCategory = MediaCategory.HD_VIDEO
                                    finalName = if (baseName.contains('.')) baseName else "$baseName.mp4"
                                    finalQuality = selectedVideoPreset.label
                                    finalMime = probed?.mimeType?.takeIf { it.startsWith("video/") } ?: "video/mp4"
                                    extractFlag = alsoExtractAudio
                                }
                                DownloadModeTab.FILE -> {
                                    finalCategory = probed?.category ?: MediaCategory.OTHER
                                    finalName = baseName
                                    finalQuality = "${threadSlider.roundToInt()}-Thread"
                                    finalMime = probed?.mimeType ?: "application/octet-stream"
                                }
                            }

                            onStartCustomDownload(
                                resolvedUrl,
                                finalName,
                                finalMime,
                                finalCategory,
                                finalQuality,
                                probed?.contentLength ?: -1L,
                                probed?.supportsRange ?: true,
                                threadSlider.roundToInt(),
                                extractFlag
                            )
                        }
                    },
                    enabled = urlText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("start_download_btn")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Download")
                }
            }
        }
    }
}
