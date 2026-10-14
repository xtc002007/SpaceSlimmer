package com.spaceslimmer.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.MediaThumbnail
import com.spaceslimmer.app.ui.components.SsAlertDialog
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsDialogTextButton
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsScreen
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.app.ui.components.SsSection
import com.spaceslimmer.app.ui.shared.JobCompressionSummary
import com.spaceslimmer.app.ui.shared.JobMessageText
import com.spaceslimmer.app.ui.shared.compressRequestSummary
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.shared.formatHistoryDate
import com.spaceslimmer.app.ui.shared.isHistoryTerminal
import com.spaceslimmer.app.ui.shared.isPendingSave
import com.spaceslimmer.app.ui.shared.jobSourcePath
import com.spaceslimmer.app.ui.shared.jobStateLabel
import com.spaceslimmer.app.ui.shared.mediaKindLabel
import com.spaceslimmer.core.EngineEventHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind

@Composable
fun HistoryDetailScreen(
    engine: CoreEngine?,
    groupId: String,
    jobId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenProcessing: (MediaKind, String) -> Unit = { _, _ -> },
) {
    var job by remember { mutableStateOf<JobView?>(null) }
    var request by remember { mutableStateOf<CompressRequest?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(engine, groupId, jobId) {
        val loaded = withContext(Dispatchers.IO) {
            val group = runCatching { engine?.group(groupId) }.getOrNull()
            val found = group?.jobs?.firstOrNull { it.spec.id == jobId }
            val req = found?.spec?.requestOverride ?: group?.request
            found to req
        }
        job = loaded.first
        request = loaded.second
    }

    SsScreen(
        topBar = {
            SsAppBar(
                title = job?.let { it.spec.displayName }
                    ?: stringResource(R.string.history_detail_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        val current = job
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_detail_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@SsScreen
        }

        val canDelete = isHistoryTerminal(current.state)
        val isPending = isPendingSave(current)
        val canProcess = isPending || (current.state == JobState.SUCCEEDED && current.outputUri != null)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶部媒体缩略图预览与摘要卡片
            SsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val previewUri = current.outputUri ?: current.spec.sourceUri
                    MediaThumbnail(
                        uri = previewUri,
                        kind = current.spec.fingerprint.kind,
                        size = 80.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = current.spec.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = mediaKindLabel(current.spec.fingerprint.kind),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            if (isPending) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF9800).copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.history_pending_save_title),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        fingerprintMeta(current)?.let { meta ->
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SsSection(title = stringResource(R.string.history_detail_before)) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DetailLine(stringResource(R.string.history_detail_path), jobSourcePath(current))
                    DetailLine(stringResource(R.string.history_detail_filename), current.spec.displayName)
                    DetailLine(
                        stringResource(R.string.history_detail_type),
                        mediaKindLabel(current.spec.fingerprint.kind),
                    )
                    DetailLine(
                        stringResource(R.string.history_detail_original_size),
                        formatBytes(context, current.spec.fingerprint.fileBytes.toLong()),
                    )
                    DetailLine(
                        stringResource(R.string.history_detail_date),
                        formatHistoryDate(current.updatedAt),
                    )
                    request?.let { req ->
                        DetailLine(
                            stringResource(R.string.history_detail_params),
                            compressRequestSummary(req, current.spec.fingerprint.kind),
                        )
                    }
                    fingerprintMeta(current)?.let { meta ->
                        DetailLine(stringResource(R.string.history_detail_media_info), meta)
                    }
                }
            }

            SsSection(title = stringResource(R.string.history_detail_after)) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val statusText = if (isPending) {
                        stringResource(R.string.history_detail_completed_pending)
                    } else {
                        jobStateLabel(current.state)
                    }
                    DetailLine(
                        stringResource(R.string.history_detail_status),
                        statusText,
                    )
                    current.outputUri?.let { uri ->
                        DetailLine(stringResource(R.string.history_detail_output_path), uri)
                    }
                    current.outputBytes?.let { bytes ->
                        DetailLine(
                            stringResource(R.string.history_detail_output_size),
                            formatBytes(context, bytes.toLong()),
                        )
                    }
                    JobCompressionSummary(job = current, prominent = false)
                    JobMessageText(current)
                }
            }

            if (deleteError != null) {
                Text(deleteError!!, color = MaterialTheme.colorScheme.error)
            }

            // 处理流转按钮：当有压缩输出时，允许前往处理页面确认保存/替换
            if (canProcess) {
                SsPrimaryButton(
                    text = if (isPending) stringResource(R.string.history_goto_process_page) else stringResource(R.string.history_view_process_page),
                    onClick = {
                        onOpenProcessing(current.spec.fingerprint.kind, groupId)
                    },
                )
            }

            if (canDelete) {
                if (canProcess) {
                    SsSecondaryButton(
                        text = stringResource(R.string.history_delete_record),
                        onClick = { showDeleteConfirm = true },
                    )
                } else {
                    SsPrimaryButton(
                        text = stringResource(R.string.history_delete_record),
                        onClick = { showDeleteConfirm = true },
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        SsAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body)) },
            confirmButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.history_delete_confirm),
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { engine?.deleteJob(jobId) }
                            }
                            withContext(Dispatchers.Main) {
                                result
                                    .onSuccess {
                                        EngineEventHub.requestHistoryRefresh()
                                        onDeleted()
                                    }
                                    .onFailure { deleteError = it.message }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showDeleteConfirm = false },
                )
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun fingerprintMeta(job: JobView): String? {
    val fp = job.spec.fingerprint
    val parts = buildList {
        if (fp.width > 0u && fp.height > 0u) add("${fp.width}×${fp.height}")
        if (fp.durationMs > 0uL) add("${fp.durationMs / 1000uL}s")
        fp.videoCodec?.takeIf { it.isNotBlank() }?.let { add(it) }
        fp.audioCodec?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (fp.mime.isNotBlank()) add(fp.mime)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
