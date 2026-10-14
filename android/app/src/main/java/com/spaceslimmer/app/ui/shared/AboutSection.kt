package com.spaceslimmer.app.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsSection
import uniffi.ss_core.CoreEngine

@Composable
fun AboutSection(
    engine: CoreEngine?,
    engineError: String?,
    modifier: Modifier = Modifier,
) {
    SsSection(
        title = stringResource(R.string.about_title),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (engine == null) {
                Text(
                    text = engineError ?: "CoreEngine unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                AboutInfoRow(stringResource(R.string.about_core_version), engine.version())
                AboutInfoRow(
                    stringResource(R.string.about_preset_version),
                    engine.presetTableVersion(),
                )
                AboutInfoRow(stringResource(R.string.about_data_dir), engine.dataDir())
                AboutInfoRow(stringResource(R.string.about_tmp_dir), engine.tmpDir())
                AboutInfoRow(stringResource(R.string.about_vault_dir), engine.vaultDir())
            }
            Text(
                text = stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
