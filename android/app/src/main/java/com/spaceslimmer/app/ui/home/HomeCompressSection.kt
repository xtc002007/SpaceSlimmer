package com.spaceslimmer.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.service.CompressForegroundService
import com.spaceslimmer.app.ui.audioPresetLabel
import com.spaceslimmer.app.ui.components.SsAlertDialog
import com.spaceslimmer.app.ui.components.SsDialogTextButton
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSection
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.imagePresetLabel
import com.spaceslimmer.app.ui.media.VideoCustomPanel
import com.spaceslimmer.app.ui.media.VideoCustomUiState
import com.spaceslimmer.app.ui.media.VideoPurposePresetGrid
import com.spaceslimmer.app.ui.media.toParams
import com.spaceslimmer.app.ui.media.videoPurposeDescription
import com.spaceslimmer.app.ui.photoFormatLabel
import com.spaceslimmer.app.ui.shared.CompatibilityRow
import com.spaceslimmer.app.ui.shared.OutputPolicyRow
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.theme.InputShape
import com.spaceslimmer.app.ui.videoPresetLabel
import com.spaceslimmer.app.viewmodel.MediaCompressViewModel
import com.spaceslimmer.app.viewmodel.PhotoCompressViewModel
import com.spaceslimmer.core.CoreFeatures
import com.spaceslimmer.videohw.HwCapsProbe
import kotlinx.coroutines.launch
import uniffi.ss_core.Compatibility
import uniffi.ss_core.Decision
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.QualityPreset
import com.spaceslimmer.app.billing.BillingManager
import com.spaceslimmer.app.billing.SpaceSlimmerApiClient
import com.spaceslimmer.app.ui.auth.PaywallGateDialog
import com.spaceslimmer.app.ui.auth.PaywallReason
import uniffi.ss_core.TimeClass
import uniffi.ss_core.VideoCustomParams

@Composable
fun HomeCompressSection(
    engineAvailable: Boolean,
    photoViewModel: PhotoCompressViewModel,
    mediaViewModel: MediaCompressViewModel,
    importedKind: MediaKind?,
    importNonce: Int,
    onRequestNotificationPermission: () -> Unit,
    onSubmitted: (MediaKind, String) -> Unit,
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit = {},
) {
    var selectedKind by remember { mutableStateOf(MediaKind.VIDEO) }
    var selectionTick by remember { mutableIntStateOf(0) }
    var selectedPreset by remember { mutableStateOf(QualityPreset.VIDEO_BALANCED) }
    var selectedFormat by remember { mutableStateOf("jpeg") }
    var selectedAudioFormat by remember { mutableStateOf("aac") }
    var compatibility by remember { mutableStateOf(Compatibility.COMPATIBLE) }
    var outputPolicy by remember { mutableStateOf(OutputPolicy.SAVE_AS_NEW) }
    var targetSizeMb by remember { mutableStateOf("50") }
    var customUi by remember { mutableStateOf(VideoCustomUiState()) }
    var decisions by remember { mutableStateOf<List<Decision>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showReplaceConfirm by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showPaywallGate by remember { mutableStateOf(false) }
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hwCaps = remember { HwCapsProbe.probe() }
    val vaultDays = photoViewModel.engine?.settings()?.vaultDays
        ?: mediaViewModel.engine?.settings()?.vaultDays
        ?: 7u
    val opusAvailable = CoreFeatures.opusEncodeAvailable(mediaViewModel.engine)

    LaunchedEffect(importNonce, importedKind) {
        if (importNonce > 0 && importedKind != null) {
            selectedKind = importedKind
            selectedPreset = defaultPreset(importedKind)
        }
        selectionTick++
    }

    LaunchedEffect(photoViewModel.engine, mediaViewModel.engine, selectedKind) {
        val settings = photoViewModel.engine?.settings() ?: mediaViewModel.engine?.settings()
        settings?.let {
            compatibility = it.defaultCompat
            selectedPreset = when (selectedKind) {
                MediaKind.IMAGE -> it.defaultPresetImage
                MediaKind.VIDEO -> it.defaultPresetVideo
                MediaKind.AUDIO -> it.defaultPresetAudio
            }
            selectedFormat = it.defaultImageFormat
        }
    }

    LaunchedEffect(opusAvailable) {
        if (!opusAvailable && selectedAudioFormat == "opus") {
            selectedAudioFormat = "aac"
        }
    }

    LaunchedEffect(selectedPreset) {
        if (selectedPreset != QualityPreset.AUDIO_SPEECH) {
            selectedAudioFormat = "aac"
        }
    }

    val selectedCount = remember(selectionTick, selectedKind) {
        when (selectedKind) {
            MediaKind.IMAGE -> photoViewModel.selectedImages.size
            else -> mediaViewModel.selectedMedia.size
        }
    }
    val canReplace = remember(selectionTick, selectedKind, context) {
        when (selectedKind) {
            MediaKind.IMAGE -> photoViewModel.selectedImages.isNotEmpty() &&
                photoViewModel.selectedImages.any {
                    com.spaceslimmer.core.GalleryWriter.canAttemptReplace(context, it.uri, MediaKind.IMAGE)
                }
            else -> mediaViewModel.selectedMedia.isNotEmpty() &&
                mediaViewModel.selectedMedia.any {
                    com.spaceslimmer.core.GalleryWriter.canAttemptReplace(context, it.uri, selectedKind)
                }
        }
    }

    val allSkipped = selectedCount > 0 && decisions.isNotEmpty() && decisions.all { it.skip }
    val hasSavable = selectedCount > 0 && decisions.any { !it.skip }
    val canCompress = engineAvailable &&
        !loading &&
        !importing &&
        selectedCount > 0 &&
        !allSkipped &&
        (decisions.isEmpty() || hasSavable)

    LaunchedEffect(canReplace, selectedCount) {
        if (canReplace) {
            outputPolicy = OutputPolicy.REPLACE_TO_VAULT
        } else if (selectedCount > 0 && !canReplace) {
            outputPolicy = OutputPolicy.SAVE_AS_NEW
        }
    }

    fun buildCustom(): VideoCustomParams? {
        if (selectedKind != MediaKind.VIDEO) return null
        val params = customUi.toParams()
        if (selectedPreset != QualityPreset.VIDEO_CUSTOM &&
            selectedPreset != QualityPreset.VIDEO_TARGET_SIZE &&
            params == null
        ) {
            return null
        }
        return params ?: VideoCustomParams(
            maxLongEdge = null,
            fpsCap = null,
            videoBitrateBps = null,
            audioBitrateBps = null,
            codec = null,
            audioCopy = null,
            trimStartMs = null,
            trimEndMs = null,
            strictTarget = null,
        )
    }

    fun targetBytes(): ULong? {
        if (selectedKind != MediaKind.VIDEO || selectedPreset != QualityPreset.VIDEO_TARGET_SIZE) {
            return null
        }
        val mb = targetSizeMb.toULongOrNull() ?: return null
        return mb * 1024uL * 1024uL
    }

    LaunchedEffect(
        selectedKind,
        selectedPreset,
        selectedFormat,
        selectedAudioFormat,
        compatibility,
        outputPolicy,
        targetSizeMb,
        customUi,
        selectionTick,
    ) {
        if (selectedCount <= 0) {
            decisions = emptyList()
            return@LaunchedEffect
        }
        decisions = when (selectedKind) {
            MediaKind.IMAGE -> photoViewModel.estimate(
                selectedPreset,
                selectedFormat,
                compatibility,
                outputPolicy,
            )
            else -> mediaViewModel.estimate(
                kind = selectedKind,
                preset = selectedPreset,
                compatibility = compatibility,
                outputPolicy = outputPolicy,
                targetBytes = targetBytes(),
                custom = buildCustom(),
                audioFormat = if (selectedKind == MediaKind.AUDIO) selectedAudioFormat else null,
            )
        }
    }

    fun startCompress() {
        val isPro = BillingManager.isProUnlocked(context)
        if (!isPro) {
            val maxBatch = when (selectedKind) {
                MediaKind.VIDEO -> 3
                MediaKind.IMAGE -> 10
                MediaKind.AUDIO -> 5
            }
            if (selectedCount > maxBatch) {
                paywallReason = PaywallReason.BatchLimit(selectedCount, maxBatch)
                showPaywallGate = true
                return
            }

            if (selectedPreset == QualityPreset.VIDEO_TARGET_SIZE || selectedPreset == QualityPreset.VIDEO_CUSTOM) {
                paywallReason = PaywallReason.ProFeature(context.getString(R.string.target_size_mb))
                showPaywallGate = true
                return
            }

            if (selectedFormat == "avif" || (selectedKind == MediaKind.VIDEO && compatibility == Compatibility.EFFICIENT)) {
                paywallReason = PaywallReason.ProFeature(context.getString(R.string.home_compress_pro_hevc_feature))
                showPaywallGate = true
                return
            }

            val remaining = SpaceSlimmerApiClient.getRemainingQuota(context)
            if (remaining <= 0) {
                paywallReason = PaywallReason.QuotaExhausted
                showPaywallGate = true
                return
            }
        }

        loading = true
        error = null
        onRequestNotificationPermission()
        AppPrefs.markFirstCompressStarted(context)
        com.spaceslimmer.core.AppLogger.i(
            "HomeCompress",
            "startCompress: kind=$selectedKind, preset=$selectedPreset, policy=$outputPolicy, count=$selectedCount",
        )
        scope.launch {
            CompressForegroundService.start(context)
            val result = when (selectedKind) {
                MediaKind.IMAGE -> photoViewModel.submit(
                    selectedPreset,
                    selectedFormat,
                    compatibility,
                    outputPolicy,
                )
                else -> mediaViewModel.submit(
                    kind = selectedKind,
                    preset = selectedPreset,
                    compatibility = compatibility,
                    outputPolicy = outputPolicy,
                    targetBytes = targetBytes(),
                    custom = buildCustom(),
                    audioFormat = if (selectedKind == MediaKind.AUDIO) selectedAudioFormat else null,
                )
            }
            result.onSuccess {
                com.spaceslimmer.core.AppLogger.i("HomeCompress", "submit succeeded: groupId=$it")
                scope.launch {
                    val countToConsume = decisions.count { !it.skip }.coerceAtLeast(1)
                    SpaceSlimmerApiClient.consumeQuota(
                        context = context,
                        count = countToConsume,
                        mediaType = selectedKind.name.lowercase(),
                    )
                }
                when (selectedKind) {
                    MediaKind.IMAGE -> photoViewModel.clearSelection()
                    else -> mediaViewModel.clearSelection()
                }
                decisions = emptyList()
                selectionTick++
                onSubmitted(selectedKind, it)
            }
                .onFailure {
                    com.spaceslimmer.core.AppLogger.e("HomeCompress", "submit failed", it)
                    error = it.message
                }
            loading = false
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        importError = null
        scope.launch {
            val resolvedUris = uris.map { uri ->
                com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(context, uri, MediaKind.IMAGE) ?: uri
            }
            photoViewModel.importUris(resolvedUris)
                .onSuccess {
                    selectedKind = MediaKind.IMAGE
                    selectedPreset = defaultPreset(MediaKind.IMAGE)
                    selectionTick++
                }
                .onFailure { importError = it.message }
            importing = false
        }
    }
    val pickVideos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        importError = null
        scope.launch {
            val resolvedUris = uris.map { uri ->
                com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(context, uri, MediaKind.VIDEO) ?: uri
            }
            mediaViewModel.importUris(resolvedUris, MediaKind.VIDEO)
                .onSuccess {
                    selectedKind = MediaKind.VIDEO
                    selectedPreset = defaultPreset(MediaKind.VIDEO)
                    selectionTick++
                }
                .onFailure { importError = it.message }
            importing = false
        }
    }
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        importError = null
        scope.launch {
            mediaViewModel.importUris(uris, MediaKind.AUDIO)
                .onSuccess {
                    selectedKind = MediaKind.AUDIO
                    selectedPreset = defaultPreset(MediaKind.AUDIO)
                    selectionTick++
                }
                .onFailure { importError = it.message }
            importing = false
        }
    }

    fun launchPicker() {
        when (selectedKind) {
            MediaKind.IMAGE -> pickPhotos.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            MediaKind.VIDEO -> pickVideos.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
            MediaKind.AUDIO -> pickAudio.launch(
                arrayOf(
                    "audio/*",
                    "audio/amr",
                    "audio/amr-wb",
                    "audio/3gpp",
                    "audio/ogg",
                    "application/ogg",
                ),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            headerContent()

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SsSection(title = stringResource(R.string.home_pick_title)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SsSegmented(
                            options = listOf(
                                SsSegmentOption(MediaKind.IMAGE, stringResource(R.string.pick_photo)),
                                SsSegmentOption(MediaKind.VIDEO, stringResource(R.string.pick_video)),
                                SsSegmentOption(MediaKind.AUDIO, stringResource(R.string.pick_audio)),
                            ),
                            selected = selectedKind,
                            onSelected = { kind ->
                                selectedKind = kind
                                selectedPreset = defaultPreset(kind)
                            },
                            scrollable = false,
                        )
                        if (importing) {
                            Text(
                                stringResource(R.string.photo_importing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (importError != null) {
                            Text(importError!!, color = MaterialTheme.colorScheme.error)
                        }
                        if (selectedCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (selectedKind == MediaKind.IMAGE) {
                                        stringResource(R.string.photo_selected_count, selectedCount)
                                    } else {
                                        stringResource(R.string.media_selected_count, selectedCount)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.home_clear_selected),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clickable {
                                            when (selectedKind) {
                                                MediaKind.IMAGE -> photoViewModel.clearSelection()
                                                else -> mediaViewModel.clearSelection()
                                            }
                                            decisions = emptyList()
                                            selectionTick++
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        when (selectedKind) {
                            MediaKind.VIDEO -> {
                                VideoPurposePresetGrid(
                                    selected = selectedPreset,
                                    onSelect = { selectedPreset = it },
                                )
                                VideoCustomPanel(
                                    state = customUi,
                                    onChange = { customUi = it },
                                    showResolution = true,
                                    showAv1 = hwCaps.av1HwEncode,
                                    showImportant = true,
                                    showAdvanced = false,
                                )
                            }
                            MediaKind.IMAGE -> {
                                Text(
                                    stringResource(R.string.compress_ratio_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SsSegmented(
                                    options = photoViewModel.presets.map { preset ->
                                        SsSegmentOption(preset, imagePresetLabel(preset))
                                    },
                                    selected = selectedPreset,
                                    onSelected = { selectedPreset = it },
                                    scrollable = false,
                                )
                                Text(
                                    photoPurposeDescription(selectedPreset),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.photo_preset_format),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SsSegmented(
                                    options = photoViewModel.formats.map { format ->
                                        SsSegmentOption(format, photoFormatLabel(format))
                                    },
                                    selected = selectedFormat,
                                    onSelected = { selectedFormat = it },
                                    scrollable = false,
                                )
                            }
                            MediaKind.AUDIO -> {
                                Text(
                                    stringResource(R.string.compress_ratio_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SsSegmented(
                                    options = mediaViewModel.audioPresets.map { preset ->
                                        SsSegmentOption(preset, audioPresetLabel(preset))
                                    },
                                    selected = selectedPreset,
                                    onSelected = { selectedPreset = it },
                                    scrollable = false,
                                )
                                Text(
                                    audioPurposeDescription(selectedPreset),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (selectedPreset == QualityPreset.AUDIO_SPEECH) {
                                    Text(
                                        stringResource(R.string.audio_preset_format),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (opusAvailable) {
                                        SsSegmented(
                                            options = listOf(
                                                SsSegmentOption("aac", stringResource(R.string.audio_format_aac)),
                                                SsSegmentOption("opus", stringResource(R.string.audio_format_opus)),
                                            ),
                                            selected = selectedAudioFormat,
                                            onSelected = { selectedAudioFormat = it },
                                        )
                                    }
                                    Text(
                                        if (opusAvailable) {
                                            stringResource(R.string.audio_format_aac_default_note)
                                        } else {
                                            stringResource(R.string.audio_format_opus_unavailable)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        OutputPolicyRow(
                            selected = outputPolicy,
                            onSelected = {
                                if (it == OutputPolicy.REPLACE_TO_VAULT && outputPolicy != OutputPolicy.REPLACE_TO_VAULT) {
                                    showReplaceConfirm = true
                                } else {
                                    outputPolicy = it
                                }
                            },
                            canReplace = canReplace,
                        )

                        Text(
                            text = if (showAdvanced) {
                                stringResource(R.string.compress_fewer_params)
                            } else {
                                stringResource(R.string.compress_more_params)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvanced = !showAdvanced }
                                .padding(vertical = 4.dp),
                        )
                        AnimatedVisibility(visible = showAdvanced) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (selectedKind == MediaKind.VIDEO) {
                                    SsSegmented(
                                        options = listOf(
                                            QualityPreset.VIDEO_TARGET_SIZE,
                                            QualityPreset.VIDEO_CUSTOM,
                                        ).map { preset ->
                                            SsSegmentOption(preset, videoPresetLabel(preset))
                                        },
                                        selected = selectedPreset,
                                        onSelected = { selectedPreset = it },
                                    )
                                    if (selectedPreset == QualityPreset.VIDEO_TARGET_SIZE) {
                                        OutlinedTextField(
                                            value = targetSizeMb,
                                            onValueChange = { targetSizeMb = it.filter { ch -> ch.isDigit() } },
                                            label = { Text(stringResource(R.string.target_size_mb)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = InputShape,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    VideoCustomPanel(
                                        state = customUi,
                                        onChange = { customUi = it },
                                        showResolution = false,
                                        showAv1 = hwCaps.av1HwEncode,
                                        showImportant = false,
                                        showAdvanced = true,
                                    )
                                }
                                CompatibilityRow(compatibility, { compatibility = it }, hwCaps)
                            }
                        }
                    }
                }

                if (allSkipped) {
                    SsCard(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.compress_not_needed_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.compress_not_needed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (loading || importing) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
                if (selectedCount > 0) {
                    val savableIndices = decisions.indices.filter { !decisions[it].skip }
                    val totalOriginalBytes = remember(selectedCount, selectedKind, selectionTick) {
                        when (selectedKind) {
                            MediaKind.IMAGE -> photoViewModel.selectedImages.sumOf { it.fileBytes }
                            else -> mediaViewModel.selectedMedia.sumOf { it.fileBytes }
                        }
                    }
                    val count = if (decisions.isNotEmpty()) savableIndices.size else selectedCount
                    val origStr = formatBytes(context, totalOriginalBytes)
                    val outStr = if (decisions.isEmpty()) {
                        context.getString(R.string.estimating)
                    } else if (allSkipped) {
                        origStr
                    } else {
                        val low = savableIndices.sumOf { decisions[it].estimateBytesLow.toLong() }
                        val high = savableIndices.sumOf { decisions[it].estimateBytesHigh.toLong() }
                        if (low == high) formatBytes(context, low)
                        else "${formatBytes(context, low)}~${formatBytes(context, high)}"
                    }
                    val savedStr = if (decisions.isEmpty()) {
                        context.getString(R.string.estimating)
                    } else if (allSkipped) {
                        formatBytes(context, 0L)
                    } else {
                        val low = savableIndices.sumOf { decisions[it].estimateBytesLow.toLong() }
                        val high = savableIndices.sumOf { decisions[it].estimateBytesHigh.toLong() }
                        val savableOrigBytes = when (selectedKind) {
                            MediaKind.IMAGE -> savableIndices.sumOf { photoViewModel.selectedImages.getOrNull(it)?.fileBytes ?: 0L }
                            else -> savableIndices.sumOf { mediaViewModel.selectedMedia.getOrNull(it)?.fileBytes ?: 0L }
                        }
                        val savedLow = (savableOrigBytes - high).coerceAtLeast(0L)
                        val savedHigh = (savableOrigBytes - low).coerceAtLeast(0L)
                        if (savedLow == savedHigh) formatBytes(context, savedLow)
                        else "${formatBytes(context, savedLow)}~${formatBytes(context, savedHigh)}"
                    }
                    val timeStr = if (allSkipped) {
                        context.getString(R.string.estimate_no_time_needed)
                    } else if (decisions.isEmpty()) {
                        context.getString(R.string.estimate_calculating)
                    } else {
                        val estSecs = when (selectedKind) {
                            MediaKind.IMAGE -> (savableIndices.size * 0.4).toInt().coerceAtLeast(1)
                            MediaKind.AUDIO -> (savableIndices.size * 1.5).toInt().coerceAtLeast(2)
                            MediaKind.VIDEO -> {
                                val totalMs = savableIndices.sumOf { mediaViewModel.selectedMedia.getOrNull(it)?.durationMs ?: 0L }
                                val totalBytes = savableIndices.sumOf { mediaViewModel.selectedMedia.getOrNull(it)?.fileBytes ?: 0L }
                                if (totalMs > 0) {
                                    (totalMs / 3500).toInt().coerceAtLeast(3)
                                } else {
                                    (totalBytes / 12_000_000).toInt().coerceAtLeast(3)
                                }
                            }
                        }
                        if (estSecs < 60) {
                            context.getString(R.string.estimate_time_seconds, estSecs)
                        } else {
                            val mins = estSecs / 60
                            val remSecs = estSecs % 60
                            if (remSecs == 0) context.getString(R.string.estimate_time_minutes, mins)
                            else context.getString(R.string.estimate_time_mins_secs, mins, remSecs)
                        }
                    }

                    val redColor = Color(0xFFE53935)
                    val prefix = context.getString(R.string.home_compress_estimate_prefix)
                    val details = context.getString(R.string.home_compress_estimate_details, origStr, outStr, savedStr, timeStr)
                    val estimateText = buildAnnotatedString {
                        append(prefix)
                        withStyle(SpanStyle(color = redColor, fontWeight = FontWeight.Bold)) {
                            append("$count")
                        }
                        append(details)
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = estimateText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                SsSecondaryButton(
                    text = stringResource(R.string.home_pick_files_button),
                    onClick = { launchPicker() },
                    enabled = !loading && !importing,
                )
                SsPrimaryButton(
                    text = stringResource(R.string.media_start_compress),
                    onClick = { startCompress() },
                    enabled = canCompress,
                )
            }
        }
    }

    if (showReplaceConfirm) {
        SsAlertDialog(
            onDismissRequest = {
                showReplaceConfirm = false
                outputPolicy = OutputPolicy.SAVE_AS_NEW
            },
            title = { Text(stringResource(R.string.replace_confirm_title)) },
            text = { Text(stringResource(R.string.replace_confirm_body, vaultDays.toInt())) },
            confirmButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.replace_confirm_ok),
                    onClick = {
                        showReplaceConfirm = false
                        outputPolicy = OutputPolicy.REPLACE_TO_VAULT
                    },
                )
            },
            dismissButton = {
                SsDialogTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        showReplaceConfirm = false
                        outputPolicy = OutputPolicy.SAVE_AS_NEW
                    },
                )
            },
        )
    }

    if (showPaywallGate && paywallReason != null) {
        PaywallGateDialog(
            reason = paywallReason!!,
            onDismissRequest = { showPaywallGate = false },
            onUnlockSuccess = {
                showPaywallGate = false
                startCompress()
            },
        )
    }
}

private fun defaultPreset(kind: MediaKind): QualityPreset = when (kind) {
    MediaKind.IMAGE -> QualityPreset.IMAGE_HIGH
    MediaKind.VIDEO -> QualityPreset.VIDEO_BALANCED
    MediaKind.AUDIO -> QualityPreset.AUDIO_MUSIC_HIGH
}

@Composable
private fun photoPurposeDescription(preset: QualityPreset): String = when (preset) {
    QualityPreset.IMAGE_MAX -> stringResource(R.string.photo_purpose_max)
    QualityPreset.IMAGE_HIGH -> stringResource(R.string.photo_purpose_high)
    QualityPreset.IMAGE_SMALL -> stringResource(R.string.photo_purpose_small)
    else -> ""
}

@Composable
private fun audioPurposeDescription(preset: QualityPreset): String = when (preset) {
    QualityPreset.AUDIO_SPEECH -> stringResource(R.string.audio_purpose_speech)
    QualityPreset.AUDIO_MUSIC_HIGH -> stringResource(R.string.audio_purpose_high)
    QualityPreset.AUDIO_MUSIC_SMALL -> stringResource(R.string.audio_purpose_small)
    else -> ""
}
