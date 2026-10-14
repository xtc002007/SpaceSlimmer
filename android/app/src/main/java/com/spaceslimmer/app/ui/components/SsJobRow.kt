package com.spaceslimmer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spaceslimmer.app.ui.theme.savingsColor
import uniffi.ss_core.MediaKind

@Composable
fun SsJobRow(
    title: String,
    subtitle: String? = null,
    meta: String? = null,
    thumbnailUri: String? = null,
    thumbnailContentDescription: String? = null,
    mediaKind: MediaKind? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    } else {
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (checked != null && onCheckedChange != null) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }

        if (thumbnailUri != null) {
            if (mediaKind != null) {
                MediaThumbnail(
                    uri = thumbnailUri,
                    kind = mediaKind,
                    onClick = onClick,
                )
            } else {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = thumbnailContentDescription,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = savingsColor(),
                )
            }
        }

        trailing()
    }
}
