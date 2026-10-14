package com.spaceslimmer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.ui.theme.savingsColor

@Composable
fun SsHeroMetric(
    title: String? = null,
    value: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hint: String? = null,
    loading: Boolean = false,
    embedded: Boolean = false,
    valueColor: Color = savingsColor(),
    action: @Composable () -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 8.dp),
                    strokeWidth = 2.dp,
                )
            } else if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = valueColor,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action()
        }
    }
    if (embedded) {
        Column(modifier = modifier) {
            content()
        }
    } else {
        SsCard(modifier = modifier) {
            content()
        }
    }
}
