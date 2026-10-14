package com.spaceslimmer.core



/**

 * Tracks per-job progress samples to estimate ETA and throughput (bytes/sec).

 */

class JobProgressTracker {

    private data class Sample(

        val timeMs: Long,

        val progress: Float,

        val bytesProcessed: Long,

    )



    private var lastSample: Sample? = null

    private var lastStats: ProgressStats = ProgressStats(0f, null, null)

    private var sourceBytes: Long = 0L



    fun reset(sourceBytes: Long = 0L) {

        this.sourceBytes = sourceBytes.coerceAtLeast(0L)

        lastSample = null

        lastStats = ProgressStats(0f, null, null)

    }



    fun stats(): ProgressStats = lastStats



    fun update(progress: Float, sourceBytes: Long = this.sourceBytes): ProgressStats {

        this.sourceBytes = sourceBytes.coerceAtLeast(0L)

        val clamped = progress.coerceIn(0f, 1f)

        val bytes = this.sourceBytes

        val now = System.currentTimeMillis()

        val bytesProcessed = (bytes * clamped).toLong()

        val prev = lastSample

        if (prev == null || bytes <= 0L) {

            lastSample = Sample(now, clamped, bytesProcessed)

            lastStats = ProgressStats(clamped, null, null)

            return lastStats

        }

        if (clamped <= prev.progress) {

            lastStats = ProgressStats(clamped, lastStats.etaSeconds, lastStats.bytesPerSecond)

            return lastStats

        }

        lastSample = Sample(now, clamped, bytesProcessed)

        val deltaSec = (now - prev.timeMs) / 1000.0

        if (deltaSec <= 0.0) {

            lastStats = ProgressStats(clamped, lastStats.etaSeconds, lastStats.bytesPerSecond)

            return lastStats

        }

        val deltaBytes = bytesProcessed - prev.bytesProcessed

        if (deltaBytes <= 0L) {

            lastStats = ProgressStats(clamped, lastStats.etaSeconds, lastStats.bytesPerSecond)

            return lastStats

        }

        val bytesPerSec = deltaBytes / deltaSec

        val remaining = bytes - bytesProcessed

        val etaSec = if (bytesPerSec > 0.0) (remaining / bytesPerSec).toLong() else null

        lastStats = ProgressStats(clamped, etaSec, bytesPerSec)

        return lastStats

    }



    data class ProgressStats(

        val progress: Float,

        val etaSeconds: Long?,

        val bytesPerSecond: Double?,

    )

}


