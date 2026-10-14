package com.spaceslimmer.app.ui.radar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsEmptyState
import com.spaceslimmer.app.ui.components.SsHeroMetric
import com.spaceslimmer.app.ui.components.SsJobRow
import com.spaceslimmer.app.ui.components.SsSection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsScreen
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.core.GalleryWriter
import com.spaceslimmer.videohw.HwCapsProbe
import kotlinx.coroutines.launch
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.MediaKind

enum class RadarFilter { ALL, VIDEO, IMAGE, AUDIO }

enum class RadarSort { SAVINGS, SIZE, DATE }

@Composable
fun RadarScreen(
    engine: CoreEngine?,
    onBack: () -> Unit,
    onConfirmSelection: (List<RadarItem>) -> Unit,
    onRequestMediaPermissions: () -> Unit,
    launchMode: String? = null,
) {
    var scanResult by remember { mutableStateOf<RadarScanResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var filter by remember {
        mutableStateOf(if (launchMode == "recent_week") RadarFilter.VIDEO else RadarFilter.ALL)
    }
    var sort by remember { mutableStateOf(RadarSort.SAVINGS) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun runScan() {
        val core = engine ?: return
        onRequestMediaPermissions()
        loading = true
        error = null
        scope.launch {
            runCatching {
                scanResult = MediaRadar.scan(
                    context = context,
                    engine = core,
                    caps = HwCapsProbe.probe(),
                    limit = MediaRadar.MAX_TOTAL,
                    onProgress = { done, total ->
                        scanProgress = done to total
                    },
                )
                scanResult?.let { result ->
                    AppPrefs.saveRadarCache(
                        context,
                        result.items.size,
                        result.totalEstimateLow.toLong(),
                        result.totalEstimateHigh.toLong(),
                        result.scannedEntries,
                        result.libraryBytes,
                        result.elapsedMs,
                    )
                    selected = if (launchMode == "recent_week") {
                        result.items
                            .filter {
                                it.probed.kind == MediaKind.VIDEO &&
                                    RadarTime.isWithinLastWeek(it.dateAdded)
                            }
                            .map { it.probed.uri }
                            .toSet()
                    } else {
                        emptySet()
                    }
                }
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(engine, launchMode) {
        if (launchMode == "recent_week" && engine != null) {
            runScan()
        }
    }

    val visibleItems = remember(scanResult, filter, sort) {
        val base = scanResult?.items.orEmpty()
        val filtered = when (filter) {
            RadarFilter.ALL -> base
            RadarFilter.VIDEO -> base.filter { it.probed.kind == MediaKind.VIDEO }
            RadarFilter.IMAGE -> base.filter { it.probed.kind == MediaKind.IMAGE }
            RadarFilter.AUDIO -> base.filter { it.probed.kind == MediaKind.AUDIO }
        }
        when (sort) {
            RadarSort.SAVINGS -> filtered.sortedByDescending { it.savingsBytes }
            RadarSort.SIZE -> filtered.sortedByDescending { it.probed.fileBytes }
            RadarSort.DATE -> filtered.sortedByDescending { it.dateAdded }
        }
    }

    SsScreen(
        topBar = { SsAppBar(title = stringResource(R.string.radar_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            scanResult?.let { result ->
                SsHeroMetric(
                    title = stringResource(R.string.radar_summary_title),
                    value = stringResource(
                        R.string.radar_result,
                        result.items.size,
                        formatBytes(context, result.totalEstimateLow.toLong()),
                        formatBytes(context, result.totalEstimateHigh.toLong()),
                    ),
                    subtitle = if (result.libraryBytes > 0) {
                        stringResource(
                            R.string.radar_library_usage,
                            formatBytes(context, result.libraryBytes),
                            result.scannedEntries,
                        )
                    } else {
                        null
                    },
                    hint = stringResource(R.string.radar_summary_hint),
                )
                if (result.elapsedMs > 0) {
                    Text(
                        stringResource(
                            if (result.elapsedMs <= MediaRadar.CONTRACT_MS) {
                                R.string.radar_scan_elapsed
                            } else {
                                R.string.radar_scan_elapsed_over
                            },
                            result.elapsedMs,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.elapsedMs <= MediaRadar.CONTRACT_MS) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            SsSegmented(
                options = RadarFilter.entries.map { f ->
                    SsSegmentOption(f, radarFilterLabel(f))
                },
                selected = filter,
                onSelected = { filter = it },
            )
            SsSegmented(
                options = RadarSort.entries.map { s ->
                    SsSegmentOption(s, radarSortLabel(s))
                },
                selected = sort,
                onSelected = { sort = it },
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                scanProgress?.let { (done, total) ->
                    Text(
                        stringResource(R.string.radar_scan_progress, done, total.coerceAtLeast(1)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            if (visibleItems.isEmpty() && !loading) {
                SsEmptyState(
                    title = stringResource(R.string.radar_empty),
                    body = stringResource(R.string.radar_empty_body),
                    modifier = Modifier.weight(1f),
                    action = {
                        SsPrimaryButton(
                            text = stringResource(R.string.radar_empty_cta),
                            onClick = { runScan() },
                            enabled = engine != null,
                        )
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleItems, key = { it.probed.uri }) { item ->
                        RadarItemRow(
                            item = item,
                            checked = selected.contains(item.probed.uri),
                            onToggle = {
                                selected = if (selected.contains(item.probed.uri)) {
                                    selected - item.probed.uri
                                } else {
                                    selected + item.probed.uri
                                }
                            },
                        )
                    }
                }
            }
            SsSecondaryButton(
                text = stringResource(R.string.radar_scan),
                onClick = { runScan() },
                enabled = engine != null && !loading,
            )
            SsPrimaryButton(
                text = stringResource(R.string.radar_smart_compress, selected.size),
                onClick = {
                    val picked = visibleItems.filter { selected.contains(it.probed.uri) }
                    if (picked.isNotEmpty()) onConfirmSelection(picked)
                },
                enabled = selected.isNotEmpty(),
            )
            SsSecondaryButton(
                text = stringResource(R.string.radar_compress_all, visibleItems.size),
                onClick = {
                    if (visibleItems.isNotEmpty()) onConfirmSelection(visibleItems)
                },
                enabled = visibleItems.isNotEmpty() && !loading,
            )
        }
    }
}

private fun openMediaPreview(context: Context, item: RadarItem) {
    val resolved = runCatching { GalleryWriter.resolveReplaceUri(context, item.probed.uri) }.getOrNull()
    val targetUri = resolved ?: runCatching { Uri.parse(item.probed.uri) }.getOrNull() ?: return
    val mime = when (item.probed.kind) {
        MediaKind.VIDEO -> "video/*"
        MediaKind.IMAGE -> "image/*"
        MediaKind.AUDIO -> "audio/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(targetUri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}

@Composable
private fun RadarItemRow(
    item: RadarItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val savingsMeta = buildList {
        add(stringResource(R.string.radar_item_savings, formatBytes(context, item.savingsBytes)))
        if (item.probed.isLivePhoto) add(stringResource(R.string.radar_live_photo))
    }.joinToString(" · ")
    SsJobRow(
        title = item.probed.displayName,
        subtitle = stringResource(
            R.string.radar_item_subtitle,
            item.probed.kind.name,
            formatBytes(context, item.probed.fileBytes),
        ),
        meta = savingsMeta,
        thumbnailUri = item.probed.uri,
        thumbnailContentDescription = stringResource(
            R.string.radar_item_thumbnail,
            item.probed.displayName,
        ),
        mediaKind = item.probed.kind,
        checked = checked,
        onCheckedChange = { onToggle() },
        onClick = { openMediaPreview(context, item) },
    )
}

@Composable
private fun radarFilterLabel(filter: RadarFilter): String = when (filter) {
    RadarFilter.ALL -> stringResource(R.string.radar_filter_all)
    RadarFilter.VIDEO -> stringResource(R.string.radar_filter_video)
    RadarFilter.IMAGE -> stringResource(R.string.radar_filter_image)
    RadarFilter.AUDIO -> stringResource(R.string.radar_filter_audio)
}

@Composable
private fun radarSortLabel(sort: RadarSort): String = when (sort) {
    RadarSort.SAVINGS -> stringResource(R.string.radar_sort_savings)
    RadarSort.SIZE -> stringResource(R.string.radar_sort_size)
    RadarSort.DATE -> stringResource(R.string.radar_sort_date)
}
