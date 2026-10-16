package com.spaceslimmer.app.core

import android.content.Context
import android.util.Log
import com.spaceslimmer.app.service.CompressForegroundService
import com.spaceslimmer.audiohw.AacTranscoder
import com.spaceslimmer.core.AppLogger
import com.spaceslimmer.core.EngineEventHub
import com.spaceslimmer.videohw.VideoTranscodeRegistry
import com.spaceslimmer.videohw.VideoTranscoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.CoreObserver
import uniffi.ss_core.FailReason
import uniffi.ss_core.GroupView
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind
import uniffi.ss_core.Recipe
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class EngineObserverBridge(
    private val context: Context,
    private val engineProvider: () -> CoreEngine?,
) : CoreObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeVideo = ConcurrentHashMap.newKeySet<String>()
    private val activeAudio = ConcurrentHashMap.newKeySet<String>()
    private val cancelledJobs = ConcurrentHashMap.newKeySet<String>()
    private val jobGenerations = ConcurrentHashMap<String, AtomicLong>()

    override fun onGroupChanged(group: GroupView) {
        AppLogger.d(TAG, "onGroupChanged: group=${group.id}, running=${group.running}, done=${group.done}, failed=${group.failed}, skipped=${group.skipped}")
        EngineEventHub.emitGroup(group)
        CompressForegroundService.update(context, group)
        if (com.spaceslimmer.app.viewmodel.CompressSession.isGroupFinished(group)) {
            CompressForegroundService.completeAndStop(context, group)
            if (group.savedBytes > 0) {
                com.spaceslimmer.app.prefs.AppPrefs.recordFinishedGroup(context, group.id, group.savedBytes)
                com.spaceslimmer.app.widget.WeeklySavingsWidget.refresh(context)
            }
        }
    }

    override fun onJobChanged(job: JobView) {
        AppLogger.d(TAG, "onJobChanged: job=${job.spec.id}, state=${job.state}, progress=${(job.progress * 100).toInt()}%")
        EngineEventHub.emitJob(job)
        if (job.state == JobState.SUCCEEDED || job.state == JobState.FAILED || job.state == JobState.SKIPPED || job.state == JobState.COMMITTED_REPLACE) {
            val localPath = job.spec.localPath
            if (!localPath.isNullOrBlank() && (localPath.contains("spaceslimmer/staging") || localPath.contains("spaceslimmer/import"))) {
                runCatching {
                    val f = File(localPath)
                    if (f.isFile) f.delete()
                }
            }
        }
    }

    override fun requestStartVideo(jobId: String, recipe: Recipe, src: String, dst: String) {
        val resolvedSrc = com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(
            context,
            src,
            MediaKind.VIDEO,
        )?.toString() ?: src
        AppLogger.i(TAG, "requestStartVideo: jobId=$jobId, src=$resolvedSrc (orig=$src), dst=$dst, codec=${recipe.videoCodec}, bitrate=${recipe.videoBitrateBps}")
        VideoTranscodeRegistry.cancel(jobId)
        val generation = nextGeneration(jobId)
        cancelledJobs.remove(jobId)
        activeVideo.add(jobId)
        scope.launch {
            runCatching {
                if (isStale(jobId, generation)) throw VideoTranscoder.CancelledException()
                val output = if (dst.contains('.')) dst else "$dst.mp4"
                val bytes = VideoTranscoder.transcode(
                    context = context,
                    params = VideoTranscoder.TranscodeParams(
                        sourceUri = resolvedSrc,
                        outputPath = output,
                        recipe = recipe,
                        useHevc = recipe.videoCodec.equals("hevc", ignoreCase = true),
                        jobId = jobId,
                        onProgress = { ratio ->
                            if (!isStale(jobId, generation)) {
                                engineProvider()?.onPlatformProgress(jobId, ratio)
                            }
                        },
                        isCancelled = { isStale(jobId, generation) || cancelledJobs.contains(jobId) },
                        onTelemetry = { name, json ->
                            runCatching { engineProvider()?.recordTelemetry(name, json) }
                            if (name == "quality.flat_frame") {
                                EngineEventHub.emitNotification(name, 1f)
                            }
                        },
                    ),
                )
                if (isStale(jobId, generation)) throw VideoTranscoder.CancelledException()
                AppLogger.i(TAG, "video succeeded: jobId=$jobId, out=$output, bytes=$bytes")
                engineProvider()?.onPlatformSucceeded(jobId, output, bytes.toULong())
            }.onFailure { err ->
                if (handleStaleFailure(jobId, generation)) return@onFailure
                AppLogger.e(TAG, "video failed: jobId=$jobId, err=${err.message}", err)
                val reason = mapFailure(err)
                engineProvider()?.onPlatformFailed(jobId, reason, err.message ?: "encode")
            }
            activeVideo.remove(jobId)
            if (generation == currentGeneration(jobId)) {
                cancelledJobs.remove(jobId)
            }
        }
    }

    override fun requestStartAudio(jobId: String, recipe: Recipe, src: String, dst: String) {
        if (recipe.audioOutFormat?.equals("opus", ignoreCase = true) == true ||
            recipe.audioCodec.equals("opus", ignoreCase = true)
        ) {
            Log.d(TAG, "skip platform audio for opus job $jobId")
            return
        }
        VideoTranscodeRegistry.cancel(jobId)
        val generation = nextGeneration(jobId)
        cancelledJobs.remove(jobId)
        activeAudio.add(jobId)
        scope.launch {
            var tempInputFile: File? = null
            runCatching {
                if (isStale(jobId, generation)) throw VideoTranscoder.CancelledException()
                val (input, tempFile) = resolveLocalPathWithTemp(src)
                tempInputFile = tempFile
                val output = if (dst.endsWith(".m4a")) dst else "$dst.m4a"
                val channels = recipe.audioChannels?.toInt() ?: 2
                val bytes = AacTranscoder.transcode(
                    inputPath = input,
                    outputPath = output,
                    bitrateBps = recipe.audioBitrateBps.toInt(),
                    targetChannels = channels,
                    trimStartMs = recipe.trimStartMs?.toLong(),
                    trimEndMs = recipe.trimEndMs?.toLong(),
                    onProgress = { ratio ->
                        if (!isStale(jobId, generation)) {
                            engineProvider()?.onPlatformProgress(jobId, ratio)
                        }
                    },
                    isCancelled = { isStale(jobId, generation) || cancelledJobs.contains(jobId) },
                )
                if (isStale(jobId, generation)) throw VideoTranscoder.CancelledException()
                engineProvider()?.onPlatformSucceeded(jobId, output, bytes.toULong())
            }.onFailure { err ->
                if (handleStaleFailure(jobId, generation)) return@onFailure
                Log.e(TAG, "audio failed $jobId", err)
                val reason = mapFailure(err)
                engineProvider()?.onPlatformFailed(jobId, reason, err.message ?: "encode")
            }
            tempInputFile?.let { runCatching { it.delete() } }
            activeAudio.remove(jobId)
            if (generation == currentGeneration(jobId)) {
                cancelledJobs.remove(jobId)
            }
        }
    }

    override fun requestCancelVideo(jobId: String) {
        cancelPlatformJob(jobId)
    }

    override fun requestNotification(progressText: String, ratio: Float) {
        when (progressText) {
            "power.low_battery", "power.charging_resume" ->
                EngineEventHub.emitNotification(progressText, ratio)
            else -> CompressForegroundService.notify(context, progressText, ratio)
        }
    }

    fun cancelPlatformJob(jobId: String) {
        nextGeneration(jobId)
        cancelledJobs.add(jobId)
        VideoTranscodeRegistry.cancel(jobId)
    }

    private fun handleStaleFailure(jobId: String, generation: Long): Boolean {
        if (!isStale(jobId, generation)) return false
        if (cancelledJobs.remove(jobId)) {
            engineProvider()?.onPlatformFailed(jobId, FailReason.CANCELLED, "cancelled")
        }
        return true
    }

    private fun nextGeneration(jobId: String): Long =
        jobGenerations.computeIfAbsent(jobId) { AtomicLong(0) }.incrementAndGet()

    private fun currentGeneration(jobId: String): Long =
        jobGenerations[jobId]?.get() ?: 0L

    private fun isStale(jobId: String, generation: Long): Boolean {
        val current = currentGeneration(jobId)
        return generation != current || cancelledJobs.contains(jobId)
    }

    private fun mapFailure(err: Throwable): FailReason {
        val msg = err.message ?: ""
        return when {
            err is VideoTranscoder.CancelledException || msg.contains("cancel", ignoreCase = true) ->
                FailReason.CANCELLED
            msg.contains("fail.disk_full", ignoreCase = true) ||
                msg.contains("no space", ignoreCase = true) ||
                msg.contains("enospc", ignoreCase = true) -> FailReason.DISK_FULL
            msg.contains("fail.permission", ignoreCase = true) -> FailReason.PERMISSION
            msg.contains("fail.invalid_output", ignoreCase = true) ||
                msg.contains("skip.duration_mismatch", ignoreCase = true) -> FailReason.INVALID_OUTPUT
            msg.contains("fail.timeout", ignoreCase = true) -> FailReason.ENCODE_FAILED
            else -> FailReason.ENCODE_FAILED
        }
    }

    private fun resolveLocalPathWithTemp(src: String): Pair<String, File?> {
        if (src.startsWith("/") || src.contains(":\\")) return Pair(src, null)
        val cache = File(context.cacheDir, "import/${System.nanoTime()}")
        cache.parentFile?.mkdirs()
        context.contentResolver.openInputStream(android.net.Uri.parse(src))?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        }
        return Pair(cache.absolutePath, cache)
    }

    companion object {
        private const val TAG = "EngineObserverBridge"
    }
}
