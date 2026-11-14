package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.components.Formatters
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald

private enum class VaultTab(val label: String) {
    ALL("All Media"),
    AUDIO("HD MP3 Songs"),
    VIDEO("HD Videos"),
    FILES("Documents & ZIP")
}

@Composable
fun VaultScreen(
    allDownloads: List<DownloadEntity>,
    onPlayOrOpen: (DownloadEntity) -> Unit,
    onExtractAudio: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(VaultTab.ALL) }
    val completed = remember(allDownloads) {
        allDownloads.filter { it.status == DownloadStatus.COMPLETED }
    }
    val visibleItems = remember(completed, selectedTab) {
        when (selectedTab) {
            VaultTab.ALL -> completed
            VaultTab.AUDIO -> completed.filter { it.category == MediaCategory.HD_AUDIO }
            VaultTab.VIDEO -> completed.filter { it.category == MediaCategory.HD_VIDEO }
            VaultTab.FILES -> completed.filter {
                it.category != MediaCategory.HD_AUDIO && it.category != MediaCategory.HD_VIDEO
            }
        }
    }

    val audioCount = completed.count { it.category == MediaCategory.HD_AUDIO }
    val videoCount = completed.count { it.category == MediaCategory.HD_VIDEO }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SonicEmerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_media_vault),
                        contentDescription = "SatyA Media Vault",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xEE0B101B), Color(0x990B101B))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "MEDIA & SONG VAULT",
                            style = MaterialTheme.typography.labelLarge,
                            color = SonicEmerald
                        )
                        Text(
                            text = "$audioCount HD Songs • $videoCount HD Videos",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Saved directly to Music/SatyA, Movies/SatyA, and Downloads/SatyA. Tap any video to play or extract its HD audio track.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SonicEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = SonicEmerald
                        ),
                        modifier = Modifier.testTag("vault_tab_${tab.name}")
                    )
                }
            }
        }

        if (visibleItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = SonicEmerald,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No completed media in ${selectedTab.label}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Completed MP3 songs, HD videos, and files appear here for instant playback and PC Wi-Fi sharing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(visibleItems, key = { it.id }) { item ->
                val accent = when (item.category) {
                    MediaCategory.HD_AUDIO -> SonicEmerald
                    MediaCategory.HD_VIDEO -> NeonAmber
                    else -> ElectricCyan
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayOrOpen(item) }
                        .testTag("vault_item_${item.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accent.copy(alpha = 0.16f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = when (item.category) {
                                        MediaCategory.HD_AUDIO -> Icons.Filled.AudioFile
                                        MediaCategory.HD_VIDEO -> Icons.Filled.Movie
                                        else -> Icons.Filled.PlayArrow
                                    },
                                    contentDescription = item.category.displayName,
                                    tint = accent,
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
                                Text(
                                    text = "${item.qualityLabel} • ${Formatters.formatBytes(item.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }

                            IconButton(
                                onClick = { onPlayOrOpen(item) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("vault_play_${item.id}")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play media", tint = accent)
                            }
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("vault_delete_${item.id}")
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (item.category == MediaCategory.HD_VIDEO && item.localFilePath != null) {
                            OutlinedButton(
                                onClick = { onExtractAudio(item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("extract_audio_btn_${item.id}")
                            ) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Extract HD Audio Track to Music/SatyA")
                            }
                        }
                    }
                }
            }
        }
    }
}
