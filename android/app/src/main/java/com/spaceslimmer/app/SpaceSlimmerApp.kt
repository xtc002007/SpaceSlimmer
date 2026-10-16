package com.spaceslimmer.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.spaceslimmer.app.core.EngineObserverBridge
import com.spaceslimmer.app.service.CompressForegroundService
import com.spaceslimmer.app.worker.IdleChargeWorker
import com.spaceslimmer.app.worker.QueueRecoveryWorker
import com.spaceslimmer.app.worker.VaultExpiryWorker
import com.spaceslimmer.core.AppLogger
import com.spaceslimmer.core.CoreEngineFactory
import com.spaceslimmer.core.LocaleHelper
import com.spaceslimmer.core.SystemMonitor
import com.spaceslimmer.videohw.HwCapsProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uniffi.ss_core.CoreEngine

class SpaceSlimmerApp : Application() {
    var engine: CoreEngine? = null
        private set

    var engineError: String? = null
        private set

    var observerBridge: EngineObserverBridge? = null
        private set

    var thermalThrottled: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("SpaceSlimmerApp", "Application starting...")
        CompressForegroundService.ensureChannel(this)
        try {
            val core = CoreEngineFactory.open(this)
            core.setHwCaps(HwCapsProbe.probe())
            core.setDualVideoHw(HwCapsProbe.supportsDualVideoEncode())

            val bridge = EngineObserverBridge(this) { engine }
            core.setObserver(bridge)
            syncEngineLanguage(core)
            observerBridge = bridge
            engine = core

            if (core.settings().idleChargeOnly) {
                runCatching {
                    core.idleChargeRecoveryGroupIds()
                        .forEach { groupId -> IdleChargeWorker.enqueue(this, groupId) }
                }
            }

            SystemMonitor.start(this, { engine }) { throttled ->
                thermalThrottled = throttled
            }

            VaultExpiryWorker.schedule(this)
            QueueRecoveryWorker.schedule(this)
            registerProcessLifecycleObserver()
            cleanupStaleStorage()
        } catch (t: Throwable) {
            engineError = t.message ?: t::class.java.simpleName
        }
    }

    private fun cleanupStaleStorage() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            // 1. Clean stale transcode output files in filesDir/tmp (frees 3.59 GB!)
            runCatching {
                val tmpDir = java.io.File(filesDir, "tmp")
                if (tmpDir.isDirectory) {
                    var freed = 0L
                    tmpDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val len = file.length()
                            if (file.delete()) freed += len
                        }
                    }
                    if (freed > 0) {
                        AppLogger.i("SpaceSlimmerApp", "Startup storage cleanup freed ${freed / (1024 * 1024)} MB from files/tmp")
                    }
                }
            }

            // 2. Clean stale cache files in cacheDir (frees 2.10 GB!)
            runCatching {
                val radarQueue = java.io.File(cacheDir, "radar-queue")
                if (radarQueue.isDirectory) {
                    radarQueue.deleteRecursively()
                }
                val importCache = java.io.File(cacheDir, "import")
                if (importCache.isDirectory) {
                    importCache.deleteRecursively()
                }
                val stagingDir = java.io.File(cacheDir, "spaceslimmer/staging")
                if (stagingDir.isDirectory) {
                    stagingDir.deleteRecursively()
                }
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("planb-import-") || file.name.startsWith("spaceslimmer-")) {
                        file.delete()
                    }
                }
            }

            // 3. Clean stale probe import files in filesDir/spaceslimmer/import
            runCatching {
                val importDir = java.io.File(filesDir, "spaceslimmer/import")
                com.spaceslimmer.core.MediaProbe.cleanStaleImports(importDir, maxAgeMs = 15 * 60 * 1000L)
            }
        }
    }

    private fun syncEngineLanguage(core: CoreEngine) {
        val stored = LocaleHelper.storedLanguage(this) ?: return
        val current = core.settings()
        if (LocaleHelper.selectorCode(current.language) != LocaleHelper.selectorCode(stored)) {
            runCatching { core.setSettings(current.copy(language = LocaleHelper.selectorCode(stored))) }
        }
    }

    private fun registerProcessLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (CompressForegroundService.isRunning()) {
                        AppLogger.d("SpaceSlimmerApp", "App backgrounded, but CompressForegroundService is running. Continuing compression.")
                        return
                    }
                    pauseOnBackground()
                }

                override fun onStart(owner: LifecycleOwner) {
                    resumeOnForeground()
                }
            },
        )
    }

    private fun pauseOnBackground() {
        val core = engine ?: return
        runBlocking(Dispatchers.IO) {
            runCatching { core.pauseOnBackground() }
        }
    }

    private fun resumeOnForeground() {
        val core = engine ?: return
        runBlocking(Dispatchers.IO) {
            runCatching { core.resumeOnForeground() }
        }
    }

    override fun onTerminate() {
        SystemMonitor.stop()
        super.onTerminate()
    }
}
