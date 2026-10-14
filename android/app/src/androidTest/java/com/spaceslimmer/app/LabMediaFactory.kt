package com.spaceslimmer.app

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object LabMediaFactory {
    fun jpegOverSkipThreshold(dir: File): File {
        dir.mkdirs()
        val file = File(dir, "lab-jpeg.jpg")
        var quality = 95
        var width = 1280
        while (true) {
            val bitmap = Bitmap.createBitmap(width, width * 3 / 4, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(48, 96, 160))
            for (y in 0 until bitmap.height step 17) {
                for (x in 0 until bitmap.width step 19) {
                    bitmap.setPixel(x, y, Color.rgb((x + y) % 255, x % 255, y % 255))
                }
            }
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            bitmap.recycle()
            if (file.length() >= 200 * 1024 || width >= 2560) break
            width += 256
            quality = (quality - 2).coerceAtLeast(70)
        }
        return file
    }

    fun gif(dir: File): File {
        dir.mkdirs()
        val file = File(dir, "lab.gif")
        file.writeBytes(
            byteArrayOf(
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
                0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
            ),
        )
        return file
    }

    fun wavPcm(dir: File, durationMs: Int = 800): File {
        dir.mkdirs()
        val sampleRate = 16_000
        val channels = 1
        val bits = 16
        val samples = sampleRate * durationMs / 1000
        val dataBytes = samples * channels * (bits / 8)
        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeInt(v: Int) {
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
            out.write(buf.array())
        }
        fun writeShort(v: Int) {
            val buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort())
            out.write(buf.array())
        }
        writeAscii("RIFF")
        writeInt(36 + dataBytes)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeInt(16)
        writeShort(1)
        writeShort(channels)
        writeInt(sampleRate)
        writeInt(sampleRate * channels * bits / 8)
        writeShort(channels * bits / 8)
        writeShort(bits)
        writeAscii("data")
        writeInt(dataBytes)
        val pcm = ByteArray(dataBytes)
        fileBytes(pcm, samples)
        out.write(pcm)
        val file = File(dir, "lab.wav")
        file.writeBytes(out.toByteArray())
        return file
    }

    fun tryShortMp4(dir: File): File? = runCatching {
        dir.mkdirs()
        val file = File(dir, "lab-short.mp4")
        val width = 320
        val height = 240
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        val yuv = ByteArray(width * height * 3 / 2)
        var inputEos = false
        var outputEos = false
        var frame = 0
        val frames = 12
        while (!outputEos) {
            if (!inputEos) {
                val inIndex = encoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val pts = frame * 1_000_000L / 15
                    if (frame >= frames) {
                        encoder.queueInputBuffer(inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEos = true
                    } else {
                        val input = encoder.getInputBuffer(inIndex)!!
                        input.clear()
                        input.put(yuv, 0, yuv.size.coerceAtMost(input.capacity()))
                        encoder.queueInputBuffer(inIndex, 0, yuv.size.coerceAtMost(input.capacity()), pts, 0)
                        frame++
                    }
                }
            }
            val outIndex = encoder.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outIndex >= 0 -> {
                    val encoded = encoder.getOutputBuffer(outIndex)
                    if (encoded != null && info.size > 0 && muxerStarted) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer.writeSampleData(track, encoded, info)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEos = true
                    }
                }
            }
        }
        if (muxerStarted) muxer.stop()
        muxer.release()
        encoder.stop()
        encoder.release()
        check(file.isFile && file.length() > 0)
        file
    }.getOrNull()

    private fun fileBytes(pcm: ByteArray, samples: Int) {
        var i = 0
        var sample = 0
        while (sample < samples && i + 1 < pcm.size) {
            val v = (kotlin.math.sin(sample * 2 * Math.PI * 440.0 / 16_000) * 8_000).toInt().toShort()
            pcm[i++] = (v.toInt() and 0xFF).toByte()
            pcm[i++] = ((v.toInt() shr 8) and 0xFF).toByte()
            sample++
        }
    }
}
