package com.spaceslimmer.app.ui.storage

import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.navigationBarsPadding
import com.spaceslimmer.core.DeviceStorageInfo
import com.spaceslimmer.app.ui.components.SsAlertDialog
import com.spaceslimmer.app.ui.components.SsDialogTextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsAlertDialog
import com.spaceslimmer.app.ui.components.SsDialogTextButton
import com.spaceslimmer.app.ui.components.SsSegmentOption
import com.spaceslimmer.app.ui.components.SsSegmented
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.viewmodel.StorageScanViewModel
import uniffi.ss_core.NodeView

private enum class ScanResultTab {
    TREEMAP, DIRECTORY, CATEGORIES, TOP_FILES, DUPLICATES
}

@Composable
fun ScanResultScreen(
    scanId: String,
    viewModel: StorageScanViewModel,
    onBack: () -> Unit,
    onCompressNodes: (List<NodeView>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(ScanResultTab.TREEMAP) }

    val mediaConsentAction by viewModel.mediaConsentAction.collectAsState()
    val mediaConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onMediaConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(mediaConsentAction) {
        val action = mediaConsentAction
        if (action != null) {
            try {
                mediaConsentLauncher.launch(
                    IntentSenderRequest.Builder(action.intentSender).build()
                )
            } catch (_: Throwable) {
                viewModel.onMediaConsentResult(false)
            }
        }
    }

    val currentSummary by viewModel.currentSummary.collectAsState()
    val currentNode by viewModel.currentNode.collectAsState()
    val breadcrumbTrail by viewModel.breadcrumbTrail.collectAsState()
    val children by viewModel.children.collectAsState()
    val treemapResult by viewModel.treemapResult.collectAsState()
    val categoryStats by viewModel.categoryStats.collectAsState()
    val topFiles by viewModel.topFiles.collectAsState()
    val duplicates by viewModel.duplicates.collectAsState()
    val selectedIds by viewModel.selectedNodeIds.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scanId) {
        if (currentSummary?.id != scanId) {
            viewModel.loadTreeRoot(scanId)
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == ScanResultTab.DUPLICATES && duplicates.isEmpty()) {
            viewModel.loadDuplicates()
        }
    }

    // Selected nodes lookup
    val allKnownNodes = remember(children, topFiles) {
        (children + topFiles).distinctBy { it.id }
    }
    val selectedNodes = remember(selectedIds, allKnownNodes) {
        allKnownNodes.filter { selectedIds.contains(it.id) }
    }
    val selectedBytes = remember(selectedNodes) {
        selectedNodes.sumOf { it.sizePhysical.toLong() }
    }
    val hasCompressibleSelected = remember(selectedNodes) {
        selectedNodes.any { it.isCompressible }
    }

    val totalScanned = currentSummary?.scannedBytes?.toLong() ?: 0L
    val currNode = currentNode
    val nodeTotalBytes = currNode?.sizePhysical?.toLong() ?: totalScanned
    val deviceStorage by viewModel.deviceStorage.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp)
        ) {
            // 1. Top Navigation & Breadcrumbs Bar
            TopBreadcrumbBar(
                trail = breadcrumbTrail,
                onBack = {
                    if (breadcrumbTrail.size > 1) {
                        viewModel.drillUp()
                    } else {
                        onBack()
                    }
                },
                onJumpBreadcrumb = { idx -> viewModel.jumpToBreadcrumb(idx) },
                onExportCsv = {
                    val file = viewModel.exportCsv(context, scanId)
                    if (file != null) {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.storage_btn_export_csv)))
                        } catch (_: Throwable) {}
                    }
                },
                onExportJson = {
                    val file = viewModel.exportJson(context, scanId)
                    if (file != null) {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.storage_btn_export_json)))
                        } catch (_: Throwable) {}
                    }
                },
            )

            // 2. Current Folder Summary Card
            CurrentFolderHeader(
                node = currNode,
                totalScannedBytes = totalScanned,
                deviceStorage = deviceStorage,
            )

            // 3. View Switcher Tabs
            val tabOptions = listOf(
                SsSegmentOption(ScanResultTab.TREEMAP, stringResource(R.string.storage_view_treemap)),
                SsSegmentOption(ScanResultTab.DIRECTORY, stringResource(R.string.storage_view_directory)),
                SsSegmentOption(ScanResultTab.CATEGORIES, stringResource(R.string.storage_view_categories)),
                SsSegmentOption(ScanResultTab.TOP_FILES, stringResource(R.string.storage_view_top_files)),
                SsSegmentOption(ScanResultTab.DUPLICATES, stringResource(R.string.storage_view_duplicates)),
            )

            SsSegmented(
                options = tabOptions,
                selected = currentTab,
                onSelected = { currentTab = it },
                scrollable = true,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )

            // 4. Tab Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                when (currentTab) {
                    ScanResultTab.TREEMAP -> {
                        TreemapCanvasView(
                            treemap = treemapResult,
                            onSizeChanged = { w, h -> viewModel.updateTreemapDimensions(w, h) },
                            onDrillDown = { item ->
                                val childNode = children.firstOrNull { it.id == item.id }
                                if (childNode != null) {
                                    viewModel.drillDown(childNode)
                                }
                            },
                            onCompressItem = { item ->
                                val childNode = children.firstOrNull { it.id == item.id }
                                if (childNode != null) {
                                    onCompressNodes(listOf(childNode))
                                }
                            },
                        )
                    }
                    ScanResultTab.DIRECTORY -> {
                        DirectoryListView(
                            children = children,
                            selectedIds = selectedIds,
                            onToggleSelect = { id -> viewModel.toggleSelectNode(id) },
                            onDrillDown = { node -> viewModel.drillDown(node) },
                            parentTotalBytes = nodeTotalBytes,
                        )
                    }
                    ScanResultTab.CATEGORIES -> {
                        CategoryBreakdownView(
                            categoryStats = categoryStats,
                            totalScannedBytes = nodeTotalBytes,
                        )
                    }
                    ScanResultTab.TOP_FILES -> {
                        TopBigFilesView(
                            topFiles = topFiles,
                            selectedIds = selectedIds,
                            onToggleSelect = { id -> viewModel.toggleSelectNode(id) },
                            onSelectAll = {
                                viewModel.selectAllNodes(topFiles.map { it.id })
                            },
                            onClearSelection = { viewModel.clearSelection() },
                        )
                    }
                    ScanResultTab.DUPLICATES -> {
                        DuplicateFilesView(
                            duplicates = duplicates,
                            isLoading = isBusy,
                            onCleanDuplicateItem = { item ->
                                viewModel.deleteDuplicateItem(item) { success ->
                                    if (success) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.storage_delete_success, 1, formatBytes(File(item.path).length()))
                                            )
                                        }
                                    }
                                }
                            },
                            onCleanAllDuplicates = {
                                viewModel.cleanAllDuplicates(moveToVault = true) { count, bytes ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.storage_delete_success, count, formatBytes(bytes))
                                        )
                                    }
                                }
                            },
                            onRefresh = { viewModel.loadDuplicates() },
                        )
                    }
                }
            }
        }

        // 5. Floating Bottom Action Bar for Multi-Select Disposal
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_selected_count, selectedIds.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatBytes(selectedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (hasCompressibleSelected) {
                        Button(
                            onClick = { onCompressNodes(selectedNodes) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.storage_action_compress))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.storage_action_delete))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.storage_clear_selection),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        var moveToVaultOption by remember { mutableStateOf(true) }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            SsAlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(stringResource(R.string.storage_delete_confirm_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.storage_delete_confirm_body,
                                selectedNodes.size,
                                formatBytes(selectedBytes),
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { moveToVaultOption = !moveToVaultOption }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = moveToVaultOption,
                                onCheckedChange = { moveToVaultOption = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.storage_vault_backup_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.storage_vault_backup_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    SsDialogTextButton(
                        text = if (moveToVaultOption) stringResource(R.string.storage_action_delete_vault) else stringResource(R.string.storage_action_delete),
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteSelectedNodes(selectedNodes, moveToVault = moveToVaultOption) { count, bytes ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.storage_delete_success, count, formatBytes(bytes))
                                    )
                                }
                            }
                        },
                    )
                },
                dismissButton = {
                    SsDialogTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showDeleteConfirmDialog = false },
                    )
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (selectedIds.isNotEmpty()) 88.dp else 16.dp),
        )
    }
}

@Composable
private fun TopBreadcrumbBar(
    trail: List<NodeView>,
    onBack: () -> Unit,
    onJumpBreadcrumb: (Int) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(trail.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.storage_back),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Breadcrumb path chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trail.forEachIndexed { index, node ->
                    val isLast = index == trail.lastIndex
                    val label = if (index == 0) stringResource(R.string.storage_root_label) else node.name

                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onJumpBreadcrumb(index) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1,
                    )

                    if (!isLast) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            var showExportMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.storage_btn_export_csv),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.storage_btn_export_csv)) },
                        onClick = {
                            showExportMenu = false
                            onExportCsv()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.storage_btn_export_json)) },
                        onClick = {
                            showExportMenu = false
                            onExportJson()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentFolderHeader(
    node: NodeView?,
    totalScannedBytes: Long,
    deviceStorage: DeviceStorageInfo? = null,
) {
    if (node == null) return

    val nodeBytes = node.sizePhysical.toLong()
    val percentage = if (totalScannedBytes > 0) {
        (nodeBytes.toDouble() / totalScannedBytes.toDouble() * 100.0)
    } else 100.0

    var showExplanationDialog by remember { mutableStateOf(false) }
    val isRoot = node.name.isEmpty() || node.name == "/storage/emulated/0"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val title = if (isRoot) stringResource(R.string.storage_internal_user_partition) else node.name
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.storage_dir_summary, node.fileCount.toString(), node.dirCount.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatBytes(nodeBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.storage_scanned_percentage, String.format(java.util.Locale.US, "%.1f", percentage)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // If viewing root and deviceStorage has physical capacity info, show hardware ledger banner
            if (isRoot && deviceStorage != null && deviceStorage.physicalTotalBytes > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExplanationDialog = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.storage_ledger_summary,
                                    formatBytes(deviceStorage.physicalTotalBytes),
                                    formatBytes(deviceStorage.systemFirmwareBytes)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.storage_ledger_view_btn),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showExplanationDialog && deviceStorage != null) {
        SsAlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.storage_ledger_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.storage_ledger_dialog_desc,
                            formatBytes(deviceStorage.physicalTotalBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.storage_ledger_item_1), style = MaterialTheme.typography.labelSmall)
                            Text(stringResource(R.string.storage_ledger_item_2), style = MaterialTheme.typography.labelSmall)
                            Text(stringResource(R.string.storage_ledger_item_3), style = MaterialTheme.typography.labelSmall)
                            Text(
                                stringResource(R.string.storage_ledger_item_4, formatBytes(deviceStorage.totalBytes)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.storage_ledger_dialog_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                SsDialogTextButton(text = stringResource(R.string.action_got_it), onClick = { showExplanationDialog = false })
            },
        )
    }
}
