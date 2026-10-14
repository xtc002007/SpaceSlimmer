package com.spaceslimmer.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import com.spaceslimmer.app.ui.components.SsAlertDialog
import com.spaceslimmer.app.ui.components.SsDialogTextButton
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsHeroMetric
import com.spaceslimmer.app.ui.components.SsLargeTitle
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSection
import com.spaceslimmer.app.ui.home.HomeCompressSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spaceslimmer.app.ui.shell.MainTab
import androidx.compose.material3.SnackbarHostState
import com.spaceslimmer.app.SpaceSlimmerApp
import com.spaceslimmer.app.R
import com.spaceslimmer.core.EngineEventHub
import com.spaceslimmer.app.ui.media.MediaJobsScreen
import com.spaceslimmer.app.ui.media.VideoCompareScreen
import com.spaceslimmer.app.ui.photo.PhotoCompareScreen
import com.spaceslimmer.app.ui.photo.PhotoJobsScreen
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.ui.onboarding.OnboardingScreen
import com.spaceslimmer.app.ui.radar.RadarPresetScreen
import com.spaceslimmer.app.ui.radar.RadarItem
import com.spaceslimmer.app.ui.radar.RadarScreen
import com.spaceslimmer.app.ui.radar.MediaRadar
import com.spaceslimmer.app.ui.report.WatchReportScreen
import com.spaceslimmer.app.ui.shared.VaultSavingsSummary
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.shared.isReplaceOutputPolicy
import com.spaceslimmer.app.ui.shared.rememberVaultUsage
import com.spaceslimmer.app.ui.shell.HistoryDetailScreen
import com.spaceslimmer.app.ui.shell.LogViewerScreen
import com.spaceslimmer.app.ui.shell.MainShell
import com.spaceslimmer.app.viewmodel.MediaCompressViewModel
import com.spaceslimmer.app.viewmodel.PhotoCompressViewModel
import com.spaceslimmer.app.prefs.RadarCacheSummary
import com.spaceslimmer.videohw.HwCapsProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.MediaKind

@Composable
fun SpaceSlimmerRoot(
    engine: CoreEngine?,
    engineError: String?,
    onRequestMediaPermissions: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    launchAction: String? = null,
) {
    val navController = rememberNavController()
    val photoViewModel: PhotoCompressViewModel = viewModel()
    val mediaViewModel: MediaCompressViewModel = viewModel()
    val context = LocalContext.current
    var showOnboarding by remember { mutableStateOf(AppPrefs.isFirstRun(context)) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    var radarSelection by remember { mutableStateOf<List<RadarItem>>(emptyList()) }
    var mainStartTab by remember { mutableStateOf(MainTab.Home) }
    var homeImportedKind by remember { mutableStateOf<MediaKind?>(null) }
    var homeImportNonce by remember { mutableStateOf(0) }
    LaunchedEffect(launchAction) {
        when (launchAction) {
            "radar" -> pendingRoute = "radar"
            "history" -> mainStartTab = MainTab.History
            "report" -> pendingRoute = "report"
            "recent_week" -> pendingRoute = "radar/recent"
            "compress" -> Unit
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val app = context.applicationContext as? SpaceSlimmerApp
    var showLowBattery by remember { mutableStateOf(false) }
    var lowBatteryPct by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(app?.thermalThrottled) {
        if (app?.thermalThrottled == true) {
            snackbarHostState.showSnackbar(context.getString(R.string.thermal_throttled))
        }
    }

    LaunchedEffect(Unit) {
        EngineEventHub.notifications.collect { (key, ratio) ->
            when (key) {
                "power.low_battery" -> {
                    lowBatteryPct = (ratio * 100f).toInt().coerceIn(0, 100)
                    showLowBattery = true
                }
                "quality.flat_frame" -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.quality_flat_frame))
                }
                "power.charging_resume" -> {
                    showLowBattery = false
                    snackbarHostState.showSnackbar(context.getString(R.string.power_charging_resume))
                    val pausedIds = AppPrefs.getLowBatteryPausedGroupIds(context)
                    if (pausedIds.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                pausedIds.forEach { id -> engine?.resumeGroup(id) }
                                AppPrefs.clearLowBatteryPausedGroupIds(context)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLowBattery) {
        SsAlertDialog(
            onDismissRequest = { showLowBattery = false },
            title = { Text(stringResource(R.string.low_battery_title)) },
            text = { Text(stringResource(R.string.low_battery_body, lowBatteryPct)) },
            confirmButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.low_battery_continue),
                    onClick = { showLowBattery = false },
                )
            },
            dismissButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.low_battery_pause),
                    onClick = {
                        showLowBattery = false
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val paused = engine?.listGroups()
                                    ?.filter { it.running > 0u }
                                    ?.map { group ->
                                        engine?.pauseGroup(group.id)
                                        group.id
                                    }
                                    ?.toSet()
                                    ?: emptySet()
                                if (paused.isNotEmpty()) {
                                    AppPrefs.setLowBatteryPausedGroupIds(context, paused)
                                }
                            }
                        }
                    },
                )
            },
        )
    }

    if (showOnboarding) {
        OnboardingScreen(
            onFinished = { showOnboarding = false },
            onPickFiles = { pendingRoute = null; showOnboarding = false },
            onOpenRadar = { pendingRoute = "radar"; showOnboarding = false },
        )
        return
    }

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route)
            pendingRoute = null
        }
    }

    val handleRadarSelection: (List<RadarItem>) -> Unit = { items ->
        radarSelection = items
        val kinds = items.map { it.probed.kind }.distinct()
        if (kinds.size == 1) {
            scope.launch {
                val uris = items.map { Uri.parse(it.probed.uri) }
                val result = when (kinds.first()) {
                    MediaKind.IMAGE -> photoViewModel.importUris(uris)
                    MediaKind.VIDEO -> mediaViewModel.importUris(uris, MediaKind.VIDEO)
                    MediaKind.AUDIO -> mediaViewModel.importUris(uris, MediaKind.AUDIO)
                }
                result.onSuccess {
                    mainStartTab = MainTab.Home
                    homeImportedKind = kinds.first()
                    homeImportNonce += 1
                    navController.popBackStack("main", false)
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(err.message ?: context.getString(R.string.import_failed))
                }
            }
        } else {
            navController.navigate("radar/preset")
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = Modifier.padding(scaffoldPadding),
    ) {
        composable("main") {
            MainShell(
                engine = engine,
                engineError = engineError,
                onNavigate = { route -> navController.navigate(route) },
                startTab = mainStartTab,
                homeContent = { modifier ->
                    HomeScreen(
                        engine = engine,
                        engineAvailable = engine != null,
                        onRequestMediaPermissions = onRequestMediaPermissions,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenRadar = { navController.navigate("radar") },
                        onSubmitted = { kind, groupId ->
                            val route = when (kind) {
                                MediaKind.IMAGE -> "photo/jobs/$groupId"
                                MediaKind.VIDEO -> "video/jobs/$groupId"
                                MediaKind.AUDIO -> "audio/jobs/$groupId"
                            }
                            navController.navigate(route) { popUpTo("main") }
                        },
                        importedKind = homeImportedKind,
                        importNonce = homeImportNonce,
                        photoViewModel = photoViewModel,
                        mediaViewModel = mediaViewModel,
                        modifier = modifier,
                    )
                },
            )
        }
        composable("history/job/{groupId}/{jobId}") { entry ->
            val groupId = entry.arguments?.getString("groupId") ?: return@composable
            val jobId = entry.arguments?.getString("jobId") ?: return@composable
            HistoryDetailScreen(
                engine = engine,
                groupId = groupId,
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onOpenProcessing = { kind, gid ->
                    val route = when (kind) {
                        MediaKind.IMAGE -> "photo/jobs/$gid"
                        MediaKind.VIDEO -> "video/jobs/$gid"
                        MediaKind.AUDIO -> "audio/jobs/$gid"
                    }
                    navController.navigate(route)
                },
            )
        }
        composable("settings/logs") {
            LogViewerScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable("radar") {
            RadarScreen(
                engine = engine,
                onBack = { navController.popBackStack() },
                onConfirmSelection = handleRadarSelection,
                onRequestMediaPermissions = onRequestMediaPermissions,
            )
        }
        composable("radar/recent") {
            RadarScreen(
                engine = engine,
                onBack = { navController.popBackStack() },
                onConfirmSelection = handleRadarSelection,
                onRequestMediaPermissions = onRequestMediaPermissions,
                launchMode = "recent_week",
            )
        }
        composable("radar/preset") {
            RadarPresetScreen(
                items = radarSelection,
                engine = engine,
                onBack = { navController.popBackStack() },
                onRequestNotificationPermission = onRequestNotificationPermission,
                onSubmitted = {
                    mainStartTab = MainTab.History
                    navController.navigate("main") {
                        popUpTo("main") { inclusive = true }
                    }
                },
            )
        }
        composable("report") {
            WatchReportScreen(
                engine = engine,
                onBack = { navController.popBackStack() },
            )
        }
        composable("photo/jobs/{groupId}") { entry ->
            val groupId = entry.arguments?.getString("groupId") ?: return@composable
            PhotoJobsScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onCompare = { job ->
                    photoViewModel.compareJob = job
                    navController.navigate("photo/compare")
                },
                viewModel = photoViewModel,
            )
        }
        composable("photo/compare") {
            val job = photoViewModel.compareJob
            if (job != null) {
                PhotoCompareScreen(
                    job = job,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("video/jobs/{groupId}") { entry ->
            val groupId = entry.arguments?.getString("groupId") ?: return@composable
            MediaJobsScreen(
                kind = MediaKind.VIDEO,
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onCompare = { job ->
                    mediaViewModel.compareJob = job
                    navController.navigate("video/compare")
                },
                viewModel = mediaViewModel,
            )
        }
        composable("video/compare") {
            val job = mediaViewModel.compareJob
            if (job != null) {
                VideoCompareScreen(
                    job = job,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("audio/jobs/{groupId}") { entry ->
            val groupId = entry.arguments?.getString("groupId") ?: return@composable
            MediaJobsScreen(
                kind = MediaKind.AUDIO,
                groupId = groupId,
                onBack = { navController.popBackStack() },
                viewModel = mediaViewModel,
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    engine: CoreEngine?,
    engineAvailable: Boolean,
    onRequestMediaPermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenRadar: () -> Unit,
    onSubmitted: (MediaKind, String) -> Unit,
    importedKind: MediaKind?,
    importNonce: Int,
    photoViewModel: PhotoCompressViewModel,
    mediaViewModel: MediaCompressViewModel,
    modifier: Modifier = Modifier,
) {
    var vaultExpiring by remember { mutableStateOf(0) }
    var replaceGallerySaved by remember { mutableStateOf(0L) }
    var radarCache by remember { mutableStateOf<RadarCacheSummary?>(null) }
    var radarScanning by remember { mutableStateOf(false) }
    val vaultUsage = rememberVaultUsage(engine)
    val context = LocalContext.current
    LaunchedEffect(engine) {
        radarCache = AppPrefs.loadRadarCache(context)
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis() / 1000
            val soon = now + java.util.concurrent.TimeUnit.DAYS.toSeconds(3)
            vaultExpiring = engine?.listVaultItems()
                ?.count { !it.restored && it.expiresAt <= soon }
                ?: 0
            replaceGallerySaved = engine?.listGroups()
                ?.filter { isReplaceOutputPolicy(it.outputPolicy) }
                ?.sumOf { it.savedBytes }
                ?: 0L
        }
    }
    LaunchedEffect(engine, engineAvailable) {
        val core = engine ?: return@LaunchedEffect
        if (!engineAvailable) return@LaunchedEffect
        onRequestMediaPermissions()
        radarScanning = true
        runCatching {
            val result = withContext(Dispatchers.IO) {
                MediaRadar.quickScan(context, core, HwCapsProbe.probe())
            }
            AppPrefs.saveRadarCache(
                context,
                result.items.size,
                result.totalEstimateLow.toLong(),
                result.totalEstimateHigh.toLong(),
                result.scannedEntries,
                result.libraryBytes,
                result.elapsedMs,
            )
            radarCache = RadarCacheSummary(
                itemCount = result.items.size,
                estimateLow = result.totalEstimateLow.toLong(),
                estimateHigh = result.totalEstimateHigh.toLong(),
                scannedAtMs = System.currentTimeMillis(),
                scannedEntries = result.scannedEntries,
                libraryBytes = result.libraryBytes,
                elapsedMs = result.elapsedMs,
            )
        }
        radarScanning = false
    }

    HomeCompressSection(
        engineAvailable = engineAvailable,
        photoViewModel = photoViewModel,
        mediaViewModel = mediaViewModel,
        importedKind = importedKind,
        importNonce = importNonce,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onSubmitted = onSubmitted,
        modifier = modifier,
        headerContent = {
            SsLargeTitle(title = stringResource(R.string.home_title))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SsSection(title = stringResource(R.string.radar_title)) {
                    SsHeroMetric(
                        embedded = true,
                        title = null,
                        value = if (!radarScanning && radarCache != null && radarCache!!.itemCount > 0) {
                            stringResource(
                                R.string.radar_result,
                                radarCache!!.itemCount,
                                formatBytes(context, radarCache!!.estimateLow),
                                formatBytes(context, radarCache!!.estimateHigh),
                            )
                        } else {
                            null
                        },
                        subtitle = if (!radarScanning && radarCache != null && radarCache!!.libraryBytes > 0) {
                            stringResource(
                                R.string.radar_library_usage,
                                formatBytes(context, radarCache!!.libraryBytes),
                                radarCache!!.scannedEntries,
                            )
                        } else {
                            null
                        },
                        hint = if (!radarScanning && radarCache != null && radarCache!!.itemCount > 0) {
                            stringResource(R.string.radar_summary_hint)
                        } else if (!radarScanning) {
                            stringResource(R.string.radar_empty)
                        } else {
                            stringResource(R.string.radar_scanning)
                        },
                        loading = radarScanning,
                        modifier = Modifier.fillMaxWidth(),
                        action = {
                            SsPrimaryButton(
                                text = stringResource(R.string.radar_open),
                                onClick = onOpenRadar,
                                enabled = engineAvailable,
                            )
                        },
                    )
                }
                if (vaultUsage > 0 || replaceGallerySaved > 0) {
                    SsCard {
                        VaultSavingsSummary(
                            gallerySavedBytes = replaceGallerySaved,
                            vaultUsageBytes = vaultUsage,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (vaultExpiring > 0) {
                    Text(
                        stringResource(R.string.vault_expiring_soon, vaultExpiring),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}
