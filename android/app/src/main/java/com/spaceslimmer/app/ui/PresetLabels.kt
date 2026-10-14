package com.spaceslimmer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spaceslimmer.app.R
import uniffi.ss_core.QualityPreset
import uniffi.ss_core.TimeClass

@Composable
fun imagePresetLabel(preset: QualityPreset): String = when (preset) {
    QualityPreset.IMAGE_MAX -> stringResource(R.string.preset_image_max)
    QualityPreset.IMAGE_HIGH -> stringResource(R.string.preset_image_high)
    QualityPreset.IMAGE_SMALL -> stringResource(R.string.preset_image_small)
    else -> preset.name
}

@Composable
fun videoPresetLabel(preset: QualityPreset): String = when (preset) {
    QualityPreset.VIDEO_EXTREME -> stringResource(R.string.preset_video_extreme)
    QualityPreset.VIDEO_HIGH -> stringResource(R.string.preset_video_high)
    QualityPreset.VIDEO_BALANCED -> stringResource(R.string.preset_video_balanced)
    QualityPreset.VIDEO_SMALL -> stringResource(R.string.preset_video_small)
    QualityPreset.VIDEO_TINY -> stringResource(R.string.preset_video_tiny)
    QualityPreset.VIDEO_TARGET_SIZE -> stringResource(R.string.preset_video_target_size)
    QualityPreset.VIDEO_CUSTOM -> stringResource(R.string.preset_video_custom)
    else -> preset.name
}

@Composable
fun photoFormatLabel(format: String): String = when (format) {
    "jpeg" -> stringResource(R.string.photo_format_jpeg)
    "webp" -> stringResource(R.string.photo_format_webp)
    "png" -> stringResource(R.string.photo_format_png)
    else -> format
}

@Composable
fun audioPresetLabel(preset: QualityPreset): String = when (preset) {
    QualityPreset.AUDIO_SPEECH -> stringResource(R.string.preset_audio_speech)
    QualityPreset.AUDIO_MUSIC_HIGH -> stringResource(R.string.preset_audio_high)
    QualityPreset.AUDIO_MUSIC_SMALL -> stringResource(R.string.preset_audio_small)
    else -> preset.name
}

@Composable
fun timeClassLabel(timeClass: TimeClass): String = when (timeClass) {
    TimeClass.FAST -> stringResource(R.string.time_class_fast)
    TimeClass.MEDIUM -> stringResource(R.string.time_class_medium)
    TimeClass.PLUG_IN -> stringResource(R.string.time_class_plug_in)
}
