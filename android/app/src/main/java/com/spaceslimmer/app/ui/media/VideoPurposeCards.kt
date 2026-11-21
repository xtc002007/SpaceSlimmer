package com.spaceslimmer.app.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.videoPresetLabel
import uniffi.ss_core.QualityPreset

val videoPurposePresets = listOf(
    QualityPreset.VIDEO_EXTREME,
    QualityPreset.VIDEO_HIGH,
    QualityPreset.VIDEO_BALANCED,
    QualityPreset.VIDEO_SMALL,
    QualityPreset.VIDEO_TINY,
)

@Composable
fun VideoPurposePresetGrid(
    selected: QualityPreset,
    onSelect: (QualityPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.compress_ratio_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SsSegmented(
            options = videoPurposePresets.map { preset ->
                SsSegmentOption(preset, videoPresetLabel(preset))
            },
            selected = selected,
            onSelected = onSelect,
            scrollable = true,
        )
        val detail = videoPurposeDescription(selected)
        if (detail.isNotEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun videoPurposeDescription(preset: QualityPreset): String = when (preset) {
    QualityPreset.VIDEO_EXTREME -> stringResource(R.string.video_purpose_extreme)
    QualityPreset.VIDEO_HIGH -> stringResource(R.string.video_purpose_high)
    QualityPreset.VIDEO_BALANCED -> stringResource(R.string.video_purpose_balanced)
    QualityPreset.VIDEO_SMALL -> stringResource(R.string.video_purpose_small)
    QualityPreset.VIDEO_TINY -> stringResource(R.string.video_purpose_tiny)
    else -> ""
}
