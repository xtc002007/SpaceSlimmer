package com.spaceslimmer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.shared.CompressStatusBucket
import com.spaceslimmer.app.ui.shared.JobCompressionSummary
import com.spaceslimmer.app.ui.shared.JobMessageText
import com.spaceslimmer.app.ui.shared.JobRetryButton
import com.spaceslimmer.app.ui.shared.compressStatusBucketColor
import com.spaceslimmer.app.ui.shared.compressStatusBucketLabel
import com.spaceslimmer.app.ui.shared.jobStateLabel
import com.spaceslimmer.app.ui.shared.jobStatusBucket
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView

@Composable
fun SsTaskJobCard(
    job: JobView,
    onRetry: () -> Unit,
    onCancel: (() -> Unit)? = null,
    progress: Float? = null,
    progressLine: String? = null,
    modifier: Modifier = Modifier,
    showSizeComparison: Boolean = false,
    footer: @Composable () -> Unit = {},
) {
    val succeeded = job.state == JobState.SUCCEEDED || job.state == JobState.COMMITTED_REPLACE
    val bucket = jobStatusBucket(job.state)
    val bucketLabel = compressStatusBucketLabel(bucket)
    val detailLabel = jobStateLabel(job.state)
    SsCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SsJobRow(
                title = job.spec.displayName,
                subtitle = if (detailLabel != bucketLabel) {
                    stringResource(R.string.tasks_job_state, detailLabel)
                } else {
                    null
                },
                meta = null,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (bucket) {
                            CompressStatusBucket.RUNNING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = compressStatusBucketColor(bucket),
                                )
                            }
                            CompressStatusBucket.SUCCEEDED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = compressStatusBucketColor(bucket),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            CompressStatusBucket.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = compressStatusBucketColor(bucket),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = compressStatusBucketLabel(bucket),
                            style = MaterialTheme.typography.labelLarge,
                            color = compressStatusBucketColor(bucket),
                        )
                    }
                },
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (showSizeComparison && succeeded) {
                    JobCompressionSummary(job = job, prominent = true)
                }
                val barProgress = progress ?: job.progress.takeIf { it > 0f }
                if (barProgress != null &&
                    (job.state == JobState.RUNNING || job.state == JobState.PAUSED)
                ) {
                    SsProgressBar(
                        progress = barProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val line = progressLine ?: if (job.state == JobState.RUNNING) {
                        stringResource(R.string.job_running, (barProgress * 100f).toInt())
                    } else {
                        null
                    }
                    if (line != null) {
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
                JobMessageText(job)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onCancel != null &&
                        (job.state == JobState.RUNNING ||
                            job.state == JobState.QUEUED ||
                            job.state == JobState.PAUSED)
                    ) {
                        SsSecondaryButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onCancel,
                            expand = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    JobRetryButton(job, onRetry, expand = false, modifier = Modifier.weight(1f))
                }
                footer()
            }
        }
    }
}
