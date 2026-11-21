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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.shared.formatBytes
import uniffi.ss_core.CategoryStatView

@Composable
fun CategoryBreakdownView(
    categoryStats: List<CategoryStatView>,
    totalScannedBytes: Long,
    modifier: Modifier = Modifier,
) {
    val total = if (totalScannedBytes > 0) totalScannedBytes else categoryStats.sumOf { it.bytes.toLong() }
    val validStats = categoryStats.filter { it.bytes > 0uL }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Stacked colored proportion bar
        if (total > 0 && validStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                validStats.forEach { stat ->
                    val weight = (stat.bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    if (weight > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(14.dp)
                                .background(parseHexColor(stat.colorHex)),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(validStats, key = { it.categoryKey }) { stat ->
                CategoryStatCard(
                    stat = stat,
                    totalBytes = total,
                )
            }
        }
    }
}

@Composable
private fun CategoryStatCard(
    stat: CategoryStatView,
    totalBytes: Long,
) {
    val catColor = parseHexColor(stat.colorHex)
    val percentage = if (totalBytes > 0) {
        (stat.bytes.toDouble() / totalBytes.toDouble() * 100.0)
    } else 0.0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(catColor),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stat.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = formatBytes(stat.bytes.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_cat_stat_format, stat.fileCount.toString(), String.format(java.util.Locale.getDefault(), "%.1f%%", percentage)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stat.isCompressible) {
                        ActionPill(
                            label = stringResource(R.string.storage_cat_compressible),
                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.15f),
                            textColor = Color(0xFF059669),
                        )
                    }
                    if (stat.isJunkCandidate) {
                        ActionPill(
                            label = stringResource(R.string.storage_cat_junk_candidate),
                            backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            textColor = Color(0xFFD97706),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((percentage / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(catColor),
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}
