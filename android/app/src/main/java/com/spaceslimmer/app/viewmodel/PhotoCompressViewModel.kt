package com.spaceslimmer.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.spaceslimmer.core.MediaProbe
import com.spaceslimmer.core.ProbedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uniffi.ss_core.Compatibility
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.Decision
import uniffi.ss_core.GroupView
import uniffi.ss_core.JobSpec
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.QualityPreset
import java.util.UUID

class PhotoCompressViewModel(app: Application) : AndroidViewModel(app) {
    val engine: CoreEngine? = CompressSession.engine(app)

    var selectedImages: List<ProbedImage> = emptyList()
        private set

    var compareJob: JobView? = null

    var groupId: String? = null
        private set

    private val _latestGroup = MutableStateFlow<GroupView?>(null)
    val latestGroupFlow: StateFlow<GroupView?> = _latestGroup.asStateFlow()

    var latestGroup: GroupView?
        get() = _latestGroup.value
        private set(value) {
            _latestGroup.value = value
        }

    fun clearSelection() {
        selectedImages = emptyList()
    }

    fun adoptGroup(group: GroupView) {
        latestGroup = group
        groupId = group.id
        lastRequest = group.request
    }

    var lastRequest: CompressRequest? = null
        private set

    val presets = listOf(
        QualityPreset.IMAGE_MAX,
        QualityPreset.IMAGE_HIGH,
        QualityPreset.IMAGE_SMALL,
    )

    val formats = listOf("jpeg", "webp", "png")

    suspend fun importUris(uris: List<Uri>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = getApplication<Application>()
            selectedImages = uris.mapNotNull { uri ->
                val resolvedUri = com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(appContext, uri, MediaKind.IMAGE) ?: uri
                val directPath = if (resolvedUri.scheme == "file") resolvedUri.path else null
                val readablePath = if (directPath != null && java.io.File(directPath).canRead()) directPath else ""
                com.spaceslimmer.core.MediaProbe.probeRemote(appContext, resolvedUri, MediaKind.IMAGE)?.let { probed ->
                    com.spaceslimmer.core.ProbedImage(
                        uri = probed.uri,
                        displayName = probed.displayName,
                        mime = probed.mime,
                        width = probed.width,
                        height = probed.height,
                        fileBytes = probed.fileBytes,
                        localPath = readablePath,
                        hasAlpha = probed.hasAlpha,
                        isLivePhoto = probed.isLivePhoto,
                    )
                }
            }
        }
    }

    suspend fun estimate(
        preset: QualityPreset,
        imageFormat: String,
        compatibility: Compatibility,
        outputPolicy: OutputPolicy,
    ): List<Decision> = withContext(Dispatchers.IO) {
        val core = engine ?: return@withContext emptyList()
        val fps = selectedImages.map { MediaProbe.toFingerprint(it) }
        val request = CompressSession.buildRequest(
            preset = preset,
            compatibility = compatibility,
            outputPolicy = outputPolicy,
            imageFormat = imageFormat,
            engine = core,
        )
        CompressSession.estimate(core, fps, request)
    }

    suspend fun submit(
        preset: QualityPreset,
        imageFormat: String,
        compatibility: Compatibility,
        outputPolicy: OutputPolicy,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val core = engine ?: error("CoreEngine unavailable")
            val appContext = getApplication<Application>()
            val stagingDir = java.io.File(appContext.cacheDir, "spaceslimmer/staging").apply { mkdirs() }
            val jobs = selectedImages.map { item ->
                var localPath = item.localPath
                if (localPath.isBlank()) {
                    val srcUri = Uri.parse(item.uri)
                    val directPath = if (srcUri.scheme == "file") srcUri.path else null
                    if (directPath != null && java.io.File(directPath).canRead()) {
                        localPath = directPath
                    } else {
                        val ext = when {
                            item.mime.contains("png", true) -> "png"
                            item.mime.contains("webp", true) -> "webp"
                            else -> "jpg"
                        }
                        val stagedFile = java.io.File(stagingDir, "${UUID.randomUUID()}.$ext")
                        appContext.contentResolver.openInputStream(srcUri)?.use { input ->
                            stagedFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        localPath = stagedFile.absolutePath
                    }
                }
                JobSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = item.displayName,
                    sourceUri = item.uri,
                    localPath = localPath,
                    fingerprint = MediaProbe.toFingerprint(item),
                    requestOverride = null,
                )
            }
            val request = CompressSession.buildRequest(
                preset = preset,
                compatibility = compatibility,
                outputPolicy = outputPolicy,
                imageFormat = imageFormat,
                engine = core,
            )
            lastRequest = request
            val startPaused = core.settings().idleChargeOnly
            val id = core.submitGroup(jobs, request, startPaused)
            if (startPaused) {
                com.spaceslimmer.app.worker.IdleChargeWorker.enqueue(getApplication(), id)
            }
            groupId = id
            latestGroup = core.group(id)
            id
        }
    }

    suspend fun refreshGroup(targetGroupId: String? = null): GroupView? = withContext(Dispatchers.IO) {
        val id = targetGroupId ?: groupId ?: return@withContext null
        groupId = id
        val core = engine ?: return@withContext null
        val group = core.group(id)
        latestGroup = group
        group?.let { lastRequest = it.request }
        latestGroup
    }

    fun isGroupFinished(group: GroupView): Boolean = CompressSession.isGroupFinished(group)

    suspend fun pauseGroup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = groupId ?: error("no group")
            engine?.pauseGroup(id)
            Unit
        }
    }

    suspend fun resumeGroup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = groupId ?: error("no group")
            engine?.resumeGroup(id)
            Unit
        }
    }

    suspend fun retryJob(jobId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val core = engine ?: error("no engine")
            core.retryJob(jobId)
            groupId?.let { latestGroup = core.group(it) }
            Unit
        }
    }

    suspend fun cancelGroup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = groupId ?: error("no group")
            val core = engine ?: error("no engine")
            val group = core.cancelGroup(id)
            group.jobs.filter { it.state == JobState.RUNNING || it.state == JobState.CANCELLING }
                .forEach { job ->
                    CompressSession.bridge(getApplication())?.cancelPlatformJob(job.spec.id)
                }
            com.spaceslimmer.app.service.CompressForegroundService.stop(getApplication())
            latestGroup = group
        }
    }

    suspend fun saveSucceededToGallery(overridePolicy: OutputPolicy? = null): Result<com.spaceslimmer.app.export.GallerySaveOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val group = refreshGroup() ?: error("no group")
                val context = getApplication<Application>()
                val core = engine ?: error("no engine")
                val policy = overridePolicy ?: lastRequest?.outputPolicy ?: OutputPolicy.SAVE_AS_NEW
                val outcome = com.spaceslimmer.app.export.GalleryCommit.saveSucceeded(
                    context = context,
                    core = core,
                    group = group,
                    kind = uniffi.ss_core.MediaKind.IMAGE,
                    policy = policy,
                )
                latestGroup = core.group(group.id)
                outcome
            }
        }
}
