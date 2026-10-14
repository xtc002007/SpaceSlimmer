package com.spaceslimmer.core

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import uniffi.ss_core.MediaKind

class WriteConsentRequired(val pendingIntent: PendingIntent) : RuntimeException("fail.permission")

object GalleryWriter {
    private const val TAG = "SsGallery"
    const val ALBUM_FOLDER = "DCIM/SpaceSlimmer/"
    private const val CAMERA_FOLDER = "DCIM/Camera/"

    data class ItemMeta(
        val durationMs: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val dateTaken: Long? = null,
        val dateModified: Long? = null,
        val dateAdded: Long? = null,
    )

    data class MediaTimestamps(
        val dateTaken: Long? = null,
        val dateModified: Long? = null,
        val dateAdded: Long? = null,
    )

    fun queryTimestamps(context: Context, uri: Uri?): MediaTimestamps? {
        if (uri == null) return null
        return runCatching {
            if (uri.scheme == "file") {
                val f = uri.path?.let { File(it) }
                if (f != null && f.exists()) {
                    val lm = f.lastModified()
                    return MediaTimestamps(dateTaken = lm, dateModified = lm / 1000, dateAdded = lm / 1000)
                }
            }
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATE_ADDED,
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dtIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    val dmIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val daIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val dt = if (dtIdx >= 0) cursor.getLong(dtIdx).takeIf { it > 0 } else null
                    val dm = if (dmIdx >= 0) cursor.getLong(dmIdx).takeIf { it > 0 } else null
                    val da = if (daIdx >= 0) cursor.getLong(daIdx).takeIf { it > 0 } else null
                    MediaTimestamps(
                        dateTaken = dt ?: dm?.let { it * 1000 } ?: da?.let { it * 1000 },
                        dateModified = dm ?: dt?.let { it / 1000 },
                        dateAdded = da ?: dm ?: dt?.let { it / 1000 },
                    )
                } else null
            }
        }.getOrNull()
    }

    fun resolveOutputFile(path: String): File {
        val trimmed = path.trim()
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            return File(Uri.parse(trimmed).path ?: trimmed.removePrefix("file://"))
        }
        return File(trimmed)
    }

    fun hasDirectWriteAccess(context: Context, uriString: String, kind: MediaKind? = null): Boolean {
        val resolved = resolveReplaceUri(context, uriString, kind) ?: uriFrom(uriString) ?: return false
        if (resolved.scheme == "file") {
            val path = resolved.path ?: return false
            val file = File(path)
            return file.isFile && file.canWrite()
        }
        return runCatching {
            context.contentResolver.openFileDescriptor(resolved, "rw")?.use { } ?: return false
            true
        }.getOrDefault(false)
    }

    @Deprecated("Use hasDirectWriteAccess or canAttemptReplace. This method used to truncate files.")
    fun canWriteUri(context: Context, uriString: String): Boolean =
        hasDirectWriteAccess(context, uriString) || canAttemptReplace(context, uriString)

    fun canAttemptReplace(context: Context, uriString: String, kind: MediaKind? = null): Boolean {
        if (hasDirectWriteAccess(context, uriString, kind)) return true
        return resolveReplaceUri(context, uriString, kind) != null
    }

    fun saveImage(
        context: Context,
        sourcePath: String,
        displayName: String,
        mime: String,
        relativePath: String? = null,
        meta: ItemMeta = ItemMeta(),
    ): Uri = insertAndCopy(
        context = context,
        sourcePath = sourcePath,
        displayName = displayName,
        mime = mime,
        relativePath = relativePath ?: ALBUM_FOLDER,
        extras = probeImageMeta(resolveOutputFile(sourcePath), meta),
        collection = { imageCollection() },
        fallback = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI },
        displayNameKey = MediaStore.Images.Media.DISPLAY_NAME,
        mimeKey = MediaStore.Images.Media.MIME_TYPE,
        relativePathKey = MediaStore.Images.Media.RELATIVE_PATH,
        pendingKey = MediaStore.Images.Media.IS_PENDING,
    )

    fun saveVideo(
        context: Context,
        sourcePath: String,
        displayName: String,
        relativePath: String? = null,
        meta: ItemMeta = ItemMeta(),
    ): Uri = insertAndCopy(
        context = context,
        sourcePath = sourcePath,
        displayName = displayName,
        mime = "video/mp4",
        relativePath = relativePath ?: ALBUM_FOLDER,
        extras = probeVideoMeta(resolveOutputFile(sourcePath), meta),
        collection = { videoCollection() },
        fallback = { MediaStore.Video.Media.EXTERNAL_CONTENT_URI },
        displayNameKey = MediaStore.Video.Media.DISPLAY_NAME,
        mimeKey = MediaStore.Video.Media.MIME_TYPE,
        relativePathKey = MediaStore.Video.Media.RELATIVE_PATH,
        pendingKey = MediaStore.Video.Media.IS_PENDING,
    )

    fun saveAudio(
        context: Context,
        sourcePath: String,
        displayName: String,
        relativePath: String? = null,
        meta: ItemMeta = ItemMeta(),
    ): Uri = insertAndCopy(
        context = context,
        sourcePath = sourcePath,
        displayName = displayName,
        mime = "audio/mp4",
        relativePath = relativePath,
        extras = meta,
        collection = { audioCollection() },
        fallback = { MediaStore.Audio.Media.EXTERNAL_CONTENT_URI },
        displayNameKey = MediaStore.Audio.Media.DISPLAY_NAME,
        mimeKey = MediaStore.Audio.Media.MIME_TYPE,
        relativePathKey = MediaStore.Audio.Media.RELATIVE_PATH,
        pendingKey = MediaStore.Audio.Media.IS_PENDING,
    )

    fun mimeForFormat(format: String): String = when (format.lowercase()) {
        "webp" -> "image/webp"
        "png" -> "image/png"
        else -> "image/jpeg"
    }

    fun replaceImage(
        context: Context,
        sourceUri: String,
        outputPath: String,
        mime: String,
    ): Uri {
        val uri = writableUri(context, sourceUri, MediaKind.IMAGE)
        val file = resolveOutputFile(outputPath)
        val origTimestamps = queryTimestamps(context, uri) ?: queryTimestamps(context, uriFrom(sourceUri))
        origTimestamps?.dateTaken?.let { dtMs -> runCatching { file.setLastModified(dtMs) } }
        writeFileToUri(context, outputPath, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.SIZE, file.length())
                    origTimestamps?.dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
                    origTimestamps?.dateModified?.let { put(MediaStore.Images.Media.DATE_MODIFIED, it) }
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }
        reveal(context, uri, mime)
        return uri
    }

    fun replaceVideo(context: Context, sourceUri: String, outputPath: String): Uri {
        val uri = writableUri(context, sourceUri, MediaKind.VIDEO)
        val file = resolveOutputFile(outputPath)
        val resolvedUri = resolveReplaceUri(context, sourceUri, MediaKind.VIDEO)
        val origTimestamps = queryTimestamps(context, uri)
            ?: queryTimestamps(context, resolvedUri)
            ?: queryTimestamps(context, uriFrom(sourceUri))

        val dateTakenMs = origTimestamps?.dateTaken
            ?: parseDateFromFilename(queryDisplayName(context, uri) ?: "")
            ?: parseDateFromFilename(resolvedUri?.let { queryDisplayName(context, it) } ?: "")
            ?: parseDateFromFilename(sourceUri.substringAfterLast('/'))

        dateTakenMs?.let { dtMs ->
            runCatching { file.setLastModified(dtMs) }
        }
        writeFileToUri(context, outputPath, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.SIZE, file.length())
                    dateTakenMs?.let { put(MediaStore.Video.Media.DATE_TAKEN, it) }
                    (origTimestamps?.dateModified ?: dateTakenMs?.let { it / 1000 })?.let {
                        put(MediaStore.Video.Media.DATE_MODIFIED, it)
                    }
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }
        val path = queryDataPath(context, uri)
        if (!path.isNullOrBlank()) {
            val latch = java.util.concurrent.CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("video/mp4")) { _, _ ->
                latch.countDown()
            }
            latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
        }
        // Force re-apply timestamps after scanner in case MediaScanner touched them
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && dateTakenMs != null) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.SIZE, file.length())
                    put(MediaStore.Video.Media.DATE_TAKEN, dateTakenMs)
                    (origTimestamps?.dateModified ?: (dateTakenMs / 1000)).let {
                        put(MediaStore.Video.Media.DATE_MODIFIED, it)
                    }
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }
        runCatching { context.contentResolver.notifyChange(uri, null) }
        return uri
    }

    fun replaceAudio(context: Context, sourceUri: String, outputPath: String): Uri {
        val uri = writableUri(context, sourceUri, MediaKind.AUDIO)
        val file = resolveOutputFile(outputPath)
        val origTimestamps = queryTimestamps(context, uri) ?: queryTimestamps(context, uriFrom(sourceUri))
        origTimestamps?.dateTaken?.let { dtMs -> runCatching { file.setLastModified(dtMs) } }
        writeFileToUri(context, outputPath, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.SIZE, file.length())
                    origTimestamps?.dateModified?.let { put(MediaStore.Audio.Media.DATE_MODIFIED, it) }
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }
        reveal(context, uri, "audio/mpeg")
        return uri
    }

    fun restoreToUri(context: Context, targetUri: String, vaultPath: String, kind: MediaKind? = null): Uri {
        val meta = VaultManager.readMeta(context, vaultPath, targetUri)
        val file = File(vaultPath)
        val resolvedUri = resolveReplaceUri(context, targetUri, kind)
        val origTimestamps = queryTimestamps(context, resolvedUri)
            ?: queryTimestamps(context, uriFrom(targetUri))

        val dateTakenMs = meta.dateTakenMs
            ?: origTimestamps?.dateTaken
            ?: parseDateFromFilename(meta.displayName ?: "")
            ?: parseDateFromFilename(targetUri.substringAfterLast('/'))
            ?: file.lastModified().takeIf { it > 0 }

        dateTakenMs?.let { dtMs -> runCatching { file.setLastModified(dtMs) } }

        try {
            val uri = writableUri(context, targetUri, kind)
            writeFileToUri(context, vaultPath, uri)
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: if (vaultPath.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "image/jpeg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.SIZE, file.length())
                        dateTakenMs?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
                        (origTimestamps?.dateModified ?: dateTakenMs?.let { it / 1000 })?.let {
                            put(MediaStore.MediaColumns.DATE_MODIFIED, it)
                        }
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
            }
            val path = queryDataPath(context, uri)
            if (!path.isNullOrBlank()) {
                val latch = java.util.concurrent.CountDownLatch(1)
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, _ ->
                    latch.countDown()
                }
                latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && dateTakenMs != null) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.SIZE, file.length())
                        put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMs)
                        (origTimestamps?.dateModified ?: (dateTakenMs / 1000)).let {
                            put(MediaStore.MediaColumns.DATE_MODIFIED, it)
                        }
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
            }
            runCatching { context.contentResolver.notifyChange(uri, null) }
            return uri
        } catch (e: WriteConsentRequired) {
            throw e
        } catch (e: Throwable) {
            // Target cannot be overwritten directly (e.g. photopicker URI or entry deleted),
            // restore as a new item with original date and name into Camera/Gallery!
            val isVideo = vaultPath.endsWith(".mp4", ignoreCase = true) || (meta.displayName?.endsWith(".mp4", ignoreCase = true) == true)
            val isAudio = vaultPath.endsWith(".mp3", ignoreCase = true) || (meta.displayName?.endsWith(".mp3", ignoreCase = true) == true)
            val itemMeta = ItemMeta(
                dateTaken = dateTakenMs,
                dateModified = dateTakenMs?.let { it / 1000 },
            )
            val displayName = meta.displayName?.takeIf { !it.contains('/') } ?: (if (isVideo) "restored_${System.currentTimeMillis()}.mp4" else "restored_${System.currentTimeMillis()}.jpg")
            return when {
                isVideo -> saveVideo(context, vaultPath, displayName, CAMERA_FOLDER, itemMeta)
                isAudio -> saveAudio(context, vaultPath, displayName, null, itemMeta)
                else -> saveImage(context, vaultPath, displayName, "image/jpeg", CAMERA_FOLDER, itemMeta)
            }
        }
    }

    fun writeConsentIntent(context: Context, uriString: String, kind: MediaKind? = null): PendingIntent? {
        val uri = resolveReplaceUri(context, uriString, kind) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
        }
        return null
    }

    fun writeConsentIntent(context: Context, uriStrings: List<String>, kind: MediaKind? = null): PendingIntent? {
        val uris = uriStrings.mapNotNull { resolveReplaceUri(context, it, kind) }
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createWriteRequest(context.contentResolver, uris)
        }
        return null
    }

    private fun writableUri(context: Context, sourceUri: String, kind: MediaKind? = null): Uri {
        val resolved = resolveReplaceUri(context, sourceUri, kind)
            ?: uriFrom(sourceUri)
            ?: error("fail.permission")
        if (hasDirectWriteAccess(context, resolved.toString(), kind)) return resolved
        requestWriteOrThrow(context, resolved)
        return resolved
    }

    private fun requestWriteOrThrow(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
            throw WriteConsentRequired(pending)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.contentResolver.openFileDescriptor(uri, "rw")?.close()
            } catch (e: RecoverableSecurityException) {
                throw WriteConsentRequired(e.userAction.actionIntent)
            }
        }
        error("fail.permission")
    }

    fun resolveReplaceUri(context: Context, uri: Uri, kind: MediaKind? = null): Uri? =
        resolveReplaceUri(context, uri.toString(), kind)

    fun resolveReplaceUri(context: Context, uriString: String, kind: MediaKind? = null): Uri? {
        val uri = uriFrom(uriString) ?: return null
        if (isMediaStoreUri(uri)) return uri

        // Handle file:// and direct filesystem paths
        val filePath = if (uri.scheme == "file") uri.path ?: uriString
            else if (uriString.startsWith("/") || uriString.contains(":\\")) uriString else null
        if (filePath != null) {
            val collection = when (kind) {
                MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                null -> null
            }
            val collections = if (collection != null) listOf(collection) else listOf(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            )
            for (col in collections) {
                val found = runCatching {
                    context.contentResolver.query(
                        col,
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.DATA}=?",
                        arrayOf(filePath),
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) ContentUris.withAppendedId(col, cursor.getLong(0)) else null
                    }
                }.getOrNull()
                if (found != null) return found
            }
            val f = File(filePath)
            if (f.isFile) {
                val found = queryMediaStoreByNameAndSize(context, f.name, f.length(), kind)
                if (found != null) return found
            }
            return uri
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()?.let { return it }
        }

        // Handle DocumentsProvider URIs (e.g., com.android.providers.media.documents)
        if (uri.authority?.contains("media.documents", ignoreCase = true) == true) {
            val docId = runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrNull()
            val id = docId?.substringAfterLast(':')?.toLongOrNull()
            if (id != null && id > 0L) {
                when {
                    docId.startsWith("video:", ignoreCase = true) || kind == MediaKind.VIDEO ->
                        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    docId.startsWith("image:", ignoreCase = true) || kind == MediaKind.IMAGE ->
                        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    docId.startsWith("audio:", ignoreCase = true) || kind == MediaKind.AUDIO ->
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }

        val isPicker = uri.authority?.contains("photopicker", ignoreCase = true) == true ||
            uri.path?.contains("picker", ignoreCase = true) == true
        if (isPicker) {
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null && id > 0L) {
                when (kind) {
                    MediaKind.VIDEO -> return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    MediaKind.IMAGE -> return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    MediaKind.AUDIO -> return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    null -> {
                        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
                        if (mime.startsWith("video/")) {
                            return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        } else if (mime.startsWith("image/")) {
                            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        } else if (mime.startsWith("audio/")) {
                            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        }
                        val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        if (runCatching { context.contentResolver.getType(videoUri) }.getOrNull() != null) return videoUri
                        val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        if (runCatching { context.contentResolver.getType(imageUri) }.getOrNull() != null) return imageUri
                    }
                }
            }
        }
        return queryMediaStoreByName(context, uri, kind)
    }

    fun queryMediaStoreByNameAndSize(
        context: Context,
        name: String,
        size: Long,
        kind: MediaKind? = null,
    ): Uri? {
        val collections = when (kind) {
            MediaKind.VIDEO -> listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            MediaKind.IMAGE -> listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            MediaKind.AUDIO -> listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            null -> listOf(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            )
        }
        for (col in collections) {
            val found = runCatching {
                context.contentResolver.query(
                    col,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(name),
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    while (cursor.moveToNext()) {
                        val rowSize = if (sizeCol >= 0) cursor.getLong(sizeCol) else -1L
                        if (size <= 0L || rowSize == size) {
                            return@use ContentUris.withAppendedId(col, cursor.getLong(idCol))
                        }
                    }
                    null
                }
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    private fun queryMediaStoreByName(context: Context, uri: Uri, kind: MediaKind? = null): Uri? {
        val name = queryDisplayName(context, uri) ?: return null
        val size = querySize(context, uri)
        val collections = when (kind) {
            MediaKind.VIDEO -> listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            MediaKind.IMAGE -> listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            MediaKind.AUDIO -> listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            null -> listOf(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            )
        }
        val matches = mutableListOf<Uri>()
        collections.forEach { collection ->
            runCatching {
                context.contentResolver.query(
                    collection,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.SIZE,
                    ),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(name),
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    while (cursor.moveToNext()) {
                        val rowSize = if (sizeCol >= 0) cursor.getLong(sizeCol) else -1L
                        if (size <= 0L || rowSize == size) {
                            matches += ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                        }
                    }
                }
            }
        }
        return matches.distinct().firstOrNull()
    }

    private fun queryDateModified(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else 0L
            } ?: 0L
        }.getOrDefault(0L)

    private fun isMediaStoreUri(uri: Uri): Boolean {
        if (uri.authority?.contains("media", ignoreCase = true) != true) return false
        if (uri.authority?.contains("photopicker", ignoreCase = true) == true) return false
        return uri.path?.contains("picker", ignoreCase = true) != true
    }

    private fun imageCollection(): Uri =
        volumeCollection(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ) { MediaStore.Images.Media.getContentUri(it) }

    private fun videoCollection(): Uri =
        volumeCollection(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ) { MediaStore.Video.Media.getContentUri(it) }

    private fun audioCollection(): Uri =
        volumeCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        ) { MediaStore.Audio.Media.getContentUri(it) }

    private fun volumeCollection(legacy: Uri, factory: (String) -> Uri): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Prefer the merged external volume. PRIMARY on ColorOS can sandbox items
            // so only this app sees them and DCIM/SpaceSlimmer stays empty.
            runCatching { return factory(MediaStore.VOLUME_EXTERNAL) }
            runCatching { return factory(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
        }
        return legacy
    }

    private fun insertAndCopy(
        context: Context,
        sourcePath: String,
        displayName: String,
        mime: String,
        relativePath: String?,
        extras: ItemMeta,
        collection: () -> Uri,
        fallback: () -> Uri,
        displayNameKey: String,
        mimeKey: String,
        relativePathKey: String,
        pendingKey: String,
    ): Uri {
        val source = resolveOutputFile(sourcePath)
        require(source.isFile && source.length() > 0L) { "output file missing" }
        val names = uniqueNames(displayName)
        val folders = buildList {
            relativePath?.trim()?.takeIf { it.isNotEmpty() }?.let { add(normalizeFolder(it)) }
            if (mime.startsWith("video") || mime.startsWith("image")) {
                add(ALBUM_FOLDER)
                add(CAMERA_FOLDER)
            }
        }.distinct()
        val collections = buildList {
            add(fallback())
            add(collection())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { add(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)) }
            }
        }.distinct()
        var lastError: Throwable? = null
        for (name in names) {
            for (folder in folders) {
                for (coll in collections) {
                    for (pending in listOf(false, true)) {
                        runCatching {
                            val uri = insertOnce(
                                context = context,
                                source = source,
                                collection = coll,
                                displayName = name,
                                mime = mime,
                                relativePath = folder,
                                extras = extras,
                                displayNameKey = displayNameKey,
                                mimeKey = mimeKey,
                                relativePathKey = relativePathKey,
                                pendingKey = pendingKey,
                                usePending = pending,
                            )
                            Log.i(TAG, "saved $name -> $uri folder=$folder pending=$pending")
                            return uri
                        }.onFailure {
                            lastError = it
                            Log.w(TAG, "insert failed name=$name folder=$folder pending=$pending coll=$coll", it)
                        }
                    }
                }
            }
        }
        copyToPublicAlbum(source, displayName)?.let { file ->
            scanAndWait(context, file.absolutePath, mime)
            listedAlbumFile(displayName, source.length())?.let { return Uri.fromFile(it) }
        }
        throw lastError ?: IllegalStateException("MediaStore insert failed")
    }

    private fun normalizeFolder(path: String): String = path.trim().trimEnd('/') + "/"

    private fun uniqueNames(displayName: String): List<String> {
        val stem = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val stamp = System.currentTimeMillis()
        return listOf(displayName, "${stem}_$stamp$ext", "SpaceSlimmer_$stamp$ext")
    }

    fun storedSize(context: Context, uri: Uri): Long =
        queryLong(context, uri, MediaStore.MediaColumns.SIZE)

    fun isPending(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return queryLong(context, uri, MediaStore.MediaColumns.IS_PENDING) == 1L
    }

    fun readableBytes(context: Context, uri: Uri): Long {
        val pfdSize = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        }.getOrDefault(-1L)
        if (pfdSize > 0L) return pfdSize
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                var n = 0L
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    n += r
                }
                n
            } ?: -1L
        }.getOrDefault(-1L)
    }

    fun isPublished(context: Context, uri: Uri): Boolean = isPublished(context, uri, 1L)

    fun isPublished(context: Context, uri: Uri, expectedBytes: Long): Boolean {
        if (isPending(context, uri)) return false
        return readableBytes(context, uri) >= expectedBytes.coerceAtLeast(1L)
    }

    fun listedAlbumFile(displayName: String, minSize: Long): File? {
        @Suppress("DEPRECATION")
        val dirs = listOf(
            File("/storage/emulated/0/DCIM/SpaceSlimmer"),
            File("/storage/emulated/0/DCIM/Camera"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "SpaceSlimmer"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
        )
        for (dir in dirs) {
            val files = dir.listFiles() ?: continue
            files.firstOrNull { it.isFile && it.name == displayName && it.length() >= minSize }?.let { return it }
        }
        return null
    }

    private fun insertOnce(
        context: Context,
        source: File,
        collection: Uri,
        displayName: String,
        mime: String,
        relativePath: String?,
        extras: ItemMeta,
        displayNameKey: String,
        mimeKey: String,
        relativePathKey: String,
        pendingKey: String,
        usePending: Boolean,
    ): Uri {
        val resolver = context.contentResolver
        val folder = relativePath?.let { normalizeFolder(it) }
        val now = System.currentTimeMillis()
        val targetDateTaken = extras.dateTaken ?: now
        val targetDateModified = extras.dateModified ?: (targetDateTaken / 1000)
        val targetDateAdded = extras.dateAdded ?: targetDateModified

        val values = ContentValues().apply {
            put(displayNameKey, displayName)
            put(mimeKey, mime)
            put(MediaStore.MediaColumns.TITLE, displayName.substringBeforeLast('.'))
            put(MediaStore.MediaColumns.DATE_ADDED, targetDateAdded)
            put(MediaStore.MediaColumns.DATE_MODIFIED, targetDateModified)
            extras.durationMs?.takeIf { it > 0L }?.let { put(MediaStore.MediaColumns.DURATION, it) }
            extras.width?.takeIf { it > 0 }?.let { put(MediaStore.MediaColumns.WIDTH, it) }
            extras.height?.takeIf { it > 0 }?.let { put(MediaStore.MediaColumns.HEIGHT, it) }
            if (collection.toString().contains("/file", ignoreCase = true)) {
                val mediaType = when {
                    mime.startsWith("video") -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    mime.startsWith("image") -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    mime.startsWith("audio") -> MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
                    else -> MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
                }
                put(MediaStore.Files.FileColumns.MEDIA_TYPE, mediaType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(pendingKey, if (usePending) 1 else 0)
                if (!folder.isNullOrBlank()) put(relativePathKey, folder)
                if (mime.startsWith("image") || mime.startsWith("video")) {
                    put(MediaStore.MediaColumns.DATE_TAKEN, targetDateTaken)
                }
            }
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            source.setLastModified(targetDateTaken)
            writeFileToUri(context, source.absolutePath, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = ContentValues().apply {
                    put(pendingKey, 0)
                    put(MediaStore.MediaColumns.SIZE, source.length())
                    put(MediaStore.MediaColumns.DATE_MODIFIED, targetDateModified)
                    extras.durationMs?.takeIf { it > 0L }?.let {
                        put(MediaStore.MediaColumns.DURATION, it)
                    }
                    if (mime.startsWith("image") || mime.startsWith("video")) {
                        put(MediaStore.MediaColumns.DATE_TAKEN, targetDateTaken)
                    }
                }
                val updated = resolver.update(uri, published, null, null)
                Log.i(TAG, "publish update=$updated pendingWas=$usePending uri=$uri")
            }
            reveal(context, uri, mime)
            repeat(10) {
                listedAlbumFile(displayName, source.length())?.let { file ->
                    scanAndWait(context, file.absolutePath, mime)
                    Log.i(TAG, "visible on disk ${file.absolutePath} size=${file.length()}")
                    return uri
                }
                Thread.sleep(150)
            }
            val path = queryDataPath(context, uri)
            Log.w(
                TAG,
                "not listed in DCIM. data=$path pending=${isPending(context, uri)} readable=${readableBytes(context, uri)}",
            )
            copyToPublicAlbum(source, displayName)
            listedAlbumFile(displayName, source.length())?.let { file ->
                scanAndWait(context, file.absolutePath, mime)
                return uri
            }
            error("gallery file not visible in DCIM/SpaceSlimmer")
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun writeFileToUri(context: Context, sourcePath: String, uri: Uri) {
        val source = resolveOutputFile(sourcePath)
        require(source.isFile && source.length() > 0L) { "output file missing" }
        try {
            val copied = copyToUri(context, source, uri)
            require(copied >= source.length()) { "gallery write truncated: $copied < ${source.length()}" }
        } catch (e: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                throw WriteConsentRequired(e.userAction.actionIntent)
            }
            throw e
        }
    }

    private fun copyToUri(context: Context, source: File, uri: Uri): Long {
        val resolver = context.contentResolver
        fun copyInto(out: OutputStream): Long = source.inputStream().use { input ->
            val copied = input.copyTo(out)
            out.flush()
            (out as? FileOutputStream)?.let { runCatching { it.fd.sync() } }
            copied
        }

        val openers: List<() -> OutputStream?> = listOf(
            { resolver.openOutputStream(uri, "rwt") },
            { resolver.openOutputStream(uri, "wt") },
            { resolver.openFileDescriptor(uri, "rwt")?.let { ParcelFileDescriptor.AutoCloseOutputStream(it) } },
            { resolver.openOutputStream(uri, "rw") },
            { resolver.openOutputStream(uri, "w") },
            { resolver.openOutputStream(uri) },
        )
        var lastError: Throwable? = null
        for (open in openers) {
            val stream = runCatching { open() }.onFailure { lastError = it }.getOrNull() ?: continue
            val copied = runCatching { stream.use { copyInto(it) } }.onFailure { lastError = it }.getOrNull()
            if (copied != null && copied >= source.length()) return copied
        }
        throw lastError ?: IllegalStateException("cannot open gallery output")
    }

    private fun copyToPublicAlbum(
        source: File,
        displayName: String,
    ): File? {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "SpaceSlimmer")
        return runCatching {
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) return@runCatching null
            val dest = File(dir, displayName)
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                    (output as? FileOutputStream)?.let { runCatching { it.fd.sync() } }
                }
            }
            if (dest.isFile && dest.length() >= source.length()) {
                Log.i(TAG, "public album file ${dest.absolutePath} size=${dest.length()}")
                dest
            } else {
                dest.delete()
                null
            }
        }.onFailure { Log.w(TAG, "public album copy failed", it) }.getOrNull()
    }

    private fun scanAndWait(context: Context, path: String, mime: String): Uri? {
        val latch = CountDownLatch(1)
        var scanned: Uri? = null
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, uri ->
            scanned = uri
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
        runCatching {
            @Suppress("DEPRECATION")
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(path))))
        }
        return scanned
    }

    private fun probeVideoMeta(source: File, provided: ItemMeta): ItemMeta {
        val fileDate = source.lastModified().takeIf { it > 0L }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            ItemMeta(
                durationMs = provided.durationMs?.takeIf { it > 0L }
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = provided.width?.takeIf { it > 0 }
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = provided.height?.takeIf { it > 0 }
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                dateTaken = provided.dateTaken ?: fileDate,
                dateModified = provided.dateModified ?: fileDate?.let { it / 1000 },
                dateAdded = provided.dateAdded ?: provided.dateModified ?: fileDate?.let { it / 1000 },
            )
        } catch (_: Throwable) {
            provided.copy(
                dateTaken = provided.dateTaken ?: fileDate,
                dateModified = provided.dateModified ?: fileDate?.let { it / 1000 },
                dateAdded = provided.dateAdded ?: provided.dateModified ?: fileDate?.let { it / 1000 },
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun probeImageMeta(source: File, provided: ItemMeta): ItemMeta {
        val fileDate = source.lastModified().takeIf { it > 0L }
        return runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(source.absolutePath, bounds)
            ItemMeta(
                width = provided.width?.takeIf { it > 0 } ?: bounds.outWidth.takeIf { it > 0 },
                height = provided.height?.takeIf { it > 0 } ?: bounds.outHeight.takeIf { it > 0 },
                dateTaken = provided.dateTaken ?: fileDate,
                dateModified = provided.dateModified ?: fileDate?.let { it / 1000 },
                dateAdded = provided.dateAdded ?: provided.dateModified ?: fileDate?.let { it / 1000 },
            )
        }.getOrElse {
            provided.copy(
                dateTaken = provided.dateTaken ?: fileDate,
                dateModified = provided.dateModified ?: fileDate?.let { it / 1000 },
                dateAdded = provided.dateAdded ?: provided.dateModified ?: fileDate?.let { it / 1000 },
            )
        }
    }

    private fun reveal(context: Context, uri: Uri, mime: String) {
        runCatching { context.contentResolver.notifyChange(uri, null) }
        val path = queryDataPath(context, uri)
        if (!path.isNullOrBlank()) {
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
        }
    }

    private fun queryLong(context: Context, uri: Uri, column: String): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else -1L
            } ?: -1L
        }.getOrDefault(-1L)

    fun queryPublicPath(context: Context, uri: Uri): String? = queryDataPath(context, uri)

    @Suppress("DEPRECATION")
    private fun queryDataPath(context: Context, uri: Uri): String? {
        val fromStore = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (!fromStore.isNullOrBlank()) return fromStore
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                android.system.Os.readlink("/proc/self/fd/${pfd.fd}")
            }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() && !it.startsWith("/proc/") }
    }

    private fun uriFrom(uriString: String): Uri? =
        runCatching { Uri.parse(uriString) }.getOrNull()?.takeIf { it.scheme != null || it.path != null }

    fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
        }.getOrNull()

    private fun querySize(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else 0L
                } ?: 0L
        }.getOrDefault(0L)

    fun parseDateFromFilename(name: String): Long? {
        val regex = Regex(".*?(20\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2}).*")
        val m = regex.matchEntire(name) ?: return null
        return try {
            val y = m.groupValues[1]
            val mo = m.groupValues[2]
            val d = m.groupValues[3]
            val h = m.groupValues[4]
            val mi = m.groupValues[5]
            val s = m.groupValues[6]
            val sdf = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault())
            sdf.parse("$y$mo$d$h$mi$s")?.time
        } catch (_: Throwable) { null }
    }
}
