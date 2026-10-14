package com.spaceslimmer.app.ui.shared

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.audioPresetLabel
import com.spaceslimmer.app.ui.imagePresetLabel
import com.spaceslimmer.app.ui.videoPresetLabel
import uniffi.ss_core.Compatibility
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.VideoCustomParams
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class HistoryEntry(
    val groupId: String,
    val groupRequest: CompressRequest,
    val job: JobView,
)

fun isHistoryTerminal(state: JobState): Boolean = when (state) {
    JobState.SUCCEEDED,
    JobState.SKIPPED,
    JobState.FAILED,
    JobState.COMMITTED_REPLACE,
    -> true
    else -> false
}

fun isHistoryInProgress(state: JobState): Boolean = when (state) {
    JobState.QUEUED,
    JobState.RUNNING,
    JobState.PAUSED,
    JobState.CANCELLING,
    -> true
    else -> false
}

fun isPendingSave(job: JobView): Boolean =
    job.state == JobState.SUCCEEDED && com.spaceslimmer.app.export.GalleryCommit.outputFileReady(job)

fun flattenPendingSaveHistory(groups: List<uniffi.ss_core.GroupView>): List<HistoryEntry> =
    groups.flatMap { group ->
        group.jobs
            .filter { isPendingSave(it) }
            .map { job ->
                HistoryEntry(
                    groupId = group.id,
                    groupRequest = group.request,
                    job = job,
                )
            }
    }.sortedByDescending { it.job.updatedAt }

fun flattenHistory(groups: List<uniffi.ss_core.GroupView>): List<HistoryEntry> =
    groups.flatMap { group ->
        group.jobs
            .filter { isHistoryTerminal(it.state) }
            .map { job ->
                HistoryEntry(
                    groupId = group.id,
                    groupRequest = group.request,
                    job = job,
                )
            }
    }.sortedByDescending { it.job.updatedAt }

fun flattenInProgressHistory(groups: List<uniffi.ss_core.GroupView>): List<HistoryEntry> =
    groups.flatMap { group ->
        group.jobs
            .filter { isHistoryInProgress(it.state) }
            .map { job ->
                HistoryEntry(
                    groupId = group.id,
                    groupRequest = group.request,
                    job = job,
                )
            }
    }.sortedByDescending { it.job.updatedAt }

fun formatHistoryDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    return formatter.format(
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
    )
}

@Composable
fun mediaKindLabel(kind: MediaKind): String = when (kind) {
    MediaKind.VIDEO -> stringResource(R.string.radar_filter_video)
    MediaKind.IMAGE -> stringResource(R.string.radar_filter_image)
    MediaKind.AUDIO -> stringResource(R.string.radar_filter_audio)
}

fun jobSourcePath(job: JobView): String =
    job.spec.localPath?.takeIf { it.isNotBlank() } ?: job.spec.sourceUri

@Composable
fun compressRequestSummary(request: CompressRequest, kind: MediaKind): String {
    val preset = when (kind) {
        MediaKind.VIDEO -> videoPresetLabel(request.preset)
        MediaKind.IMAGE -> imagePresetLabel(request.preset)
        MediaKind.AUDIO -> audioPresetLabel(request.preset)
    }
    val compat = when (request.compatibility) {
        Compatibility.COMPATIBLE -> stringResource(R.string.compat_compatible)
        Compatibility.EFFICIENT -> stringResource(R.string.compat_efficient)
        Compatibility.MORE_EFFICIENT -> stringResource(R.string.compat_more_efficient)
        Compatibility.KEEP_CONTAINER -> stringResource(R.string.compat_compatible)
    }
    val output = when (request.outputPolicy) {
        OutputPolicy.SAVE_AS_NEW -> stringResource(R.string.output_save_as_new)
        OutputPolicy.REPLACE_AFTER_CONFIRM,
        OutputPolicy.REPLACE_TO_VAULT,
        -> stringResource(R.string.output_replace_vault)
    }
    val format = request.imageFormat ?: request.audioFormat
    return buildList {
        add(preset)
        add(compat)
        add(output)
        if (!format.isNullOrBlank()) add(format.uppercase(Locale.US))
        if (kind == MediaKind.VIDEO) {
            addAll(videoCustomSummaryParts(request.custom))
        }
    }.joinToString(" · ")
}

@Composable
private fun videoCustomSummaryParts(custom: VideoCustomParams?): List<String> {
    if (custom == null) return emptyList()
    return buildList {
        custom.maxLongEdge?.takeIf { it > 0u }?.let { edge ->
            add(stringResource(R.string.resolution_label) + " " + edge)
        }
        custom.fpsCap?.takeIf { it > 0f }?.let { fps ->
            add(stringResource(R.string.video_fps_label) + " " + fps)
        }
        custom.videoBitrateBps?.takeIf { it > 0uL }?.let { bps ->
            add(stringResource(R.string.video_bitrate_kbps) + " " + (bps / 1000uL))
        }
        custom.audioBitrateBps?.takeIf { it > 0uL }?.let { bps ->
            add(stringResource(R.string.audio_bitrate_kbps) + " " + (bps / 1000uL))
        }
        custom.codec?.takeIf { it.isNotBlank() }?.let { codec ->
            add(stringResource(R.string.video_codec_label) + " " + codec.uppercase(Locale.US))
        }
        custom.audioCopy?.let { copy ->
            val label = if (copy) {
                stringResource(R.string.video_audio_copy)
            } else {
                stringResource(R.string.video_audio_reencode)
            }
            add(stringResource(R.string.video_audio_track) + " " + label)
        }
        val trimStart = custom.trimStartMs
        val trimEnd = custom.trimEndMs
        if (trimStart != null || trimEnd != null) {
            val startSec = trimStart?.let { it / 1000uL }?.toString() ?: "—"
            val endSec = trimEnd?.let { it / 1000uL }?.toString() ?: "—"
            add(stringResource(R.string.video_clip_label) + " $startSec–$endSec")
        }
        if (custom.strictTarget == true) {
            add(stringResource(R.string.video_strict_target))
        }
    }
}

fun formatHistoryRowMeta(
    context: Context,
    kind: MediaKind,
    kindLabel: String,
    dateLabel: String,
    originalBytes: ULong,
): String {
    val size = formatBytes(context, originalBytes.toLong())
    return "$kindLabel · $dateLabel · $size"
}
