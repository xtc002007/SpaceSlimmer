package com.spaceslimmer.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceslimmer.app.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Clock-style dynamic countdown banner.
 * Features an analog ticking clock dial and digital MM:SS countdown timer,
 * dynamically adapting to actual compression speed to give users reliable expectations.
 */
@Composable
fun CompressClockCountdown(
    progress: Float,
    totalCount: Int,
    doneCount: Int,
    initialEstimateSecs: Int = 30,
    startedAtMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            tickMs = System.currentTimeMillis()
        }
    }

    val baseTimeMs = remember(startedAtMs) {
        if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis()
    }
    val elapsedSecs = max(0, ((tickMs - baseTimeMs) / 1000L).toInt())

    // Dynamic remaining seconds calculation
    val remainingSecs = remember(progress, elapsedSecs, initialEstimateSecs) {
        val safeProgress = progress.coerceIn(0f, 1f)
        if (safeProgress >= 0.99f) {
            0
        } else if (safeProgress > 0.02f && elapsedSecs > 1) {
            val speed = safeProgress / elapsedSecs.toFloat()
            val remainingRatio = 1f - safeProgress
            val speedEta = (remainingRatio / speed).roundToInt()

            val weight = (safeProgress * 5f).coerceIn(0f, 1f)
            val timeEta = max(1, initialEstimateSecs - elapsedSecs)
            max(1, (timeEta * (1f - weight) + speedEta * weight).roundToInt())
        } else {
            if (elapsedSecs < initialEstimateSecs) {
                initialEstimateSecs - elapsedSecs
            } else {
                max(3, (initialEstimateSecs * 0.25).roundToInt())
            }
        }
    }

    val minutes = remainingSecs / 60
    val seconds = remainingSecs % 60
    val countdownText = String.format("%02d:%02d", minutes, seconds)

    val elapsedMinutes = elapsedSecs / 60
    val elapsedSeconds = elapsedSecs % 60
    val elapsedText = String.format("%02d:%02d", elapsedMinutes, elapsedSeconds)

    val cardBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val clockPrimaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Analog Clock Canvas
                AnalogClockDial(
                    elapsedSeconds = elapsedSecs,
                    dialColor = clockPrimaryColor,
                    modifier = Modifier.size(46.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.compress_est_remaining_time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = countdownText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (minutes > 0) stringResource(R.string.compress_time_unit_min_sec) else stringResource(R.string.compress_time_unit_sec),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.compress_elapsed_time, elapsedText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.compress_progress_items, doneCount, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Renders an animated analog clock dial with ticking hands and hour markers.
 */
@Composable
private fun AnalogClockDial(
    elapsedSeconds: Int,
    dialColor: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "clockHandRotation")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepHand",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 2.dp.toPx()

        // Outer rim
        drawCircle(
            color = dialColor.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Hour tick marks (12 ticks)
        for (i in 0 until 12) {
            val angleRad = Math.toRadians((i * 30 - 90).toDouble())
            val startDist = radius - 4.dp.toPx()
            val endDist = radius - 1.dp.toPx()
            val start = Offset(
                x = center.x + (startDist * cos(angleRad)).toFloat(),
                y = center.y + (startDist * sin(angleRad)).toFloat(),
            )
            val end = Offset(
                x = center.x + (endDist * cos(angleRad)).toFloat(),
                y = center.y + (endDist * sin(angleRad)).toFloat(),
            )
            drawLine(
                color = dialColor.copy(alpha = if (i % 3 == 0) 0.8f else 0.4f),
                start = start,
                end = end,
                strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Minute hand (moves based on elapsed seconds)
        val minuteAngleRad = Math.toRadians((((elapsedSeconds % 3600) / 3600.0) * 360.0 - 90).toDouble())
        val minuteHandLength = radius * 0.65f
        drawLine(
            color = dialColor,
            start = center,
            end = Offset(
                x = center.x + (minuteHandLength * cos(minuteAngleRad)).toFloat(),
                y = center.y + (minuteHandLength * sin(minuteAngleRad)).toFloat(),
            ),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Second hand (smooth sweep animation)
        val secondAngleRad = Math.toRadians((sweepAngle - 90).toDouble())
        val secondHandLength = radius * 0.82f
        val secondColor = Color(0xFFE53935) // Accent red second hand
        drawLine(
            color = secondColor,
            start = center,
            end = Offset(
                x = center.x + (secondHandLength * cos(secondAngleRad)).toFloat(),
                y = center.y + (secondHandLength * sin(secondAngleRad)).toFloat(),
            ),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Center hub pin
        drawCircle(
            color = secondColor,
            radius = 2.5.dp.toPx(),
            center = center,
        )
    }
}
