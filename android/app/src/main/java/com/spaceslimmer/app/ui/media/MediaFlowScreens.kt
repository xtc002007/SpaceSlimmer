package com.spaceslimmer.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.export.GalleryCommit
import com.spaceslimmer.app.service.CompressForegroundService
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsProgressBar
import com.spaceslimmer.app.ui.components.SsScreen
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.app.ui.components.SsTaskJobCard
import com.spaceslimmer.app.ui.components.CompressClockCountdown
import com.spaceslimmer.app.ui.shared.CompressResultSummary
import com.spaceslimmer.app.ui.shared.ExportToFolderButton
import com.spaceslimmer.app.ui.shared.GroupSavingsSummary
import com.spaceslimmer.app.ui.shared.JobControlBar
import com.spaceslimmer.app.ui.shared.CompressStatusBucket
import com.spaceslimmer.app.ui.shared.compressStatusBucketColor
import com.spaceslimmer.app.ui.shared.compressStatusBucketLabel
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.shared.groupStatusBucket
import com.spaceslimmer.app.ui.shared.isReplaceOutputPolicy
import com.spaceslimmer.app.ui.shared.rememberGallerySaveHandler
import com.spaceslimmer.app.ui.shared.shareSavedBytes
import com.spaceslimmer.app.viewmodel.MediaCompressViewModel
import com.spaceslimmer.core.EngineEventHub
import kotlinx.coroutines.launch
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy

@Composable
fun MediaJobsScreen(
    kind: MediaKind,
    groupId: String,
    onBack: () -> Unit,
    onCompare: (JobView) -> Unit = {},
    viewModel: MediaCompressViewModel,
) {
    val group by viewModel.latestGroupFlow.collectAsState()
    val finished = group?.let { viewModel.isGroupFinished(it) } == true
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val replaceMode = isReplaceOutputPolicy(viewModel.lastRequest?.outputPolicy)
    var savedMsg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val replaceToGallery = rememberGallerySaveHandler(
        replaceMode = true,
        save = { viewModel.saveSucceeded(kind, OutputPolicy.REPLACE_TO_VAULT) },
        onMessage = {
            savedMsg = it
            saving = false
        },
        savedCountRes = if (kind == MediaKind.AUDIO) R.string.audio_saved_to_library else R.string.media_saved_to_gallery,
        replacedCountRes = if (kind == MediaKind.AUDIO) R.string.audio_replaced_count else R.string.media_replaced_count,
        kind = kind,
    )
    val saveAsNewToGallery = rememberGallerySaveHandler(
        replaceMode = false,
        save = { viewModel.saveSucceeded(kind, OutputPolicy.SAVE_AS_NEW) },
        onMessage = {
            savedMsg = it
            saving = false
        },
        savedCountRes = if (kind == MediaKind.AUDIO) R.string.audio_saved_to_library else R.string.media_saved_to_gallery,
        replacedCountRes = if (kind == MediaKind.AUDIO) R.string.audio_replaced_count else R.string.media_replaced_count,
        kind = kind,
    )

    LaunchedEffect(groupId) {
        CompressForegroundService.start(context)
        viewModel.refreshGroup(groupId)
        EngineEventHub.groups.collect { updated ->
            if (updated.id == groupId) {
                viewModel.adoptGroup(updated)
                if (viewModel.isGroupFinished(updated)) {
                    CompressForegroundService.stop(context)
                }
            }
        }
    }

    SsScreen(
        topBar = { SsAppBar(title = stringResource(R.string.media_jobs_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (group == null) {
                CircularProgressIndicator()
            } else {
                val g = group!!
                if (!finished) {
                    val initialSecs = remember(g.jobs) {
                        val totalDurationMs = g.jobs.sumOf { it.spec.fingerprint.durationMs.toLong() }.takeIf { it > 0 }
                            ?: viewModel.selectedMedia.sumOf { it.durationMs }
                        val totalBytes = g.jobs.sumOf { it.spec.fingerprint.fileBytes.toLong() }.takeIf { it > 0 }
                            ?: viewModel.selectedMedia.sumOf { it.fileBytes }
                        if (totalDurationMs > 0) (totalDurationMs / 3500).toInt().coerceAtLeast(5)
                        else (totalBytes / 12_000_000).toInt().coerceAtLeast(5)
                    }
                    val totalJobs = g.jobs.size.coerceAtLeast(1)
                    val doneJobs = (g.done + g.skipped + g.failed).toInt()
                    val totalProgress = if (g.jobs.isNotEmpty()) {
                        val sumProgress = g.jobs.sumOf { it.progress.toDouble() }
                        (sumProgress / g.jobs.size).toFloat()
                    } else doneJobs.toFloat() / totalJobs

                    CompressClockCountdown(
                        progress = totalProgress,
                        totalCount = totalJobs,
                        doneCount = doneJobs,
                        initialEstimateSecs = initialSecs,
                        startedAtMs = g.createdAt,
                    )

                    JobControlBar(
                        groupId = groupId,
                        onPause = { scope.launch { viewModel.pauseGroup() } },
                        onResume = { scope.launch { viewModel.resumeGroup() } },
                        onCancel = { scope.launch { viewModel.cancelGroup() } },
                    )
                }
                Text(
                    stringResource(
                        R.string.media_jobs_summary,
                        g.done.toInt(),
                        g.skipped.toInt(),
                        g.failed.toInt(),
                        formatBytes(context, g.savedBytes),
                    ),
                )
                if (replaceMode && !finished) {
                    GroupSavingsSummary(
                        savedBytes = g.savedBytes,
                        replaceMode = true,
                        engine = viewModel.engine,
                        totalSavedRes = R.string.media_total_saved,
                    )
                }
                val total = g.jobs.size.coerceAtLeast(1)
                val doneCount = g.done + g.skipped + g.failed
                SsProgressBar(
                    progress = doneCount.toFloat() / total,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(g.jobs, key = { it.spec.id }) { job ->
                        SsTaskJobCard(
                            job = job,
                            onRetry = { scope.launch { viewModel.retryJob(job.spec.id) } },
                            showSizeComparison = finished,
                            footer = {
                                if (kind == MediaKind.VIDEO &&
                                    finished &&
                                    job.state == JobState.SUCCEEDED &&
                                    job.outputUri != null
                                ) {
                                    SsSecondaryButton(
                                        text = stringResource(R.string.video_compare),
                                        onClick = { onCompare(job) },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            },
                        )
                    }
                    if (finished) {
                        item(key = "result-summary") {
                            CompressResultSummary(
                                group = g,
                                replaceMode = replaceMode,
                                engine = viewModel.engine,
                                totalSavedRes = R.string.media_total_saved,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        item(key = "result-actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (savedMsg != null) {
                                    Text(savedMsg!!)
                                }
                                val isAllReplaced = g.jobs.all { it.state == JobState.COMMITTED_REPLACE }
                                val replaceLabel = if (isAllReplaced) {
                                    stringResource(R.string.vault_already_replaced)
                                } else if (kind == MediaKind.AUDIO) {
                                    stringResource(R.string.audio_replace_library)
                                } else {
                                    stringResource(R.string.media_replace_gallery)
                                }
                                val saveLabel = if (kind == MediaKind.AUDIO) {
                                    stringResource(R.string.audio_save_library)
                                } else {
                                    stringResource(R.string.media_save_gallery)
                                }
                                SsPrimaryButton(
                                    text = replaceLabel,
                                    onClick = {
                                        saving = true
                                        replaceToGallery()
                                    },
                                    enabled = !saving && !isAllReplaced,
                                )
                                SsSecondaryButton(
                                    text = saveLabel,
                                    onClick = {
                                        saving = true
                                        saveAsNewToGallery()
                                    },
                                    enabled = !saving,
                                )
                                val exportPaths = GalleryCommit.savableJobs(g).mapNotNull { it.outputUri }
                                ExportToFolderButton(
                                    paths = exportPaths,
                                    onResult = { savedMsg = it },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.clearSelection()
                                            onBack()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.action_back_home), fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = { shareSavedBytes(context, g.savedBytes) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(stringResource(R.string.share_savings), fontWeight = FontWeight.SemiBold)
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
