package com.spaceslimmer.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsEmptyState
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.viewmodel.StorageScanViewModel
import uniffi.ss_core.ScanDiffView
import kotlin.math.abs

@Composable
fun ScanDiffScreen(
    viewModel: StorageScanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diffResult by viewModel.diffResult.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        SsAppBar(
            title = stringResource(R.string.storage_diff_title),
            onBack = onBack,
        )

        if (isBusy) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (diffResult == null || diffResult!!.isEmpty()) {
            SsEmptyState(
                title = stringResource(R.string.storage_diff_empty_title),
                body = stringResource(R.string.storage_diff_empty_body),
                modifier = Modifier.weight(1f),
            )
        } else {
            val list = diffResult!!
            val totalDeltaBytes = list.sumOf { it.deltaBytes }
            val totalDeltaFiles = list.sumOf { it.deltaFiles }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header Summary Card
                item {
                    val isFreed = totalDeltaBytes < 0
                    val isIncreased = totalDeltaBytes > 0
                    val deltaColor = when {
                        isFreed -> Color(0xFF10B981) // Green for freed
                        isIncreased -> Color(0xFFEF4444) // Red for grown
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val sign = if (isIncreased) "+" else ""

                    SsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.storage_diff_net_change),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$sign${formatBytes(totalDeltaBytes)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = deltaColor,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = (if (totalDeltaFiles >= 0) "+" else "") + stringResource(R.string.storage_diff_files_count, totalDeltaFiles),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }

                items(list, key = { it.categoryKey }) { diff ->
                    DiffCategoryRow(diff = diff)
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DiffCategoryRow(diff: ScanDiffView) {
    val isFreed = diff.deltaBytes < 0
    val isIncreased = diff.deltaBytes > 0
    val deltaColor = when {
        isFreed -> Color(0xFF10B981)
        isIncreased -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (isIncreased) "+" else ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = diff.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatBytes(diff.olderBytes.toLong())}  →  ${formatBytes(diff.newerBytes.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign${formatBytes(diff.deltaBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = deltaColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                val fileSign = if (diff.deltaFiles > 0) "+" else ""
                Text(
                    text = fileSign + stringResource(R.string.storage_diff_files_count, diff.deltaFiles),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
