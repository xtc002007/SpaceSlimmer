package com.spaceslimmer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spaceslimmer.app.export.ExportNaming
import com.spaceslimmer.app.export.GalleryCommit
import com.spaceslimmer.app.ui.shared.compressionSizeStats
import com.spaceslimmer.app.ui.shared.groupCompressionStats
import com.spaceslimmer.core.GalleryWriter
import com.spaceslimmer.core.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.ss_core.Compatibility
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.Decision
import uniffi.ss_core.GroupView
import uniffi.ss_core.QualityPreset
import uniffi.ss_core.JobSpec
import uniffi.ss_core.JobState
import uniffi.ss_core.JobView
import uniffi.ss_core.MediaFingerprint
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.Recipe
import uniffi.ss_core.TimeClass
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocaleGalleryResultInstrumentedTest {
    @Test
    fun languageTagsNormalizeToZhCnAndEnglish() {
        assertEquals("zh", LocaleHelper.selectorCode("zh"))
        assertEquals("zh", LocaleHelper.selectorCode("zh-CN"))
        assertEquals("zh", LocaleHelper.selectorCode("zh_CN"))
        assertEquals("en", LocaleHelper.selectorCode("en"))
        assertEquals("en", LocaleHelper.selectorCode("en-US"))
        assertEquals("zh-CN", LocaleHelper.toBcp47("zh"))
        assertEquals("en", LocaleHelper.toBcp47("en"))
    }

    @Test
    fun wrapDoesNotRequireApplicationContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapped = LocaleHelper.wrap(context)
        assertTrue(wrapped.resources.configuration.locales.size() >= 1)
    }

    @Test
    fun languagePersistAndReadBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = LocaleHelper.storedLanguage(context)
        try {
            LocaleHelper.persistLanguage(context, "zh")
            assertEquals("zh", LocaleHelper.storedLanguage(context))
            assertEquals("zh", LocaleHelper.selectorCode(context))
            LocaleHelper.persistLanguage(context, "en")
            assertEquals("en", LocaleHelper.storedLanguage(context))
        } finally {
            if (previous != null) {
                LocaleHelper.persistLanguage(context, previous)
            } else {
                LocaleHelper.followSystem(context)
            }
        }
    }

    @Test
    fun resolveOutputFileHandlesPlainAndFileUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "spaceslimmer-test-output.mp4")
        file.writeBytes(ByteArray(32) { 1 })
        try {
            assertTrue(GalleryWriter.resolveOutputFile(file.absolutePath).isFile)
            assertTrue(GalleryWriter.resolveOutputFile("file://${file.absolutePath}").isFile)
            assertEquals(file.length(), GalleryWriter.resolveOutputFile(file.absolutePath).length())
        } finally {
            file.delete()
        }
    }

    @Test
    fun writeProbeDoesNotTruncateExistingFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "spaceslimmer-probe.bin")
        val payload = ByteArray(2048) { 7 }
        file.writeBytes(payload)
        try {
            val uri = "file://${file.absolutePath}"
            val before = file.length()
            GalleryWriter.hasDirectWriteAccess(context, uri)
            @Suppress("DEPRECATION")
            GalleryWriter.canWriteUri(context, uri)
            assertEquals(before, file.length())
            assertTrue(file.readBytes().contentEquals(payload))
        } finally {
            file.delete()
        }
    }

    @Test
    fun compressionStatsCompareOriginalAndOutput() {
        val original = 40L * 1024L * 1024L
        val output = 25L * 1024L * 1024L
        val job = sampleJob(
            originalBytes = original.toULong(),
            outputBytes = output.toULong(),
            savedBytes = original - output,
            width = 1920u,
            height = 1080u,
            fps = 30f,
            outW = 1280u,
            outH = 720u,
            outFps = 24f,
        )
        val stats = compressionSizeStats(job)
        assertEquals(original, stats.originalBytes)
        assertEquals(output, stats.outputBytes)
        assertEquals(original - output, stats.savedBytes)
        assertEquals(38, stats.savedPercent)
        assertEquals(1920, stats.originalWidth)
        assertEquals(1280, stats.outputWidth)
        assertEquals(24f, stats.outputFps)

        val grouped = groupCompressionStats(listOf(job, job))
        requireNotNull(grouped)
        assertEquals(original * 2, grouped.originalBytes)
        assertEquals(output * 2, grouped.outputBytes)
    }

    @Test
    fun savableJobsRequiresExistingOutputFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = File(context.cacheDir, "ss-ready.mp4")
        ready.writeBytes(ByteArray(128) { 3 })
        val missing = File(context.cacheDir, "ss-missing.mp4")
        missing.delete()
        try {
            val succeeded = sampleJob(
                outputUri = ready.absolutePath,
                outputBytes = ready.length().toULong(),
                state = JobState.SUCCEEDED,
                id = "ok",
            )
            val running = sampleJob(
                outputUri = null,
                outputBytes = null,
                state = JobState.RUNNING,
                id = "run",
            )
            val failed = sampleJob(
                outputUri = ready.absolutePath,
                outputBytes = ready.length().toULong(),
                state = JobState.FAILED,
                id = "fail",
            )
            val ghost = sampleJob(
                outputUri = missing.absolutePath,
                outputBytes = 64uL,
                state = JobState.SUCCEEDED,
                id = "ghost",
            )
            val group = GroupView(
                id = "g-save",
                createdAt = 0L,
                request = sampleCompressRequest(),
                jobs = listOf(succeeded, running, failed, ghost),
                running = 1u,
                done = 1u,
                failed = 1u,
                skipped = 0u,
                savedBytes = 1,
                outputPolicy = OutputPolicy.SAVE_AS_NEW,
            )
            val savable = GalleryCommit.savableJobs(group)
            assertEquals(listOf("ok"), savable.map { it.spec.id })
        } finally {
            ready.delete()
        }
    }

    @Test
    fun videoExportPathIsDcimAlbum() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = ExportNaming.relativePath(context, MediaKind.VIDEO)
        requireNotNull(path)
        assertTrue(path.startsWith("DCIM/SpaceSlimmer"))
        val imagePath = ExportNaming.relativePath(context, MediaKind.IMAGE)
        requireNotNull(imagePath)
        assertTrue(imagePath.startsWith("DCIM/SpaceSlimmer"))
    }

    @Test
    fun saveImagePublishesVisibleGalleryItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ss-gallery-test.png")
        file.writeBytes(MIN_PNG)
        val name = "ss_test_${System.currentTimeMillis()}.png"
        val uri = GalleryWriter.saveImage(
            context,
            file.absolutePath,
            name,
            "image/png",
            "DCIM/SpaceSlimmer/",
        )
        try {
            assertFalse(GalleryWriter.isPending(context, uri))
            assertTrue(GalleryWriter.readableBytes(context, uri) >= file.length())
            context.contentResolver.openInputStream(uri)!!.use { input ->
                assertTrue(input.readBytes().isNotEmpty())
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
            File("/storage/emulated/0/DCIM/SpaceSlimmer/$name").delete()
            file.delete()
        }
    }

    @Test
    fun saveVideoPublishesVisibleGalleryItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ss-gallery-test.mp4")
        file.writeBytes(ByteArray(4096) { 9 })
        val name = "ss_test_${System.currentTimeMillis()}.mp4"
        val uri = GalleryWriter.saveVideo(
            context,
            file.absolutePath,
            name,
            "DCIM/SpaceSlimmer/",
        )
        try {
            assertFalse(GalleryWriter.isPending(context, uri))
            assertTrue("video row should be readable", GalleryWriter.isPublished(context, uri, file.length()))
            assertTrue(GalleryWriter.readableBytes(context, uri) >= file.length())
            val publicPath = GalleryWriter.queryPublicPath(context, uri)
            val listed = GalleryWriter.listedAlbumFile(name, file.length())
            assertTrue(
                "video must be listable in DCIM, data=$publicPath listed=${listed?.absolutePath}",
                listed != null && listed.length() >= file.length(),
            )
        } finally {
            context.contentResolver.delete(uri, null, null)
            File("/storage/emulated/0/DCIM/SpaceSlimmer/$name").delete()
            file.delete()
        }
    }

    private fun sampleJob(
        originalBytes: ULong = 40UL * 1024UL * 1024UL,
        outputBytes: ULong? = 25UL * 1024UL * 1024UL,
        savedBytes: Long = 15L * 1024L * 1024L,
        width: UInt = 1920u,
        height: UInt = 1080u,
        fps: Float = 30f,
        outW: UInt = 1280u,
        outH: UInt = 720u,
        outFps: Float = 24f,
        state: JobState = JobState.SUCCEEDED,
        outputUri: String? = "/tmp/out.mp4",
        id: String = "job-1",
    ): JobView {
        val fingerprint = MediaFingerprint(
            kind = MediaKind.VIDEO,
            mime = "video/mp4",
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            width = width,
            height = height,
            durationMs = 10_000u,
            fps = fps,
            rotation = 0u,
            fileBytes = originalBytes,
            videoBitrateBps = 8_000_000u,
            audioBitrateBps = 128_000u,
            hasAlpha = false,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = false,
            channels = null,
            sampleRate = null,
        )
        val recipe = Recipe(
            kind = MediaKind.VIDEO,
            outW = outW,
            outH = outH,
            fps = outFps,
            videoCodec = "h264",
            videoBitrateBps = 2_500_000u,
            audioCopy = true,
            audioCodec = "aac",
            audioBitrateBps = 128_000u,
            container = "mp4",
            imageOutFormat = null,
            imageQuality = null,
            maxSide = null,
            audioOutFormat = null,
            audioChannels = null,
            trimStartMs = null,
            trimEndMs = null,
            targetBytes = null,
        )
        return JobView(
            spec = JobSpec(
                id = id,
                displayName = "clip.mp4",
                sourceUri = "content://media/external/video/media/1",
                localPath = "/tmp/clip.mp4",
                fingerprint = fingerprint,
                requestOverride = null,
            ),
            state = state,
            progress = if (state == JobState.SUCCEEDED) 1f else 0.4f,
            decision = Decision(
                skip = false,
                skipReason = null,
                recipe = recipe,
                estimateBytesLow = outputBytes ?: 0u,
                estimateBytesHigh = outputBytes ?: 0u,
                timeClass = TimeClass.FAST,
            ),
            outputUri = outputUri,
            outputBytes = outputBytes,
            savedBytes = savedBytes,
            fail = null,
            skip = null,
            message = "",
            updatedAt = 0L,
        )
    }

    private fun sampleCompressRequest(): CompressRequest = CompressRequest(
        preset = QualityPreset.VIDEO_BALANCED,
        compatibility = Compatibility.COMPATIBLE,
        outputPolicy = OutputPolicy.SAVE_AS_NEW,
        targetBytes = null,
        custom = null,
        keepGps = true,
        keepHdr = false,
        skipEnabled = true,
        imageFormat = null,
        audioFormat = null,
    )

    companion object {
        private val MIN_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}
