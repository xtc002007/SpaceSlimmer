package com.spaceslimmer.videohw

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.os.Build
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.spaceslimmer.audiohw.AacTranscoder
import com.spaceslimmer.core.AppLogger
import uniffi.ss_core.Recipe
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaCodec fallback: Extractor + Decoder + Surface + Encoder + Muxer.
 * Used when Media3 export fails or output is not smaller than input.
 */
object MediaCodecPlanB {
    private const val TAG = "MediaCodecPlanB"
    private const val TIMEOUT_US = 10_000L
    private const val MIN_WALL_TIMEOUT_MS = 30L * 60 * 1000

    @Throws(Exception::class)
    fun transcode(
        context: Context,
        sourceUri: String,
        outputPath: String,
        recipe: Recipe,
        useHevc: Boolean,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
        onTelemetry: (String, String) -> Unit = { _, _ -> },
        jobId: String? = null,
    ): Long {
        val cancelFlag = AtomicBoolean(false)
        val registryToken = if (!jobId.isNullOrEmpty()) {
            VideoTranscodeRegistry.register(jobId) { cancelFlag.set(true) }
        } else {
            -1L
        }
        val cancelled = { isCancelled() || cancelFlag.get() }
        val startedAt = System.currentTimeMillis()
        val inputPath = resolvePath(context, sourceUri)
        File(outputPath).parentFile?.mkdirs()

        val videoExtractor = MediaExtractor()
        setDataSource(context, videoExtractor, sourceUri, inputPath)

        var videoTrack = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                videoTrack = i
                videoFormat = format
                break
            }
        }
        if (videoTrack < 0 || videoFormat == null) {
            videoExtractor.release()
            error("no video track")
        }
        videoExtractor.selectTrack(videoTrack)
        val trimStartUs = (recipe.trimStartMs?.toLong() ?: 0L) * 1000L
        if (trimStartUs > 0) {
            videoExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }

        val sourceDurationUs = videoFormat.getLong(MediaFormat.KEY_DURATION)
        val trimEndUs = recipe.trimEndMs?.toLong()?.takeIf { it > 0 }?.times(1000)
            ?: sourceDurationUs
        val clipDurationUs = (trimEndUs - trimStartUs).coerceAtLeast(1L)
        val maxWallMs = maxOf(MIN_WALL_TIMEOUT_MS, (clipDurationUs / 1000L) * 3)

        val inputMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: error("missing video mime")
        val srcW = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val srcH = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val targetW = even(recipe.outW.toInt().coerceAtLeast(2))
        val targetH = even(recipe.outH.toInt().coerceAtLeast(2))
        val outW = minOf(targetW, even(srcW)).coerceAtLeast(2)
        val outH = minOf(targetH, even(srcH)).coerceAtLeast(2)
        val scaling = outW != even(srcW) || outH != even(srcH)
        if (scaling) {
            AppLogger.i(TAG, "Plan B scaling $srcW x $srcH -> $outW x $outH")
        }
        val frameRate = recipe.fps?.toInt()?.takeIf { it > 0 }
            ?: videoFormat.takeIf { it.containsKey(MediaFormat.KEY_FRAME_RATE) }
                ?.getInteger(MediaFormat.KEY_FRAME_RATE)
            ?: 30
        val videoMime = when {
            recipe.videoCodec.contains("av1", ignoreCase = true) ||
                recipe.videoCodec.contains("av01", ignoreCase = true) -> "video/av01"
            useHevc -> MediaFormat.MIMETYPE_VIDEO_HEVC
            else -> MediaFormat.MIMETYPE_VIDEO_AVC
        }

        val encoderFormat = MediaFormat.createVideoFormat(videoMime, outW, outH).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, recipe.videoBitrateBps.toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                )
            }
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
        }

        val encoder = MediaCodec.createEncoderByType(videoMime)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val scaler = if (scaling) PlanBEglScaler(inputSurface, outW, outH, srcW, srcH) else null
        val decodeSurface = scaler?.decoderSurface ?: inputSurface

        val decoder = MediaCodec.createDecoderByType(inputMime)
        decoder.configure(videoFormat, decodeSurface, null, 0)
        decoder.start()

        val rotationDegrees = runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            if (inputPath != null) retriever.setDataSource(inputPath)
            else retriever.setDataSource(context, Uri.parse(sourceUri))
            val r = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()
            r
        }.getOrDefault(0)

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
            if (rotationDegrees != 0) {
                setOrientationHint(rotationDegrees)
            }
        }
        var muxerVideoTrack = -1
        var muxerAudioTrack = -1
        var muxerStarted = false

        val tempFiles = mutableListOf<File>()
        val audioExtractor = prepareAudioSource(
            context = context,
            sourceUri = sourceUri,
            inputPath = inputPath,
            recipe = recipe,
            trimStartUs = trimStartUs,
            workDir = File(outputPath).parentFile ?: context.cacheDir,
            isCancelled = cancelled,
            onTelemetry = onTelemetry,
        ) { temp ->
            tempFiles.add(temp)
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var videoExtractorDone = false
        var encoderDone = false
        var pendingAudio = audioExtractor != null
        var trimStarted = trimStartUs == 0L
        var lastEncodedPtsUs = 0L

        try {
            while (!encoderDone) {
                if (cancelled()) throw VideoTranscoder.CancelledException()
                if (System.currentTimeMillis() - startedAt > maxWallMs) {
                    error("fail.timeout")
                }

                if (!videoExtractorDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex) ?: break
                        val sampleSize = videoExtractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            videoExtractorDone = true
                        } else {
                            val sampleTime = videoExtractor.sampleTime
                            if (!trimStarted) {
                                if (sampleTime < trimStartUs) {
                                    decoder.queueInputBuffer(inIndex, 0, 0, 0, 0)
                                    videoExtractor.advance()
                                    continue
                                }
                                trimStarted = true
                            }
                            if (sampleTime > trimEndUs) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                videoExtractorDone = true
                            } else {
                                val pts = sampleTime - trimStartUs
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                val decodeStatus = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    decodeStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    decodeStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    decodeStatus >= 0 -> {
                        val render = bufferInfo.size > 0
                        val ptsUs = bufferInfo.presentationTimeUs
                        decoder.releaseOutputBuffer(decodeStatus, render)
                        if (render && scaler != null) {
                            scaler.drawFrame(ptsUs)
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoder.signalEndOfInputStream()
                        }
                    }
                }

                val encodeStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    encodeStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    encodeStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            if (audioExtractor != null) {
                                muxerAudioTrack = muxer.addTrack(audioExtractor.format)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    encodeStatus >= 0 -> {
                        if (!muxerStarted) {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            if (audioExtractor != null) {
                                muxerAudioTrack = muxer.addTrack(audioExtractor.format)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                        val encoded = encoder.getOutputBuffer(encodeStatus)
                        if (encoded != null && bufferInfo.size > 0) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encoded, bufferInfo)
                            lastEncodedPtsUs = bufferInfo.presentationTimeUs
                        }
                        encoder.releaseOutputBuffer(encodeStatus, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                    }
                }

                if (muxerStarted && pendingAudio && audioExtractor != null && muxerAudioTrack >= 0) {
                    pendingAudio = !drainAudioCopy(
                        pass = audioExtractor,
                        muxer = muxer,
                        trackIndex = muxerAudioTrack,
                        info = bufferInfo,
                        trimStartUs = trimStartUs,
                        trimEndUs = trimEndUs,
                    )
                }

                val progressPts = lastEncodedPtsUs.coerceAtLeast(0L)
                onProgress((progressPts.toFloat() / clipDurationUs.toFloat()).coerceIn(0f, 0.99f))
            }

            if (muxerStarted && audioExtractor != null && muxerAudioTrack >= 0) {
                while (drainAudioCopy(
                    pass = audioExtractor,
                    muxer = muxer,
                    trackIndex = muxerAudioTrack,
                    info = bufferInfo,
                    trimStartUs = trimStartUs,
                    trimEndUs = trimEndUs,
                )) {
                    if (isCancelled()) throw VideoTranscoder.CancelledException()
                }
            }
        } finally {
            if (!jobId.isNullOrEmpty() && registryToken > 0) {
                VideoTranscodeRegistry.unregister(jobId, registryToken)
            }
            runCatching { scaler?.release() }
            runCatching { inputSurface.release() }
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor?.extractor?.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            tempFiles.forEach { runCatching { it.delete() } }
        }

        onProgress(1f)
        val outBytes = File(outputPath).length()
        AppLogger.i(TAG, "Plan B complete: $outBytes bytes")
        return outBytes
    }

    private data class AudioPass(val extractor: MediaExtractor, val format: MediaFormat)

    private fun prepareAudioSource(
        context: Context,
        sourceUri: String,
        inputPath: String?,
        recipe: Recipe,
        trimStartUs: Long,
        workDir: File,
        isCancelled: () -> Boolean,
        onTelemetry: (String, String) -> Unit,
        onTempFile: (File) -> Unit,
    ): AudioPass? {
        val sourceHasAudio = hasAudioTrack(context, sourceUri, inputPath)
        if (!sourceHasAudio) return null
        if (recipe.audioCopy) {
            return openAudioExtractor(context, sourceUri, inputPath, trimStartUs)
        }
        val (localInput, importedTemp) = resolveLocalInputWithTemp(context, sourceUri, inputPath)
        importedTemp?.let { onTempFile(it) }
        if (localInput == null) {
            onTelemetry("planb.audio_import_failed", """{"uri":"$sourceUri"}""")
            AppLogger.w(TAG, "audio import failed for $sourceUri, falling back to audio copy")
            return openAudioExtractor(context, sourceUri, inputPath, trimStartUs)
        }
        val temp = File(workDir, "planb-aac-${System.nanoTime()}.m4a")
        val channels = recipe.audioChannels?.toInt() ?: 2
        AacTranscoder.transcode(
            inputPath = localInput,
            outputPath = temp.absolutePath,
            bitrateBps = recipe.audioBitrateBps.toInt(),
            targetChannels = channels,
            trimStartMs = recipe.trimStartMs?.toLong(),
            trimEndMs = recipe.trimEndMs?.toLong(),
            isCancelled = isCancelled,
        )
        onTempFile(temp)
        return openAudioExtractor(context, temp.absolutePath, temp.absolutePath)
    }

    private fun hasAudioTrack(
        context: Context,
        sourceUri: String,
        inputPath: String?,
    ): Boolean {
        val extractor = MediaExtractor()
        return try {
            setDataSource(context, extractor, sourceUri, inputPath)
            (0 until extractor.trackCount).any { i ->
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: return@any false
                mime.startsWith("audio/")
            }
        } catch (_: Exception) {
            false
        } finally {
            extractor.release()
        }
    }

    private fun openAudioExtractor(
        context: Context,
        sourceUri: String,
        inputPath: String?,
        trimStartUs: Long = 0L,
    ): AudioPass? {
        val extractor = MediaExtractor()
        setDataSource(context, extractor, sourceUri, inputPath)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                if (trimStartUs > 0) {
                    extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                }
                return AudioPass(extractor, format)
            }
        }
        extractor.release()
        return null
    }

    private fun resolveLocalInputWithTemp(context: Context, sourceUri: String, inputPath: String?): Pair<String?, File?> {
        if (!inputPath.isNullOrEmpty()) return Pair(inputPath, null)
        if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) return Pair(sourceUri, null)
        val cache = File(context.cacheDir, "planb-import-${System.nanoTime()}")
        cache.parentFile?.mkdirs()
        return try {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            } ?: return Pair(null, null)
            Pair(cache.absolutePath, cache)
        } catch (_: Exception) {
            cache.delete()
            Pair(null, null)
        }
    }

    private fun drainAudioCopy(
        pass: AudioPass,
        muxer: MediaMuxer,
        trackIndex: Int,
        info: MediaCodec.BufferInfo,
        trimStartUs: Long,
        trimEndUs: Long,
    ): Boolean {
        val buffer = ByteBuffer.allocate(256 * 1024)
        val sampleSize = pass.extractor.readSampleData(buffer, 0)
        if (sampleSize < 0) return false
        val sampleTime = pass.extractor.sampleTime
        if (sampleTime < trimStartUs) {
            pass.extractor.advance()
            return true
        }
        if (sampleTime > trimEndUs) return false
        info.offset = 0
        info.size = sampleSize
        info.presentationTimeUs = sampleTime - trimStartUs
        info.flags = pass.extractor.sampleFlags
        buffer.position(0)
        buffer.limit(sampleSize)
        muxer.writeSampleData(trackIndex, buffer, info)
        pass.extractor.advance()
        return true
    }

    private fun setDataSource(
        context: Context,
        extractor: MediaExtractor,
        sourceUri: String,
        localPath: String?,
    ) {
        when {
            localPath != null -> extractor.setDataSource(localPath)
            sourceUri.startsWith("/") || sourceUri.contains(":\\") -> extractor.setDataSource(sourceUri)
            else -> {
                context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: error("cannot open source")
            }
        }
    }

    private fun resolvePath(context: Context, sourceUri: String): String? {
        if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) return sourceUri
        return null
    }

    private fun even(value: Int): Int = value and 1.inv()
}
