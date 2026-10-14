package com.spaceslimmer.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.spaceslimmer.core.MediaProbe
import com.spaceslimmer.core.ProbedMedia
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

class MediaCompressViewModel(app: Application) : AndroidViewModel(app) {
    val engine: CoreEngine? = CompressSession.engine(app)

    var selectedMedia: List<ProbedMedia> = emptyList()
        private set

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
        selectedMedia = emptyList()
    }

    fun adoptGroup(group: GroupView) {
        latestGroup = group
        groupId = group.id
        lastRequest = group.request
    }

    var lastRequest: CompressRequest? = null
        private set

    var compareJob: JobView? = null

    val videoPresets = listOf(
        QualityPreset.VIDEO_EXTREME,
        QualityPreset.VIDEO_HIGH,
        QualityPreset.VIDEO_BALANCED,
        QualityPreset.VIDEO_SMALL,
        QualityPreset.VIDEO_TINY,
        QualityPreset.VIDEO_TARGET_SIZE,
        QualityPreset.VIDEO_CUSTOM,
    )

    val audioPresets = listOf(
        QualityPreset.AUDIO_SPEECH,
        QualityPreset.AUDIO_MUSIC_HIGH,
        QualityPreset.AUDIO_MUSIC_SMALL,
    )

    suspend fun importUris(uris: List<Uri>, kind: MediaKind): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val importDir = java.io.File(
                getApplication<Application>().filesDir,
                "spaceslimmer/import",
            )
            val appContext = getApplication<Application>()
            selectedMedia = uris.map { uri ->
                val resolvedUri = com.spaceslimmer.core.GalleryWriter.resolveReplaceUri(appContext, uri, kind) ?: uri
                MediaProbe.probeAndImportMedia(appContext, resolvedUri, importDir, kind)
            }
        }
    }

    suspend fun estimate(
        kind: MediaKind,
        preset: QualityPreset,
        compatibility: Compatibility,
        outputPolicy: OutputPolicy,
        targetBytes: ULong? = null,
        custom: uniffi.ss_core.VideoCustomParams? = null,
        audioFormat: String? = null,
    ): List<Decision> = withContext(Dispatchers.IO) {
        val core = engine ?: return@withContext emptyList()
        val fps = selectedMedia.map { MediaProbe.toFingerprint(it) }
        val request = CompressSession.buildRequest(
            preset = preset,
            compatibility = compatibility,
            outputPolicy = outputPolicy,
            targetBytes = targetBytes,
            custom = custom,
            audioFormat = if (kind == MediaKind.AUDIO) audioFormat ?: "aac" else null,
            engine = core,
        )
        CompressSession.estimate(core, fps, request)
    }

    suspend fun submit(
        kind: MediaKind,
        preset: QualityPreset,
        compatibility: Compatibility,
        outputPolicy: OutputPolicy,
        targetBytes: ULong? = null,
        custom: uniffi.ss_core.VideoCustomParams? = null,
        audioFormat: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val core = engine ?: error("CoreEngine unavailable")
            val jobs = selectedMedia.map { item ->
                JobSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = item.displayName,
                    sourceUri = item.uri,
                    localPath = item.localPath,
                    fingerprint = MediaProbe.toFingerprint(item),
                    requestOverride = null,
                )
            }
            val request = CompressSession.buildRequest(
                preset = preset,
                compatibility = compatibility,
                outputPolicy = outputPolicy,
                targetBytes = targetBytes,
                custom = custom,
                audioFormat = if (kind == MediaKind.AUDIO) audioFormat ?: "aac" else null,
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
            engine?.pauseGroup(groupId ?: error("no group"))
            Unit
        }
    }

    suspend fun resumeGroup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            engine?.resumeGroup(groupId ?: error("no group"))
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
            latestGroup = group
        }
    }

    suspend fun saveSucceeded(kind: MediaKind, overridePolicy: OutputPolicy? = null): Result<com.spaceslimmer.app.export.GallerySaveOutcome> =
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
                    kind = kind,
                    policy = policy,
                )
                latestGroup = core.group(group.id)
                outcome
            }
        }
}
