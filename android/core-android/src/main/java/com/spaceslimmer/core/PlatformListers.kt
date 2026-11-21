package com.spaceslimmer.core

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.util.concurrent.ConcurrentHashMap
import uniffi.ss_core.PlatformDirEntry
import uniffi.ss_core.PlatformDirLister
import uniffi.ss_core.PlatformListerException

/**
 * High-performance Storage Access Framework (SAF) Directory Lister.
 * Uses raw ContentResolver cursors on DocumentsContract instead of slow DocumentFile.
 */
class SafDirLister(
    private val context: Context,
    rootTreeUriString: String? = null,
) : PlatformDirLister {

    companion object {
        private val uriCache = ConcurrentHashMap<String, Uri>()
        fun getDocumentUri(path: String): Uri? = uriCache[path]
        fun clearCache() = uriCache.clear()
    }

    @Volatile
    private var baseTreeUri: Uri? = rootTreeUriString?.let { Uri.parse(it) }

    override fun listDir(handle: String): List<PlatformDirEntry> {
        val cachedUri = uriCache[handle]
        val parsedUri = Uri.parse(handle)
        val targetUri = cachedUri ?: parsedUri

        // Establish the root Tree URI if not yet known
        if (baseTreeUri == null) {
            baseTreeUri = if (DocumentsContract.isTreeUri(parsedUri) || !DocumentsContract.isDocumentUri(context, parsedUri)) {
                parsedUri
            } else {
                targetUri
            }
        }
        val rootTree = baseTreeUri ?: targetUri

        val resolver = context.contentResolver
        val entries = mutableListOf<PlatformDirEntry>()

        val docId = try {
            if (DocumentsContract.isDocumentUri(context, targetUri)) {
                DocumentsContract.getDocumentId(targetUri)
            } else {
                DocumentsContract.getTreeDocumentId(targetUri)
            }
        } catch (_: Throwable) {
            targetUri.lastPathSegment ?: ""
        }

        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(rootTree, docId)
        } catch (e: Throwable) {
            throw PlatformListerException.Failed("Invalid SAF tree/document URI: ${e.message}")
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val mtimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId = if (idCol >= 0) cursor.getString(idCol) else null
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val mtimeMs = if (mtimeCol >= 0 && !cursor.isNull(mtimeCol)) cursor.getLong(mtimeCol) else 0L
                    val mtimeSec = (mtimeMs / 1000L).toUInt()

                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (childDocId != null) {
                        try {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, childDocId)
                            val pathKey = if (handle.endsWith("/")) "$handle$name" else "$handle/$name"
                            uriCache[pathKey] = docUri
                        } catch (_: Throwable) {}
                    }

                    entries.add(
                        PlatformDirEntry(
                            name = name,
                            isDir = isDir,
                            sizeBytes = size.toULong(),
                            mtimeSec = mtimeSec,
                            mimeType = mime
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            throw PlatformListerException.Failed("Failed to query SAF cursor: ${e.message}")
        }

        return entries
    }
}

/**
 * Fast in-memory MediaStore Directory Lister for Android Scoped Storage (L1).
 * Pre-indexes MediaStore.Files into an in-memory directory hierarchy on first call,
 * enabling ultra-fast O(1) directory queries without filesystem traversal restrictions.
 */
class MediaStoreDirLister(
    private val context: Context,
    @Suppress("unused") private val selectedCategories: List<String> = emptyList()
) : PlatformDirLister {

    companion object {
        private val mediaUriCache = ConcurrentHashMap<String, Uri>()
        fun getContentUri(path: String): Uri? = mediaUriCache[path]
        fun clearCache() = mediaUriCache.clear()
    }

    private val dirTree = ConcurrentHashMap<String, MutableList<PlatformDirEntry>>()
    @Volatile
    private var initialized = false
    private val initLock = Any()

    private fun normalizePath(path: String): String {
        var p = path.replace('\\', '/').trim()
        while (p.endsWith('/') && p.length > 1) {
            p = p.substring(0, p.length - 1)
        }
        return p
    }

    private fun ensureInitialized(rootHandle: String) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return

            val resolver: ContentResolver = context.contentResolver
            val collectionUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA
            )

            val knownDirs = mutableSetOf<String>()
            val normalizedRoot = normalizePath(rootHandle)

            try {
                resolver.query(collectionUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val mtimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                        var name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val mtimeSec = if (mtimeCol >= 0 && !cursor.isNull(mtimeCol)) cursor.getLong(mtimeCol).coerceAtLeast(0L).toUInt() else 0u
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                        val rawData = if (dataCol >= 0) cursor.getString(dataCol) else null

                        val fullPath = if (!rawData.isNullOrBlank()) {
                            normalizePath(rawData)
                        } else {
                            if (name.isEmpty()) name = "media_$id"
                            "$normalizedRoot/$name"
                        }

                        if (name.isEmpty()) {
                            name = fullPath.substringAfterLast('/')
                        }

                        if (id > 0) {
                            val uri = ContentUris.withAppendedId(collectionUri, id)
                            mediaUriCache[fullPath] = uri
                        }

                        val parentPath = if (fullPath.contains('/')) {
                            fullPath.substringBeforeLast('/')
                        } else {
                            normalizedRoot
                        }

                        val fileList = dirTree.getOrPut(parentPath) { mutableListOf() }
                        fileList.add(
                            PlatformDirEntry(
                                name = name,
                                isDir = false,
                                sizeBytes = size.toULong(),
                                mtimeSec = mtimeSec,
                                mimeType = mime
                            )
                        )

                        // Walk upwards to ensure intermediate directories exist
                        var curr = parentPath
                        while (curr.isNotEmpty() && curr != normalizedRoot && curr != "/") {
                            val p = curr.substringBeforeLast('/', "")
                            val parentOfCurr = if (p.isEmpty()) "/" else p
                            val dirName = curr.substringAfterLast('/')
                            val dirKey = "$parentOfCurr/$dirName"
                            if (knownDirs.add(dirKey)) {
                                val list = dirTree.getOrPut(parentOfCurr) { mutableListOf() }
                                list.add(
                                    PlatformDirEntry(
                                        name = dirName,
                                        isDir = true,
                                        sizeBytes = 0uL,
                                        mtimeSec = 0u,
                                        mimeType = null
                                    )
                                )
                            }
                            curr = parentOfCurr
                        }
                    }
                }
            } catch (e: Throwable) {
                // If query fails, continue with partial tree
            }

            initialized = true
        }
    }

    override fun listDir(handle: String): List<PlatformDirEntry> {
        val norm = normalizePath(handle)
        ensureInitialized(norm)
        return dirTree[norm]?.toList() ?: emptyList()
    }
}
