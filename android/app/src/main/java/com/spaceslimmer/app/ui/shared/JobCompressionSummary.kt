package com.spaceslimmer.app.ui.shared

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spaceslimmer.app.R
import com.spaceslimmer.app.export.GallerySaveOutcome
import com.spaceslimmer.app.ui.theme.savingsColor
import com.spaceslimmer.core.MessageKeys
import kotlinx.coroutines.launch
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind
import java.util.Locale

@Composable
fun jobStateLabel(state: JobState): String = when (state) {
    JobState.QUEUED -> stringResource(R.string.job_state_queued)
    JobState.RUNNING -> stringResource(R.string.job_state_running)
    JobState.PAUSED -> stringResource(R.string.job_state_paused)
    JobState.CANCELLING -> stringResource(R.string.job_state_cancelling)
    JobState.SUCCEEDED -> stringResource(R.string.job_state_succeeded)
    JobState.SKIPPED -> stringResource(R.string.job_state_skipped)
    JobState.FAILED -> stringResource(R.string.job_state_failed)
    JobState.COMMITTED_REPLACE -> stringResource(R.string.job_state_replaced)
}

@Composable
fun JobCompressionSummary(job: JobView, prominent: Boolean = false) {
    val stats = compressionSizeStats(job)
    if (stats.outputBytes <= 0L) return
    val context = LocalContext.current
    val srcText = formatBytes(context, stats.originalBytes)
    val outText = formatBytes(context, stats.outputBytes)
    val savedText = formatBytes(context, stats.savedBytes)
    val meta = formatCompressionMeta(context, stats)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (prominent) 4.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = srcText,
                style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.result_size_arrow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = outText,
                style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = savingsColor(),
            )
        }
        Text(
            text = stringResource(R.string.result_saved_percent, savedText, stats.savedPercent),
            style = if (prominent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = savingsColor(),
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatCompressionMeta(context: android.content.Context, stats: CompressionSizeStats): String? {
    val parts = mutableListOf<String>()
    val origW = stats.originalWidth
    val origH = stats.originalHeight
    val outW = stats.outputWidth
    val outH = stats.outputHeight
    if (origW > 0 && origH > 0) {
        val resolution = if (outW != null && outH != null && (outW != origW || outH != origH)) {
            context.getString(
                R.string.result_meta_resolution_change,
                origW,
                origH,
                outW,
                outH,
            )
        } else {
            context.getString(R.string.result_meta_resolution, origW, origH)
        }
        parts += resolution
    }
    val fps = stats.outputFps ?: stats.originalFps
    if (fps != null && fps > 0f) {
        parts += context.getString(R.string.result_meta_fps, formatFps(fps))
    }
    stats.codec?.takeIf { it.isNotBlank() }?.let {
        parts += it.uppercase(Locale.US)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatFps(fps: Float): String =
    if (fps % 1f == 0f) fps.toInt().toString() else String.format(Locale.US, "%.2f", fps)

@Composable
fun rememberGallerySaveHandler(
    replaceMode: Boolean,
    save: suspend () -> Result<GallerySaveOutcome>,
    onMessage: (String) -> Unit,
    savedCountRes: Int = R.string.media_saved_to_gallery,
    replacedCountRes: Int = R.string.media_replaced_count,
    kind: MediaKind = MediaKind.VIDEO,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun applyOutcome(outcome: GallerySaveOutcome) {
        when (outcome) {
            is GallerySaveOutcome.Saved -> {
                onMessage(
                    when {
                        outcome.count <= 0 -> context.getString(R.string.save_none)
                        replaceMode && outcome.replaced -> context.getString(replacedCountRes, outcome.count)
                        replaceMode && outcome.savedAsNewFallback -> context.getString(
                            R.string.save_fallback_new,
                            outcome.count,
                        )
                        else -> context.getString(savedCountRes, outcome.count)
                    },
                )
            }
            is GallerySaveOutcome.NeedWriteConsent -> Unit
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                save()
                    .onSuccess { outcome ->
                        if (outcome is GallerySaveOutcome.NeedWriteConsent) {
                            onMessage(context.getString(R.string.replace_consent_denied))
                        } else {
                            applyOutcome(outcome)
                        }
                    }
                    .onFailure { onMessage(localizeSaveError(context, it)) }
            }
        } else {
            onMessage(context.getString(R.string.replace_consent_denied))
        }
    }

    fun executeSave() {
        scope.launch {
            save()
                .onSuccess { outcome ->
                    when (outcome) {
                        is GallerySaveOutcome.NeedWriteConsent -> {
                            consentLauncher.launch(
                                IntentSenderRequest.Builder(outcome.pendingIntent).build(),
                            )
                        }
                        is GallerySaveOutcome.Saved -> applyOutcome(outcome)
                    }
                }
                .onFailure { onMessage(localizeSaveError(context, it)) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        executeSave()
    }

    return {
        if (replaceMode) {
            val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when (kind) {
                    MediaKind.VIDEO -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
                    MediaKind.IMAGE -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
                    MediaKind.AUDIO -> listOf(Manifest.permission.READ_MEDIA_AUDIO)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                emptyList()
            }.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            } else {
                executeSave()
            }
        } else {
            executeSave()
        }
    }
}

fun localizeSaveError(context: android.content.Context, error: Throwable): String {
    val key = error.message.orEmpty()
    val localized = MessageKeys.localize(context, key)
    if (localized != key && key.isNotEmpty()) return localized
    return context.getString(R.string.save_failed, error.message ?: error.javaClass.simpleName)
}
