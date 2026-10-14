package com.spaceslimmer.app.viewmodel



import android.app.Application

import com.spaceslimmer.app.SpaceSlimmerApp

import com.spaceslimmer.app.core.EngineObserverBridge

import com.spaceslimmer.videohw.HwCapsProbe

import uniffi.ss_core.Compatibility

import uniffi.ss_core.CompressRequest

import uniffi.ss_core.CoreEngine

import uniffi.ss_core.Decision

import uniffi.ss_core.GroupView

import uniffi.ss_core.HwCaps

import uniffi.ss_core.JobState

import uniffi.ss_core.MediaFingerprint

import uniffi.ss_core.OutputPolicy

import uniffi.ss_core.QualityPreset

import uniffi.ss_core.VideoCustomParams



object CompressSession {

    fun engine(app: Application): CoreEngine? =

        (app as? SpaceSlimmerApp)?.engine



    fun bridge(app: Application): EngineObserverBridge? =

        (app as? SpaceSlimmerApp)?.observerBridge



    fun caps(): HwCaps = HwCapsProbe.probe()



    suspend fun estimate(

        engine: CoreEngine,

        fingerprints: List<MediaFingerprint>,

        request: CompressRequest,

    ): List<Decision> = engine.decideBatch(fingerprints, request, caps())



    fun isGroupFinished(group: GroupView): Boolean =

        group.jobs.isNotEmpty() && group.jobs.all { job ->

            job.state == JobState.SUCCEEDED ||

                job.state == JobState.SKIPPED ||

                job.state == JobState.FAILED ||

                job.state == JobState.COMMITTED_REPLACE

        }



    fun buildRequest(

        preset: QualityPreset,

        compatibility: Compatibility = Compatibility.COMPATIBLE,

        outputPolicy: OutputPolicy = OutputPolicy.SAVE_AS_NEW,

        targetBytes: ULong? = null,

        custom: VideoCustomParams? = null,

        imageFormat: String? = null,

        audioFormat: String? = null,

        engine: CoreEngine?,

    ): CompressRequest {

        val settings = engine?.settings()

        return CompressRequest(

            preset = preset,

            compatibility = compatibility,

            outputPolicy = outputPolicy,

            targetBytes = targetBytes,

            custom = custom,

            keepGps = settings?.keepGps ?: true,

            keepHdr = false,

            skipEnabled = settings?.skipEnabled ?: true,

            imageFormat = imageFormat,

            audioFormat = audioFormat,

        )

    }

}


