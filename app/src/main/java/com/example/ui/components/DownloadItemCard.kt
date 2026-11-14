package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaCategory
import com.example.data.model.SegmentProgress
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald

@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    segments: List<SegmentProgress>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlayOrOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryAccent = when (item.category) {
        MediaCategory.HD_AUDIO -> SonicEmerald
        MediaCategory.HD_VIDEO -> NeonAmber
        MediaCategory.ARCHIVE -> ElectricCyan
        MediaCategory.DOCUMENT -> MaterialTheme.colorScheme.primary
        MediaCategory.OTHER -> MaterialTheme.colorScheme.secondary
    }

    val categoryIcon = when (item.category) {
        MediaCategory.HD_AUDIO -> Icons.Filled.AudioFile
        MediaCategory.HD_VIDEO -> Icons.Filled.Movie
        MediaCategory.ARCHIVE -> Icons.Filled.FolderZip
        MediaCategory.DOCUMENT -> Icons.Filled.Description
        MediaCategory.OTHER -> Icons.Filled.InsertDriveFile
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}")
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) categoryAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = categoryAccent.copy(alpha = 0.14f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = item.category.displayName,
                        tint = categoryAccent,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = categoryAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = item.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${Formatters.formatBytes(item.downloadedBytes)} / ${Formatters.formatBytes(item.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary action button (48dp minimum touch target)
                when (item.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.INSPECTING, DownloadStatus.QUEUED -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pause_btn_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pause download",
                                tint = NeonAmber
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("resume_btn_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Resume download",
                                tint = ElectricCyan
                            )
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_btn_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry download",
                                tint = ElectricCyan
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(
                            onClick = onPlayOrOpen,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_btn_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play or open file",
                                tint = SonicEmerald
                            )
                        }
                    }
                    DownloadStatus.MERGING -> {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Merging segments",
                            tint = ElectricCyan,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Progress visualization
            if (item.status != DownloadStatus.COMPLETED) {
                MultiSegmentBar(
                    segments = segments,
                    overallFraction = item.progressFraction,
                    threadCount = item.threadCount
                )
            }

            // Bottom telemetry bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        val pct = (item.progressFraction * 100).toInt()
                        Text(
                            text = "$pct% • ${Formatters.formatSpeed(item.speedBytesPerSec)} • ${Formatters.formatEta(item.totalBytes, item.downloadedBytes, item.speedBytesPerSec)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                    DownloadStatus.MERGING -> {
                        Text(
                            text = "Assembling ${item.threadCount} segments into ${item.category.folderName}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                    DownloadStatus.INSPECTING -> {
                        Text(
                            text = "Probing server headers & range support...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        Text(
                            text = "Queued • Waiting for slot",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        Text(
                            text = item.errorMessage ?: "Paused • ${(item.progressFraction * 100).toInt()}% saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonAmber
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SonicEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Saved to ${item.category.folderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SonicEmerald
                            )
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        Text(
                            text = item.errorMessage ?: item.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonCoral,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_btn_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = "Cancel download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isSelected && item.errorMessage != null && item.status == DownloadStatus.FAILED) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonCoral
                )
            }
        }
    }
}
