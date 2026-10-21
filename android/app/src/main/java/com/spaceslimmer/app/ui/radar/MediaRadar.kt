package com.spaceslimmer.app.ui.radar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.spaceslimmer.core.MediaProbe
import com.spaceslimmer.core.ProbedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.HwCaps
import uniffi.ss_core.JobSpec
import uniffi.ss_core.MediaFingerprint
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.QualityPreset
import uniffi.ss_core.VideoCustomParams
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class RadarItem(
    val probed: ProbedMedia,
    val estimateLow: ULong,
    val estimateHigh: ULong,
    val skip: Boolean,
    val dateAdded: Long,
) {
    val savingsBytes: Long
        get() = if (skip) {
            0L
        } else {
            saturatedSavingLong(probed.fileBytes, estimateHigh)
        }
}

internal fun saturatedSavingLong(fileBytes: Long, estimate: ULong): Long {
    if (estimate >= fileBytes.toULong()) return 0L
    return fileBytes - estimate.toLong()
}

object RadarTime {
    fun isWithinLastWeek(dateAdded: Long): Boolean {
        val nowSec = System.currentTimeMillis() / 1000L
        val tsSec = if (dateAdded > 10_000_000_000L) dateAdded / 1000L else dateAdded
        return nowSec - tsSec in 0..(7L * 24L * 3600L)
    }
}

data class RadarScanResult(
    val items: List<RadarItem>,
    val totalEstimateLow: ULong,
    val totalEstimateHigh: ULong,
    val scannedEntries: Int = 0,
    val libraryBytes: Long = 0L,
    val elapsedMs: Long = 0L,
)

data class RadarBatchOptions(
    val imageRequest: CompressRequest? = null,
    val videoRequest: CompressRequest? = null,
    val audioRequest: CompressRequest? = null,
)

enum class RadarMediaFilter { ALL, VIDEO, IMAGE, AUDIO }

object MediaRadar {
    const val BATCH_SIZE = 500
    const val MAX_TOTAL = 5000
    const val CONTRACT_MS = 8_000L
    private const val QUICK_SCAN_LIMIT = 40
    private val fingerprintCache = ConcurrentHashMap<String, RadarItem>()

    suspend fun quickScan(
        context: Context,
        engine: CoreEngine,
        caps: HwCaps,
    ): RadarScanResult = scan(
        context = context,
        engine = engine,
        caps = caps,
        limit = QUICK_SCAN_LIMIT,
        filter = RadarMediaFilter.ALL,
    )

    suspend fun scan(
        context: Context,
        engine: CoreEngine,
        caps: HwCaps,
        limit: Int = MAX_TOTAL,
        filter: RadarMediaFilter = RadarMediaFilter.ALL,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): RadarScanResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val settings = engine.settings()
        val libraryBytes = queryMediaLibraryBytes(context)
        val entries = queryLargeMedia(context, limit.coerceAtMost(MAX_TOTAL), filter)
        val probed = ArrayList<Pair<ProbedMedia, Long>>(entries.size)
        entries.forEachIndexed { index, entry ->
            yield()
            onProgress(index, entries.size)
            val cached = fingerprintCache[entry.cacheKey()]
            if (cached != null) {
                probed += cached.probed to entry.dateAdded
                return@forEachIndexed
            }
            val fromStore = probedFromStore(context, entry)
            val media = fromStore ?: MediaProbe.probeRemote(context, entry.uri, entry.kind)
            if (media != null) {
                probed += media to entry.dateAdded
            }
        }
        onProgress(entries.size, entries.size)

        val items = ArrayList<RadarItem>(probed.size)
        probed.groupBy { it.first.kind }.forEach { (kind, group) ->
            val request = buildRequest(
                engine = engine,
                kind = kind,
                preset = presetForKind(kind, settings),
                compatibility = settings.defaultCompat,
                outputPolicy = OutputPolicy.SAVE_AS_NEW,
                imageFormat = if (kind == MediaKind.IMAGE) settings.defaultImageFormat else null,
            )
            val fingerprints = group.map { MediaProbe.toFingerprint(it.first) }
            val decisions = engine.decideBatch(fingerprints, request, caps)
            group.zip(decisions).forEach { (entry, decision) ->
                val item = RadarItem(
                    probed = entry.first,
                    estimateLow = decision.estimateBytesLow,
                    estimateHigh = decision.estimateBytesHigh,
                    skip = decision.skip,
                    dateAdded = entry.second,
                )
                fingerprintCache[cacheKey(entry.first.uri, entry.first.fileBytes, entry.second)] = item
                items += item
            }
        }

        val savable = items
            .filter { !it.skip }
            .sortedByDescending { it.savingsBytes }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        RadarScanResult(
            items = savable,
            totalEstimateLow = savable.fold(0uL) { acc, item ->
                acc + saturatedSaving(item.probed.fileBytes.toULong(), item.estimateHigh)
            },
            totalEstimateHigh = savable.fold(0uL) { acc, item ->
                acc + saturatedSaving(item.probed.fileBytes.toULong(), item.estimateLow)
            },
            scannedEntries = entries.size,
            libraryBytes = libraryBytes,
            elapsedMs = elapsedMs,
        )
    }

    fun buildRequest(
        engine: CoreEngine,
        kind: MediaKind,
        preset: QualityPreset,
        compatibility: uniffi.ss_core.Compatibility,
        outputPolicy: OutputPolicy,
        imageFormat: String?,
        targetBytes: ULong? = null,
        custom: VideoCustomParams? = null,
        audioFormat: String? = null,
    ): CompressRequest {
        val settings = engine.settings()
        return CompressRequest(
            preset = preset,
            compatibility = compatibility,
            outputPolicy = outputPolicy,
            targetBytes = targetBytes,
            custom = custom,
            keepGps = settings.keepGps,
            keepHdr = false,
            skipEnabled = settings.skipEnabled,
            imageFormat = imageFormat ?: settings.defaultImageFormat,
            audioFormat = audioFormat,
        )
    }

    fun toFingerprint(probed: ProbedMedia): MediaFingerprint = MediaProbe.toFingerprint(probed)

    suspend fun queueCompress(
        context: Context,
        engine: CoreEngine,
        items: List<RadarItem>,
        options: RadarBatchOptions = RadarBatchOptions(),
    ): String = withContext(Dispatchers.IO) {
        val importDir = java.io.File(context.cacheDir, "radar-queue")
        importDir.mkdirs()
        val settings = engine.settings()
        val jobs = ArrayList<JobSpec>()
        items.forEach { item ->
            val request = when (item.probed.kind) {
                MediaKind.IMAGE -> options.imageRequest ?: buildRequest(
                    engine, MediaKind.IMAGE, settings.defaultPresetImage,
                    settings.defaultCompat, OutputPolicy.SAVE_AS_NEW, settings.defaultImageFormat,
                )
                MediaKind.VIDEO -> options.videoRequest ?: buildRequest(
                    engine, MediaKind.VIDEO, settings.defaultPresetVideo,
                    settings.defaultCompat, OutputPolicy.SAVE_AS_NEW, null,
                )
                MediaKind.AUDIO -> options.audioRequest ?: buildRequest(
                    engine, MediaKind.AUDIO, settings.defaultPresetAudio,
                    settings.defaultCompat, OutputPolicy.SAVE_AS_NEW, null,
                )
            }
            val probed = if (item.probed.localPath.isEmpty()) {
                MediaProbe.probeAndImportMedia(
                    context,
                    Uri.parse(item.probed.uri),
                    importDir,
                    item.probed.kind,
                )
            } else {
                item.probed
            }
            jobs += JobSpec(
                id = UUID.randomUUID().toString(),
                displayName = probed.displayName,
                sourceUri = probed.uri,
                localPath = probed.localPath,
                fingerprint = MediaProbe.toFingerprint(probed),
                requestOverride = request,
            )
        }
        if (jobs.isEmpty()) return@withContext ""
        val groupRequest = jobs.first().requestOverride ?: buildRequest(
            engine, MediaKind.IMAGE, settings.defaultPresetImage,
            settings.defaultCompat, OutputPolicy.SAVE_AS_NEW, settings.defaultImageFormat,
        )
        val startPaused = settings.idleChargeOnly
        val groupId = engine.submitGroup(jobs, groupRequest, startPaused)
        if (startPaused) {
            com.spaceslimmer.app.worker.IdleChargeWorker.enqueue(context, groupId)
        }
        groupId
    }

    private fun presetForKind(
        kind: MediaKind,
        settings: uniffi.ss_core.Settings,
    ): QualityPreset = when (kind) {
        MediaKind.IMAGE -> settings.defaultPresetImage
        MediaKind.VIDEO -> settings.defaultPresetVideo
        MediaKind.AUDIO -> settings.defaultPresetAudio
    }

    private data class MediaEntry(
        val uri: Uri,
        val kind: MediaKind,
        val size: Long,
        val dateAdded: Long,
        val width: Int = 0,
        val height: Int = 0,
        val durationMs: Long = 0L,
        val displayName: String = "",
        val mime: String = "",
    ) {
        fun cacheKey(): String = cacheKey(uri.toString(), size, dateAdded)
    }

    private fun cacheKey(uri: String, size: Long, dateAdded: Long): String = "$uri|$size|$dateAdded"

    private fun probedFromStore(context: Context, entry: MediaEntry): ProbedMedia? {
        if (entry.size <= 0) return null
        val needsDims = entry.kind == MediaKind.IMAGE || entry.kind == MediaKind.VIDEO
        if (needsDims && (entry.width <= 0 || entry.height <= 0)) return null
        val mime = entry.mime.ifEmpty {
            when (entry.kind) {
                MediaKind.VIDEO -> "video/mp4"
                MediaKind.AUDIO -> "audio/mpeg"
                else -> "image/jpeg"
            }
        }
        return ProbedMedia(
            uri = entry.uri.toString(),
            displayName = entry.displayName.ifEmpty { "media" },
            mime = mime,
            kind = entry.kind,
            width = entry.width.coerceAtLeast(if (entry.kind == MediaKind.AUDIO) 0 else 1),
            height = entry.height.coerceAtLeast(if (entry.kind == MediaKind.AUDIO) 0 else 1),
            durationMs = entry.durationMs,
            fps = null,
            fileBytes = entry.size,
            localPath = "",
            hasAlpha = false,
            videoCodec = null,
            audioCodec = null,
            videoBitrateBps = null,
            audioBitrateBps = null,
            channels = null,
            sampleRate = null,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = entry.kind == MediaKind.IMAGE &&
                MediaProbe.detectLiveOrMotion(context, entry.uri, mime),
        )
    }

    private fun saturatedSaving(fileBytes: ULong, estimate: ULong): ULong {
        if (estimate >= fileBytes) return 0uL
        return fileBytes - estimate
    }

    fun queryMediaLibraryBytes(context: Context): Long {
        var total = 0L
        total += sumCollectionBytes(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.SIZE)
        total += sumCollectionBytes(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.SIZE)
        total += sumCollectionBytes(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.SIZE)
        return total
    }

    private fun sumCollectionBytes(context: Context, collection: Uri, sizeColumn: String): Long {
        var sum = 0L
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(sizeColumn),
                null,
                null,
                null,
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(sizeColumn)
                while (cursor.moveToNext()) {
                    sum += cursor.getLong(sizeCol).coerceAtLeast(0)
                }
            }
        }
        return sum
    }

    private suspend fun queryLargeMedia(
        context: Context,
        limit: Int,
        filter: RadarMediaFilter,
    ): List<MediaEntry> {
        val results = mutableListOf<MediaEntry>()
        val perKindLimit = limit
        if (filter == RadarMediaFilter.ALL || filter == RadarMediaFilter.VIDEO) {
            queryCollection(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                sizeColumn = MediaStore.Video.Media.SIZE,
                dateColumn = MediaStore.Video.Media.DATE_ADDED,
                minSize = 5 * 1024 * 1024,
                kind = MediaKind.VIDEO,
                limit = perKindLimit,
                results = results,
            )
        }
        if (filter == RadarMediaFilter.ALL || filter == RadarMediaFilter.IMAGE) {
            queryCollection(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                sizeColumn = MediaStore.Images.Media.SIZE,
                dateColumn = MediaStore.Images.Media.DATE_ADDED,
                minSize = 500 * 1024,
                kind = MediaKind.IMAGE,
                limit = perKindLimit,
                results = results,
            )
        }
        if (filter == RadarMediaFilter.ALL || filter == RadarMediaFilter.AUDIO) {
            queryCollection(
                context = context,
                collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                sizeColumn = MediaStore.Audio.Media.SIZE,
                dateColumn = MediaStore.Audio.Media.DATE_ADDED,
                minSize = 500 * 1024,
                kind = MediaKind.AUDIO,
                limit = perKindLimit,
                results = results,
            )
        }
        return results
            .sortedByDescending { it.size }
            .take(limit)
    }

    private suspend fun queryCollection(
        context: Context,
        collection: Uri,
        sizeColumn: String,
        dateColumn: String,
        minSize: Int,
        kind: MediaKind,
        limit: Int,
        results: MutableList<MediaEntry>,
    ) {
        var offset = 0
        val maxFetch = limit.coerceAtMost(MAX_TOTAL)
        while (offset < maxFetch && results.size < limit) {
            val batchLimit = minOf(BATCH_SIZE, maxFetch - offset, limit - results.size)
            val batch = queryCollectionBatch(
                context = context,
                collection = collection,
                sizeColumn = sizeColumn,
                dateColumn = dateColumn,
                minSize = minSize,
                kind = kind,
                offset = offset,
                limit = batchLimit,
            )
            if (batch.isEmpty()) break
            results.addAll(batch)
            offset += batch.size
            yield()
            if (batch.size < batchLimit) break
        }
    }

    private fun queryCollectionBatch(
        context: Context,
        collection: Uri,
        sizeColumn: String,
        dateColumn: String,
        minSize: Int,
        kind: MediaKind,
        offset: Int,
        limit: Int,
    ): List<MediaEntry> {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            sizeColumn,
            dateColumn,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        when (kind) {
            MediaKind.VIDEO -> {
                projection += MediaStore.Video.Media.WIDTH
                projection += MediaStore.Video.Media.HEIGHT
                projection += MediaStore.Video.Media.DURATION
            }
            MediaKind.IMAGE -> {
                projection += MediaStore.Images.Media.WIDTH
                projection += MediaStore.Images.Media.HEIGHT
            }
            MediaKind.AUDIO -> {
                projection += MediaStore.Audio.Media.DURATION
            }
        }
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$sizeColumn > ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(minSize.toString()))
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "$sizeColumn DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        val batch = mutableListOf<MediaEntry>()
        runCatching {
            context.contentResolver.query(collection, projection.toTypedArray(), queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(sizeColumn)
            val dateCol = cursor.getColumnIndexOrThrow(dateColumn)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                batch.add(
                    MediaEntry(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        kind = kind,
                        size = cursor.getLong(sizeCol),
                        dateAdded = cursor.getLong(dateCol),
                        width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "",
                        mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else "",
                    ),
                )
            }
        }
        }
        return batch
    }
}
