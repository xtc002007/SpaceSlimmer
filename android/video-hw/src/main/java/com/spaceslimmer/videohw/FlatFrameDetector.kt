package com.spaceslimmer.videohw

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlin.math.sqrt

/**
 * Cheap 花屏 heuristic: sampled frames that are almost a single luma value.
 * Not a CV model; used as a user-visible warning + telemetry.
 */
object FlatFrameDetector {
    fun looksFlat(path: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return@runCatching false
            if (duration <= 0L) return@runCatching false
            val timesUs = longArrayOf(
                duration * 250L,
                duration * 500L,
                duration * 750L,
            )
            var flat = 0
            var sampled = 0
            for (t in timesUs) {
                val frame = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: continue
                sampled += 1
                if (lumaStdDev(frame) < 6f) flat += 1
                if (!frame.isRecycled) frame.recycle()
            }
            sampled >= 2 && flat >= 2
        }.getOrDefault(false).also {
            runCatching { retriever.release() }
        }
    }

    private fun lumaStdDev(bitmap: Bitmap): Float {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val stepX = (w / 24).coerceAtLeast(1)
        val stepY = (h / 24).coerceAtLeast(1)
        var n = 0
        var sum = 0.0
        var sumSq = 0.0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val luma = (0.299 * ((c shr 16) and 0xFF) +
                    0.587 * ((c shr 8) and 0xFF) +
                    0.114 * (c and 0xFF))
                sum += luma
                sumSq += luma * luma
                n += 1
                x += stepX
            }
            y += stepY
        }
        if (n == 0) return 255f
        val mean = sum / n
        val variance = (sumSq / n) - mean * mean
        return sqrt(variance.coerceAtLeast(0.0)).toFloat()
    }
}
