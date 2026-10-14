package com.spaceslimmer.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object ExportHelper {
    private const val PREFS = "spaceslimmer_export"
    private const val KEY_TREE = "export_tree_uri"
    private const val KEY_AUDIO_TREE = "audio_export_tree_uri"

    fun getSavedExportTree(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TREE, null) ?: prefs.getString(KEY_AUDIO_TREE, null)
            ?: return null
        return Uri.parse(raw)
    }

    fun getSavedAudioExportTree(context: Context): Uri? = getSavedExportTree(context)

    fun saveExportTree(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE, treeUri.toString())
            .apply()
    }

    fun saveAudioExportTree(context: Context, treeUri: Uri) = saveExportTree(context, treeUri)

    fun mimeForFile(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".m4a") || lower.endsWith(".aac") -> "audio/mp4"
            lower.endsWith(".ogg") || lower.endsWith(".opus") -> "audio/ogg"
            lower.endsWith(".amr") -> "audio/amr"
            else -> "application/octet-stream"
        }
    }

    fun exportFile(
        context: Context,
        sourcePath: String,
        displayName: String,
        mime: String,
        archiveByDate: Boolean = false,
    ): Uri? {
        val treeUri = getSavedExportTree(context) ?: return null
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val dest = if (archiveByDate) {
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            tree.findFile(day) ?: tree.createDirectory(day) ?: tree
        } else {
            tree
        }
        val targetName = if (displayName.contains('.')) {
            displayName
        } else {
            val ext = when {
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("video") -> "mp4"
                else -> "m4a"
            }
            "SpaceSlimmer_$displayName.$ext"
        }
        dest.findFile(targetName)?.delete()
        val doc = dest.createFile(mime, targetName) ?: return null
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            File(sourcePath).inputStream().use { input -> input.copyTo(out) }
        } ?: return null
        return doc.uri
    }

    fun exportAudioFile(
        context: Context,
        sourcePath: String,
        displayName: String,
        archiveByDate: Boolean = false,
    ): Uri? = exportFile(context, sourcePath, displayName, "audio/mp4", archiveByDate)
}
