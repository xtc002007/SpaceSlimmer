package com.spaceslimmer.app.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.app.ui.theme.savingsColor

const val GB_SHARE_THRESHOLD = 1L * 1024L * 1024L * 1024L

@Composable
fun GbShareCard(
    savedBytes: Long,
    modifier: Modifier = Modifier,
) {
    if (savedBytes < GB_SHARE_THRESHOLD) return
    val context = LocalContext.current
    SsCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.share_gb_card_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.share_gb_card_body, formatBytes(context, savedBytes)),
                style = MaterialTheme.typography.headlineSmall,
                color = savingsColor(),
            )
            SsSecondaryButton(
                text = stringResource(R.string.share_savings),
                onClick = { shareSavedBytes(context, savedBytes) },
            )
        }
    }
}
