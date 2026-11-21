package com.spaceslimmer.app.ui.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsEmptyState
import com.spaceslimmer.app.ui.shared.formatBytes
import uniffi.ss_core.NodeView

private enum class SortOrder {
    SIZE_DESC, NAME_ASC, FILES_DESC
}

@Composable
fun DirectoryListView(
    children: List<NodeView>,
    selectedIds: Set<UInt>,
    onToggleSelect: (UInt) -> Unit,
    onDrillDown: (NodeView) -> Unit,
    parentTotalBytes: Long,
    modifier: Modifier = Modifier,
) {
    var sortOrder by remember { mutableStateOf(SortOrder.SIZE_DESC) }

    val sortedList = remember(children, sortOrder) {
        when (sortOrder) {
            SortOrder.SIZE_DESC -> children.sortedByDescending { it.sizePhysical.toLong() }
            SortOrder.NAME_ASC -> children.sortedBy { it.name.lowercase() }
            SortOrder.FILES_DESC -> children.sortedByDescending { it.fileCount.toLong() }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sort toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.storage_items_count, children.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        sortOrder = when (sortOrder) {
                            SortOrder.SIZE_DESC -> SortOrder.NAME_ASC
                            SortOrder.NAME_ASC -> SortOrder.FILES_DESC
                            SortOrder.FILES_DESC -> SortOrder.SIZE_DESC
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                val sortLabel = when (sortOrder) {
                    SortOrder.SIZE_DESC -> stringResource(R.string.storage_sort_size)
                    SortOrder.NAME_ASC -> stringResource(R.string.storage_sort_name)
                    SortOrder.FILES_DESC -> stringResource(R.string.storage_sort_files)
                }
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (sortedList.isEmpty()) {
            SsEmptyState(
                title = stringResource(R.string.storage_folder_empty_title),
                body = stringResource(R.string.storage_folder_empty_body),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sortedList, key = { it.id.toLong() }) { node ->
                    DirectoryItemRow(
                        node = node,
                        isSelected = selectedIds.contains(node.id),
                        onToggleSelect = { onToggleSelect(node.id) },
                        onDrillDown = { onDrillDown(node) },
                        parentTotalBytes = parentTotalBytes,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryItemRow(
    node: NodeView,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDrillDown: () -> Unit,
    parentTotalBytes: Long,
) {
    val categoryColor = parseHexColor(node.colorHex)
    val ratio = if (parentTotalBytes > 0) {
        (node.sizePhysical.toFloat() / parentTotalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (node.isDir) {
                    onDrillDown()
                } else {
                    onToggleSelect()
                }
            },
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(28.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (node.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val meta = if (node.isDir) {
                        "${formatBytes(node.sizePhysical.toLong())} · ${node.fileCount.toString()} files"
                    } else {
                        "${formatBytes(node.sizePhysical.toLong())} · ${node.categoryKey.uppercase()}"
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (node.isDir) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Proportion bar
            if (ratio > 0.005f) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(categoryColor),
                    )
                }
            }
        }
    }
}
