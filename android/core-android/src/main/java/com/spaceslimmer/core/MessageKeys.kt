package com.spaceslimmer.core

import android.content.Context
import com.spaceslimmer.core.R

object MessageKeys {
    fun localize(context: Context, key: String): String {
        val resId = when (key) {
            "skip.already_efficient" -> R.string.skip_already_efficient
            "skip.too_small" -> R.string.skip_too_small
            "skip.not_worth" -> R.string.skip_not_worth
            "skip.would_grow" -> R.string.skip_would_grow
            "skip.hdr" -> R.string.skip_hdr
            "skip.slowmo" -> R.string.skip_slowmo
            "skip.codec" -> R.string.skip_codec
            "skip.raw" -> R.string.skip_raw
            "skip.gif" -> R.string.skip_gif
            "skip.duration_mismatch" -> R.string.skip_duration_mismatch
            "skip.user_disabled" -> R.string.skip_user_disabled
            "skip.target_unreachable" -> R.string.skip_target_unreachable
            "fail.probe" -> R.string.fail_probe
            "fail.decode" -> R.string.fail_decode
            "fail.encode" -> R.string.fail_encode
            "fail.mux" -> R.string.fail_mux
            "fail.disk_full" -> R.string.fail_disk_full
            "fail.permission" -> R.string.fail_permission
            "fail.invalid_output" -> R.string.fail_invalid_output
            "fail.cancelled", "cancelled" -> R.string.fail_cancelled
            "fail.platform" -> R.string.fail_platform
            "fail.unknown" -> R.string.fail_unknown
            else -> null
        }
        return if (resId != null) context.getString(resId) else key
    }
}
