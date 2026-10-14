package com.spaceslimmer.core

import uniffi.ss_core.CoreEngine

/** Runtime feature probes on the linked `libss_core.so`. */
object CoreFeatures {
    fun opusEncodeAvailable(engine: CoreEngine?): Boolean {
        engine ?: return false
        return runCatching { engine.opusEncodeAvailable() }.getOrDefault(false)
    }
}
