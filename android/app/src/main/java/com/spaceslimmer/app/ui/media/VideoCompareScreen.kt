package com.spaceslimmer.app.ui.media

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsScreen
import com.spaceslimmer.app.ui.shared.JobCompressionSummary
import kotlinx.coroutines.delay
import uniffi.ss_core.JobView
import java.io.File
import java.util.Locale

private enum class VideoCompareMode {
    SLIDER,
    TOGGLE,
}

@Composable
fun VideoCompareScreen(
    job: JobView,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val beforeUri = job.spec.sourceUri
    val afterUri = job.outputUri ?: job.spec.sourceUri

    val beforePlayer = remember(beforeUri) {
        val uri = if (beforeUri.startsWith("/")) Uri.fromFile(File(beforeUri)) else Uri.parse(beforeUri)
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }
    val afterPlayer = remember(afterUri) {
        val uri = if (afterUri.startsWith("/")) Uri.fromFile(File(afterUri)) else Uri.parse(afterUri)
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(beforePlayer, afterPlayer) {
        onDispose {
            beforePlayer.release()
            afterPlayer.release()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var compareMode by remember { mutableStateOf(VideoCompareMode.SLIDER) }
    var showOriginalInToggle by remember { mutableStateOf(false) }

    LaunchedEffect(beforePlayer, afterPlayer, isDraggingSlider, isPlaying) {
        while (true) {
            if (!isDraggingSlider && isPlaying) {
                val bPos = beforePlayer.currentPosition
                val aPos = afterPlayer.currentPosition
                val dur = beforePlayer.duration.takeIf { it > 0 }
                    ?: afterPlayer.duration.takeIf { it > 0 }
                    ?: 1L
                currentPositionMs = bPos
                durationMs = dur

                // Keep playback position aligned
                if (kotlin.math.abs(bPos - aPos) > 150L && afterPlayer.playbackState == Player.STATE_READY) {
                    afterPlayer.seekTo(bPos)
                }
            }
            delay(100L)
        }
    }

    SsScreen(
        topBar = { SsAppBar(title = stringResource(R.string.video_compare_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = job.spec.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    JobCompressionSummary(job = job, prominent = true)
                }
            }

            // 2. Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { compareMode = VideoCompareMode.SLIDER },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (compareMode == VideoCompareMode.SLIDER) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (compareMode == VideoCompareMode.SLIDER) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.compare_slider_mode), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { compareMode = VideoCompareMode.TOGGLE },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (compareMode == VideoCompareMode.TOGGLE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (compareMode == VideoCompareMode.TOGGLE) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.compare_toggle_mode), fontWeight = FontWeight.SemiBold)
                }
            }

            // 3. Video Comparison Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                when (compareMode) {
                    VideoCompareMode.SLIDER -> {
                        VideoCompareSliderView(
                            beforePlayer = beforePlayer,
                            afterPlayer = afterPlayer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    VideoCompareMode.TOGGLE -> {
                        VideoCompareToggleView(
                            beforePlayer = beforePlayer,
                            afterPlayer = afterPlayer,
                            showOriginal = showOriginalInToggle,
                            onToggleOriginal = { showOriginalInToggle = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // 4. Playback Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {
                        val next = !isPlaying
                        isPlaying = next
                        beforePlayer.playWhenReady = next
                        afterPlayer.playWhenReady = next
                    },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Slider(
                    value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { newValue ->
                        isDraggingSlider = true
                        currentPositionMs = newValue.toLong()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        beforePlayer.seekTo(currentPositionMs)
                        afterPlayer.seekTo(currentPositionMs)
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VideoCompareSliderView(
    beforePlayer: ExoPlayer,
    afterPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    var split by remember { mutableFloatStateOf(0.5f) }
    val handleCd = stringResource(R.string.compare_slider_handle)

    BoxWithConstraints(
        modifier = modifier
            .semantics { contentDescription = handleCd }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    split = (split + dragAmount.x / size.width.toFloat()).coerceIn(0.02f, 0.98f)
                }
            },
    ) {
        val fullWidth = maxWidth
        val splitWidth = fullWidth * split

        // Background: After (Compressed) video
        VideoPlayerView(
            player = afterPlayer,
            modifier = Modifier.fillMaxSize(),
        )

        // Foreground: Before (Original) video - clipped to splitWidth, but requiredWidth = fullWidth
        Box(
            modifier = Modifier
                .width(splitWidth)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            VideoPlayerView(
                player = beforePlayer,
                modifier = Modifier
                    .requiredWidth(fullWidth)
                    .fillMaxHeight(),
            )
        }

        // Vertical divider line
        Box(
            modifier = Modifier
                .offset(x = splitWidth - 1.5.dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(Color.White),
        )

        // Center drag handle
        Box(
            modifier = Modifier
                .offset(x = splitWidth - 18.dp)
                .align(Alignment.CenterStart)
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Floating labels at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.compare_video_original),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.compare_video_compressed),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoCompareToggleView(
    beforePlayer: ExoPlayer,
    afterPlayer: ExoPlayer,
    showOriginal: Boolean,
    onToggleOriginal: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.pointerInput(showOriginal) {
            detectTapGestures(
                onPress = {
                    val prev = showOriginal
                    onToggleOriginal(true)
                    tryAwaitRelease()
                    onToggleOriginal(prev)
                },
            )
        },
    ) {
        // Bottom layer: Compressed video
        VideoPlayerView(
            player = afterPlayer,
            modifier = Modifier.fillMaxSize(),
        )

        // Top layer: Original video (fades in when showOriginal is true)
        VideoPlayerView(
            player = beforePlayer,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (showOriginal) 1f else 0f),
        )

        // Floating controls at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.compare_hold_to_compare),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onToggleOriginal(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showOriginal) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(stringResource(R.string.compare_video_original), fontWeight = if (showOriginal) FontWeight.Bold else FontWeight.Normal)
                }
                Button(
                    onClick = { onToggleOriginal(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!showOriginal) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(stringResource(R.string.compare_video_compressed), fontWeight = if (!showOriginal) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerView(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val view = LayoutInflater.from(ctx).inflate(R.layout.item_compare_player, null, false) as PlayerView
            view.player = player
            view
        },
        update = { view ->
            if (view.player != player) {
                view.player = player
            }
        },
        modifier = modifier,
    )
}

private fun formatDuration(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
