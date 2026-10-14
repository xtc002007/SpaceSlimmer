package com.spaceslimmer.videohw

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.spaceslimmer.core.AppLogger
import uniffi.ss_core.Recipe
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Media3 scale-only pre-pass so Plan B receives frames already at recipe size.
 */
@UnstableApi
object VideoScalePrePass {
    private const val TAG = "VideoScalePrePass"

    fun tryScale(
        context: Context,
        sourceUri: String,
        recipe: Recipe,
        workDir: File?,
        jobId: String,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): String? {
        val (srcW, srcH) = probeSize(context, sourceUri)
        if (srcW <= 0 || srcH <= 0) return null
        val outW = recipe.outW.toInt().coerceAtLeast(2)
        val outH = recipe.outH.toInt().coerceAtLeast(2)
        val scale = min(outW.toFloat() / srcW, outH.toFloat() / srcH)
        if (scale >= 0.999f) return null

        val dir = workDir ?: context.cacheDir
        dir.mkdirs()
        val outFile = File(dir, "planb-scale-${System.nanoTime()}.mp4")
        val uri = if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) {
            Uri.fromFile(File(sourceUri))
        } else {
            Uri.parse(sourceUri)
        }
        val effects = Effects(
            emptyList(),
            listOf(ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()),
        )
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()
        val error = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        transformer.addListener(
            object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onProgress(1f)
                    latch.countDown()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException,
                ) {
                    error.set(exception)
                    latch.countDown()
                }
            },
        )
        val registryToken = VideoTranscodeRegistry.register(jobId, transformer)
        val progressHolder = ProgressHolder()
        return try {
            transformer.start(edited, outFile.absolutePath)
            val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
            while (!latch.await(200, TimeUnit.MILLISECONDS)) {
                if (isCancelled() || System.currentTimeMillis() >= deadline) {
                    transformer.cancel()
                    outFile.delete()
                    return null
                }
                runCatching {
                    val pState = transformer.getProgress(progressHolder)
                    if (pState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                }
            }
            if (isCancelled()) {
                transformer.cancel()
                outFile.delete()
                return null
            }
            error.get()?.let {
                AppLogger.w(TAG, "scale pre-pass failed: ${it.message}")
                outFile.delete()
                return null
            }
            if (!outFile.isFile || outFile.length() <= 0) {
                outFile.delete()
                return null
            }
            AppLogger.i(TAG, "scaled $srcW x $srcH -> recipe $outW x $outH (${outFile.length()} bytes)")
            outFile.absolutePath
        } catch (t: Throwable) {
            AppLogger.w(TAG, "scale pre-pass threw: ${t.message}")
            outFile.delete()
            null
        } finally {
            VideoTranscodeRegistry.unregister(jobId, registryToken)
        }
    }

    private fun probeSize(context: Context, sourceUri: String): Pair<Int, Int> {
        val extractor = android.media.MediaExtractor()
        return try {
            if (sourceUri.startsWith("/") || sourceUri.contains(":\\")) {
                extractor.setDataSource(sourceUri)
            } else {
                context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: return 0 to 0
            }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    val w = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                    val h = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                    return w to h
                }
            }
            0 to 0
        } catch (_: Exception) {
            0 to 0
        } finally {
            extractor.release()
        }
    }
}
