package com.spaceslimmer.videohw

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Robust in-place MP4 metadata patcher.
 * Handles top-level box traversal across the entire file so that `moov` boxes
 * located at the end of large/long video files (after `mdat`) are reliably found and patched.
 * Zero bytes are added or removed; stco / co64 chunk offsets remain 100% intact.
 */
object Mp4MetadataWriter {
    private const val TAG = "Mp4MetadataWriter"
    private const val MP4_EPOCH_OFFSET_SECS = 2082844800L // Difference between 1904-01-01 and 1970-01-01

    /**
     * Patches creation_time and modification_time in mvhd, tkhd, and mdhd boxes in-place.
     * ZERO bytes are added or removed; stco / co64 chunk offsets remain 100% intact.
     */
    fun patchTimestamps(outputPath: String, unixTimestampSecs: Long) {
        patchMetadata(outputPath, unixTimestampSecs = unixTimestampSecs, rotationMatrix = null)
    }

    /**
     * Patches the 36-byte transformation matrix in the video track's tkhd box.
     * ZERO bytes are added or removed; preserves exact camera orientation without transcoding artifacts.
     */
    fun patchRotationMatrix(outputPath: String, matrix: ByteArray) {
        patchMetadata(outputPath, unixTimestampSecs = null, rotationMatrix = matrix)
    }

    /**
     * Combined in-place patcher for timestamps and rotation matrix.
     * Locates the `moov` atom anywhere in the file (front or end after large `mdat`),
     * updates all relevant headers, and writes back in-place with zero offset shift.
     */
    fun patchMetadata(outputPath: String, unixTimestampSecs: Long?, rotationMatrix: ByteArray?) {
        if (unixTimestampSecs == null && rotationMatrix == null) return
        runCatching {
            val file = File(outputPath)
            if (!file.isFile || file.length() < 32) return

            RandomAccessFile(file, "rw").use { raf ->
                val (moovOffset, moovSize) = findMoovBox(raf) ?: run {
                    Log.w(TAG, "moov box not found in $outputPath")
                    return@use
                }

                // Moov boxes are metadata only (usually 30KB - 2MB). Cap at 32MB to prevent OOM
                val readSize = moovSize.toInt().coerceAtMost(32 * 1024 * 1024)
                val moovData = ByteArray(readSize)
                raf.seek(moovOffset)
                raf.readFully(moovData)

                var modified = false

                // 1. Patch timestamps in mvhd, tkhd, and mdhd
                if (unixTimestampSecs != null) {
                    val mp4Time = (unixTimestampSecs + MP4_EPOCH_OFFSET_SECS).coerceAtLeast(0L)
                    var pos = 0
                    while (pos <= moovData.size - 16) {
                        val isMatch = (moovData[pos] == 'm'.code.toByte() && moovData[pos + 1] == 'v'.code.toByte() && moovData[pos + 2] == 'h'.code.toByte() && moovData[pos + 3] == 'd'.code.toByte()) ||
                            (moovData[pos] == 't'.code.toByte() && moovData[pos + 1] == 'k'.code.toByte() && moovData[pos + 2] == 'h'.code.toByte() && moovData[pos + 3] == 'd'.code.toByte()) ||
                            (moovData[pos] == 'm'.code.toByte() && moovData[pos + 1] == 'd'.code.toByte() && moovData[pos + 2] == 'h'.code.toByte() && moovData[pos + 3] == 'd'.code.toByte())

                        if (isMatch) {
                            val version = moovData[pos + 4].toInt() and 0xFF
                            val bb = ByteBuffer.wrap(moovData)
                            if (version == 0 && pos + 16 <= moovData.size) {
                                bb.position(pos + 8)
                                bb.putInt(mp4Time.toInt())
                                bb.putInt(mp4Time.toInt())
                                modified = true
                            } else if (version == 1 && pos + 24 <= moovData.size) {
                                bb.position(pos + 8)
                                bb.putLong(mp4Time)
                                bb.putLong(mp4Time)
                                modified = true
                            }
                            pos += 16
                        } else {
                            pos++
                        }
                    }
                }

                // 2. Patch rotation matrix in tkhd
                if (rotationMatrix != null && rotationMatrix.size == 36) {
                    var pos = 0
                    while (pos <= moovData.size - 48) {
                        if (moovData[pos] == 't'.code.toByte() &&
                            moovData[pos + 1] == 'k'.code.toByte() &&
                            moovData[pos + 2] == 'h'.code.toByte() &&
                            moovData[pos + 3] == 'd'.code.toByte()
                        ) {
                            val boxStart = pos - 4
                            if (boxStart >= 0) {
                                val boxSize = ((moovData[boxStart].toInt() and 0xFF) shl 24) or
                                    ((moovData[boxStart + 1].toInt() and 0xFF) shl 16) or
                                    ((moovData[boxStart + 2].toInt() and 0xFF) shl 8) or
                                    (moovData[boxStart + 3].toInt() and 0xFF)
                                if (boxSize >= 48 && boxStart + boxSize <= moovData.size) {
                                    val matrixPos = boxStart + boxSize - 44
                                    System.arraycopy(rotationMatrix, 0, moovData, matrixPos, 36)
                                    modified = true
                                    Log.i(TAG, "successfully patched tkhd matrix inside moov at offset $matrixPos")
                                }
                            }
                            pos += 48
                        } else {
                            pos++
                        }
                    }
                }

                if (modified) {
                    raf.seek(moovOffset)
                    raf.write(moovData)
                    Log.i(TAG, "successfully wrote patched moov back to $outputPath at offset $moovOffset")
                }
            }

            if (unixTimestampSecs != null) {
                file.setLastModified(unixTimestampSecs * 1000L)
            }
        }.onFailure { Log.w(TAG, "failed to patch MP4 metadata: ${it.message}", it) }
    }

    /**
     * Traverses top-level boxes to locate the `moov` atom anywhere in the file.
     * Handles large `mdat` boxes with 64-bit extended length instantly.
     */
    private fun findMoovBox(raf: RandomAccessFile): Pair<Long, Long>? {
        val fileLength = raf.length()
        var offset = 0L
        val hdr = ByteArray(16)
        while (offset < fileLength - 8) {
            raf.seek(offset)
            val read = raf.read(hdr, 0, 16)
            if (read < 8) break
            val size32 = ((hdr[0].toLong() and 0xFF) shl 24) or
                ((hdr[1].toLong() and 0xFF) shl 16) or
                ((hdr[2].toLong() and 0xFF) shl 8) or
                (hdr[3].toLong() and 0xFF)
            val type = String(hdr, 4, 4, StandardCharsets.US_ASCII)

            var actualSize = size32
            var headerSize = 8L

            if (size32 == 1L) {
                if (read < 16) break
                actualSize = ((hdr[8].toLong() and 0xFF) shl 56) or
                    ((hdr[9].toLong() and 0xFF) shl 48) or
                    ((hdr[10].toLong() and 0xFF) shl 40) or
                    ((hdr[11].toLong() and 0xFF) shl 32) or
                    ((hdr[12].toLong() and 0xFF) shl 24) or
                    ((hdr[13].toLong() and 0xFF) shl 16) or
                    ((hdr[14].toLong() and 0xFF) shl 8) or
                    (hdr[15].toLong() and 0xFF)
                headerSize = 16L
            } else if (size32 == 0L) {
                actualSize = fileLength - offset
            }

            if (actualSize < headerSize || offset + actualSize > fileLength + 1024) break

            if (type == "moov") {
                return Pair(offset, actualSize)
            }
            offset += actualSize
        }
        return null
    }

    fun matrixForDegrees(degrees: Int): ByteArray {
        val norm = ((degrees % 360) + 360) % 360
        val out = ByteArray(36)
        val bb = ByteBuffer.wrap(out)
        when (norm) {
            90 -> {
                bb.putInt(0)
                bb.putInt(0x00010000)
                bb.putInt(0)
                bb.putInt(-0x00010000)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0x40000000)
            }
            180 -> {
                bb.putInt(-0x00010000)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(-0x00010000)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0x40000000)
            }
            270 -> {
                bb.putInt(0)
                bb.putInt(-0x00010000)
                bb.putInt(0)
                bb.putInt(0x00010000)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0x40000000)
            }
            else -> {
                bb.putInt(0x00010000)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0x00010000)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0)
                bb.putInt(0x40000000)
            }
        }
        return out
    }

    fun writeMetadata(outputPath: String) {
        // Intentionally no-op to protect MP4 container integrity
    }
}
