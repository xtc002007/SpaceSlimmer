package com.spaceslimmer.core

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager

data class DeviceStorageInfo(
    val totalBytes: Long,               // User partition total (/storage/emulated/0, e.g. 165.35 GB)
    val freeBytes: Long,                // Free bytes in user partition
    val usedBytes: Long,                // Used bytes in user partition
    val physicalTotalBytes: Long = 0L,  // Phone hardware nominal flash chip capacity (e.g. 256 GB)
    val systemFirmwareBytes: Long = 0L, // System firmware, A/B slots, swap & decimal overhead
)

object StorageStatsProvider {

    /**
     * Query total, free, and used space on primary external storage.
     * Uses StatFs for robust permission-free fallback, and StorageStatsManager on API 26+ when available.
     * Computes the complete 256GB / 128GB / 512GB hardware storage breakdown.
     */
    fun queryStorage(context: Context): DeviceStorageInfo {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (storageStatsManager != null) {
                    val total = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val free = storageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                    val used = (total - free).coerceAtLeast(0L)
                    DeviceStorageInfo(totalBytes = total, freeBytes = free, usedBytes = used)
                } else {
                    queryFromStatFs()
                }
            } catch (_: Throwable) {
                queryFromStatFs()
            }
        } else {
            queryFromStatFs()
        }

        val physicalTotal = resolvePhysicalTotal(context, base.totalBytes)
        val firmwareBytes = (physicalTotal - base.totalBytes).coerceAtLeast(0L)

        return base.copy(
            physicalTotalBytes = physicalTotal,
            systemFirmwareBytes = firmwareBytes,
        )
    }

    private fun resolvePhysicalTotal(context: Context, userTotalBytes: Long): Long {
        // 1. Try StorageManager.getPrimaryStorageSize() via reflection (standard in Android 9+)
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null) {
                val method = sm.javaClass.getMethod("getPrimaryStorageSize")
                val size = method.invoke(sm) as? Long
                if (size != null && size > userTotalBytes) {
                    return size
                }
            }
        } catch (_: Throwable) {}

        if (userTotalBytes <= 0L) return 0L

        // 2. Hardware nominal capacities (decimal GB standard)
        val standardTiers = listOf(
            16L * 1000 * 1000 * 1000,
            32L * 1000 * 1000 * 1000,
            64L * 1000 * 1000 * 1000,
            128L * 1000 * 1000 * 1000,
            256L * 1000 * 1000 * 1000,
            512L * 1000 * 1000 * 1000,
            1024L * 1000 * 1000 * 1000,
        )
        for (tier in standardTiers) {
            if (userTotalBytes <= tier) {
                return tier
            }
        }
        return userTotalBytes
    }

    private fun queryFromStatFs(): DeviceStorageInfo {
        return try {
            val path = Environment.getExternalStorageDirectory().path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = (total - free).coerceAtLeast(0L)
            DeviceStorageInfo(totalBytes = total, freeBytes = free, usedBytes = used)
        } catch (_: Throwable) {
            DeviceStorageInfo(totalBytes = 0L, freeBytes = 0L, usedBytes = 0L)
        }
    }
}
