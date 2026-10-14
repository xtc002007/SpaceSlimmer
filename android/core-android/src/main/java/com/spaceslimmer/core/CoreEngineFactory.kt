package com.spaceslimmer.core

import android.content.Context
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.EngineConfig
import uniffi.ss_core.ParallelProfile
import java.io.File

/**
 * Opens [CoreEngine] with sandbox directories. `tmp/` and `vault/` are excluded from backup.
 */
object CoreEngineFactory {
    const val DATA_DIR_NAME = "data"
    const val TMP_DIR_NAME = "tmp"
    const val VAULT_DIR_NAME = "vault"

    fun open(
        context: Context,
        profile: ParallelProfile = ParallelProfile.AUTO,
    ): CoreEngine {
        val root = context.filesDir
        val data = File(root, DATA_DIR_NAME).apply { mkdirs() }
        val tmp = File(root, TMP_DIR_NAME).apply { mkdirs() }
        val vault = File(root, VAULT_DIR_NAME).apply { mkdirs() }
        return CoreEngine.open(
            EngineConfig(
                dataDir = data.absolutePath,
                tmpDir = tmp.absolutePath,
                vaultDir = vault.absolutePath,
                parallelProfile = profile,
            ),
        )
    }
}
