package com.spaceslimmer.app.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
import com.spaceslimmer.app.ui.shared.CompareLabelBar
import com.spaceslimmer.app.ui.shared.CompressResultSummary
import com.spaceslimmer.app.ui.shared.ExportToFolderButton
import com.spaceslimmer.app.ui.shared.GroupSavingsSummary
import com.spaceslimmer.app.ui.shared.JobControlBar
import com.spaceslimmer.app.ui.shared.compressStatusBucketColor
import com.spaceslimmer.app.ui.shared.compressStatusBucketLabel
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.shared.groupStatusBucket
import com.spaceslimmer.app.ui.shared.isReplaceOutputPolicy
import com.spaceslimmer.app.ui.shared.rememberGallerySaveHandler
import com.spaceslimmer.app.ui.shared.shareSavedBytes
import com.spaceslimmer.app.viewmodel.PhotoCompressViewModel
import com.spaceslimmer.core.EngineEventHub
import com.spaceslimmer.app.ui.shared.CompressStatusBucket
import kotlinx.coroutines.launch
import uniffi.ss_core.MediaKind
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.OutputPolicy

@Composable
fun PhotoJobsScreen(
    groupId: String,
    onBack: () -> Unit,
    onCompare: (JobView) -> Unit,
    viewModel: PhotoCompressViewModel = viewModel(),
) {
    val group by viewModel.latestGroupFlow.collectAsState()
    val finished = group?.let { viewModel.isGroupFinished(it) } == true
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val replaceMode = isReplaceOutputPolicy(viewModel.lastRequest?.outputPolicy)
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val replaceToGallery = rememberGallerySaveHandler(
        replaceMode = true,
        save = { viewModel.saveSucceededToGallery(OutputPolicy.REPLACE_TO_VAULT) },
        onMessage = {
            saveMessage = it
            saving = false
        },
        savedCountRes = R.string.photo_saved_to_gallery,
        replacedCountRes = R.string.photo_replaced_count,
        kind = MediaKind.IMAGE,
    )
    val saveAsNewToGallery = rememberGallerySaveHandler(
        replaceMode = false,
        save = { viewModel.saveSucceededToGallery(OutputPolicy.SAVE_AS_NEW) },
        onMessage = {
            saveMessage = it
            saving = false
        },
        savedCountRes = R.string.photo_saved_to_gallery,
        replacedCountRes = R.string.photo_replaced_count,
        kind = MediaKind.IMAGE,
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
        topBar = { SsAppBar(title = stringResource(R.string.photo_jobs_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = group
            if (current == null) {
                CircularProgressIndicator()
            } else {
                if (!finished) {
                    val initialSecs = remember(current.jobs) {
                        (viewModel.selectedImages.size * 0.35).toInt().coerceAtLeast(3)
                    }
                    val totalJobs = current.jobs.size.coerceAtLeast(1)
                    val doneJobs = (current.done + current.skipped + current.failed).toInt()
                    val totalProgress = if (current.jobs.isNotEmpty()) {
                        val sumProgress = current.jobs.sumOf { it.progress.toDouble() }
                        (sumProgress / current.jobs.size).toFloat()
                    } else doneJobs.toFloat() / totalJobs

                    CompressClockCountdown(
                        progress = totalProgress,
                        totalCount = totalJobs,
                        doneCount = doneJobs,
                        initialEstimateSecs = initialSecs,
                        startedAtMs = current.createdAt,
                    )

                    JobControlBar(
                        groupId = groupId,
                        onPause = { scope.launch { viewModel.pauseGroup() } },
                        onResume = { scope.launch { viewModel.resumeGroup() } },
                        onCancel = { scope.launch { viewModel.cancelGroup() } },
                    )
                }
                val total = current.jobs.size.coerceAtLeast(1)
                val done = current.done + current.skipped + current.failed
                SsProgressBar(
                    progress = done.toFloat() / total.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.photo_jobs_summary,
                        current.done.toInt(),
                        current.skipped.toInt(),
                        current.failed.toInt(),
                        formatBytes(context, current.savedBytes),
                    ),
                )
                if (replaceMode && !finished) {
                    GroupSavingsSummary(
                        savedBytes = current.savedBytes,
                        replaceMode = true,
                        engine = viewModel.engine,
                        totalSavedRes = R.string.photo_total_saved,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.jobs, key = { it.spec.id }) { job ->
                        SsTaskJobCard(
                            job = job,
                            onRetry = { scope.launch { viewModel.retryJob(job.spec.id) } },
                            showSizeComparison = finished,
                            footer = {
                                if (finished &&
                                    job.state == JobState.SUCCEEDED &&
                                    job.outputUri != null
                                ) {
                                    SsSecondaryButton(
                                        text = stringResource(R.string.photo_compare),
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
                                group = current,
                                replaceMode = replaceMode,
                                engine = viewModel.engine,
                                totalSavedRes = R.string.photo_total_saved,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        item(key = "result-actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (saveMessage != null) {
                                    Text(saveMessage!!)
                                }
                                val isAllReplaced = current.jobs.all { it.state == JobState.COMMITTED_REPLACE }
                                SsPrimaryButton(
                                    text = if (isAllReplaced) stringResource(R.string.vault_already_replaced)
                                    else stringResource(R.string.photo_replace_gallery),
                                    onClick = {
                                        saving = true
                                        replaceToGallery()
                                    },
                                    enabled = !saving && !isAllReplaced,
                                )
                                SsSecondaryButton(
                                    text = stringResource(R.string.photo_save_gallery),
                                    onClick = {
                                        saving = true
                                        saveAsNewToGallery()
                                    },
                                    enabled = !saving,
                                )
                                val exportPaths = GalleryCommit.savableJobs(current)
                                    .mapNotNull { it.outputUri }
                                ExportToFolderButton(
                                    paths = exportPaths,
                                    onResult = { saveMessage = it },
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
                                        onClick = { shareSavedBytes(context, current.savedBytes) },
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

@Composable
fun PhotoCompareScreen(
    job: JobView,
    onBack: () -> Unit,
) {
    SsScreen(
        topBar = { SsAppBar(title = stringResource(R.string.photo_compare_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(job.spec.displayName, style = MaterialTheme.typography.titleMedium)
            CompareSlider(
                beforeModel = job.spec.sourceUri,
                afterModel = job.outputUri ?: job.spec.sourceUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
fun CompareSlider(
    beforeModel: Any,
    afterModel: Any,
    modifier: Modifier = Modifier,
) {
    var split by remember { mutableFloatStateOf(0.5f) }
    val beforeCd = stringResource(R.string.compare_image_before)
    val afterCd = stringResource(R.string.compare_image_after)
    val handleCd = stringResource(R.string.compare_slider_handle)

    BoxWithConstraints(modifier = modifier) {
        val splitWidth = maxWidth * split
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = beforeModel,
                contentDescription = beforeCd,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .width(splitWidth)
                    .fillMaxHeight(),
            ) {
                AsyncImage(
                    model = afterModel,
                    contentDescription = afterCd,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = splitWidth - 2.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
                    .semantics { contentDescription = handleCd }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            split = (split + drag.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                        }
                    },
            )
        }
        CompareLabelBar(
            startLabel = stringResource(R.string.photo_after),
            endLabel = stringResource(R.string.photo_before),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }
}
