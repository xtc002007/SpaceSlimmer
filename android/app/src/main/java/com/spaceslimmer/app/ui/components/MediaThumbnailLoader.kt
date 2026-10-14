package com.spaceslimmer.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.core.GalleryWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.ss_core.MediaKind
import java.io.File

object ThumbnailMemoryCache : LruCache<String, Bitmap>(120)

suspend fun loadMediaThumbnail(
    context: Context,
    uriString: String,
    kind: MediaKind,
): Bitmap? = withContext(Dispatchers.IO) {
    if (uriString.isBlank()) return@withContext null
    ThumbnailMemoryCache.get(uriString)?.let { return@withContext it }

    val localFile = if (uriString.startsWith("/") || uriString.contains(":\\")) {
        File(uriString).takeIf { it.isFile }
    } else {
        null
    }

    var resultBitmap: Bitmap? = null

    // 1. Direct local file loading
    if (localFile != null && localFile.isFile) {
        when (kind) {
            MediaKind.IMAGE -> {
                resultBitmap = runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(localFile.absolutePath, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 200)
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeFile(localFile.absolutePath, decodeOpts)
                    } else null
                }.getOrNull()
            }
            MediaKind.VIDEO -> {
                resultBitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ThumbnailUtils.createVideoThumbnail(localFile, Size(200, 200), null)
                    } else {
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(localFile.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
                    }
                }.getOrNull() ?: runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(localFile.absolutePath)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            }
            MediaKind.AUDIO -> {
                resultBitmap = runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(localFile.absolutePath)
                        val art = retriever.embeddedPicture
                        if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            }
        }
    }

    // 2. URI handling (content:// or fallback)
    if (resultBitmap == null) {
        val resolved = runCatching { GalleryWriter.resolveReplaceUri(context, uriString) }.getOrNull()
        val targetUri = resolved ?: runCatching { Uri.parse(uriString) }.getOrNull()

        if (targetUri != null) {
            // Android Q+ (API 29+) native loadThumbnail: extremely fast and hardware-accelerated for MediaStore URIs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetUri.scheme == "content") {
                resultBitmap = runCatching {
                    context.contentResolver.loadThumbnail(targetUri, Size(200, 200), null)
                }.getOrNull()
            }

            if (resultBitmap == null) {
                when (kind) {
                    MediaKind.VIDEO -> {
                        resultBitmap = runCatching {
                            context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(pfd.fileDescriptor)
                                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                        ?: retriever.frameAtTime
                                } finally {
                                    runCatching { retriever.release() }
                                }
                            }
                        }.getOrNull()

                        if (resultBitmap == null) {
                            val path = GalleryWriter.queryPublicPath(context, targetUri)
                                ?: if (targetUri.scheme == "file") targetUri.path else null
                            if (path != null && File(path).isFile) {
                                resultBitmap = runCatching {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        ThumbnailUtils.createVideoThumbnail(File(path), Size(200, 200), null)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
                                    }
                                }.getOrNull()
                            }
                        }
                    }
                    MediaKind.IMAGE -> {
                        resultBitmap = runCatching {
                            context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                                if (opts.outWidth > 0 && opts.outHeight > 0) {
                                    val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 200)
                                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOpts)
                                } else null
                            }
                        }.getOrNull()
                    }
                    MediaKind.AUDIO -> {
                        resultBitmap = runCatching {
                            context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(pfd.fileDescriptor)
                                    val art = retriever.embeddedPicture
                                    if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
                                } finally {
                                    runCatching { retriever.release() }
                                }
                            }
                        }.getOrNull()
                    }
                }
            }
        }
    }

    if (resultBitmap != null) {
        ThumbnailMemoryCache.put(uriString, resultBitmap)
    }
    resultBitmap
}

@Composable
fun MediaThumbnail(
    uri: String,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf(ThumbnailMemoryCache.get(uri)) }

    LaunchedEffect(uri, kind) {
        if (bitmap == null && uri.isNotBlank()) {
            val loaded = loadMediaThumbnail(context, uri, kind)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    val clickableMod = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = clickableMod
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val placeholderIcon = when (kind) {
                MediaKind.VIDEO -> Icons.Default.Movie
                MediaKind.IMAGE -> Icons.Default.Image
                MediaKind.AUDIO -> Icons.Default.Audiotrack
            }
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.45f),
            )
        }

        // If it's a video, render a semi-transparent play overlay badge
        if (kind == MediaKind.VIDEO) {
            Box(
                modifier = Modifier
                    .size(size * 0.45f)
                    .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.action_play),
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.3f),
                )
            }
        }
    }
}
