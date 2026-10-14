package com.spaceslimmer.videohw

import android.media.MediaCodecList
import android.media.MediaFormat
import uniffi.ss_core.HwCaps

object HwCapsProbe {
    fun probe(): HwCaps {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var hevcHw = false
        var av1Hw = false
        var hevcMaxW = 0u
        var hevcMaxH = 0u
        var av1MaxW = 0u
        var av1MaxH = 0u
        for (info in list.codecInfos) {
            if (!info.isEncoder || !info.isHardwareAccelerated) continue
            for (type in info.supportedTypes) {
                when (type.lowercase()) {
                    MediaFormat.MIMETYPE_VIDEO_HEVC -> {
                        hevcHw = true
                        val caps = info.getCapabilitiesForType(type).videoCapabilities
                        hevcMaxW = caps.supportedWidths.upper.toUInt()
                        hevcMaxH = caps.supportedHeights.upper.toUInt()
                    }
                    "video/av01", "video/av1" -> {
                        av1Hw = true
                        val caps = info.getCapabilitiesForType(type).videoCapabilities
                        av1MaxW = caps.supportedWidths.upper.toUInt()
                        av1MaxH = caps.supportedHeights.upper.toUInt()
                    }
                }
            }
        }
        if (!hevcHw) {
            hevcMaxW = 0u
            hevcMaxH = 0u
        }
        return HwCaps(
            hevcHwEncode = hevcHw,
            av1HwEncode = av1Hw,
            hevcMaxW = if (hevcHw) hevcMaxW.coerceAtMost(3840u) else 0u,
            hevcMaxH = if (hevcHw) hevcMaxH.coerceAtMost(2160u) else 0u,
            av1MaxW = if (av1Hw) av1MaxW.coerceAtMost(3840u) else 0u,
            av1MaxH = if (av1Hw) av1MaxH.coerceAtMost(2160u) else 0u,
            maxImageSide = 8192u,
        )
    }

    fun supportsDualVideoEncode(): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var hwVideoEncoders = 0
        for (info in list.codecInfos) {
            if (!info.isEncoder || !info.isHardwareAccelerated) continue
            if (info.supportedTypes.any { mime ->
                    mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                        mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
                }
            ) {
                hwVideoEncoders++
            }
        }
        return hwVideoEncoders >= 2
    }
}
