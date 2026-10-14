package com.spaceslimmer.app.export

import android.content.Context
import android.os.Build
import com.spaceslimmer.app.prefs.AppPrefs
import uniffi.ss_core.MediaKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportNaming {
    fun displayName(
        context: Context,
        original: String,
        index: Int,
        outputExt: String? = null,
    ): String {
        val stem = original.substringBeforeLast('.').ifEmpty { original }
        val ext = outputExt?.removePrefix(".")
            ?: original.substringAfterLast('.', "").ifEmpty { null }
        if (!AppPrefs.batchRenameEnabled(context)) {
            val prefixed = "SpaceSlimmer_$stem"
            return if (ext.isNullOrEmpty()) prefixed else "$prefixed.$ext"
        }
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val n = String.format(Locale.US, "%03d", index + 1)
        val renamed = "slim_${date}_${n}_$stem"
        return if (ext.isNullOrEmpty()) renamed else "$renamed.$ext"
    }

    fun relativePath(context: Context, kind: MediaKind): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val root = when (kind) {
            // ColorOS/Realme 相册主要扫 DCIM。视频不要放 Movies/，也不要按日期再套一层，
            // 否则系统相册看不到，或拆成一堆日期相册。
            MediaKind.VIDEO, MediaKind.IMAGE -> "DCIM/SpaceSlimmer"
            MediaKind.AUDIO -> "Music/SpaceSlimmer"
        }
        if (kind == MediaKind.AUDIO && AppPrefs.archiveByDateEnabled(context)) {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return "$root/$day/"
        }
        return "$root/"
    }
}
