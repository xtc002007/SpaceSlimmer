package com.spaceslimmer.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.theme.InputShape
import uniffi.ss_core.VideoCustomParams

data class VideoCustomUiState(
    val maxLongEdge: UInt? = null,
    val fpsCap: Float? = null,
    val videoBitrateKbps: String = "",
    val audioBitrateKbps: String = "",
    val codec: String? = null,
    val audioCopy: Boolean? = null,
    val trimStartSec: String = "",
    val trimEndSec: String = "",
    val strictTarget: Boolean = false,
)

fun VideoCustomUiState.toParams(): VideoCustomParams? {
    val videoBps = videoBitrateKbps.toULongOrNull()?.times(1000u)
    val audioBps = audioBitrateKbps.toULongOrNull()?.times(1000u)
    val trimStart = trimStartSec.toULongOrNull()?.times(1000u)
    val trimEnd = trimEndSec.toULongOrNull()?.times(1000u)
    if (maxLongEdge == null &&
        fpsCap == null &&
        videoBps == null &&
        audioBps == null &&
        codec == null &&
        audioCopy == null &&
        trimStart == null &&
        trimEnd == null &&
        !strictTarget
    ) {
        return null
    }
    return VideoCustomParams(
        maxLongEdge = maxLongEdge,
        fpsCap = fpsCap,
        videoBitrateBps = videoBps,
        audioBitrateBps = audioBps,
        codec = codec,
        audioCopy = audioCopy,
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        strictTarget = if (strictTarget) true else null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoCustomPanel(
    state: VideoCustomUiState,
    onChange: (VideoCustomUiState) -> Unit,
    showResolution: Boolean = true,
    showAv1: Boolean = false,
    showImportant: Boolean = true,
    showAdvanced: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showImportant && showResolution) {
            Text(stringResource(R.string.resolution_label), style = MaterialTheme.typography.labelLarge)
            SsSegmented(
                options = listOf(
                    SsSegmentOption(null, stringResource(R.string.resolution_keep)),
                    SsSegmentOption(3840u, "4K"),
                    SsSegmentOption(1920u, "1080"),
                    SsSegmentOption(1280u, "720"),
                    SsSegmentOption(960u, "540"),
                ),
                selected = state.maxLongEdge,
                onSelected = { onChange(state.copy(maxLongEdge = it)) },
            )
        }
        if (showImportant) {
            Text(stringResource(R.string.video_fps_label), style = MaterialTheme.typography.labelLarge)
            SsSegmented(
                options = listOf(
                    SsSegmentOption(null, stringResource(R.string.video_fps_keep)),
                    SsSegmentOption(24f, "24"),
                    SsSegmentOption(30f, "30"),
                    SsSegmentOption(60f, "60"),
                ),
                selected = state.fpsCap,
                onSelected = { onChange(state.copy(fpsCap = it)) },
            )
        }
        if (showAdvanced) {
        Text(stringResource(R.string.video_codec_label), style = MaterialTheme.typography.labelLarge)
        val codecs: List<SsSegmentOption<String?>> = buildList {
            add(SsSegmentOption(null, stringResource(R.string.video_codec_auto)))
            add(SsSegmentOption("h264", "H.264"))
            add(SsSegmentOption("hevc", "HEVC"))
            if (showAv1) add(SsSegmentOption("av1", "AV1"))
        }
        SsSegmented(
            options = codecs,
            selected = state.codec,
            onSelected = { onChange(state.copy(codec = it)) },
        )
        OutlinedTextField(
            value = state.videoBitrateKbps,
            onValueChange = { onChange(state.copy(videoBitrateKbps = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.video_bitrate_kbps)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = InputShape,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.audioBitrateKbps,
            onValueChange = { onChange(state.copy(audioBitrateKbps = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.audio_bitrate_kbps)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = InputShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.video_audio_track), style = MaterialTheme.typography.labelLarge)
        SsSegmented(
            options = listOf(
                SsSegmentOption(null, stringResource(R.string.video_audio_auto)),
                SsSegmentOption(true, stringResource(R.string.video_audio_copy)),
                SsSegmentOption(false, stringResource(R.string.video_audio_reencode)),
            ),
            selected = state.audioCopy,
            onSelected = { onChange(state.copy(audioCopy = it)) },
        )
        Text(stringResource(R.string.video_clip_label), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = state.trimStartSec,
            onValueChange = { onChange(state.copy(trimStartSec = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.video_clip_start)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = InputShape,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.trimEndSec,
            onValueChange = { onChange(state.copy(trimEndSec = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.video_clip_end)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = InputShape,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.video_strict_target), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.strictTarget,
                onCheckedChange = { onChange(state.copy(strictTarget = it)) },
            )
        }
        }
    }
}
