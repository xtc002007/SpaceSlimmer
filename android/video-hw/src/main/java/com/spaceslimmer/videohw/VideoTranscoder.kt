package com.spaceslimmer.videohw



import android.content.Context

import android.media.MediaExtractor

import android.media.MediaFormat

import android.net.Uri
import android.provider.MediaStore
import android.util.Log

import androidx.media3.common.MediaItem

import androidx.media3.common.MimeTypes

import androidx.media3.common.util.UnstableApi

import androidx.media3.effect.ScaleAndRotateTransformation

import androidx.media3.transformer.AudioEncoderSettings

import androidx.media3.transformer.Composition

import androidx.media3.transformer.DefaultEncoderFactory

import androidx.media3.transformer.EditedMediaItem

import androidx.media3.transformer.Effects

import androidx.media3.transformer.ExportException

import androidx.media3.transformer.ExportResult

import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.spaceslimmer.core.AppLogger
import uniffi.ss_core.Recipe
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

@UnstableApi
object VideoTranscoder {
    private const val TAG = "VideoTranscoder"
    private const val DURATION_ABS_TOLERANCE_MS = 500L
    private const val DURATION_RATIO_TOLERANCE = 0.02f
    private const val MIN_WALL_TIMEOUT_MS = 30L * 60 * 1000

    private fun isAv1Codec(codec: String): Boolean {
        val c = codec.lowercase()
        return c.contains("av1") || c.contains("av01")
    }

    data class TranscodeParams(
        val sourceUri: String,
        val outputPath: String,
        val recipe: Recipe,
        val useHevc: Boolean,
        val jobId: String,
        val onProgress: (Float) -> Unit,
        val isCancelled: () -> Boolean,
        val onTelemetry: (String, String) -> Unit = { _, _ -> },
    )

    @Throws(Exception::class)
    fun transcode(context: Context, params: TranscodeParams): Long {
        val resolvedUri = if (params.sourceUri.startsWith("content://") &&
            (params.sourceUri.contains("photopicker") || params.sourceUri.contains("picker"))
        ) {
            com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(
                context,
                params.sourceUri,
                uniffi.ss_core.MediaKind.VIDEO,
            )?.toString() ?: params.sourceUri
        } else {
            params.sourceUri
        }
        val actualParams = if (resolvedUri != params.sourceUri) {
            params.copy(sourceUri = resolvedUri)
        } else {
            params
        }

        val sourceDurationMs = probeDurationMs(context, actualParams.sourceUri)
        val expectedDurationMs = effectiveDurationMs(sourceDurationMs, actualParams.recipe)
        val inputSize = probeInputFileSize(context, actualParams.sourceUri)
        var lastError: Throwable? = null

        AppLogger.i(
            TAG,
            "transcode start job=${actualParams.jobId}, src=${actualParams.sourceUri}, out=${actualParams.outputPath}, " +
                "durationMs=$sourceDurationMs, inSize=$inputSize, codec=${actualParams.recipe.videoCodec}, " +
                "bitrate=${actualParams.recipe.videoBitrateBps}, useHevc=${actualParams.useHevc}",
        )



        val tryHevc = actualParams.useHevc && !HevcBlacklist.shouldSkipHevc(context)

        val tryAv1 = isAv1Codec(actualParams.recipe.videoCodec)
        if (tryAv1) {
            runCatching {
                val bytes = runOnce(context, actualParams, useHevc = false, expectedDurationMs)
                if (isAcceptableSize(bytes, inputSize)) {
                    return finalizeOutput(context, actualParams, maybeSecondPass(context, actualParams, bytes, usedHevc = false, expectedDurationMs))
                }
                AppLogger.w(TAG, "AV1 output too large ($bytes vs input $inputSize), falling back")
            }.onFailure { err ->
                lastError = err
                actualParams.onTelemetry("fallback", """{"from":"av1","to":"avc"}""")
                AppLogger.w(TAG, "AV1 failed, falling back: ${err.message}", err)
            }
            actualParams.recipe.videoCodec = if (tryHevc) "hevc" else "h264"
        }

        if (tryHevc) {
            runCatching {
                val bytes = runOnce(context, actualParams, useHevc = true, expectedDurationMs)
                if (isAcceptableSize(bytes, inputSize)) {
                    return finalizeOutput(context, actualParams, maybeSecondPass(context, actualParams, bytes, usedHevc = true, expectedDurationMs))
                }
                AppLogger.w(TAG, "HEVC output too large ($bytes vs input $inputSize), falling back")
            }.onFailure { err ->
                HevcBlacklist.recordFailure(context)
                lastError = err
                actualParams.onTelemetry("fallback", """{"from":"hevc","to":"avc"}""")
                AppLogger.w(TAG, "HEVC failed, falling back to H.264: ${err.message}", err)
            }
        }

        runCatching {
            val bytes = runOnce(context, actualParams, useHevc = false, expectedDurationMs)
            if (isAcceptableSize(bytes, inputSize)) {
                return finalizeOutput(context, actualParams, maybeSecondPass(context, actualParams, bytes, usedHevc = false, expectedDurationMs))
            }
            AppLogger.w(TAG, "H.264 output too large ($bytes vs input $inputSize), trying MediaCodec Plan B")
            lastError = IllegalStateException("output too large")
        }.onFailure { err ->
            lastError = err
            AppLogger.w(TAG, "Media3 failed, trying MediaCodec Plan B: ${err.message}", err)
        }



        val reducedBitrate = (actualParams.recipe.videoBitrateBps.toLong() * 3 / 4)

            .coerceAtLeast(500_000)

            .toULong()

        val reducedRecipe = actualParams.recipe.copy(videoBitrateBps = reducedBitrate)

        val planParams = actualParams.copy(recipe = reducedRecipe)

        File(planParams.outputPath).delete()



        val scaledSource = VideoScalePrePass.tryScale(

            context = context,

            sourceUri = planParams.sourceUri,

            recipe = planParams.recipe,

            workDir = File(planParams.outputPath).parentFile,

            jobId = planParams.jobId,

            onProgress = { p -> planParams.onProgress(0.05f + p * 0.15f) },

            isCancelled = planParams.isCancelled,

        )

        try {

            val bytes = MediaCodecPlanB.transcode(

                context = context,

                sourceUri = scaledSource ?: planParams.sourceUri,

                outputPath = planParams.outputPath,

                recipe = planParams.recipe,

                useHevc = false,

                onProgress = planParams.onProgress,

                isCancelled = planParams.isCancelled,

                onTelemetry = planParams.onTelemetry,

                jobId = planParams.jobId,

            )

            validateOutput(planParams.outputPath, expectedDurationMs)

            return finalizeOutput(context, actualParams, maybeSecondPass(context, actualParams, bytes, usedHevc = false, expectedDurationMs))

        } finally {

            if (scaledSource != null && scaledSource != planParams.sourceUri) {

                File(scaledSource).delete()

            }

        }

    }



    private fun maybeSecondPass(
        context: Context,
        params: TranscodeParams,
        bytes: Long,
        usedHevc: Boolean,
        expectedDurationMs: Long,
    ): Long {
        val target = params.recipe.targetBytes?.toLong() ?: return bytes
        val audioBytes = probeOutputAudioBytes(params.outputPath)
        val nextBps = uniffi.ss_core.secondPassVideoBitrate(
            params.recipe.videoBitrateBps,
            bytes.toULong(),
            target.toULong(),
            audioBytes.toULong(),
            expectedDurationMs.toULong(),
        ) ?: return bytes

        val output = File(params.outputPath)
        val backup = File(params.outputPath + ".pass1.bak")
        if (!output.renameTo(backup)) {
            output.copyTo(backup, overwrite = true)
        }

        val retry = params.copy(recipe = params.recipe.copy(videoBitrateBps = nextBps, targetBytes = null))
        return runCatching {
            val second = runOnce(context, retry, useHevc = usedHevc, expectedDurationMs)
            backup.delete()
            second
        }.getOrElse {
            output.delete()
            backup.renameTo(output)
            bytes
        }
    }

    private fun probeSourceTimestamps(context: Context, uriString: String): Pair<Long?, Long?> {
        val rawUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return Pair(null, null)
        val resolved = com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(
            context,
            uriString,
            uniffi.ss_core.MediaKind.VIDEO,
        ) ?: rawUri

        var dateTaken: Long? = null
        var dateModified: Long? = null

        // 1. Try MediaStore columns from resolved URI
        if (resolved.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    resolved,
                    arrayOf(
                        MediaStore.Video.Media.DATE_TAKEN,
                        MediaStore.Video.Media.DATE_MODIFIED,
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dtIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                        if (dtIdx >= 0) {
                            val v = cursor.getLong(dtIdx)
                            if (v > 0) dateTaken = v
                        }
                        val dmIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                        if (dmIdx >= 0) {
                            val v = cursor.getLong(dmIdx)
                            if (v > 0) dateModified = v
                        }
                    }
                }
            }
        } else if (resolved.scheme == "file") {
            val f = resolved.path?.let { File(it) }
            if (f != null && f.exists()) {
                val lm = f.lastModified()
                if (lm > 0) {
                    dateTaken = lm
                    dateModified = lm / 1000
                }
            }
        }

        // 2. Fallback: extract METADATA_KEY_DATE via MediaMetadataRetriever
        if (dateTaken == null || dateTaken <= 0L) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    if (uriString.startsWith("/") || uriString.contains(":\\")) {
                        retriever.setDataSource(uriString)
                    } else {
                        context.contentResolver.openFileDescriptor(rawUri, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                        } ?: retriever.setDataSource(context, rawUri)
                    }
                    val dateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)
                    if (!dateStr.isNullOrBlank()) {
                        val parsed = parseMetadataDate(dateStr)
                        if (parsed != null && parsed > 0L) {
                            dateTaken = parsed
                            if (dateModified == null) dateModified = parsed / 1000
                        }
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }

        // 3. Fallback: parse date from filename (e.g. VID20260906172744.mp4 or VID_20260906_172744.mp4)
        if (dateTaken == null || dateTaken <= 0L) {
            val name = com.spaceslimmer.core.GalleryWriter.queryDisplayName(context, resolved)
                ?: com.spaceslimmer.core.GalleryWriter.queryDisplayName(context, rawUri)
                ?: uriString.substringAfterLast('/')
            val fromName = parseDateFromFilename(name)
            if (fromName != null && fromName > 0L) {
                dateTaken = fromName
                if (dateModified == null) dateModified = fromName / 1000
            }
        }

        return Pair(dateTaken, dateModified)
    }

    private fun parseMetadataDate(dateStr: String): Long? {
        val formats = listOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyyMMdd HHmmss",
        )
        for (fmt in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(dateStr)
                if (d != null && d.time > 0L) return d.time
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun parseDateFromFilename(name: String): Long? {
        val regex = Regex(".*?(20\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2}).*")
        val m = regex.matchEntire(name) ?: return null
        return try {
            val y = m.groupValues[1]
            val mo = m.groupValues[2]
            val d = m.groupValues[3]
            val h = m.groupValues[4]
            val mi = m.groupValues[5]
            val s = m.groupValues[6]
            val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault())
            sdf.parse("$y$mo$d$h$mi$s")?.time
        } catch (_: Throwable) { null }
    }

    private fun probeSourceRotation(context: Context, uriString: String): ByteArray? {
        val retriever = android.media.MediaMetadataRetriever()
        val degrees = runCatching {
            if (uriString.startsWith("/") || uriString.contains(":\\")) {
                retriever.setDataSource(uriString)
            } else {
                val uri = Uri.parse(uriString)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                } ?: retriever.setDataSource(context, uri)
            }
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        }.getOrDefault(0)
        runCatching { retriever.release() }

        if (degrees != 0) {
            return Mp4MetadataWriter.matrixForDegrees(degrees)
        }
        return null
    }

    private fun finalizeOutput(context: Context, params: TranscodeParams, bytes: Long): Long {
        runCatching {
            val (srcDateTaken, srcDateModified) = probeSourceTimestamps(context, params.sourceUri)
            val targetSecs = srcDateTaken?.let { it / 1000 }
                ?: srcDateModified
                ?: (System.currentTimeMillis() / 1000)
            val rotMatrix = probeSourceRotation(context, params.sourceUri)

            Mp4MetadataWriter.patchMetadata(params.outputPath, targetSecs, rotMatrix)
        }
        return warnIfFlat(params, bytes)
    }

    private fun warnIfFlat(params: TranscodeParams, bytes: Long): Long {
        if (FlatFrameDetector.looksFlat(params.outputPath)) {
            params.onTelemetry("quality.flat_frame", "{}")
        }
        return bytes
    }

    private fun isAcceptableSize(outputBytes: Long, inputBytes: Long): Boolean {

        if (inputBytes <= 0) return true

        return outputBytes < inputBytes

    }



    @Throws(Exception::class)

    private fun runOnce(

        context: Context,

        params: TranscodeParams,

        useHevc: Boolean,

        sourceDurationMs: Long,

    ): Long {

        if (params.isCancelled()) throw CancelledException()

        File(params.outputPath).parentFile?.mkdirs()

        val uri = Uri.parse(params.sourceUri)

        val (srcW, srcH) = probeVideoSize(context, params.sourceUri)

        val srcFps = probeVideoFps(context, params.sourceUri)

        val scale = computeScale(srcW, srcH, params.recipe.outW.toInt(), params.recipe.outH.toInt())

        val videoEffects = if (scale < 0.999f) {

            listOf(

                ScaleAndRotateTransformation.Builder()

                    .setScale(scale, scale)

                    .build(),

            )

        } else {

            emptyList()

        }

        val effects = Effects(emptyList(), videoEffects)

        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        val trimStart = params.recipe.trimStartMs?.toLong()

        val trimEnd = params.recipe.trimEndMs?.toLong()

        if (trimStart != null || trimEnd != null) {

            val clip = MediaItem.ClippingConfiguration.Builder()

            if (trimStart != null && trimStart > 0) {

                clip.setStartPositionMs(trimStart)

            }

            if (trimEnd != null && trimEnd > 0) {

                clip.setEndPositionMs(trimEnd)

            }

            mediaItemBuilder.setClippingConfiguration(clip.build())

        }

        val editedBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())

            .setEffects(effects)

        params.recipe.fps?.toInt()?.takeIf { it > 0 && srcFps > it }?.let { targetFps ->

            editedBuilder.setFrameRate(targetFps)

        }

        val edited = editedBuilder.build()

        val videoMime = when {
            isAv1Codec(params.recipe.videoCodec) -> MimeTypes.VIDEO_AV1
            useHevc -> MimeTypes.VIDEO_H265
            else -> MimeTypes.VIDEO_H264
        }

        val videoEncoderSettings = VideoEncoderSettings.Builder()

            .setBitrate(params.recipe.videoBitrateBps.toInt())

            .build()

        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)

            .setRequestedVideoEncoderSettings(videoEncoderSettings)

        if (!params.recipe.audioCopy) {

            encoderFactoryBuilder.setRequestedAudioEncoderSettings(

                AudioEncoderSettings.Builder()

                    .setBitrate(params.recipe.audioBitrateBps.toInt())

                    .build(),

            )

        }

        val encoderFactory = encoderFactoryBuilder.build()

        val transformerBuilder = Transformer.Builder(context)

            .setVideoMimeType(videoMime)

            .setEncoderFactory(encoderFactory)

        if (!params.recipe.audioCopy) {

            transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)

        }

        val transformer = transformerBuilder.build()

        val registryToken = VideoTranscodeRegistry.register(params.jobId, transformer)

        val error = AtomicReference<Throwable?>(null)

        val cancelled = AtomicBoolean(false)

        val latch = CountDownLatch(1)

        transformer.addListener(

            object : Transformer.Listener {

                override fun onCompleted(composition: Composition, exportResult: ExportResult) {

                    params.onProgress(1f)

                    latch.countDown()

                }



                override fun onError(

                    composition: Composition,

                    exportResult: ExportResult,

                    exception: ExportException,

                ) {

                    error.set(exception)

                    latch.countDown()

                }

            },

        )

        params.onProgress(0.05f)

        transformer.start(edited, params.outputPath)

        val progressHolder = ProgressHolder()
        var lastReportedRatio = 0.05f
        var lastLoggedRatio = 0.05f
        val outFile = File(params.outputPath)
        val poll = Thread {
            try {
                while (!latch.await(200, TimeUnit.MILLISECONDS)) {
                    if (params.isCancelled()) {
                        cancelled.set(true)
                        transformer.cancel()
                        latch.countDown()
                        break
                    }
                    var reported = false
                    runCatching {
                        val pState = transformer.getProgress(progressHolder)
                        if (pState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val ratio = (progressHolder.progress / 100f).coerceIn(0.05f, 0.99f)
                            if (ratio - lastReportedRatio >= 0.01f || ratio >= 0.99f) {
                                lastReportedRatio = ratio
                                params.onProgress(ratio)
                            }
                            if (ratio - lastLoggedRatio >= 0.10f) {
                                lastLoggedRatio = ratio
                                AppLogger.d(TAG, "job ${params.jobId} Media3 progress: ${(ratio * 100).toInt()}%")
                            }
                            reported = true
                        }
                    }
                    if (!reported && outFile.exists() && outFile.length() > 0L && sourceDurationMs > 0L) {
                        val expectedBytes = (params.recipe.videoBitrateBps.toDouble() / 8.0) * (sourceDurationMs / 1000.0)
                        if (expectedBytes > 0) {
                            val fRatio = (outFile.length().toDouble() / expectedBytes).toFloat().coerceIn(0.05f, 0.95f)
                            if (fRatio - lastReportedRatio >= 0.02f) {
                                lastReportedRatio = fRatio
                                params.onProgress(fRatio)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.w(TAG, "poll loop error for job ${params.jobId}: ${t.message}")
            }
        }

        poll.start()

        val timeoutMs = encodeTimeoutMs(sourceDurationMs)
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            cancelled.set(true)
            transformer.cancel()
            poll.interrupt()
            VideoTranscodeRegistry.unregister(params.jobId, registryToken)
            throw IllegalStateException("fail.timeout")
        }

        poll.interrupt()

        VideoTranscodeRegistry.unregister(params.jobId, registryToken)

        if (cancelled.get() || params.isCancelled()) throw CancelledException()

        error.get()?.let { throw it }

        validateOutput(params.outputPath, sourceDurationMs)

        params.onProgress(1f)

        return File(params.outputPath).length()

    }



    private fun probeInputFileSize(context: Context, sourceUri: String): Long {

        if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) {

            return File(sourceUri).length()

        }

        return runCatching {

            context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->

                pfd.statSize

            } ?: 0L

        }.getOrDefault(0L)

    }



    private fun probeDurationMs(context: Context, sourceUri: String): Long {

        val extractor = MediaExtractor()

        try {

            if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) {

                extractor.setDataSource(sourceUri)

            } else {

                context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->

                    extractor.setDataSource(pfd.fileDescriptor)

                } ?: return 0L

            }

            for (i in 0 until extractor.trackCount) {

                val format = extractor.getTrackFormat(i)

                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_DURATION)) {

                    return format.getLong(MediaFormat.KEY_DURATION) / 1000

                }

            }

        } finally {

            extractor.release()

        }

        return 0L

    }



    private fun probeVideoSize(context: Context, sourceUri: String): Pair<Int, Int> {

        val extractor = MediaExtractor()

        try {

            if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) {

                extractor.setDataSource(sourceUri)

            } else {

                context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->

                    extractor.setDataSource(pfd.fileDescriptor)

                } ?: return 0 to 0

            }

            for (i in 0 until extractor.trackCount) {

                val format = extractor.getTrackFormat(i)

                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {

                    return format.getInteger(MediaFormat.KEY_WIDTH) to format.getInteger(MediaFormat.KEY_HEIGHT)

                }

            }

        } finally {

            extractor.release()

        }

        return 0 to 0

    }



    private fun probeVideoFps(context: Context, sourceUri: String): Float {

        val extractor = MediaExtractor()

        try {

            if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) {

                extractor.setDataSource(sourceUri)

            } else {

                context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->

                    extractor.setDataSource(pfd.fileDescriptor)

                } ?: return 30f

            }

            for (i in 0 until extractor.trackCount) {

                val format = extractor.getTrackFormat(i)

                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {

                    return format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()

                }

            }

        } finally {

            extractor.release()

        }

        return 30f

    }



    private fun validateOutput(outputPath: String, sourceDurationMs: Long) {

        val file = File(outputPath)

        if (!file.isFile || file.length() == 0L) {

            throw IllegalStateException("fail.invalid_output")

        }

        val extractor = MediaExtractor()

        try {

            extractor.setDataSource(outputPath)

            var hasVideo = false

            var outDurationMs = 0L

            for (i in 0 until extractor.trackCount) {

                val format = extractor.getTrackFormat(i)

                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {

                    hasVideo = true

                    if (format.containsKey(MediaFormat.KEY_DURATION)) {

                        outDurationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000

                    }

                }

            }

            if (!hasVideo) throw IllegalStateException("fail.invalid_output")

            if (sourceDurationMs > 0 && outDurationMs > 0) {
                val diffMs = kotlin.math.abs(outDurationMs - sourceDurationMs)
                val thresholdMs = maxOf(
                    DURATION_ABS_TOLERANCE_MS,
                    (sourceDurationMs * DURATION_RATIO_TOLERANCE).toLong(),
                )
                if (diffMs > thresholdMs) {
                    throw IllegalStateException("fail.invalid_output")
                }
            }

        } finally {

            extractor.release()

        }

    }



    private fun computeScale(srcW: Int, srcH: Int, outW: Int, outH: Int): Float {

        if (srcW <= 0 || srcH <= 0 || outW <= 0 || outH <= 0) return 1f

        return min(min(outW.toFloat() / srcW, outH.toFloat() / srcH), 1f)

    }

    private fun effectiveDurationMs(sourceDurationMs: Long, recipe: Recipe): Long {
        val trimStart = recipe.trimStartMs?.toLong() ?: 0L
        val trimEnd = recipe.trimEndMs?.toLong()?.takeIf { it > 0 } ?: sourceDurationMs
        return (trimEnd - trimStart).coerceAtLeast(0L)
    }

    private fun encodeTimeoutMs(mediaDurationMs: Long): Long =
        maxOf(MIN_WALL_TIMEOUT_MS, mediaDurationMs * 3)

    private fun probeOutputAudioBytes(outputPath: String): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(outputPath)
            var audioBytes = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    0L
                }
                val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                } else {
                    0L
                }
                if (durationUs > 0 && bitrate > 0) {
                    audioBytes += (bitrate * durationUs / 8_000_000L)
                }
            }
            audioBytes
        } catch (_: Exception) {
            0L
        } finally {
            extractor.release()
        }
    }

    class CancelledException : Exception("cancelled")

}


