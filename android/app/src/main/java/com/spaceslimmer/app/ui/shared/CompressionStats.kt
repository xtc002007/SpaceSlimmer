package com.spaceslimmer.app.ui.shared

import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind

data class CompressionSizeStats(
    val originalBytes: Long,
    val outputBytes: Long,
    val savedBytes: Long,
    val savedPercent: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int?,
    val outputHeight: Int?,
    val originalFps: Float?,
    val outputFps: Float?,
    val codec: String?,
    val kind: MediaKind,
)

fun compressionSizeStats(job: JobView): CompressionSizeStats {
    val fingerprint = job.spec.fingerprint
    val original = fingerprint.fileBytes.toLong().coerceAtLeast(0L)
    val output = job.outputBytes?.toLong()?.takeIf { it > 0L }
        ?: (original - (job.savedBytes ?: 0L)).coerceAtLeast(0L)
    val saved = job.savedBytes ?: (original - output)
    val percent = if (original > 0L) {
        kotlin.math.round((saved.toDouble() / original.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val recipe = job.decision?.recipe
    return CompressionSizeStats(
        originalBytes = original,
        outputBytes = output,
        savedBytes = saved,
        savedPercent = percent,
        originalWidth = fingerprint.width.toInt(),
        originalHeight = fingerprint.height.toInt(),
        outputWidth = recipe?.outW?.toInt()?.takeIf { it > 0 },
        outputHeight = recipe?.outH?.toInt()?.takeIf { it > 0 },
        originalFps = fingerprint.fps,
        outputFps = recipe?.fps,
        codec = recipe?.videoCodec?.takeIf { it.isNotBlank() } ?: fingerprint.videoCodec,
        kind = fingerprint.kind,
    )
}

fun groupCompressionStats(jobs: List<JobView>): CompressionSizeStats? {
    val succeeded = jobs.map { compressionSizeStats(it) }
        .filter { it.originalBytes > 0L || it.outputBytes > 0L }
    if (succeeded.isEmpty()) return null
    val original = succeeded.sumOf { it.originalBytes }
    val output = succeeded.sumOf { it.outputBytes }
    val saved = succeeded.sumOf { it.savedBytes }
    val percent = if (original > 0L) {
        ((saved.toDouble() / original.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val first = succeeded.first()
    return first.copy(
        originalBytes = original,
        outputBytes = output,
        savedBytes = saved,
        savedPercent = percent,
    )
}
