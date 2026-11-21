package com.spaceslimmer.app.ui.storage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsLargeTitle
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSection
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.viewmodel.ScanNavigationEvent
import com.spaceslimmer.app.viewmodel.ScanUiState
import com.spaceslimmer.app.viewmodel.StorageScanViewModel
import uniffi.ss_core.ScanSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ScanScopeChoice {
    FULL_DISK, CATEGORIES, PICK_FOLDER
}

@Composable
fun StorageHomeScreen(
    viewModel: StorageScanViewModel,
    onNavigateToProgress: (scanId: String) -> Unit,
    onNavigateToResult: (scanId: String) -> Unit,
    onNavigateToDiff: (olderId: String, newerId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val storage by viewModel.deviceStorage.collectAsState()
    val historyScans by viewModel.historicalScans.collectAsState()

    var scopeChoice by remember { mutableStateOf(ScanScopeChoice.FULL_DISK) }
    val selectedCategories = remember { mutableStateListOf("video", "image", "audio", "archive", "document", "package") }
    var pickedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFolderName by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            pickedFolderUri = uri
            pickedFolderName = uri.lastPathSegment ?: uri.toString()
            scopeChoice = ScanScopeChoice.PICK_FOLDER
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result handled */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is ScanNavigationEvent.NavigateToProgress -> onNavigateToProgress(event.scanId)
                is ScanNavigationEvent.NavigateToResult -> onNavigateToResult(event.scanId)
                is ScanNavigationEvent.NavigateToDiff -> onNavigateToDiff(event.olderId, event.newerId)
            }
        }
    }

    val hasFullAccess = viewModel.hasAllFilesAccess()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SsLargeTitle(
                title = stringResource(R.string.storage_analyzer_title),
            )
        }

        // 1. Device Storage Capacity Card
        item {
            StorageOverviewCard(
                totalBytes = storage.totalBytes,
                usedBytes = storage.usedBytes,
                freeBytes = storage.freeBytes,
            )
        }

        // 2. Permission Tier Banner (L1 / L2 / L3)
        item {
            PermissionTierCard(
                hasFullAccess = hasFullAccess,
                onGrantFullAccess = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                },
            )
        }

        // 3. Scan Scope Configuration
        item {
            SsSection(title = stringResource(R.string.storage_scope_title)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val scopeOptions = listOf(
                        SsSegmentOption(ScanScopeChoice.FULL_DISK, stringResource(R.string.storage_scope_full)),
                        SsSegmentOption(ScanScopeChoice.CATEGORIES, stringResource(R.string.storage_scope_category)),
                        SsSegmentOption(ScanScopeChoice.PICK_FOLDER, stringResource(R.string.storage_scope_folder)),
                    )

                    SsSegmented(
                        options = scopeOptions,
                        selected = scopeChoice,
                        onSelected = { choice ->
                            scopeChoice = choice
                            if (choice == ScanScopeChoice.PICK_FOLDER && pickedFolderUri == null) {
                                folderPicker.launch(null)
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when (scopeChoice) {
                        ScanScopeChoice.FULL_DISK -> {
                            Text(
                                text = stringResource(R.string.storage_scope_full_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ScanScopeChoice.CATEGORIES -> {
                            Text(
                                text = stringResource(R.string.storage_scope_cat_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CategoryFilterChips(
                                selected = selectedCategories,
                                onToggle = { cat ->
                                    if (selectedCategories.contains(cat)) {
                                        if (selectedCategories.size > 1) selectedCategories.remove(cat)
                                    } else {
                                        selectedCategories.add(cat)
                                    }
                                }
                            )
                        }
                        ScanScopeChoice.PICK_FOLDER -> {
                            if (pickedFolderUri != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.storage_scope_folder_picked, pickedFolderName ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    OutlinedButton(
                                        onClick = { folderPicker.launch(null) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(stringResource(R.string.storage_btn_change), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { folderPicker.launch(null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.storage_scope_pick_folder_btn),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Hero Start Scan Button
        item {
            SsPrimaryButton(
                text = stringResource(R.string.storage_btn_start_scan),
                onClick = {
                    when (scopeChoice) {
                        ScanScopeChoice.FULL_DISK -> {
                            viewModel.startScan(
                                scopeType = "FullDisk",
                                rootPath = Environment.getExternalStorageDirectory().absolutePath,
                            )
                        }
                        ScanScopeChoice.CATEGORIES -> {
                            viewModel.startScan(
                                scopeType = "Categories",
                                rootPath = Environment.getExternalStorageDirectory().absolutePath,
                                selectedCategories = selectedCategories.toList(),
                            )
                        }
                        ScanScopeChoice.PICK_FOLDER -> {
                            val uri = pickedFolderUri
                            if (uri != null) {
                                viewModel.startScan(
                                    scopeType = "Folders",
                                    rootPath = uri.toString(),
                                    isSaf = true,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 5. Recent Scan Snapshots Section
        item {
            SsSection(title = stringResource(R.string.storage_recent_scans)) {
                if (historyScans.isEmpty()) {
                    Text(
                        text = stringResource(R.string.storage_no_recent_scans),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        historyScans.take(5).forEach { scan ->
                            HistoricalScanRow(
                                scan = scan,
                                onOpen = {
                                    viewModel.openHistoricalScan(scan.id)
                                },
                                onDelete = { viewModel.deleteHistoricalScan(scan.id) },
                            )
                        }

                        if (historyScans.size >= 2) {
                            OutlinedButton(
                                onClick = {
                                    val older = historyScans[1].id
                                    val newer = historyScans[0].id
                                    viewModel.compareScans(older, newer)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CompareArrows,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.storage_btn_diff_compare))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StorageOverviewCard(
    totalBytes: Long,
    usedBytes: Long,
    freeBytes: Long,
    physicalTotalBytes: Long = 0L,
    systemFirmwareBytes: Long = 0L,
    categoryStats: List<uniffi.ss_core.CategoryStatView> = emptyList(),
) {
    SsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_internal_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.storage_metric_free, formatBytes(freeBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val validStats = categoryStats.filter { it.bytes > 0uL }
            if (totalBytes > 0 && validStats.isNotEmpty()) {
                // Multi-colored category stacked health bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    validStats.forEach { stat ->
                        val weight = (stat.bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        if (weight > 0.003f) {
                            Box(
                                modifier = Modifier
                                    .weight(weight, fill = true)
                                    .height(14.dp)
                                    .background(parseHexColor(stat.colorHex)),
                            )
                        }
                    }
                    val scannedTotal = validStats.sumOf { it.bytes.toLong() }
                    val remainingRatio = ((totalBytes - scannedTotal).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    if (remainingRatio > 0.003f) {
                        Box(
                            modifier = Modifier
                                .weight(remainingRatio, fill = true)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Legend chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    validStats.take(4).forEach { stat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(stat.colorHex)),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stat.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // Capacity Bar fallback
                val usedRatio = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(usedRatio)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.storage_overview_scan_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.storage_metric_used, formatBytes(usedBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (physicalTotalBytes > 0L) {
                        stringResource(R.string.storage_partition_spec, formatBytes(totalBytes), formatBytes(physicalTotalBytes))
                    } else {
                        stringResource(R.string.storage_metric_total, formatBytes(totalBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionTierCard(
    hasFullAccess: Boolean,
    onGrantFullAccess: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = Color(0xFF10B981).copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(28.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasFullAccess) {
                        stringResource(R.string.storage_tier_l3_title)
                    } else {
                        stringResource(R.string.storage_tier_l1_title)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasFullAccess) Color(0xFF065F46) else Color(0xFF92400E),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hasFullAccess) {
                        stringResource(R.string.storage_tier_l3_desc)
                    } else {
                        stringResource(R.string.storage_tier_l1_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Color(0xFF059669).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.storage_tier_status_granted),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF065F46),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChips(
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    val categories = listOf(
        Pair("video", stringResource(R.string.storage_cat_video)),
        Pair("image", stringResource(R.string.storage_cat_image)),
        Pair("audio", stringResource(R.string.storage_cat_audio)),
        Pair("archive", stringResource(R.string.storage_cat_archive)),
        Pair("document", stringResource(R.string.storage_cat_document)),
        Pair("package", stringResource(R.string.storage_cat_package)),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categories.take(3).forEach { (key, label) ->
            FilterChip(
                selected = selected.contains(key),
                onClick = { onToggle(key) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categories.drop(3).forEach { (key, label) ->
            FilterChip(
                selected = selected.contains(key),
                onClick = { onToggle(key) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoricalScanRow(
    scan: ScanSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(scan.createdAt) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(scan.createdAt * 1000L))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PieChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.storage_history_scan_summary, formatBytes(scan.scannedBytes.toLong()), scan.fileCount.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.storage_history_scan_meta, dateStr, scan.sourceLevel, scan.durationMs.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.storage_btn_delete_scan),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
