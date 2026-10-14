package com.spaceslimmer.videohw

import android.content.Context
import android.os.Build

/**
 * Tracks HEVC encode failures per device model in SharedPreferences.
 * Skip HEVC attempts after [FAILURE_THRESHOLD] failures on the same model.
 */
object HevcBlacklist {
    private const val PREFS = "hevc_blacklist"
    private const val FAILURE_THRESHOLD = 2

    fun shouldSkipHevc(context: Context): Boolean {
        val count = prefs(context).getInt(failureKey(), 0)
        return count >= FAILURE_THRESHOLD
    }

    fun recordFailure(context: Context) {
        val p = prefs(context)
        val key = failureKey()
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun failureKey(): String = "fail_${Build.MODEL}"
}
