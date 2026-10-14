package com.spaceslimmer.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import uniffi.ss_core.CoreEngine
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object VaultManager {
    data class Meta(
        val displayName: String?,
        val originalPath: String?,
        val dateTakenMs: Long?,
        val size: Long,
        val mimeType: String?,
    )

    fun copyToVault(
        context: Context,
        engine: CoreEngine,
        sourceUri: String,
        localPath: String?,
        jobId: String,
        vaultDays: UInt,
    ): String {
        val vaultRoot = File(engine.vaultDir())
        vaultRoot.mkdirs()
        val baseId = UUID.randomUUID().toString()
        val vaultFile = File(vaultRoot, "$baseId.bak")
        val bytes = when {
            localPath != null && File(localPath).isFile -> {
                File(localPath).copyTo(vaultFile, overwrite = true)
                File(localPath).length()
            }
            else -> {
                context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                    vaultFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("cannot read source for vault")
                vaultFile.length()
            }
        }

        // Query metadata from sourceUri before replacing!
        val resolvedUri = runCatching { GalleryWriter.resolveReplaceUri(context, sourceUri) }.getOrNull()
        val targetQueryUri = resolvedUri ?: Uri.parse(sourceUri)
        val nameFromStore = GalleryWriter.queryDisplayName(context, targetQueryUri)
            ?: runCatching {
                context.contentResolver.query(Uri.parse(sourceUri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: localPath?.let { File(it).name }
        val pubPath = resolvedUri?.let { GalleryWriter.queryPublicPath(context, it) } ?: localPath
        val timestamps = resolvedUri?.let { GalleryWriter.queryTimestamps(context, it) }
        val dateTaken = timestamps?.dateTaken
            ?: GalleryWriter.parseDateFromFilename(nameFromStore ?: "")
            ?: GalleryWriter.parseDateFromFilename(sourceUri.substringAfterLast('/'))
            ?: (timestamps?.dateModified?.let { it * 1000 })

        // Save sidecar .meta file
        val metaFile = File(vaultRoot, "$baseId.meta")
        runCatching {
            metaFile.writeText(
                "displayName=${nameFromStore.orEmpty()}\n" +
                "originalPath=${pubPath.orEmpty()}\n" +
                "dateTakenMs=${dateTaken ?: 0L}\n" +
                "size=$bytes\n"
            )
        }

        val expiresAt = System.currentTimeMillis() / 1000 + vaultDays.toLong() * 86_400
        return engine.registerVaultBackup(
            jobId = jobId,
            vaultPath = vaultFile.absolutePath,
            originalUri = sourceUri,
            originalBytes = bytes.toULong(),
            expiresAt = expiresAt,
        )
    }

    fun readMeta(context: Context, vaultPath: String, originalUri: String): Meta {
        val vaultFile = File(vaultPath)
        val metaFile = File(vaultFile.parentFile, "${vaultFile.nameWithoutExtension}.meta")
        var displayName: String? = null
        var originalPath: String? = null
        var dateTakenMs: Long? = null
        var size: Long = vaultFile.takeIf { it.isFile }?.length() ?: 0L
        var mimeType: String? = null

        if (metaFile.isFile) {
            runCatching {
                metaFile.readLines().forEach { line ->
                    val eq = line.indexOf('=')
                    if (eq > 0) {
                        val k = line.substring(0, eq).trim()
                        val v = line.substring(eq + 1).trim()
                        when (k) {
                            "displayName" -> if (v.isNotEmpty()) displayName = v
                            "originalPath" -> if (v.isNotEmpty()) originalPath = v
                            "dateTakenMs" -> v.toLongOrNull()?.let { if (it > 0) dateTakenMs = it }
                            "size" -> v.toLongOrNull()?.let { if (it > 0) size = it }
                            "mimeType" -> if (v.isNotEmpty()) mimeType = v
                        }
                    }
                }
            }
        }

        // If displayName, originalPath or dateTakenMs are missing, resolve from MediaStore or file
        if (displayName.isNullOrBlank() || originalPath.isNullOrBlank() || dateTakenMs == null) {
            val resolvedUri = runCatching { GalleryWriter.resolveReplaceUri(context, originalUri) }.getOrNull()
            if (resolvedUri != null) {
                if (displayName.isNullOrBlank()) {
                    displayName = GalleryWriter.queryDisplayName(context, resolvedUri)
                }
                if (originalPath.isNullOrBlank()) {
                    originalPath = GalleryWriter.queryPublicPath(context, resolvedUri)
                }
                if (dateTakenMs == null) {
                    val ts = GalleryWriter.queryTimestamps(context, resolvedUri)
                    dateTakenMs = ts?.dateTaken ?: (ts?.dateModified?.let { it * 1000 })
                }
            }
        }

        // Probe vault file directly if date still unknown
        if (dateTakenMs == null && vaultFile.isFile) {
            displayName?.let { name ->
                dateTakenMs = GalleryWriter.parseDateFromFilename(name)
            }
            if (dateTakenMs == null) {
                dateTakenMs = GalleryWriter.parseDateFromFilename(originalUri.substringAfterLast('/'))
            }
            if (dateTakenMs == null) {
                runCatching {
                    FileInputStream(vaultFile).use { fis ->
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(fis.fd)
                            val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            if (!dateStr.isNullOrBlank()) {
                                dateTakenMs = parseRetrieverDate(dateStr)
                            }
                        } finally {
                            runCatching { retriever.release() }
                        }
                    }
                }
            }
        }

        if (displayName.isNullOrBlank()) {
            displayName = if (originalUri.contains("photopicker/media/")) {
                "相册原文件 (ID: ${originalUri.substringAfterLast('/')})"
            } else {
                originalUri.substringAfterLast('/')
            }
        }

        return Meta(
            displayName = displayName,
            originalPath = originalPath ?: "相册原位置",
            dateTakenMs = dateTakenMs,
            size = size,
            mimeType = mimeType,
        )
    }

    private fun parseRetrieverDate(dateStr: String): Long? {
        val formats = listOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyyMMdd HHmmss",
        )
        for (fmt in formats) {
            val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                if (fmt.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
            }
            val d = runCatching { sdf.parse(dateStr) }.getOrNull()
            if (d != null) return d.time
        }
        return null
    }
}
