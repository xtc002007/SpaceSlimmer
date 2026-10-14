package com.spaceslimmer.app.export

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import com.spaceslimmer.core.GalleryWriter
import com.spaceslimmer.core.VaultManager
import com.spaceslimmer.core.WriteConsentRequired
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.GroupView
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy

sealed class GallerySaveOutcome {
    data class Saved(
        val count: Int,
        val replaced: Boolean,
        val savedAsNewFallback: Boolean,
    ) : GallerySaveOutcome()

    data class NeedWriteConsent(val pendingIntent: PendingIntent) : GallerySaveOutcome()
}

object GalleryCommit {
    fun savableJobs(group: GroupView): List<JobView> =
        group.jobs.filter { job ->
            job.state == JobState.SUCCEEDED && outputFileReady(job)
        }

    fun outputFileReady(job: JobView): Boolean {
        val path = job.outputUri ?: return false
        val file = GalleryWriter.resolveOutputFile(path)
        if (!file.isFile || file.length() <= 0L) return false
        if (job.state == JobState.SUCCEEDED || job.state == JobState.COMMITTED_REPLACE) return true
        val expected = job.outputBytes?.toLong() ?: return false
        return expected > 0L && file.length() >= expected
    }

    fun saveSucceeded(
        context: Context,
        core: CoreEngine,
        group: GroupView,
        kind: MediaKind,
        policy: OutputPolicy,
    ): GallerySaveOutcome {
        val jobs = savableJobs(group)
        if (jobs.isEmpty()) return GallerySaveOutcome.Saved(0, replaced = false, savedAsNewFallback = false)

        val replaceRequested = policy == OutputPolicy.REPLACE_TO_VAULT ||
            policy == OutputPolicy.REPLACE_AFTER_CONFIRM
        if (replaceRequested) {
            val replaceable = jobs.filter { GalleryWriter.canAttemptReplace(context, it.spec.sourceUri, kind) }
            val missingWrite = replaceable.filter { !GalleryWriter.hasDirectWriteAccess(context, it.spec.sourceUri, kind) }
            if (missingWrite.isNotEmpty()) {
                val pending = GalleryWriter.writeConsentIntent(
                    context,
                    missingWrite.map { it.spec.sourceUri },
                    kind,
                )
                if (pending != null) return GallerySaveOutcome.NeedWriteConsent(pending)
            }
        }

        val vaultDays = core.settings().vaultDays
        var saved = 0
        var replaced = 0
        var asNew = 0
        com.spaceslimmer.core.AppLogger.i(
            "GalleryCommit",
            "saveSucceeded start: ${jobs.size} jobs, policy=$policy, kind=$kind, replaceRequested=$replaceRequested",
        )
        return try {
            jobs.forEachIndexed { index, job ->
                val output = job.outputUri ?: return@forEachIndexed
                val canRep = GalleryWriter.canAttemptReplace(context, job.spec.sourceUri, kind)
                val didReplace = replaceRequested &&
                    commitOne(
                        context = context,
                        core = core,
                        job = job,
                        kind = kind,
                        output = output,
                        index = index,
                        replace = canRep,
                        vaultDays = vaultDays,
                    )
                if (didReplace) replaced += 1 else asNew += 1
                saved += 1

                // Immediately delete the transcode output file in files/tmp/ after saving to MediaStore
                runCatching {
                    val outFile = GalleryWriter.resolveOutputFile(output)
                    if (outFile.isFile) {
                        val freed = outFile.length()
                        outFile.delete()
                        com.spaceslimmer.core.AppLogger.i(
                            "GalleryCommit",
                            "Deleted committed transcode file ${outFile.name}, freed $freed bytes",
                        )
                    }
                }
            }
            if (replaced > 0) {
                runCatching { core.flush() }
            }
            com.spaceslimmer.core.AppLogger.i(
                "GalleryCommit",
                "saveSucceeded complete: saved=$saved, replaced=$replaced, asNew=$asNew",
            )
            GallerySaveOutcome.Saved(
                count = saved,
                replaced = replaced > 0 && asNew == 0,
                savedAsNewFallback = replaceRequested && asNew > 0,
            )
        } catch (consent: WriteConsentRequired) {
            com.spaceslimmer.core.AppLogger.w("GalleryCommit", "Needs user write consent for replace")
            GallerySaveOutcome.NeedWriteConsent(consent.pendingIntent)
        }
    }

    private fun commitOne(
        context: Context,
        core: CoreEngine,
        job: JobView,
        kind: MediaKind,
        output: String,
        index: Int,
        replace: Boolean,
        vaultDays: UInt,
    ): Boolean {
        if (replace) {
            try {
                com.spaceslimmer.core.AppLogger.i(
                    "GalleryCommit",
                    "Attempting replace for job ${job.spec.id}: src=${job.spec.sourceUri}",
                )
                VaultManager.copyToVault(
                    context = context,
                    engine = core,
                    sourceUri = job.spec.sourceUri,
                    localPath = job.spec.localPath,
                    jobId = job.spec.id,
                    vaultDays = vaultDays,
                )
                com.spaceslimmer.core.AppLogger.i("GalleryCommit", "copyToVault succeeded for job ${job.spec.id}")
                when (kind) {
                    MediaKind.IMAGE -> GalleryWriter.replaceImage(
                        context = context,
                        sourceUri = job.spec.sourceUri,
                        outputPath = output,
                        mime = GalleryWriter.mimeForFormat(job.decision?.recipe?.imageOutFormat ?: "jpeg"),
                    )
                    MediaKind.VIDEO -> GalleryWriter.replaceVideo(context, job.spec.sourceUri, output)
                    MediaKind.AUDIO -> GalleryWriter.replaceAudio(context, job.spec.sourceUri, output)
                }
                core.markJobCommittedReplace(job.spec.id)
                runCatching { core.flush() }
                com.spaceslimmer.core.AppLogger.i("GalleryCommit", "replace succeeded and marked for job ${job.spec.id}")
                return true
            } catch (consent: WriteConsentRequired) {
                throw consent
            } catch (t: Throwable) {
                com.spaceslimmer.core.AppLogger.e(
                    "GalleryCommit",
                    "Replace failed for job ${job.spec.id}, falling back to saveAsNew: ${t.message}",
                    t,
                )
                saveAsNew(context, job, kind, output, index)
                return false
            }
        }
        com.spaceslimmer.core.AppLogger.i("GalleryCommit", "Executing saveAsNew for job ${job.spec.id}")
        saveAsNew(context, job, kind, output, index)
        return false
    }

    private fun saveAsNew(
        context: Context,
        job: JobView,
        kind: MediaKind,
        output: String,
        index: Int,
    ) {
        val relative = ExportNaming.relativePath(context, kind)
        val fp = job.spec.fingerprint
        val recipe = job.decision?.recipe
        val srcUri = runCatching { Uri.parse(job.spec.sourceUri) }.getOrNull()
        val resolvedUri = GalleryWriter.resolveReplaceUri(context, job.spec.sourceUri, kind) ?: srcUri
        val timestamps = GalleryWriter.queryTimestamps(context, resolvedUri)
            ?: GalleryWriter.queryTimestamps(context, srcUri)

        val meta = GalleryWriter.ItemMeta(
            durationMs = fp.durationMs.toLong().takeIf { it > 0L },
            width = (recipe?.outW ?: fp.width).toInt().takeIf { it > 0 },
            height = (recipe?.outH ?: fp.height).toInt().takeIf { it > 0 },
            dateTaken = timestamps?.dateTaken,
            dateModified = timestamps?.dateModified,
            dateAdded = timestamps?.dateAdded,
        )
        when (kind) {
            MediaKind.IMAGE -> {
                val format = job.decision?.recipe?.imageOutFormat ?: "jpeg"
                GalleryWriter.saveImage(
                    context,
                    output,
                    ExportNaming.displayName(context, job.spec.displayName, index, format),
                    GalleryWriter.mimeForFormat(format),
                    relative,
                    meta,
                )
            }
            MediaKind.VIDEO -> GalleryWriter.saveVideo(
                context,
                output,
                ExportNaming.displayName(context, job.spec.displayName, index, "mp4"),
                relative,
                meta,
            )
            MediaKind.AUDIO -> {
                val ext = job.decision?.recipe?.audioOutFormat ?: "m4a"
                GalleryWriter.saveAudio(
                    context,
                    output,
                    ExportNaming.displayName(context, job.spec.displayName, index, ext),
                    relative,
                    meta,
                )
            }
        }
    }
}
