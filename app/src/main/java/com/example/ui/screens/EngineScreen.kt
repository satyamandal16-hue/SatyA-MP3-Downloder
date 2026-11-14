package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.prefs.EngineSettings
import com.example.download.LanBridgeState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald
import kotlin.math.roundToInt

private data class SpeedCapOption(val label: String, val kbps: Int)

private val SPEED_CAPS = listOf(
    SpeedCapOption("Unlimited", 0),
    SpeedCapOption("10 MB/s", 10 * 1024),
    SpeedCapOption("5 MB/s", 5 * 1024),
    SpeedCapOption("2 MB/s", 2 * 1024),
    SpeedCapOption("512 KB/s", 512)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineScreen(
    settings: EngineSettings,
    lanBridgeState: LanBridgeState,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onSetThreads: (Int) -> Unit,
    onSetMaxConcurrent: (Int) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetSpeedLimitKbps: (Int) -> Unit,
    onSetAutoExtractMp3: (Boolean) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onToggleLanBridge: (Boolean) -> Unit,
    onNotifyCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // PC <-> Mobile Wi-Fi Bridge Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (lanBridgeState.isRunning) ElectricCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Computer,
                                contentDescription = "PC Bridge",
                                tint = ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text("PC ↔ Mobile Wi-Fi Bridge", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Send links from any PC browser or download files to PC",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = lanBridgeState.isRunning,
                            onCheckedChange = onToggleLanBridge,
                            modifier = Modifier.testTag("lan_bridge_switch")
                        )
                    }

                    if (lanBridgeState.isRunning) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricCyan.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lanBridgeState.url,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = "Open on Windows, Mac, or Linux browser • ${lanBridgeState.requestsServed} requests served",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(lanBridgeState.url))
                                        onNotifyCopied()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("copy_lan_url_btn")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy PC Bridge URL")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Multi-Thread Acceleration Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = NeonAmber)
                        Text("IDM Multi-Segment Engine", style = MaterialTheme.typography.titleLarge)
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Segments per Download", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${settings.defaultThreads} Threads",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = settings.defaultThreads.toFloat(),
                            onValueChange = { onSetThreads(it.roundToInt()) },
                            valueRange = 1f..16f,
                            steps = 14,
                            modifier = Modifier.testTag("engine_threads_slider")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Max Simultaneous Transfers", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${settings.maxConcurrentDownloads} Queue Slots",
                                style = MaterialTheme.typography.labelLarge,
                                color = SonicEmerald
                            )
                        }
                        Slider(
                            value = settings.maxConcurrentDownloads.toFloat(),
                            onValueChange = { onSetMaxConcurrent(it.roundToInt()) },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.testTag("engine_concurrent_slider")
                        )
                    }

                    Text("Speed Limiter", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SPEED_CAPS.forEach { cap ->
                            FilterChip(
                                selected = settings.speedLimitKbps == cap.kbps,
                                onClick = { onSetSpeedLimitKbps(cap.kbps) },
                                label = { Text(cap.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("speed_cap_${cap.kbps}")
                            )
                        }
                    }
                }
            }
        }

        // Behavior & Preferences Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Transfer & Media Preferences", style = MaterialTheme.typography.titleLarge)

                    ToggleRow(
                        title = "Wi-Fi Only Transfers",
                        subtitle = "Pause cellular downloads automatically",
                        checked = settings.wifiOnly,
                        onCheckedChange = onSetWifiOnly,
                        tag = "wifi_only_switch"
                    )

                    ToggleRow(
                        title = "Auto-Extract HD Audio from Video",
                        subtitle = "Save an M4A audio copy to Music/SatyA for every video",
                        checked = settings.autoExtractMp3FromVideo,
                        onCheckedChange = onSetAutoExtractMp3,
                        tag = "auto_extract_switch"
                    )

                    ToggleRow(
                        title = "Cyber-Studio Dark Theme",
                        subtitle = "High-contrast telemetry palette",
                        checked = settings.darkThemeEnabled,
                        onCheckedChange = onSetDarkTheme,
                        tag = "dark_theme_switch"
                    )
                }
            }
        }

        if (!notificationsGranted) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NeonAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = NeonAmber)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Live Transfer Alerts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Allow notifications to view real-time speed while SatyA runs in the background.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onRequestNotificationPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
