package com.spaceslimmer.app.ui.shared

import android.content.Context
import com.spaceslimmer.app.R
import java.util.Locale
import kotlin.math.abs

fun formatBytes(context: Context, bytes: Long): String {
    val value = abs(bytes).toDouble()
    return when {
        value >= 1_073_741_824.0 -> context.getString(
            R.string.format_gb,
            value / 1_073_741_824.0,
        )
        value >= 1_048_576.0 -> context.getString(
            R.string.format_mb,
            value / 1_048_576.0,
        )
        value >= 1024.0 -> context.getString(
            R.string.format_kb,
            value / 1024.0,
        )
        else -> context.getString(R.string.format_bytes, bytes)
    }
}

fun formatBytesCompact(context: Context, bytes: Long): String =
    formatBytes(context, bytes)

internal fun formatBytesValue(bytes: Long): String {
    val value = abs(bytes).toDouble()
    return when {
        value >= 1_073_741_824.0 -> String.format(Locale.getDefault(), "%.2f GB", value / 1_073_741_824.0)
        value >= 1_048_576.0 -> String.format(Locale.getDefault(), "%.1f MB", value / 1_048_576.0)
        value >= 1024.0 -> String.format(Locale.getDefault(), "%.0f KB", value / 1024.0)
        else -> "$bytes B"
    }
}
