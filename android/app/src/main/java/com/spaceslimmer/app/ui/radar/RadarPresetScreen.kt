package com.spaceslimmer.app.ui.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.service.CompressForegroundService
import com.spaceslimmer.app.ui.audioPresetLabel
import com.spaceslimmer.app.ui.imagePresetLabel
import com.spaceslimmer.app.ui.media.VideoPurposePresetGrid
import com.spaceslimmer.app.ui.media.toParams
import com.spaceslimmer.app.ui.photoFormatLabel
import com.spaceslimmer.app.ui.shared.CompatibilityRow
import com.spaceslimmer.app.ui.shared.EstimatePanel
import com.spaceslimmer.app.ui.shared.OutputPolicyRow
import com.spaceslimmer.app.ui.shared.TimeClassPanel
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.videoPresetLabel
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsScreen
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.theme.InputShape
import com.spaceslimmer.core.CoreFeatures
import com.spaceslimmer.videohw.HwCapsProbe
import kotlinx.coroutines.launch
import uniffi.ss_core.Compatibility
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.Decision
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.QualityPreset

@Composable
fun RadarPresetScreen(
    items: List<RadarItem>,
    engine: CoreEngine?,
    onBack: () -> Unit,
    onSubmitted: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hwCaps = remember { HwCapsProbe.probe() }
    val hasImages = items.any { it.probed.kind == MediaKind.IMAGE }
    val hasVideos = items.any { it.probed.kind == MediaKind.VIDEO }
    val hasAudio = items.any { it.probed.kind == MediaKind.AUDIO }

    var imagePreset by remember { mutableStateOf(QualityPreset.IMAGE_HIGH) }
    var videoPreset by remember { mutableStateOf(QualityPreset.VIDEO_BALANCED) }
    var audioPreset by remember { mutableStateOf(QualityPreset.AUDIO_MUSIC_HIGH) }
    var compatibility by remember { mutableStateOf(Compatibility.COMPATIBLE) }
    var outputPolicy by remember { mutableStateOf(OutputPolicy.REPLACE_TO_VAULT) }
    var imageFormat by remember { mutableStateOf("jpeg") }
    var audioFormat by remember { mutableStateOf<String?>(null) }
    var targetSizeMb by remember { mutableStateOf("50") }
    var customUi by remember { mutableStateOf(com.spaceslimmer.app.ui.media.VideoCustomUiState()) }
    var decisions by remember { mutableStateOf<List<Decision>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val opusAvailable = CoreFeatures.opusEncodeAvailable(engine)
    LaunchedEffect(opusAvailable) {
        if (!opusAvailable && audioFormat == "opus") {
            audioFormat = null
        }
    }

    fun videoTargetBytes(): ULong? {
        if (!hasVideos || videoPreset != QualityPreset.VIDEO_TARGET_SIZE) return null
        val mb = targetSizeMb.toULongOrNull() ?: return null
        return mb * 1024uL * 1024uL
    }

    fun imageRequest(core: CoreEngine): CompressRequest = MediaRadar.buildRequest(
        engine = core,
        kind = MediaKind.IMAGE,
        preset = imagePreset,
        compatibility = compatibility,
        outputPolicy = outputPolicy,
        imageFormat = imageFormat,
    )

    fun videoRequest(core: CoreEngine): CompressRequest = MediaRadar.buildRequest(
        engine = core,
        kind = MediaKind.VIDEO,
        preset = videoPreset,
        compatibility = compatibility,
        outputPolicy = outputPolicy,
        imageFormat = null,
        targetBytes = videoTargetBytes(),
        custom = customUi.toParams(),
    )

    fun audioRequest(core: CoreEngine): CompressRequest = MediaRadar.buildRequest(
        engine = core,
        kind = MediaKind.AUDIO,
        preset = audioPreset,
        compatibility = compatibility,
        outputPolicy = outputPolicy,
        imageFormat = null,
        audioFormat = audioFormat,
    )

    LaunchedEffect(engine) {
        engine?.settings()?.let { settings ->
            compatibility = settings.defaultCompat
            imagePreset = settings.defaultPresetImage
            videoPreset = settings.defaultPresetVideo
            audioPreset = settings.defaultPresetAudio
            imageFormat = settings.defaultImageFormat
            outputPolicy = OutputPolicy.SAVE_AS_NEW
        }
    }

    LaunchedEffect(
        engine, items, imagePreset, videoPreset, audioPreset,
        compatibility, imageFormat, audioFormat, targetSizeMb, customUi,
    ) {
        val core = engine ?: return@LaunchedEffect
        val computed = mutableListOf<Decision>()
        items.forEach { item ->
            val request = when (item.probed.kind) {
                MediaKind.IMAGE -> imageRequest(core)
                MediaKind.VIDEO -> videoRequest(core)
                MediaKind.AUDIO -> audioRequest(core)
            }
            computed += core.decide(
                MediaRadar.toFingerprint(item.probed),
                request,
                hwCaps,
            )
        }
        decisions = computed
    }

    val totalLow = items.fold(0L) { acc, item ->
        acc + saturatedSavingLong(item.probed.fileBytes, item.estimateHigh)
    }
    val totalHigh = items.fold(0L) { acc, item ->
        acc + saturatedSavingLong(item.probed.fileBytes, item.estimateLow)
    }

    fun submitJobs(core: CoreEngine) {
        loading = true
        error = null
        onRequestNotificationPermission()
        scope.launch {
            runCatching {
                CompressForegroundService.start(context)
                val options = RadarBatchOptions(
                    imageRequest = if (hasImages) imageRequest(core) else null,
                    videoRequest = if (hasVideos) videoRequest(core) else null,
                    audioRequest = if (hasAudio) audioRequest(core) else null,
                )
                val groupId = MediaRadar.queueCompress(context, core, items, options)
                onSubmitted(groupId)
            }.onFailure { error = it.message }
            loading = false
        }
    }

    SsScreen(
        topBar = { SsAppBar(title = stringResource(R.string.radar_preset_title), onBack = onBack) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EstimatePanel(decisions)
                TimeClassPanel(decisions)
                SsPrimaryButton(
                    text = stringResource(R.string.radar_confirm_start),
                    onClick = {
                        val core = engine ?: return@SsPrimaryButton
                        submitJobs(core)
                    },
                    enabled = engine != null && !loading,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SsCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.radar_preset_summary,
                            items.size,
                            formatBytes(context, totalLow),
                            formatBytes(context, totalHigh),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (hasImages) {
                        Text(
                            stringResource(R.string.radar_preset_image),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SsSegmented(
                            options = listOf(
                                QualityPreset.IMAGE_MAX,
                                QualityPreset.IMAGE_HIGH,
                                QualityPreset.IMAGE_SMALL,
                            ).map { preset ->
                                SsSegmentOption(preset, imagePresetLabel(preset))
                            },
                            selected = imagePreset,
                            onSelected = { imagePreset = it },
                        )
                        SsSegmented(
                            options = listOf("jpeg", "webp", "png").map { format ->
                                SsSegmentOption(format, photoFormatLabel(format))
                            },
                            selected = imageFormat,
                            onSelected = { imageFormat = it },
                        )
                    }
                    if (hasVideos) {
                        Text(
                            stringResource(R.string.radar_preset_video),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VideoPurposePresetGrid(
                            selected = videoPreset,
                            onSelect = { videoPreset = it },
                        )
                        SsSegmented(
                            options = listOf(
                                QualityPreset.VIDEO_TARGET_SIZE,
                                QualityPreset.VIDEO_CUSTOM,
                            ).map { preset ->
                                SsSegmentOption(preset, videoPresetLabel(preset))
                            },
                            selected = videoPreset,
                            onSelected = { videoPreset = it },
                        )
                        if (videoPreset == QualityPreset.VIDEO_TARGET_SIZE) {
                            OutlinedTextField(
                                value = targetSizeMb,
                                onValueChange = { targetSizeMb = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(R.string.target_size_mb)) },
                                supportingText = { Text(stringResource(R.string.target_size_per_video_hint)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = InputShape,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        com.spaceslimmer.app.ui.media.VideoCustomPanel(
                            state = customUi,
                            onChange = { customUi = it },
                            showResolution = true,
                            showAv1 = hwCaps.av1HwEncode,
                        )
                    }
                    if (hasAudio) {
                        Text(
                            stringResource(R.string.radar_preset_audio),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SsSegmented(
                            options = listOf(
                                QualityPreset.AUDIO_SPEECH,
                                QualityPreset.AUDIO_MUSIC_HIGH,
                                QualityPreset.AUDIO_MUSIC_SMALL,
                            ).map { preset ->
                                SsSegmentOption(preset, audioPresetLabel(preset))
                            },
                            selected = audioPreset,
                            onSelected = { audioPreset = it },
                        )
                        if (audioPreset == QualityPreset.AUDIO_SPEECH && opusAvailable) {
                            SsSegmented(
                                options = listOf(
                                    SsSegmentOption("aac", stringResource(R.string.audio_format_aac)),
                                    SsSegmentOption("opus", stringResource(R.string.audio_format_opus)),
                                ),
                                selected = if (audioFormat == "opus") "opus" else "aac",
                                onSelected = { audioFormat = it },
                            )
                        }
                        if (audioPreset == QualityPreset.AUDIO_SPEECH && !opusAvailable) {
                            Text(
                                stringResource(R.string.audio_format_opus_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    CompatibilityRow(compatibility, { compatibility = it }, hwCaps)
                    OutputPolicyRow(
                        selected = outputPolicy,
                        onSelected = { outputPolicy = it },
                        canReplace = items.all {
                            com.spaceslimmer.core.GalleryWriter.canAttemptReplace(context, it.probed.uri)
                        },
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

}
