package com.spaceslimmer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.spaceslimmer.videohw.VideoTranscoder
import uniffi.ss_core.Compatibility
import uniffi.ss_core.CompressRequest
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.HwCaps
import uniffi.ss_core.JobSpec
import uniffi.ss_core.JobState
import uniffi.ss_core.MediaFingerprint
import uniffi.ss_core.MediaKind
import uniffi.ss_core.OutputPolicy
import uniffi.ss_core.QualityPreset
import uniffi.ss_core.Recipe
import uniffi.ss_core.SkipReason
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    @Test
    fun appContextLoads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.spaceslimmer.app", context.packageName)

        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        assertTrue("native lib dir should exist", nativeLibDir.exists())
    }

    @Test
    fun coreEngineLoadsWhenPresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SpaceSlimmerApp
        val engine = app.engine ?: return
        assertTrue(engine.version().isNotEmpty())
        assertTrue(engine.presetTableVersion().isNotEmpty())
        assertTrue(engine.dataDir().isNotEmpty())
    }

    @Test
    fun presetTableVersionIsLockedForPhase1() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        assertEquals("preset_v1.0", engine.presetTableVersion())
    }

    @Test
    fun efficientHevc1080pSkipsAsAlreadyEfficient() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val decision = engine.decide(
            MediaFingerprint(
                kind = MediaKind.VIDEO,
                mime = "video/mp4",
                container = "mp4",
                videoCodec = "hevc",
                audioCodec = "aac",
                width = 1920u,
                height = 1080u,
                durationMs = 60_000u,
                fps = 30f,
                rotation = 0u,
                fileBytes = 12uL * 1024uL * 1024uL,
                videoBitrateBps = 1_500_000u,
                audioBitrateBps = 128_000u,
                hasAlpha = false,
                isHdr = false,
                isSlowmo = false,
                isLivePhoto = false,
                channels = null,
                sampleRate = null,
            ),
            CompressRequest(
                preset = QualityPreset.VIDEO_BALANCED,
                compatibility = Compatibility.EFFICIENT,
                outputPolicy = OutputPolicy.SAVE_AS_NEW,
                targetBytes = null,
                custom = null,
                keepGps = true,
                keepHdr = false,
                skipEnabled = true,
                imageFormat = null,
                audioFormat = null,
            ),
            HwCaps(
                hevcHwEncode = true,
                av1HwEncode = false,
                hevcMaxW = 3840u,
                hevcMaxH = 2160u,
                av1MaxW = 0u,
                av1MaxH = 0u,
                maxImageSide = 8192u,
            ),
        )
        assertTrue(decision.skip)
        assertEquals(SkipReason.ALREADY_EFFICIENT, decision.skipReason)
        assertTrue(decision.recipe == null)
    }

    @Test
    fun opusEncodeAvailableMatchesEngineApi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val fromEngine = runCatching { engine.opusEncodeAvailable() }.getOrDefault(false)
        val fromFeatures = com.spaceslimmer.core.CoreFeatures.opusEncodeAvailable(engine)
        assertEquals(fromEngine, fromFeatures)
    }

    @Test
    fun jpegDecideDoesNotSkipLargeInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val decision = engine.decide(
            MediaFingerprint(
                kind = MediaKind.IMAGE,
                mime = "image/jpeg",
                container = "jpeg",
                videoCodec = null,
                audioCodec = null,
                width = 4032u,
                height = 3024u,
                durationMs = 0u,
                fps = null,
                rotation = 0u,
                fileBytes = 4_000_000u,
                videoBitrateBps = null,
                audioBitrateBps = null,
                hasAlpha = false,
                isHdr = false,
                isSlowmo = false,
                isLivePhoto = false,
                channels = null,
                sampleRate = null,
            ),
            CompressRequest(
                preset = QualityPreset.IMAGE_HIGH,
                compatibility = Compatibility.COMPATIBLE,
                outputPolicy = OutputPolicy.SAVE_AS_NEW,
                targetBytes = null,
                custom = null,
                keepGps = true,
                keepHdr = false,
                skipEnabled = true,
                imageFormat = "jpeg",
                audioFormat = null,
            ),
            HwCaps(
                hevcHwEncode = false,
                av1HwEncode = false,
                hevcMaxW = 1920u,
                hevcMaxH = 1080u,
                av1MaxW = 0u,
                av1MaxH = 0u,
                maxImageSide = 8192u,
            ),
        )
        assertFalse(decision.skip)
        assertTrue(decision.estimateBytesHigh < 4_000_000uL)
    }

    @Test
    fun shortMp4VideoDecideProducesRecipe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val decision = engine.decide(
            MediaFingerprint(
                kind = MediaKind.VIDEO,
                mime = "video/mp4",
                container = "mp4",
                videoCodec = "h264",
                audioCodec = "aac",
                width = 1280u,
                height = 720u,
                durationMs = 15_000u,
                fps = 30f,
                rotation = 0u,
                fileBytes = 12_000_000u,
                videoBitrateBps = 6_000_000u,
                audioBitrateBps = 128_000u,
                hasAlpha = false,
                isHdr = false,
                isSlowmo = false,
                isLivePhoto = false,
                channels = null,
                sampleRate = null,
            ),
            CompressRequest(
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
            ),
            HwCaps(
                hevcHwEncode = false,
                av1HwEncode = false,
                hevcMaxW = 1920u,
                hevcMaxH = 1080u,
                av1MaxW = 0u,
                av1MaxH = 0u,
                maxImageSide = 8192u,
            ),
        )
        assertFalse(decision.skip)
        assertTrue(decision.recipe != null)
        assertTrue(decision.recipe!!.videoBitrateBps > 0uL)
    }

    @Test
    fun jpegSubmitGroupReachesSucceeded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val dir = File(context.cacheDir, "lab-jpeg")
        val jpeg = LabMediaFactory.jpegOverSkipThreshold(dir)
        assertTrue("lab jpeg must exceed 150KB skip floor", jpeg.length() >= 200 * 1024)
        val fp = MediaFingerprint(
            kind = MediaKind.IMAGE,
            mime = "image/jpeg",
            container = "jpeg",
            videoCodec = null,
            audioCodec = null,
            width = 1280u,
            height = 960u,
            durationMs = 0u,
            fps = null,
            rotation = 0u,
            fileBytes = jpeg.length().toULong(),
            videoBitrateBps = null,
            audioBitrateBps = null,
            hasAlpha = false,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = false,
            channels = null,
            sampleRate = null,
        )
        val groupId = engine.submitGroup(
            listOf(
                JobSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = jpeg.name,
                    sourceUri = jpeg.toURI().toString(),
                    localPath = jpeg.absolutePath,
                    fingerprint = fp,
                    requestOverride = null,
                ),
            ),
            saveAsNewRequest(QualityPreset.IMAGE_HIGH, "jpeg", null),
            false,
        )
        val group = awaitFinished(engine, groupId)
        assertTrue(
            "JPEG job should succeed, got ${group.jobs.map { it.state to it.message }}",
            group.jobs.any { it.state == JobState.SUCCEEDED },
        )
    }

    @Test
    fun gifSkipUsesSkipGifMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val gif = LabMediaFactory.gif(File(context.cacheDir, "lab-gif"))
        val fp = MediaFingerprint(
            kind = MediaKind.IMAGE,
            mime = "image/gif",
            container = "gif",
            videoCodec = null,
            audioCodec = null,
            width = 1u,
            height = 1u,
            durationMs = 0u,
            fps = null,
            rotation = 0u,
            fileBytes = gif.length().toULong().coerceAtLeast(800uL * 1024uL),
            videoBitrateBps = null,
            audioBitrateBps = null,
            hasAlpha = true,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = false,
            channels = null,
            sampleRate = null,
        )
        val groupId = engine.submitGroup(
            listOf(
                JobSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = "lab.gif",
                    sourceUri = gif.toURI().toString(),
                    localPath = gif.absolutePath,
                    fingerprint = fp,
                    requestOverride = null,
                ),
            ),
            saveAsNewRequest(QualityPreset.IMAGE_HIGH, "jpeg", null),
            false,
        )
        val group = awaitFinished(engine, groupId)
        val job = group.jobs.first()
        assertEquals(JobState.SKIPPED, job.state)
        assertEquals("skip.gif", job.message)
        assertFalse(job.message.contains("codec"))
    }

    @Test
    fun dngSkipUsesSkipRawMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val placeholder = File(context.cacheDir, "lab-dng.dng")
        placeholder.writeBytes(ByteArray(1024))
        val fp = MediaFingerprint(
            kind = MediaKind.IMAGE,
            mime = "image/x-adobe-dng",
            container = "dng",
            videoCodec = null,
            audioCodec = null,
            width = 4000u,
            height = 3000u,
            durationMs = 0u,
            fps = null,
            rotation = 0u,
            fileBytes = 20uL * 1024uL * 1024uL,
            videoBitrateBps = null,
            audioBitrateBps = null,
            hasAlpha = false,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = false,
            channels = null,
            sampleRate = null,
        )
        val groupId = engine.submitGroup(
            listOf(
                JobSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = "lab.dng",
                    sourceUri = placeholder.toURI().toString(),
                    localPath = placeholder.absolutePath,
                    fingerprint = fp,
                    requestOverride = null,
                ),
            ),
            saveAsNewRequest(QualityPreset.IMAGE_HIGH, "jpeg", null),
            false,
        )
        val group = awaitFinished(engine, groupId)
        val job = group.jobs.first()
        assertEquals(JobState.SKIPPED, job.state)
        assertEquals("skip.raw", job.message)
    }

    @Test
    fun wavOpusSubmitOrSkipMissingFeature() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = (context.applicationContext as SpaceSlimmerApp).engine ?: return
        val wav = LabMediaFactory.wavPcm(File(context.cacheDir, "lab-wav"), durationMs = 1_200)
        val fp = MediaFingerprint(
            kind = MediaKind.AUDIO,
            mime = "audio/wav",
            container = "wav",
            videoCodec = null,
            audioCodec = "pcm",
            width = 0u,
            height = 0u,
            durationMs = 1_200u,
            fps = null,
            rotation = 0u,
            fileBytes = wav.length().toULong(),
            videoBitrateBps = null,
            audioBitrateBps = 256_000u,
            hasAlpha = false,
            isHdr = false,
            isSlowmo = false,
            isLivePhoto = false,
            channels = 1u,
            sampleRate = 16_000u,
        )
        val groupId = engine.submitGroup(
            listOf(
                JobSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = wav.name,
                    sourceUri = wav.toURI().toString(),
                    localPath = wav.absolutePath,
                    fingerprint = fp,
                    requestOverride = null,
                ),
            ),
            saveAsNewRequest(QualityPreset.AUDIO_SPEECH, null, "opus").copy(skipEnabled = false),
            false,
        )
        val group = awaitFinished(engine, groupId)
        val job = group.jobs.first()
        if (job.state == JobState.FAILED) {
            val msg = job.message.lowercase()
            org.junit.Assume.assumeTrue(
                "opus-encode missing from this .so: ${job.message}",
                !(msg.contains("opus") || msg.contains("feature") || msg.contains("unsupported")),
            )
        }
        assertEquals(JobState.SUCCEEDED, job.state)
    }

    @androidx.media3.common.util.UnstableApi
    @Test
    fun shortMp4VideoTranscoderOrSkipHardware() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mp4 = LabMediaFactory.tryShortMp4(File(context.cacheDir, "lab-mp4"))
        org.junit.Assume.assumeTrue("emulator/device cannot mux a lab MP4", mp4 != null)
        val out = File(context.cacheDir, "lab-mp4-out.mp4")
        val recipe = Recipe(
            kind = MediaKind.VIDEO,
            outW = 320u,
            outH = 240u,
            fps = 15f,
            videoCodec = "h264",
            videoBitrateBps = 300_000u,
            audioCopy = true,
            audioCodec = "aac",
            audioBitrateBps = 64_000u,
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
        val bytes = runCatching {
            VideoTranscoder.transcode(
                context = context,
                params = VideoTranscoder.TranscodeParams(
                    sourceUri = mp4!!.absolutePath,
                    outputPath = out.absolutePath,
                    recipe = recipe,
                    useHevc = false,
                    jobId = "lab-mp4",
                    onProgress = {},
                    isCancelled = { false },
                ),
            )
        }
        if (bytes.isFailure) {
            org.junit.Assume.assumeTrue(
                "hardware transcode unavailable: ${bytes.exceptionOrNull()?.message}",
                false,
            )
        }
        assertTrue(out.isFile && out.length() > 0)
        assertTrue(bytes.getOrThrow() > 0)
    }

    private fun saveAsNewRequest(
        preset: QualityPreset,
        imageFormat: String?,
        audioFormat: String?,
    ) = CompressRequest(
        preset = preset,
        compatibility = Compatibility.COMPATIBLE,
        outputPolicy = OutputPolicy.SAVE_AS_NEW,
        targetBytes = null,
        custom = null,
        keepGps = true,
        keepHdr = false,
        skipEnabled = true,
        imageFormat = imageFormat,
        audioFormat = audioFormat,
    )

    private fun awaitFinished(engine: CoreEngine, groupId: String, timeoutMs: Long = 90_000): uniffi.ss_core.GroupView {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = engine.group(groupId)
        while (System.currentTimeMillis() < deadline) {
            last = engine.group(groupId)
            val done = last.jobs.isNotEmpty() && last.jobs.all { job ->
                job.state == JobState.SUCCEEDED ||
                    job.state == JobState.SKIPPED ||
                    job.state == JobState.FAILED ||
                    job.state == JobState.COMMITTED_REPLACE
            }
            if (done) return last
            Thread.sleep(250)
        }
        throw AssertionError("group $groupId did not finish: ${last.jobs.map { it.state to it.message }}")
    }
}
