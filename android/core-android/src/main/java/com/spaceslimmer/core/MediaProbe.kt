package com.spaceslimmer.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import uniffi.ss_core.MediaFingerprint
import uniffi.ss_core.MediaKind
import java.io.File
import java.util.UUID

data class ProbedMedia(
    val uri: String,
    val displayName: String,
    val mime: String,
    val kind: MediaKind,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fps: Float?,
    val fileBytes: Long,
    val localPath: String,
    val hasAlpha: Boolean,
    val videoCodec: String?,
    val audioCodec: String?,
    val videoBitrateBps: Long?,
    val audioBitrateBps: Long?,
    val channels: UByte?,
    val sampleRate: UInt?,
    val isHdr: Boolean,
    val isSlowmo: Boolean,
    val isLivePhoto: Boolean = false,
)

data class ProbedImage(
    val uri: String,
    val displayName: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val fileBytes: Long,
    val localPath: String,
    val hasAlpha: Boolean,
    val isLivePhoto: Boolean = false,
)

object MediaProbe {
    fun probeAndImport(context: Context, uri: Uri, importDir: File): ProbedImage {
        val media = probeAndImportMedia(context, uri, importDir, MediaKind.IMAGE)
        return ProbedImage(
            uri = media.uri,
            displayName = media.displayName,
            mime = media.mime,
            width = media.width,
            height = media.height,
            fileBytes = media.fileBytes,
            localPath = media.localPath,
            hasAlpha = media.hasAlpha,
            isLivePhoto = media.isLivePhoto,
        )
    }

    private fun probeImageFile(file: File, uri: String, displayName: String, mime: String): ProbedImage {
        val header = file.inputStream().use { input ->
            input.readNBytes(65536.coerceAtMost(file.length().toInt().coerceAtLeast(0)))
        }
        var width = 0
        var height = 0
        rustImageDimensions(header)?.let {
            width = it.first
            height = it.second
        }
        if (width <= 0 || height <= 0) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            width = options.outWidth
            height = options.outHeight
        }
        if (width <= 0 || height <= 0) {
            probeL2Dimensions(file, mime)?.let {
                width = it.first
                height = it.second
            }
        }
        return ProbedImage(
            uri = uri,
            displayName = displayName,
            mime = mime,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            fileBytes = file.length(),
            localPath = file.absolutePath,
            hasAlpha = detectAlphaFromFile(file, mime),
        )
    }

    private fun detectAlphaFromStream(
        context: Context,
        uri: Uri,
        mime: String,
    ): Boolean {
        if (!mime.contains("png", ignoreCase = true) && !mime.contains("webp", ignoreCase = true)) {
            return false
        }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = 8
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        return bitmap?.hasAlpha() == true
    }

    private fun detectAlphaFromFile(file: File, mime: String): Boolean {
        if (!mime.contains("png", ignoreCase = true) && !mime.contains("webp", ignoreCase = true)) {
            return false
        }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = 8
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        return bitmap?.hasAlpha() == true
    }

    fun probeRemote(context: Context, uri: Uri, kind: MediaKind): ProbedMedia? {
        return runCatching {
            val mime = context.contentResolver.getType(uri) ?: ""
            val displayName = queryDisplayName(context, uri) ?: "media"
            val fileBytes = querySize(context, uri)
            when (kind) {
                MediaKind.VIDEO -> openExtractor(context, uri) { extractor ->
                    probeVideoFromExtractor(extractor, uri.toString(), displayName, mime, fileBytes, "")
                }
                MediaKind.AUDIO -> openExtractor(context, uri) { extractor ->
                    probeAudioFromExtractor(extractor, uri.toString(), displayName, mime, fileBytes, "")
                }
                MediaKind.IMAGE -> {
                    val resolvedMime = mime.ifEmpty { "image/jpeg" }
                    val header = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readNBytes(65536)
                    } ?: byteArrayOf()
                    var width = 0
                    var height = 0
                    rustImageDimensions(header)?.let {
                        width = it.first
                        height = it.second
                    }
                    if (width <= 0 || height <= 0) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(header, 0, header.size, options)
                        width = options.outWidth
                        height = options.outHeight
                    }
                    if (width <= 0 || height <= 0) {
                        L2Probe.probeImageDimensions(header)?.let {
                            width = it.first
                            height = it.second
                        }
                    }
                    ProbedMedia(
                        uri = uri.toString(),
                        displayName = displayName,
                        mime = resolvedMime,
                        kind = MediaKind.IMAGE,
                        width = width.coerceAtLeast(1),
                        height = height.coerceAtLeast(1),
                        durationMs = 0,
                        fps = null,
                        fileBytes = fileBytes,
                        localPath = "",
                        hasAlpha = detectAlphaFromStream(context, uri, resolvedMime),
                        videoCodec = null,
                        audioCodec = null,
                        videoBitrateBps = null,
                        audioBitrateBps = null,
                        channels = null,
                        sampleRate = null,
                        isHdr = false,
                        isSlowmo = false,
                        isLivePhoto = detectLiveOrMotion(context, uri, resolvedMime, header),
                    )
                }
                else -> null
            }
        }.getOrNull()
    }

    private inline fun <T> openExtractor(context: Context, uri: Uri, block: (MediaExtractor) -> T): T {
        val extractor = MediaExtractor()
        val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
            extractor.setDataSource(pfd.fileDescriptor)
            pfd.close()
        } else {
            extractor.setDataSource(context, uri, null)
        }
        return try {
            block(extractor)
        } finally {
            extractor.release()
        }
    }

    private fun probeVideoFile(file: File, uri: String, displayName: String, mime: String): ProbedMedia {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        return probeVideoFromExtractor(extractor, uri, displayName, mime, file.length(), file.absolutePath).also {
            extractor.release()
        }
    }

    private fun probeVideoFromExtractor(
        extractor: MediaExtractor,
        uri: String,
        displayName: String,
        mime: String,
        fileBytes: Long,
        localPath: String,
    ): ProbedMedia {
        var width = 0
        var height = 0
        var durationMs = 0L
        var fps: Float? = null
        var videoCodec: String? = null
        var audioCodec: String? = null
        var videoBitrate: Long? = null
        var audioBitrate: Long? = null
        var isHdr = false
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (trackMime.startsWith("video/")) {
                width = format.getInteger(MediaFormat.KEY_WIDTH)
                height = format.getInteger(MediaFormat.KEY_HEIGHT)
                durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                }
                videoCodec = normalizeCodec(trackMime.substringAfter("video/"))
                isHdr = isHdrFormat(format)
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    videoBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                }
            } else if (trackMime.startsWith("audio/")) {
                audioCodec = normalizeCodec(trackMime.substringAfter("audio/"))
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                }
            }
        }
        if (videoBitrate == null && durationMs > 0) {
            val totalBitrate = (fileBytes * 8 * 1000) / durationMs
            videoBitrate = (totalBitrate - (audioBitrate ?: 0L)).coerceAtLeast(0L).takeIf { it > 0 }
        }
        val isSlowmo = (fps ?: 0f) > 60f
        if ((width <= 0 || height <= 0) && localPath.isNotEmpty()) {
            probeL2Dimensions(File(localPath), mime)?.let {
                width = it.first
                height = it.second
            }
        }
        return ProbedMedia(
            uri = uri,
            displayName = displayName,
            mime = mime,
            kind = MediaKind.VIDEO,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            durationMs = durationMs,
            fps = fps,
            fileBytes = fileBytes,
            localPath = localPath,
            hasAlpha = false,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            videoBitrateBps = videoBitrate,
            audioBitrateBps = audioBitrate,
            channels = null,
            sampleRate = null,
            isHdr = isHdr,
            isSlowmo = isSlowmo,
        )
    }

    private fun probeAudioFile(file: File, uri: String, displayName: String, mime: String): ProbedMedia {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        return probeAudioFromExtractor(extractor, uri, displayName, mime, file.length(), file.absolutePath).also {
            extractor.release()
        }
    }

    private fun probeAudioFromExtractor(
        extractor: MediaExtractor,
        uri: String,
        displayName: String,
        mime: String,
        fileBytes: Long,
        localPath: String,
    ): ProbedMedia {
        var durationMs = 0L
        var audioCodec: String? = null
        var channels: UByte? = null
        var sampleRate: UInt? = null
        var audioBitrate: Long? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (trackMime.startsWith("audio/")) {
                durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
                audioCodec = normalizeCodec(trackMime.substringAfter("audio/"))
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).toUByte()
                }
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).toUInt()
                }
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                }
                break
            }
        }
        if (audioBitrate == null && durationMs > 0) {
            audioBitrate = (fileBytes * 8 * 1000) / durationMs
        }
        return ProbedMedia(
            uri = uri,
            displayName = displayName,
            mime = mime,
            kind = MediaKind.AUDIO,
            width = 0,
            height = 0,
            durationMs = durationMs,
            fps = null,
            fileBytes = fileBytes,
            localPath = localPath,
            hasAlpha = false,
            videoCodec = null,
            audioCodec = audioCodec,
            videoBitrateBps = null,
            audioBitrateBps = audioBitrate,
            channels = channels,
            sampleRate = sampleRate,
            isHdr = false,
            isSlowmo = false,
        )
    }

    private fun normalizeCodec(codec: String): String = when (codec.lowercase()) {
        "mp4a-latm", "mp4a", "mpeg4-generic" -> "aac"
        "avc", "h264" -> "h264"
        "hevc", "h265" -> "hevc"
        "amr", "amr-nb", "amr-wb", "3gpp", "3gpp2" -> "amr"
        "vorbis", "ogg" -> "vorbis"
        "opus" -> "opus"
        else -> codec.lowercase()
    }

    private fun isHdrFormat(format: MediaFormat): Boolean {
        if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
            val std = format.getInteger(MediaFormat.KEY_COLOR_STANDARD)
            if (std == MediaFormat.COLOR_STANDARD_BT2020) return true
        }
        if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
            val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
            if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                transfer == MediaFormat.COLOR_TRANSFER_HLG
            ) {
                return true
            }
        }
        return false
    }

    private fun querySize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && index >= 0) {
                    return cursor.getLong(index)
                }
            }
        return 0L
    }

    private fun extensionForMime(mime: String, kind: MediaKind): String = when {
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("mp4") || kind == MediaKind.VIDEO -> "mp4"
        mime.contains("wav") -> "wav"
        mime.contains("flac") -> "flac"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        mime.contains("m4a") || mime.contains("aac") -> "m4a"
        mime.contains("amr") -> "amr"
        mime.contains("3gpp") -> "3gp"
        mime.contains("ogg") || mime.contains("opus") || mime.contains("vorbis") -> "ogg"
        kind == MediaKind.AUDIO -> "m4a"
        else -> "jpg"
    }

    private fun mimeFromName(displayName: String, declared: String, kind: MediaKind): String {
        val n = displayName.lowercase()
        return when {
            n.endsWith(".amr") -> "audio/amr"
            n.endsWith(".awb") -> "audio/amr-wb"
            n.endsWith(".3gp") || n.endsWith(".3gpp") -> "audio/3gpp"
            n.endsWith(".ogg") || n.endsWith(".oga") || n.endsWith(".opus") -> "audio/ogg"
            else -> declared.ifBlank {
                when (kind) {
                    MediaKind.VIDEO -> "video/mp4"
                    MediaKind.AUDIO -> "audio/mpeg"
                    else -> "image/jpeg"
                }
            }
        }
    }

    fun probeAndImportMedia(context: Context, uri: Uri, importDir: File, kind: MediaKind): ProbedMedia {
        importDir.mkdirs()
        val displayName = queryDisplayName(context, uri) ?: "media"
        val mime = mimeFromName(
            displayName,
            context.contentResolver.getType(uri) ?: when (kind) {
                MediaKind.VIDEO -> "video/mp4"
                MediaKind.AUDIO -> "audio/mpeg"
                else -> "image/jpeg"
            },
            kind,
        )
        if (kind == MediaKind.IMAGE && isHeicMime(mime)) {
            return importHeicAsJpeg(context, uri, importDir, displayName, mime)
        }
        if (kind == MediaKind.VIDEO) {
            val probed = runCatching {
                val fileBytes = querySize(context, uri)
                openExtractor(context, uri) { extractor ->
                    probeVideoFromExtractor(extractor, uri.toString(), displayName, mime, fileBytes, "")
                }
            }.getOrNull()
            if (probed != null && probed.durationMs > 0) {
                return probed
            }
        }
        val ext = extensionForMime(mime, kind)
        val localFile = File(importDir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "cannot open uri" }
            localFile.outputStream().use { output -> input.copyTo(output) }
        }
        return when (kind) {
            MediaKind.IMAGE -> {
                val img = probeImageFile(localFile, uri.toString(), displayName, mime)
                ProbedMedia(
                    uri = img.uri,
                    displayName = img.displayName,
                    mime = img.mime,
                    kind = MediaKind.IMAGE,
                    width = img.width,
                    height = img.height,
                    durationMs = 0,
                    fps = null,
                    fileBytes = img.fileBytes,
                    localPath = img.localPath,
                    hasAlpha = img.hasAlpha,
                    videoCodec = null,
                    audioCodec = null,
                    videoBitrateBps = null,
                    audioBitrateBps = null,
                    channels = null,
                    sampleRate = null,
                    isHdr = false,
                    isSlowmo = false,
                    isLivePhoto = detectLiveOrMotion(context, uri, mime),
                )
            }
            MediaKind.VIDEO -> probeVideoFile(localFile, uri.toString(), displayName, mime)
            MediaKind.AUDIO -> probeAudioFile(localFile, uri.toString(), displayName, mime)
            else -> error("unsupported kind")
        }
    }

    fun toFingerprint(probed: ProbedMedia): MediaFingerprint {
        return MediaFingerprint(
            kind = probed.kind,
            mime = probed.mime,
            container = containerFromMime(probed.mime),
            videoCodec = probed.videoCodec,
            audioCodec = probed.audioCodec,
            width = probed.width.toUInt(),
            height = probed.height.toUInt(),
            durationMs = probed.durationMs.toULong(),
            fps = probed.fps,
            rotation = 0u,
            fileBytes = probed.fileBytes.toULong(),
            videoBitrateBps = probed.videoBitrateBps?.toULong(),
            audioBitrateBps = probed.audioBitrateBps?.toULong(),
            hasAlpha = probed.hasAlpha,
            isHdr = probed.isHdr,
            isSlowmo = probed.isSlowmo,
            isLivePhoto = probed.isLivePhoto,
            channels = probed.channels,
            sampleRate = probed.sampleRate,
        )
    }

    fun toFingerprint(probed: ProbedImage): MediaFingerprint {
        return MediaFingerprint(
            kind = MediaKind.IMAGE,
            mime = probed.mime,
            container = containerFromMime(probed.mime),
            videoCodec = null,
            audioCodec = null,
            width = probed.width.toUInt(),
            height = probed.height.toUInt(),
            durationMs = 0u,
            fps = null,
            rotation = 0u,
            fileBytes = probed.fileBytes.toULong(),
            videoBitrateBps = null,
            audioBitrateBps = null,
            hasAlpha = probed.hasAlpha,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = probed.isLivePhoto,
            channels = null,
            sampleRate = null,
        )
    }

    private fun containerFromMime(mime: String): String = when {
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        else -> "jpeg"
    }

    private fun isHeicMime(mime: String): Boolean =
        mime.contains("heic", ignoreCase = true) || mime.contains("heif", ignoreCase = true)

    private fun importHeicAsJpeg(
        context: Context,
        uri: Uri,
        importDir: File,
        displayName: String,
        mime: String,
    ): ProbedMedia {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { "HEIC requires API 28+" }
        val localFile = File(importDir, "${UUID.randomUUID()}.jpg")
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
        localFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        val img = probeImageFile(localFile, uri.toString(), displayName, "image/jpeg")
        return ProbedMedia(
            uri = img.uri,
            displayName = img.displayName,
            mime = "image/jpeg",
            kind = MediaKind.IMAGE,
            width = img.width,
            height = img.height,
            durationMs = 0,
            fps = null,
            fileBytes = img.fileBytes,
            localPath = img.localPath,
            hasAlpha = false,
            videoCodec = null,
            audioCodec = null,
            videoBitrateBps = null,
            audioBitrateBps = null,
            channels = null,
            sampleRate = null,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = detectLiveOrMotion(context, uri, mime),
        )
    }

    private fun probeL2Dimensions(file: File, mime: String): Pair<Int, Int>? {
        if (!file.isFile) return null
        val header = file.inputStream().use { input ->
            input.readNBytes(65536.coerceAtMost(file.length().toInt().coerceAtLeast(0)))
        }
        if (!mime.contains("mp4", ignoreCase = true) && !mime.startsWith("video/", ignoreCase = true)) {
            rustImageDimensions(header)?.let { return it }
        }
        if (mime.contains("mp4", ignoreCase = true) || mime.contains("video", ignoreCase = true)) {
            rustMp4VideoDimensions(header)?.let { return it }
            return L2Probe.probeMp4VideoDimensions(header)
        }
        if (mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true)) {
            return L2Probe.probeJpegDimensions(header)?.let { it.width to it.height }
        }
        if (mime.contains("png", ignoreCase = true)) {
            return L2Probe.probePngDimensions(header)?.let { it.width to it.height }
        }
        return L2Probe.probeImageDimensions(header)
    }

    private fun rustImageDimensions(header: ByteArray): Pair<Int, Int>? =
        runCatching {
            uniffi.ss_core.probeImageDimensionsL2(header)?.let { dims ->
                dims.width.toInt() to dims.height.toInt()
            }
        }.getOrNull()

    private fun rustMp4VideoDimensions(header: ByteArray): Pair<Int, Int>? =
        runCatching {
            uniffi.ss_core.probeMp4VideoDimensionsL2(header)?.let { dims ->
                dims.width.toInt() to dims.height.toInt()
            }
        }.getOrNull()

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) {
                    return cursor.getString(index)
                }
            }
        return null
    }

    fun detectLiveOrMotion(
        context: Context,
        uri: Uri,
        mime: String,
        header: ByteArray? = null,
    ): Boolean {
        if (!mime.startsWith("image/", ignoreCase = true) &&
            !mime.contains("heic", ignoreCase = true) &&
            !mime.contains("heif", ignoreCase = true) &&
            !mime.contains("jpeg", ignoreCase = true)
        ) {
            return false
        }
        val bytes = header ?: runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readNBytes(131072)
            }
        }.getOrNull()
        if (bytes != null) {
            val ascii = String(bytes, Charsets.ISO_8859_1)
            if (ascii.contains("MotionPhoto", ignoreCase = true) ||
                ascii.contains("MicroVideo", ignoreCase = true) ||
                ascii.contains("LivePhoto", ignoreCase = true) ||
                ascii.contains("GCamera:MotionPhoto", ignoreCase = true)
            ) {
                return true
            }
        }
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf("is_live_photo"),
                null,
                null,
                null,
            )?.use { cursor ->
                val col = cursor.getColumnIndex("is_live_photo")
                if (col >= 0 && cursor.moveToFirst()) cursor.getInt(col) != 0 else false
            } ?: false
        }.getOrDefault(false)
    }

    fun cleanStaleImports(importDir: File, maxAgeMs: Long = 30 * 60 * 1000L) {
        runCatching {
            if (!importDir.isDirectory) return@runCatching
            val threshold = System.currentTimeMillis() - maxAgeMs
            importDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < threshold) {
                    file.delete()
                }
            }
        }
    }
}
