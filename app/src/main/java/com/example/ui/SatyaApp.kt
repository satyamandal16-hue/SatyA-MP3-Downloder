package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.DownloadEntity
import com.example.ui.components.MediaPreviewDialog
import com.example.ui.components.NewDownloadSheet
import com.example.ui.screens.EngineScreen
import com.example.ui.screens.GrabberScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SatyaTheme
import kotlinx.coroutines.launch

private enum class SatyaDest(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    QUEUE("queue", "Queue", Icons.Filled.Download, Icons.Outlined.Download),
    GRABBER("grabber", "Grabber", Icons.Filled.Language, Icons.Outlined.Language),
    VAULT("vault", "Vault", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial),
    ENGINE("engine", "Engine", Icons.Filled.Tune, Icons.Outlined.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatyaApp(
    sharedIncomingUrl: String?,
    onConsumedIncomingUrl: () -> Unit,
    viewModel: SatyaViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val filteredDownloads by viewModel.filteredDownloads.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val aggregateSpeed by viewModel.aggregateSpeed.collectAsStateWithLifecycle()
    val speedHistory by viewModel.speedHistory.collectAsStateWithLifecycle()
    val liveSegments by viewModel.liveSegments.collectAsStateWithLifecycle()
    val selectedDownloadId by viewModel.selectedDownloadId.collectAsStateWithLifecycle()
    val probeState by viewModel.probeState.collectAsStateWithLifecycle()
    val showNewSheet by viewModel.showNewDownloadSheet.collectAsStateWithLifecycle()
    val prefillUrl by viewModel.prefillUrl.collectAsStateWithLifecycle()
    val prefillCategory by viewModel.prefillCategory.collectAsStateWithLifecycle()
    val sniffedBrowserLinks by viewModel.sniffedBrowserLinks.collectAsStateWithLifecycle()
    val lanBridgeState by viewModel.lanBridgeState.collectAsStateWithLifecycle()

    var previewItem by remember { mutableStateOf<DownloadEntity?>(null) }

    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(sharedIncomingUrl) {
        if (!sharedIncomingUrl.isNullOrBlank()) {
            viewModel.openNewDownloadSheet(sharedIncomingUrl)
            onConsumedIncomingUrl()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    SatyaTheme(darkTheme = settings.darkThemeEnabled) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute = currentDestination?.route ?: SatyaDest.QUEUE.route

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    SatyaDest.GRABBER.route -> "SatyA Media Grabber"
                                    SatyaDest.VAULT.route -> "SatyA Media Vault"
                                    SatyaDest.ENGINE.route -> "SatyA Engine & PC Bridge"
                                    else -> "SatyA"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            SatyaDest.entries.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (currentRoute == SatyaDest.QUEUE.route || currentRoute == SatyaDest.VAULT.route) {
                        FloatingActionButton(
                            onClick = { viewModel.openNewDownloadSheet() },
                            containerColor = ElectricCyan,
                            modifier = Modifier.testTag("fab_new_download")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add new download"
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            SatyaDest.entries.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("rail_${dest.route}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = SatyaDest.QUEUE.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(SatyaDest.QUEUE.route) {
                            QueueScreen(
                                downloads = filteredDownloads,
                                allCount = allDownloads.size,
                                selectedFilter = selectedFilter,
                                searchQuery = searchQuery,
                                aggregateSpeed = aggregateSpeed,
                                speedHistory = speedHistory,
                                liveSegments = liveSegments,
                                selectedDownloadId = selectedDownloadId,
                                defaultThreads = settings.defaultThreads,
                                onFilterChange = viewModel::setFilter,
                                onSearchChange = viewModel::setSearchQuery,
                                onSelectDownload = viewModel::selectDownload,
                                onOpenNewDownload = { cat -> viewModel.openNewDownloadSheet(preferredCategory = cat) },
                                onOpenGrabber = {
                                    navController.navigate(SatyaDest.GRABBER.route) {
                                        launchSingleTop = true
                                    }
                                },
                                onPause = viewModel::pause,
                                onResume = viewModel::resume,
                                onCancel = viewModel::cancel,
                                onDelete = viewModel::delete,
                                onClearCompleted = viewModel::clearCompleted,
                                onPlayOrOpen = { previewItem = it }
                            )
                        }

                        composable(SatyaDest.GRABBER.route) {
                            GrabberScreen(
                                sniffedLinks = sniffedBrowserLinks,
                                onLinkSniffed = viewModel::addSniffedBrowserLink,
                                onClearSniffed = viewModel::clearSniffedBrowserLinks,
                                onQuickAudioDownload = { url, preset, name ->
                                    viewModel.startQuickAudioDownload(url, preset, name)
                                },
                                onQuickVideoDownload = { url, preset, name ->
                                    viewModel.startQuickVideoDownload(url, preset, name)
                                },
                                onDirectFileDownload = { url, cat ->
                                    viewModel.openNewDownloadSheet(url, cat)
                                }
                            )
                        }

                        composable(SatyaDest.VAULT.route) {
                            VaultScreen(
                                allDownloads = allDownloads,
                                onPlayOrOpen = { previewItem = it },
                                onExtractAudio = viewModel::extractAudioFromVideo,
                                onDelete = viewModel::delete
                            )
                        }

                        composable(SatyaDest.ENGINE.route) {
                            EngineScreen(
                                settings = settings,
                                lanBridgeState = lanBridgeState,
                                notificationsGranted = notificationsGranted,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onSetThreads = viewModel::setDefaultThreads,
                                onSetMaxConcurrent = viewModel::setMaxConcurrent,
                                onSetWifiOnly = viewModel::setWifiOnly,
                                onSetSpeedLimitKbps = viewModel::setSpeedLimitKbps,
                                onSetAutoExtractMp3 = viewModel::setAutoExtractMp3,
                                onSetDarkTheme = viewModel::setDarkTheme,
                                onToggleLanBridge = viewModel::toggleLanBridge,
                                onNotifyCopied = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Copied PC Bridge URL to clipboard")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showNewSheet) {
            NewDownloadSheet(
                prefillUrl = prefillUrl,
                prefillCategory = prefillCategory,
                defaultThreads = settings.defaultThreads,
                probeState = probeState,
                onDismiss = viewModel::closeNewDownloadSheet,
                onInspectUrl = viewModel::inspectUrl,
                onStartCustomDownload = viewModel::startDownload,
                onEnqueueDetectedLink = viewModel::enqueueDetectedLink
            )
        }

        previewItem?.let { item ->
            MediaPreviewDialog(
                item = item,
                onDismiss = { previewItem = null }
            )
        }
    }
}
