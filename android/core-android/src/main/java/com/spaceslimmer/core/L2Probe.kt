package com.spaceslimmer.core

/**
 * L2 container-header probe (Kotlin port of ss-probe).
 * Reads JPEG SOF, PNG IHDR, and MP4 ftyp / tkhd without full decode.
 */
object L2Probe {
    data class JpegDimensions(val width: Int, val height: Int)
    data class PngDimensions(val width: Int, val height: Int)
    data class Mp4Ftyp(val majorBrand: String, val compatibleBrands: List<String>)

    fun probeJpegDimensions(bytes: ByteArray): JpegDimensions? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 4 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) break
            val marker = bytes[i + 1].toInt() and 0xFF
            if (marker == 0xDA) break
            val len = u16be(bytes, i + 2)
            if (len < 2 || i + 2 + len > bytes.size) break
            if (marker in 0xC0..0xCF && marker !in listOf(0xC4, 0xC8, 0xCC)) {
                if (len >= 7) {
                    val height = u16be(bytes, i + 5)
                    val width = u16be(bytes, i + 7)
                    return JpegDimensions(width, height)
                }
            }
            i += 2 + len
        }
        return null
    }

    fun probePngDimensions(bytes: ByteArray): PngDimensions? {
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(sig)) return null
        if (!bytes.copyOfRange(12, 16).contentEquals("IHDR".toByteArray())) return null
        val width = u32be(bytes, 16)
        val height = u32be(bytes, 20)
        return PngDimensions(width, height)
    }

    fun probeMp4Ftyp(bytes: ByteArray): Mp4Ftyp? {
        if (bytes.size < 16) return null
        var offset = 0
        while (offset + 8 <= bytes.size) {
            val size = u32be(bytes, offset)
            if (size < 8 || offset + size > bytes.size) break
            val boxType = bytes.copyOfRange(offset + 4, offset + 8)
            if (boxType.contentEquals("ftyp".toByteArray()) && size >= 16) {
                val major = bytes.copyOfRange(offset + 8, offset + 12)
                    .toString(Charsets.US_ASCII).trim()
                val compatible = mutableListOf<String>()
                var brandOff = offset + 16
                while (brandOff + 4 <= offset + size) {
                    compatible += bytes.copyOfRange(brandOff, brandOff + 4)
                        .toString(Charsets.US_ASCII).trim()
                    brandOff += 4
                }
                return Mp4Ftyp(major, compatible)
            }
            if (size == 0) break
            offset += size
        }
        return null
    }

    /** Best-effort MP4 video dimensions from the first video-track `tkhd` atom. */
    fun probeMp4VideoDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val needle = "tkhd".toByteArray()
        var idx = 0
        while (idx + 88 <= bytes.size) {
            val at = indexOf(bytes, needle, idx)
            if (at < 4) return null
            val atomStart = at - 4
            val version = bytes[at + 4].toInt()
            val dimOffset = if (version == 0) atomStart + 76 else atomStart + 88
            if (dimOffset + 8 <= bytes.size) {
                val w = u32be(bytes, dimOffset) ushr 16
                val h = u32be(bytes, dimOffset + 4) ushr 16
                if (w > 0 && h > 0) return w to h
            }
            idx = at + 4
        }
        return null
    }

    fun probeImageDimensions(bytes: ByteArray): Pair<Int, Int>? {
        probeJpegDimensions(bytes)?.let { return it.width to it.height }
        probePngDimensions(bytes)?.let { return it.width to it.height }
        return null
    }

    private fun u16be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun u32be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun indexOf(bytes: ByteArray, needle: ByteArray, from: Int): Int {
        val limit = bytes.size - needle.size
        var i = from
        while (i <= limit) {
            var matched = true
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
            i++
        }
        return -1
    }
}
