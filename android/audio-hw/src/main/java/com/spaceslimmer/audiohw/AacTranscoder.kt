package com.spaceslimmer.audiohw

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Decode source audio and re-encode to AAC in M4A via MediaCodec.
 * Optimized with non-blocking pipeline pump for high-throughput encoding.
 */
object AacTranscoder {
    private const val MAX_DURATION_MS = 30L * 60 * 1000

    fun transcode(
        inputPath: String,
        outputPath: String,
        bitrateBps: Int,
        targetChannels: Int = 2,
        trimStartMs: Long? = null,
        trimEndMs: Long? = null,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Long {
        val startedAt = System.currentTimeMillis()
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)
        var audioTrack = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrack = i
                inputFormat = format
                break
            }
        }
        if (audioTrack < 0 || inputFormat == null) {
            extractor.release()
            error("no audio track")
        }
        extractor.selectTrack(audioTrack)
        val trimStartUs = (trimStartMs ?: 0L).coerceAtLeast(0L) * 1000L
        val trimEndUs = trimEndMs?.takeIf { it > 0 }?.times(1000)
        if (trimStartUs > 0) {
            extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
        val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: error("missing input mime")

        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val mediaDurationMs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000
        } else {
            0L
        }
        val totalDurationUs = if (trimEndUs != null && trimEndUs > trimStartUs) {
            trimEndUs - trimStartUs
        } else if (mediaDurationMs > 0) {
            (mediaDurationMs * 1000L - trimStartUs).coerceAtLeast(1_000_000L)
        } else {
            1_000_000L
        }
        val maxWallMs = maxOf(MAX_DURATION_MS, mediaDurationMs * 3)
        val inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val outChannels = targetChannels.coerceIn(1, inputChannels)
        val outFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            outChannels,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        val decoder = MediaCodec.createDecoderByType(inputMime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        File(outputPath).parentFile?.mkdirs()
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false

        val decodeInfo = MediaCodec.BufferInfo()
        val encodeInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderEos = false
        var encoderEosSignaled = false
        var encoderEos = false

        fun drainEncoderOutput(): Boolean {
            var wroteAny = false
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(encodeInfo, 0L)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        wroteAny = true
                    }
                    outIndex >= 0 -> {
                        if (!muxerStarted) {
                            muxerTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        val encoded = encoder.getOutputBuffer(outIndex)
                        if (encoded != null && encodeInfo.size > 0 && muxerTrack >= 0) {
                            encoded.position(encodeInfo.offset)
                            encoded.limit(encodeInfo.offset + encodeInfo.size)
                            muxer.writeSampleData(muxerTrack, encoded, encodeInfo)
                            wroteAny = true
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (encodeInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderEos = true
                            return true
                        }
                    }
                }
            }
            return wroteAny
        }

        fun feedEncoderFromPcm(decodeOutIndex: Int, info: MediaCodec.BufferInfo) {
            val pcm = decoder.getOutputBuffer(decodeOutIndex) ?: return
            var offset = info.offset
            var remaining = info.size
            val ptsBase = (info.presentationTimeUs - trimStartUs).coerceAtLeast(0L)
            val mono = outChannels == 1 && inputChannels > 1
            var writtenFrames = 0L

            while (remaining > 0) {
                var inIndex = encoder.dequeueInputBuffer(0L)
                if (inIndex < 0) {
                    drainEncoderOutput()
                    inIndex = encoder.dequeueInputBuffer(1000L)
                    if (inIndex < 0) {
                        drainEncoderOutput()
                        continue
                    }
                }
                val inBuf = encoder.getInputBuffer(inIndex) ?: break
                inBuf.clear()
                val capacity = inBuf.remaining()
                if (mono) {
                    val maxFrames = remaining / (2 * inputChannels)
                    val framesToWrite = minOf(maxFrames, capacity / 2)
                    if (framesToWrite <= 0) break
                    val bytesToWrite = framesToWrite * 2
                    downmixToMono(pcm, offset, bytesToWrite, inputChannels, inBuf)
                    val framePts = ptsBase + (writtenFrames * 1_000_000L) / sampleRate
                    encoder.queueInputBuffer(inIndex, 0, bytesToWrite, framePts, 0)
                    offset += framesToWrite * 2 * inputChannels
                    remaining -= framesToWrite * 2 * inputChannels
                    writtenFrames += framesToWrite
                } else {
                    val frameBytes = 2 * outChannels
                    val maxFrames = remaining / frameBytes
                    val framesToWrite = minOf(maxFrames, capacity / frameBytes)
                    if (framesToWrite <= 0) break
                    val toCopy = framesToWrite * frameBytes
                    pcm.position(offset)
                    pcm.limit(offset + toCopy)
                    inBuf.put(pcm)
                    val framePts = ptsBase + (writtenFrames * 1_000_000L) / sampleRate
                    encoder.queueInputBuffer(inIndex, 0, toCopy, framePts, 0)
                    offset += toCopy
                    remaining -= toCopy
                    writtenFrames += framesToWrite
                }
            }
        }

        fun signalEncoderEos() {
            while (!encoderEosSignaled) {
                var inIndex = encoder.dequeueInputBuffer(0L)
                if (inIndex < 0) {
                    drainEncoderOutput()
                    inIndex = encoder.dequeueInputBuffer(1000L)
                }
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(
                        inIndex, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    encoderEosSignaled = true
                    break
                }
            }
        }

        var lastProgressReportMs = 0L
        while (!encoderEos) {
            if (isCancelled()) {
                cleanup(decoder, encoder, extractor, muxer, muxerStarted)
                error("cancelled")
            }
            if (System.currentTimeMillis() - startedAt > maxWallMs) {
                cleanup(decoder, encoder, extractor, muxer, muxerStarted)
                error("fail.timeout")
            }

            var didWork = false

            // 1. Feed extractor into decoder
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(0L)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    if (buffer != null) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            if (sampleTime < trimStartUs) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, 0)
                                extractor.advance()
                            } else if (trimEndUs != null && sampleTime > trimEndUs) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIndex, 0, sampleSize,
                                    sampleTime, 0,
                                )
                                extractor.advance()
                                if (totalDurationUs > 0) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressReportMs > 150) {
                                        lastProgressReportMs = now
                                        val progress = ((sampleTime - trimStartUs).toFloat() / totalDurationUs).coerceIn(0f, 0.99f)
                                        onProgress(progress)
                                    }
                                }
                            }
                        }
                        didWork = true
                    }
                }
            }

            // 2. Drain decoder into encoder
            while (!decoderEos) {
                val decodeOut = decoder.dequeueOutputBuffer(decodeInfo, 0L)
                if (decodeOut < 0) break
                didWork = true
                if (decodeInfo.size > 0) {
                    feedEncoderFromPcm(decodeOut, decodeInfo)
                }
                decoder.releaseOutputBuffer(decodeOut, false)
                if (decodeInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    decoderEos = true
                    signalEncoderEos()
                    break
                }
            }

            // 3. Drain encoder into muxer
            if (drainEncoderOutput()) {
                didWork = true
            }

            // If nothing in the entire pipeline processed any bytes in this round, yield slightly
            if (!didWork && !encoderEos) {
                try {
                    Thread.sleep(1)
                } catch (_: InterruptedException) {
                    // ignore
                }
            }
        }

        onProgress(1.0f)

        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        extractor.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
        return File(outputPath).length()
    }

    private fun downmixToMono(
        pcm: ByteBuffer,
        offset: Int,
        outBytes: Int,
        inputChannels: Int,
        out: ByteBuffer,
    ) {
        val frames = outBytes / 2
        pcm.position(offset)
        for (i in 0 until frames) {
            var sum = 0
            for (ch in 0 until inputChannels) {
                sum += pcm.short.toInt()
            }
            val avg = (sum / inputChannels).toShort()
            out.putShort(avg)
        }
    }

    private fun cleanup(
        decoder: MediaCodec,
        encoder: MediaCodec,
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerStarted: Boolean,
    ) {
        runCatching { decoder.stop() }
        runCatching { decoder.release() }
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { extractor.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }
}
