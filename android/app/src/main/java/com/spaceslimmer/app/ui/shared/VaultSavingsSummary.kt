package com.spaceslimmer.app.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.theme.savingsColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.OutputPolicy

@Composable
fun rememberVaultUsage(engine: CoreEngine?): Long {
    var usage by remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine) {
        usage = withContext(Dispatchers.IO) {
            runCatching { engine?.vaultUsage()?.toLong() ?: 0L }.getOrDefault(0L)
        }
    }
    return usage
}

@Composable
fun VaultSavingsSummary(
    gallerySavedBytes: Long,
    vaultUsageBytes: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.result_gallery_saved, formatBytes(context, gallerySavedBytes)),
            style = MaterialTheme.typography.titleMedium,
            color = savingsColor(),
        )
        Text(
            text = stringResource(R.string.vault_usage, formatBytes(context, vaultUsageBytes)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.vault_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GroupSavingsSummary(
    savedBytes: Long,
    replaceMode: Boolean,
    engine: CoreEngine?,
    totalSavedRes: Int,
) {
    val vaultUsage = rememberVaultUsage(engine)
    if (replaceMode) {
        VaultSavingsSummary(
            gallerySavedBytes = savedBytes,
            vaultUsageBytes = vaultUsage,
        )
    } else {
        val context = LocalContext.current
        Text(
            text = stringResource(totalSavedRes, formatBytes(context, savedBytes)),
            style = MaterialTheme.typography.titleMedium,
            color = savingsColor(),
        )
    }
}

fun isReplaceOutputPolicy(policy: OutputPolicy?): Boolean =
    policy == OutputPolicy.REPLACE_TO_VAULT || policy == OutputPolicy.REPLACE_AFTER_CONFIRM
