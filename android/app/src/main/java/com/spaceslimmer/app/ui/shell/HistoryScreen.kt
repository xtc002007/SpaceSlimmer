package com.spaceslimmer.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.MediaThumbnail
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsEmptyState
import com.spaceslimmer.app.ui.components.SsLargeTitle
import com.spaceslimmer.app.ui.components.SsListRow
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.components.SsTitleAction
import com.spaceslimmer.app.ui.shared.GbShareCard
import com.spaceslimmer.app.ui.shared.HistoryEntry
import com.spaceslimmer.app.ui.shared.compressStatusBucketColor
import com.spaceslimmer.app.ui.shared.compressStatusBucketLabel
import com.spaceslimmer.app.ui.shared.flattenHistory
import com.spaceslimmer.app.ui.shared.flattenInProgressHistory
import com.spaceslimmer.app.ui.shared.flattenPendingSaveHistory
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.shared.formatHistoryDate
import com.spaceslimmer.app.ui.shared.formatHistoryRowMeta
import com.spaceslimmer.app.ui.shared.isPendingSave
import com.spaceslimmer.app.ui.shared.jobStatusBucket
import com.spaceslimmer.app.ui.shared.mediaKindLabel
import com.spaceslimmer.app.ui.theme.savingsColor
import com.spaceslimmer.core.EngineEventHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.GroupView
import uniffi.ss_core.MediaKind

enum class HistoryKindFilter { ALL, VIDEO, IMAGE, AUDIO }

@Composable
fun HistoryScreen(
    engine: CoreEngine?,
    modifier: Modifier = Modifier,
    onOpenReport: () -> Unit = {},
    onOpenJob: (groupId: String, jobId: String) -> Unit = { _, _ -> },
    onOpenInProgressJob: (kind: MediaKind, groupId: String) -> Unit = { _, _ -> },
    onOpenProcessing: (kind: MediaKind, groupId: String) -> Unit = onOpenInProgressJob,
    onGoHome: () -> Unit = {},
) {
    var groups by remember { mutableStateOf<List<GroupView>>(emptyList()) }
    var filter by remember { mutableStateOf(HistoryKindFilter.ALL) }
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedJobIds by remember { mutableStateOf(setOf<String>()) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun reload() {
        groups = withContext(Dispatchers.IO) {
            runCatching { engine?.listGroups() ?: emptyList() }
                .getOrDefault(emptyList())
        }
        val currentIds = groups.flatMap { g -> g.jobs.map { j -> j.spec.id } }.toSet()
        selectedJobIds = selectedJobIds.intersect(currentIds)
    }

    LaunchedEffect(engine) {
        reload()
        launch {
            EngineEventHub.groups.collect { updated ->
                groups = groups
                    .map { if (it.id == updated.id) updated else it }
                    .let { list ->
                        if (list.any { it.id == updated.id }) list else list + updated
                    }
                    .filter { group -> group.jobs.isNotEmpty() }
            }
        }
        launch {
            EngineEventHub.historyRefresh.collect {
                reload()
            }
        }
    }

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reload() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun matchesFilter(entry: HistoryEntry): Boolean = when (filter) {
        HistoryKindFilter.ALL -> true
        HistoryKindFilter.VIDEO -> entry.job.spec.fingerprint.kind == MediaKind.VIDEO
        HistoryKindFilter.IMAGE -> entry.job.spec.fingerprint.kind == MediaKind.IMAGE
        HistoryKindFilter.AUDIO -> entry.job.spec.fingerprint.kind == MediaKind.AUDIO
    }

    val inProgress = flattenInProgressHistory(groups)
    val pendingSave = flattenPendingSaveHistory(groups)
    val entries = flattenHistory(groups).filter { !isPendingSave(it.job) }

    val visible = entries.filter { matchesFilter(it) }
    val visibleInProgress = inProgress.filter { matchesFilter(it) }
    val visiblePendingSave = pendingSave.filter { matchesFilter(it) }
    val hasAnyRecords = entries.isNotEmpty() || inProgress.isNotEmpty() || pendingSave.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
    ) {
        SsLargeTitle(
            title = stringResource(R.string.history_title),
            trailing = {
                SsTitleAction(
                    text = stringResource(R.string.watch_report_title),
                    onClick = onOpenReport,
                )
            },
        )
        if (!hasAnyRecords) {
            SsEmptyState(
                title = stringResource(R.string.history_empty),
                body = stringResource(R.string.history_empty_body),
                action = {
                    SsPrimaryButton(
                        text = stringResource(R.string.history_empty_cta),
                        onClick = onGoHome,
                    )
                },
            )
        } else {
            val totalSaved = (visible + visiblePendingSave).sumOf { it.job.savedBytes ?: 0L }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.history_total_saved, formatBytes(context, totalSaved)),
                    style = MaterialTheme.typography.titleMedium,
                    color = savingsColor(),
                )
                GbShareCard(savedBytes = totalSaved)
                SsSegmented(
                    options = HistoryKindFilter.entries.map { f ->
                        SsSegmentOption(f, historyFilterLabel(f))
                    },
                    selected = filter,
                    onSelected = { filter = it },
                )

                // 历史记录批量管理与清空操作栏
                if (visible.isNotEmpty() || visiblePendingSave.isNotEmpty()) {
                    val allActionable = visible + visiblePendingSave
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isBatchMode) {
                            OutlinedButton(
                                onClick = {
                                    isBatchMode = true
                                    selectedJobIds = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(stringResource(R.string.batch_manage), fontSize = 12.sp)
                            }

                            if (visible.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showClearAllConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.history_clear_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.batch_selected_ratio, selectedJobIds.size, allActionable.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = {
                                        selectedJobIds = if (selectedJobIds.size == allActionable.size) {
                                            emptySet()
                                        } else {
                                            allActionable.map { it.job.spec.id }.toSet()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(if (selectedJobIds.size == allActionable.size) stringResource(R.string.batch_deselect_all) else stringResource(R.string.batch_select_all), fontSize = 12.sp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showBatchDeleteConfirm = true },
                                    enabled = selectedJobIds.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(stringResource(R.string.batch_delete_selected, selectedJobIds.size), fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        isBatchMode = false
                                        selectedJobIds = emptySet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(stringResource(R.string.batch_exit), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 1. 进行中的任务
                    if (visibleInProgress.isNotEmpty()) {
                        item(key = "in_progress_header") {
                            Text(
                                text = stringResource(R.string.history_in_progress),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = visibleInProgress,
                            key = { "progress-${it.job.spec.id}" },
                        ) { entry ->
                            val job = entry.job
                            val bucket = jobStatusBucket(job.state)
                            SsCard {
                                SsListRow(
                                    title = job.spec.displayName,
                                    leadingContent = {
                                        MediaThumbnail(
                                            uri = job.spec.sourceUri,
                                            kind = job.spec.fingerprint.kind,
                                            size = 52.dp,
                                        )
                                    },
                                    subtitle = stringResource(
                                        R.string.compress_status,
                                        compressStatusBucketLabel(bucket),
                                    ),
                                    titleColor = compressStatusBucketColor(bucket),
                                    showDivider = false,
                                    showChevron = true,
                                    onClick = {
                                        onOpenInProgressJob(job.spec.fingerprint.kind, entry.groupId)
                                    },
                                )
                            }
                        }
                    }

                    // 2. 待确认保存任务专区（压缩完成但尚未处理保存/替换）
                    if (visiblePendingSave.isNotEmpty()) {
                        item(key = "pending_save_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.history_pending_save_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFF9800).copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            text = "${visiblePendingSave.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.history_pending_save_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = visiblePendingSave,
                            key = { "pending-${it.job.spec.id}" },
                        ) { entry ->
                            val job = entry.job
                            val kind = job.spec.fingerprint.kind
                            val thumbUri = job.outputUri ?: job.spec.sourceUri
                            val isSelected = selectedJobIds.contains(job.spec.id)

                            SsCard(
                                containerColor = Color(0xFFFFF8E1),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isBatchMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedJobIds = if (isSelected) {
                                                    selectedJobIds - job.spec.id
                                                } else {
                                                    selectedJobIds + job.spec.id
                                                }
                                            },
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        val pendingPrefix = stringResource(R.string.history_pending_save_prefix)
                                        SsListRow(
                                            title = job.spec.displayName,
                                            leadingContent = {
                                                MediaThumbnail(
                                                    uri = thumbUri,
                                                    kind = kind,
                                                    size = 52.dp,
                                                )
                                            },
                                            subtitle = pendingPrefix + formatHistoryRowMeta(
                                                context = context,
                                                kind = kind,
                                                kindLabel = mediaKindLabel(kind),
                                                dateLabel = formatHistoryDate(job.updatedAt),
                                                originalBytes = job.spec.fingerprint.fileBytes,
                                            ),
                                            meta = job.savedBytes?.let {
                                                stringResource(R.string.photo_saved_bytes, formatBytes(context, it))
                                            },
                                            titleColor = Color(0xFFD84315),
                                            showDivider = false,
                                            showChevron = false,
                                            trailing = {
                                                if (!isBatchMode) {
                                                    Button(
                                                        onClick = { onOpenProcessing(kind, entry.groupId) },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFFE65100),
                                                        ),
                                                        modifier = Modifier.height(32.dp),
                                                    ) {
                                                        Text(stringResource(R.string.history_process_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                if (isBatchMode) {
                                                    selectedJobIds = if (isSelected) {
                                                        selectedJobIds - job.spec.id
                                                    } else {
                                                        selectedJobIds + job.spec.id
                                                    }
                                                } else {
                                                    onOpenJob(entry.groupId, job.spec.id)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 已完成和归档记录
                    if (visible.isNotEmpty()) {
                        item(key = "records_header") {
                            Text(
                                text = stringResource(R.string.history_records),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = visible,
                            key = { it.job.spec.id },
                        ) { entry ->
                            val job = entry.job
                            val kind = job.spec.fingerprint.kind
                            val bucket = jobStatusBucket(job.state)
                            val isSelected = selectedJobIds.contains(job.spec.id)
                            val thumbUri = job.outputUri ?: job.spec.sourceUri

                            SsCard {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isBatchMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedJobIds = if (isSelected) {
                                                    selectedJobIds - job.spec.id
                                                } else {
                                                    selectedJobIds + job.spec.id
                                                }
                                            },
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        SsListRow(
                                            title = job.spec.displayName,
                                            leadingContent = {
                                                MediaThumbnail(
                                                    uri = thumbUri,
                                                    kind = kind,
                                                    size = 52.dp,
                                                )
                                            },
                                            subtitle = stringResource(
                                                R.string.compress_status,
                                                compressStatusBucketLabel(bucket),
                                            ) + " · " + formatHistoryRowMeta(
                                                context = context,
                                                kind = kind,
                                                kindLabel = mediaKindLabel(kind),
                                                dateLabel = formatHistoryDate(job.updatedAt),
                                                originalBytes = job.spec.fingerprint.fileBytes,
                                            ),
                                            meta = job.savedBytes?.let {
                                                stringResource(R.string.photo_saved_bytes, formatBytes(context, it))
                                            },
                                            titleColor = MaterialTheme.colorScheme.primary,
                                            showDivider = false,
                                            showChevron = !isBatchMode,
                                            onClick = {
                                                if (isBatchMode) {
                                                    selectedJobIds = if (isSelected) {
                                                        selectedJobIds - job.spec.id
                                                    } else {
                                                        selectedJobIds + job.spec.id
                                                    }
                                                } else {
                                                    onOpenJob(entry.groupId, job.spec.id)
                                                }
                                            },
                                        )
                                    }
                                    if (!isBatchMode) {
                                        IconButton(
                                            onClick = { itemToDelete = entry },
                                            modifier = Modifier.padding(end = 4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.history_delete_record),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp),
                                            )
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

    // 单条历史记录删除二次确认弹窗
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.history_delete_single_title)) },
            text = { Text(stringResource(R.string.history_delete_single_body, target.job.spec.displayName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = target.job.spec.id
                        itemToDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { engine?.deleteJob(idToDelete) }
                                engine?.flush()
                            }
                            reload()
                            EngineEventHub.requestHistoryRefresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.history_delete_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 批量删除历史记录二次确认弹窗
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(stringResource(R.string.history_delete_batch_title), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(stringResource(R.string.history_delete_batch_body, selectedJobIds.size))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirm = false
                        val toDelete = selectedJobIds
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                toDelete.forEach { jobId ->
                                    runCatching { engine?.deleteJob(jobId) }
                                }
                                engine?.flush()
                            }
                            selectedJobIds = emptySet()
                            isBatchMode = false
                            reload()
                            EngineEventHub.requestHistoryRefresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.batch_delete_selected, selectedJobIds.size), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 一键清空历史记录二次确认弹窗
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(stringResource(R.string.history_clear_all_title), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(stringResource(R.string.history_clear_all_body, visible.size))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllConfirm = false
                        val toDelete = visible.map { it.job.spec.id }
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                toDelete.forEach { jobId ->
                                    runCatching { engine?.deleteJob(jobId) }
                                }
                                engine?.flush()
                            }
                            selectedJobIds = emptySet()
                            reload()
                            EngineEventHub.requestHistoryRefresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.history_clear_all_ok), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun historyFilterLabel(filter: HistoryKindFilter): String = when (filter) {
    HistoryKindFilter.ALL -> stringResource(R.string.radar_filter_all)
    HistoryKindFilter.VIDEO -> stringResource(R.string.radar_filter_video)
    HistoryKindFilter.IMAGE -> stringResource(R.string.radar_filter_image)
    HistoryKindFilter.AUDIO -> stringResource(R.string.radar_filter_audio)
}
