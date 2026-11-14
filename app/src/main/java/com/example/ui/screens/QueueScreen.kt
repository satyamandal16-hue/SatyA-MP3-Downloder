package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaCategory
import com.example.data.model.SegmentProgress
import com.example.ui.QueueFilter
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.Formatters
import com.example.ui.components.MultiSegmentBar
import com.example.ui.components.SpeedSparkline
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald

@Composable
fun QueueScreen(
    downloads: List<DownloadEntity>,
    allCount: Int,
    selectedFilter: QueueFilter,
    searchQuery: String,
    aggregateSpeed: Long,
    speedHistory: List<Float>,
    liveSegments: Map<Long, List<SegmentProgress>>,
    selectedDownloadId: Long?,
    defaultThreads: Int,
    onFilterChange: (QueueFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectDownload: (Long) -> Unit,
    onOpenNewDownload: (MediaCategory?) -> Unit,
    onOpenGrabber: () -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onPlayOrOpen: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING }
    val selectedItem = downloads.firstOrNull { it.id == selectedDownloadId } ?: downloads.firstOrNull()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Canonical List-Detail split pane for Tablets / Desktop / PC Form Factor
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QueueListPane(
                    downloads = downloads,
                    allCount = allCount,
                    activeCount = activeCount,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    aggregateSpeed = aggregateSpeed,
                    speedHistory = speedHistory,
                    liveSegments = liveSegments,
                    selectedDownloadId = selectedItem?.id,
                    defaultThreads = defaultThreads,
                    onFilterChange = onFilterChange,
                    onSearchChange = onSearchChange,
                    onSelectDownload = onSelectDownload,
                    onOpenNewDownload = onOpenNewDownload,
                    onOpenGrabber = onOpenGrabber,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onDelete = onDelete,
                    onClearCompleted = onClearCompleted,
                    onPlayOrOpen = onPlayOrOpen,
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                )

                QueueDetailInspectorPane(
                    item = selectedItem,
                    segments = selectedItem?.let { liveSegments[it.id] }.orEmpty(),
                    onPause = onPause,
                    onResume = onResume,
                    onPlayOrOpen = onPlayOrOpen,
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                )
            }
        } else {
            QueueListPane(
                downloads = downloads,
                allCount = allCount,
                activeCount = activeCount,
                selectedFilter = selectedFilter,
                searchQuery = searchQuery,
                aggregateSpeed = aggregateSpeed,
                speedHistory = speedHistory,
                liveSegments = liveSegments,
                selectedDownloadId = selectedDownloadId,
                defaultThreads = defaultThreads,
                onFilterChange = onFilterChange,
                onSearchChange = onSearchChange,
                onSelectDownload = onSelectDownload,
                onOpenNewDownload = onOpenNewDownload,
                onOpenGrabber = onOpenGrabber,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onDelete = onDelete,
                onClearCompleted = onClearCompleted,
                onPlayOrOpen = onPlayOrOpen,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun QueueListPane(
    downloads: List<DownloadEntity>,
    allCount: Int,
    activeCount: Int,
    selectedFilter: QueueFilter,
    searchQuery: String,
    aggregateSpeed: Long,
    speedHistory: List<Float>,
    liveSegments: Map<Long, List<SegmentProgress>>,
    selectedDownloadId: Long?,
    defaultThreads: Int,
    onFilterChange: (QueueFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectDownload: (Long) -> Unit,
    onOpenNewDownload: (MediaCategory?) -> Unit,
    onOpenGrabber: () -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onPlayOrOpen: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TelemetryHeroCard(
                aggregateSpeed = aggregateSpeed,
                activeCount = activeCount,
                totalCount = allCount,
                defaultThreads = defaultThreads,
                speedHistory = speedHistory
            )
        }

        // Quick-Grab Action Trio: HD MP3, HD Video, Direct URL
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickGrabCard(
                    title = "HD MP3",
                    subtitle = "320kbps Song",
                    icon = Icons.Filled.AudioFile,
                    accent = SonicEmerald,
                    onClick = { onOpenNewDownload(MediaCategory.HD_AUDIO) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_hd_mp3_btn")
                )
                QuickGrabCard(
                    title = "HD Video",
                    subtitle = "4K / 1080p",
                    icon = Icons.Filled.Movie,
                    accent = NeonAmber,
                    onClick = { onOpenNewDownload(MediaCategory.HD_VIDEO) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_hd_video_btn")
                )
                QuickGrabCard(
                    title = "Any Link",
                    subtitle = "Multi-Thread",
                    icon = Icons.Filled.AddLink,
                    accent = ElectricCyan,
                    onClick = { onOpenNewDownload(null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_any_link_btn")
                )
            }
        }

        // Search & Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search downloads...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("queue_search_field")
                    )
                    if (allCount > 0) {
                        IconButton(
                            onClick = onClearCompleted,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_completed_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ClearAll,
                                contentDescription = "Clear completed downloads"
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name}")
                        )
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                EmptyQueueState(
                    onAddUrl = { onOpenNewDownload(null) },
                    onOpenGrabber = onOpenGrabber
                )
            }
        } else {
            items(downloads, key = { it.id }) { item ->
                DownloadItemCard(
                    item = item,
                    segments = liveSegments[item.id].orEmpty(),
                    isSelected = item.id == selectedDownloadId,
                    onSelect = { onSelectDownload(item.id) },
                    onPause = { onPause(item.id) },
                    onResume = { onResume(item.id) },
                    onCancel = { onCancel(item.id) },
                    onDelete = { onDelete(item) },
                    onPlayOrOpen = { onPlayOrOpen(item) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun TelemetryHeroCard(
    aggregateSpeed: Long,
    activeCount: Int,
    totalCount: Int,
    defaultThreads: Int,
    speedHistory: List<Float>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = "SatyA High-Speed Engine Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xCC0B101B),
                                Color(0xEE0B101B)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "SATYA ACCELERATOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SonicEmerald.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "OPEN EDITION • PC & MOBILE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SonicEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = Formatters.formatSpeed(aggregateSpeed),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Text(
                            text = "$activeCount active • $totalCount total • ${defaultThreads}-Thread default",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7E0)
                        )
                    }
                    SpeedSparkline(
                        historyKbps = speedHistory,
                        modifier = Modifier
                            .width(110.dp)
                            .height(42.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickGrabCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
        modifier = modifier
            .heightIn(min = 78.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyQueueState(
    onAddUrl: () -> Unit,
    onOpenGrabber: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Ready for High-Speed Transfers",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Paste any direct MP3 song, HD/4K video, or file link—or browse pages in the built-in Media Grabber to sniff and download streams with up to 16 parallel segments.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddUrl,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_add_url_btn")
                ) {
                    Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste Link")
                }
                OutlinedButton(
                    onClick = onOpenGrabber,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_open_grabber_btn")
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grabber")
                }
            }
        }
    }
}

@Composable
private fun QueueDetailInspectorPane(
    item: DownloadEntity?,
    segments: List<SegmentProgress>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onPlayOrOpen: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Select a transfer to inspect segment threads",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "SEGMENT INSPECTOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = item.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    MultiSegmentBar(
                        segments = segments,
                        overallFraction = item.progressFraction,
                        threadCount = item.threadCount
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (item.status == DownloadStatus.DOWNLOADING) {
                            OutlinedButton(onClick = { onPause(item.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Pause All Threads")
                            }
                        } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                            Button(
                                onClick = { onResume(item.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Resume Threads")
                            }
                        } else if (item.status == DownloadStatus.COMPLETED) {
                            Button(
                                onClick = { onPlayOrOpen(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = SonicEmerald),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Play / Open")
                            }
                        }
                    }
                }
                items(segments, key = { it.segmentIndex }) { seg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thread #${seg.segmentIndex + 1}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${Formatters.formatBytes(seg.downloadedBytes)} / ${Formatters.formatBytes(seg.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (seg.isComplete) SonicEmerald else ElectricCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
