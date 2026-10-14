package com.spaceslimmer.app.prefs

import android.content.Context
import com.spaceslimmer.app.ui.theme.ThemeMode

data class RadarCacheSummary(
    val itemCount: Int,
    val estimateLow: Long,
    val estimateHigh: Long,
    val scannedAtMs: Long,
    val scannedEntries: Int = 0,
    val libraryBytes: Long = 0L,
    val elapsedMs: Long = 0L,
)

object AppPrefs {
    private const val PREFS = "spaceslimmer_app"
    private const val KEY_FIRST_RUN = "first_run_complete"
    private const val KEY_FORCE_SAVE_AS_NEW = "force_save_as_new"
    private const val KEY_LOW_BATTERY_PAUSED_GROUPS = "low_battery_paused_group_ids"
    private const val KEY_RADAR_CACHE_COUNT = "radar_cache_count"
    private const val KEY_RADAR_CACHE_LOW = "radar_cache_low"
    private const val KEY_RADAR_CACHE_HIGH = "radar_cache_high"
    private const val KEY_RADAR_CACHE_TIME = "radar_cache_time"
    private const val KEY_RADAR_CACHE_SCANNED = "radar_cache_scanned"
    private const val KEY_RADAR_CACHE_LIBRARY = "radar_cache_library"
    private const val KEY_RADAR_CACHE_ELAPSED = "radar_cache_elapsed"
    private const val KEY_WEEK_START = "week_start_ms"
    private const val KEY_WEEK_SAVED = "week_saved_bytes"
    private const val KEY_COUNTED_GROUPS = "counted_group_ids"
    private const val KEY_BATCH_RENAME = "export_batch_rename"
    private const val KEY_ARCHIVE_BY_DATE = "export_archive_by_date"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromPref(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, ThemeMode.System.toPref())
                ?: ThemeMode.System.toPref(),
        )

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.toPref())
            .apply()
    }

    fun isFirstRun(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_RUN, false)

    fun completeFirstRun(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_RUN, true)
            .apply()
    }

    fun shouldForceSaveAsNew(context: Context): Boolean = false

    fun markFirstCompressStarted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FORCE_SAVE_AS_NEW, false)
            .apply()
    }

    fun getLowBatteryPausedGroupIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_LOW_BATTERY_PAUSED_GROUPS, emptySet())
            ?: emptySet()

    fun setLowBatteryPausedGroupIds(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOW_BATTERY_PAUSED_GROUPS, ids)
            .apply()
    }

    fun clearLowBatteryPausedGroupIds(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOW_BATTERY_PAUSED_GROUPS)
            .apply()
    }

    fun saveRadarCache(
        context: Context,
        itemCount: Int,
        estimateLow: Long,
        estimateHigh: Long,
        scannedEntries: Int = 0,
        libraryBytes: Long = 0L,
        elapsedMs: Long = 0L,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RADAR_CACHE_COUNT, itemCount)
            .putLong(KEY_RADAR_CACHE_LOW, estimateLow)
            .putLong(KEY_RADAR_CACHE_HIGH, estimateHigh)
            .putLong(KEY_RADAR_CACHE_TIME, System.currentTimeMillis())
            .putInt(KEY_RADAR_CACHE_SCANNED, scannedEntries)
            .putLong(KEY_RADAR_CACHE_LIBRARY, libraryBytes)
            .putLong(KEY_RADAR_CACHE_ELAPSED, elapsedMs)
            .apply()
    }

    fun loadRadarCache(context: Context): RadarCacheSummary? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_RADAR_CACHE_COUNT)) return null
        return RadarCacheSummary(
            itemCount = prefs.getInt(KEY_RADAR_CACHE_COUNT, 0),
            estimateLow = prefs.getLong(KEY_RADAR_CACHE_LOW, 0L),
            estimateHigh = prefs.getLong(KEY_RADAR_CACHE_HIGH, 0L),
            scannedAtMs = prefs.getLong(KEY_RADAR_CACHE_TIME, 0L),
            scannedEntries = prefs.getInt(KEY_RADAR_CACHE_SCANNED, 0),
            libraryBytes = prefs.getLong(KEY_RADAR_CACHE_LIBRARY, 0L),
            elapsedMs = prefs.getLong(KEY_RADAR_CACHE_ELAPSED, 0L),
        )
    }

    fun weeklySavedBytes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_WEEK_START, 0L)
        val now = System.currentTimeMillis()
        if (start == 0L || now - start > WEEK_MS) {
            prefs.edit()
                .putLong(KEY_WEEK_START, now)
                .putLong(KEY_WEEK_SAVED, 0L)
                .putStringSet(KEY_COUNTED_GROUPS, emptySet())
                .apply()
            return 0L
        }
        return prefs.getLong(KEY_WEEK_SAVED, 0L)
    }

    fun batchRenameEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BATCH_RENAME, true)

    fun setBatchRenameEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BATCH_RENAME, enabled)
            .apply()
    }

    fun archiveByDateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ARCHIVE_BY_DATE, true)

    fun setArchiveByDateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARCHIVE_BY_DATE, enabled)
            .apply()
    }

    fun recordFinishedGroup(context: Context, groupId: String, savedBytes: Long) {
        if (groupId.isBlank() || savedBytes <= 0L) return
        weeklySavedBytes(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val counted = prefs.getStringSet(KEY_COUNTED_GROUPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!counted.add(groupId)) return
        val next = prefs.getLong(KEY_WEEK_SAVED, 0L) + savedBytes
        prefs.edit()
            .putStringSet(KEY_COUNTED_GROUPS, counted)
            .putLong(KEY_WEEK_SAVED, next)
            .apply()
    }
}
