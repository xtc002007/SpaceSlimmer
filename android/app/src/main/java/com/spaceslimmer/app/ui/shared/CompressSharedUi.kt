package com.spaceslimmer.app.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.spaceslimmer.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.ui.theme.savingsColor
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.GroupView
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.timeClassLabel
import com.spaceslimmer.core.MessageKeys
import com.spaceslimmer.core.R as CoreR
import uniffi.ss_core.Compatibility
import uniffi.ss_core.FailReason
import uniffi.ss_core.Decision
import uniffi.ss_core.HwCaps
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.SkipReason

@Composable
fun EstimatePanel(decisions: List<Decision>) {
    if (decisions.isEmpty()) return
    val context = LocalContext.current
    val savable = decisions.filter { !it.skip }
    val low = savable.sumOf { (it.estimateBytesLow).toLong() }
    val high = savable.sumOf { (it.estimateBytesHigh).toLong() }
    val skipCount = decisions.count { it.skip }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                R.string.estimate_summary,
                savable.size,
                formatBytes(context, low),
                formatBytes(context, high),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (skipCount > 0) {
            Text(
                stringResource(R.string.estimate_skip_count, skipCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CompatibilityRow(
    selected: Compatibility,
    onSelected: (Compatibility) -> Unit,
    hwCaps: HwCaps? = null,
) {
    Text(
        stringResource(R.string.compat_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val modes = buildList {
        add(Compatibility.COMPATIBLE)
        add(Compatibility.EFFICIENT)
        if (hwCaps?.av1HwEncode == true) add(Compatibility.MORE_EFFICIENT)
    }
    SsSegmented(
        options = modes.map { mode ->
            SsSegmentOption(
                mode,
                when (mode) {
                    Compatibility.COMPATIBLE -> stringResource(R.string.compat_compatible)
                    Compatibility.EFFICIENT -> stringResource(R.string.compat_efficient)
                    Compatibility.MORE_EFFICIENT -> stringResource(R.string.compat_more_efficient)
                    else -> mode.name
                },
            )
        },
        selected = selected,
        onSelected = onSelected,
    )
}

@Composable
fun TimeClassPanel(decisions: List<Decision>) {
    if (decisions.isEmpty()) return
    val worst = decisions.maxByOrNull { it.timeClass.ordinal }?.timeClass ?: return
    Text(
        stringResource(R.string.time_class_label, timeClassLabel(worst)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun OutputPolicyRow(
    selected: OutputPolicy,
    onSelected: (OutputPolicy) -> Unit,
    canReplace: Boolean = true,
    selectedCount: Int = 1,
) {
    Text(
        stringResource(R.string.output_policy_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val options = buildList {
        add(SsSegmentOption(OutputPolicy.SAVE_AS_NEW, stringResource(R.string.output_save_as_new)))
        if (canReplace || selectedCount == 0) {
            add(SsSegmentOption(OutputPolicy.REPLACE_TO_VAULT, stringResource(R.string.output_replace_vault)))
        }
    }
    SsSegmented(
        options = options,
        selected = selected,
        onSelected = onSelected,
    )
    if (selectedCount > 0 && !canReplace) {
        Text(
            stringResource(R.string.replace_save_as_new_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (selected == OutputPolicy.REPLACE_TO_VAULT) {
        Text(
            stringResource(R.string.output_policy_replace_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Text(
            stringResource(R.string.output_policy_new_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SkipReasonAggregation(jobs: List<JobView>) {
    val context = LocalContext.current
    val skipped = jobs.filter { it.state == JobState.SKIPPED }
    if (skipped.isEmpty()) return
    val grouped = skipped.groupBy { job ->
        when {
            job.message == "skip.raw" -> "skip.raw"
            job.message == "skip.gif" -> "skip.gif"
            job.message.isNotEmpty() -> job.message
            job.skip != null -> skipReasonKey(job.skip!!)
            else -> "skip.unknown"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        grouped.entries.sortedByDescending { it.value.size }.forEach { (key, items) ->
            val reasonText = when (key) {
                "skip.already_efficient" -> stringResource(R.string.skip_agg_already_efficient)
                "skip.too_small" -> stringResource(R.string.skip_agg_too_small)
                "skip.not_worth" -> stringResource(R.string.skip_agg_not_worth)
                "skip.would_grow" -> stringResource(R.string.skip_agg_would_grow)
                "skip.gif" -> stringResource(R.string.skip_agg_gif)
                "skip.raw" -> stringResource(R.string.skip_agg_raw)
                else -> MessageKeys.localize(context, key)
            }
            Text(
                stringResource(R.string.skip_agg_line, items.size, reasonText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun skipReasonKey(reason: SkipReason): String = when (reason) {
    SkipReason.ALREADY_EFFICIENT -> "skip.already_efficient"
    SkipReason.TOO_SMALL -> "skip.too_small"
    SkipReason.ESTIMATE_NOT_WORTH -> "skip.not_worth"
    SkipReason.WOULD_GROW -> "skip.would_grow"
    SkipReason.UNSUPPORTED_HDR -> "skip.hdr"
    SkipReason.UNSUPPORTED_FPS -> "skip.slowmo"
    SkipReason.UNSUPPORTED_CODEC -> "skip.codec"
    SkipReason.USER_DISABLED -> "skip.user_disabled"
    SkipReason.TARGET_SIZE_UNREACHABLE -> "skip.target_unreachable"
}

@Composable
fun failReasonLabel(reason: FailReason): String = when (reason) {
    FailReason.PROBE_FAILED -> stringResource(CoreR.string.fail_probe)
    FailReason.DECODE_FAILED -> stringResource(CoreR.string.fail_decode)
    FailReason.ENCODE_FAILED -> stringResource(CoreR.string.fail_encode)
    FailReason.MUX_FAILED -> stringResource(CoreR.string.fail_mux)
    FailReason.DISK_FULL -> stringResource(CoreR.string.fail_disk_full)
    FailReason.PERMISSION -> stringResource(CoreR.string.fail_permission)
    FailReason.CANCELLED -> stringResource(CoreR.string.fail_cancelled)
    FailReason.INVALID_OUTPUT -> stringResource(CoreR.string.fail_invalid_output)
    FailReason.PLATFORM_CRASH -> stringResource(CoreR.string.fail_platform)
    FailReason.UNKNOWN -> stringResource(CoreR.string.fail_unknown)
}

@Composable
fun resolveJobStatusMessage(job: JobView): String? {
    val context = LocalContext.current
    return when {
        job.message.isNotEmpty() -> MessageKeys.localize(context, job.message)
        job.skip != null -> resolveSkipReasonText(context, job)
        job.fail != null -> stringResource(R.string.job_failed, failReasonLabel(job.fail!!))
        job.state == JobState.SKIPPED -> stringResource(
            R.string.job_skipped,
            stringResource(R.string.skip_agg_not_worth),
        )
        else -> null
    }
}

private fun resolveSkipReasonText(context: android.content.Context, job: JobView): String {
    val key = when {
        job.message == "skip.raw" -> "skip.raw"
        job.message == "skip.gif" -> "skip.gif"
        job.message.isNotEmpty() -> job.message
        job.skip != null -> skipReasonKey(job.skip!!)
        else -> "skip.unknown"
    }
    val reasonText = when (key) {
        "skip.already_efficient" -> context.getString(R.string.skip_agg_already_efficient)
        "skip.too_small" -> context.getString(R.string.skip_agg_too_small)
        "skip.not_worth" -> context.getString(R.string.skip_agg_not_worth)
        "skip.would_grow" -> context.getString(R.string.skip_agg_would_grow)
        "skip.gif" -> context.getString(R.string.skip_agg_gif)
        "skip.raw" -> context.getString(R.string.skip_agg_raw)
        else -> MessageKeys.localize(context, key)
    }
    return context.getString(R.string.job_skipped, reasonText)
}

@Composable
fun JobMessageText(job: JobView) {
    val text = resolveJobStatusMessage(job)
    if (text != null) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun JobRetryButton(
    job: JobView,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    expand: Boolean = true,
) {
    if (job.state == uniffi.ss_core.JobState.FAILED) {
        SsSecondaryButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
            modifier = modifier,
            expand = expand,
        )
    }
}

@Composable
fun JobControlBar(
    groupId: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SsSecondaryButton(
            text = stringResource(R.string.action_pause),
            onClick = onPause,
            expand = false,
            modifier = Modifier.weight(1f),
        )
        SsPrimaryButton(
            text = stringResource(R.string.action_resume),
            onClick = onResume,
            expand = false,
            modifier = Modifier.weight(1f),
        )
        SsSecondaryButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            expand = false,
            modifier = Modifier.weight(1f),
        )
    }
}

enum class CompressStatusBucket { RUNNING, FAILED, SUCCEEDED }

fun jobStatusBucket(state: JobState): CompressStatusBucket = when (state) {
    JobState.FAILED -> CompressStatusBucket.FAILED
    JobState.SUCCEEDED, JobState.COMMITTED_REPLACE, JobState.SKIPPED -> CompressStatusBucket.SUCCEEDED
    else -> CompressStatusBucket.RUNNING
}

fun groupStatusBucket(jobs: List<JobView>): CompressStatusBucket {
    if (jobs.isEmpty()) {
        return CompressStatusBucket.RUNNING
    }
    if (jobs.any { jobStatusBucket(it.state) == CompressStatusBucket.RUNNING }) {
        return CompressStatusBucket.RUNNING
    }
    if (jobs.any { it.state == JobState.FAILED }) {
        return CompressStatusBucket.FAILED
    }
    return CompressStatusBucket.SUCCEEDED
}

@Composable
fun compressStatusBucketLabel(bucket: CompressStatusBucket): String = when (bucket) {
    CompressStatusBucket.RUNNING -> stringResource(R.string.job_state_running)
    CompressStatusBucket.FAILED -> stringResource(R.string.job_state_failed)
    CompressStatusBucket.SUCCEEDED -> stringResource(R.string.job_state_succeeded)
}

@Composable
fun compressStatusBucketColor(bucket: CompressStatusBucket): Color = when (bucket) {
    CompressStatusBucket.RUNNING -> MaterialTheme.colorScheme.primary
    CompressStatusBucket.FAILED -> MaterialTheme.colorScheme.error
    CompressStatusBucket.SUCCEEDED -> savingsColor()
}

@Composable
fun CompressResultSummary(
    group: GroupView,
    replaceMode: Boolean,
    engine: CoreEngine?,
    totalSavedRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GroupSavingsSummary(
            savedBytes = group.savedBytes,
            replaceMode = replaceMode,
            engine = engine,
            totalSavedRes = totalSavedRes,
        )
        SkipReasonAggregation(group.jobs)
        GbShareCard(savedBytes = group.savedBytes)
    }
}
