package com.spaceslimmer.app.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import androidx.activity.result.IntentSenderRequest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.app.PendingIntent
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spaceslimmer.app.service.ScanForegroundService
import com.spaceslimmer.core.DeviceStorageInfo
import com.spaceslimmer.core.MediaStoreDirLister
import com.spaceslimmer.core.SafDirLister
import com.spaceslimmer.core.StorageStatsProvider
import com.spaceslimmer.core.VaultManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ss_core.CategoryStatView
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.DuplicateGroupView
import uniffi.ss_core.DuplicateItemView
import uniffi.ss_core.NodeView
import uniffi.ss_core.ScanDiffView
import uniffi.ss_core.ScanObserver
import uniffi.ss_core.ScanProgressView
import uniffi.ss_core.ScanRequest
import uniffi.ss_core.ScanSummary
import uniffi.ss_core.TreemapViewResult

sealed interface ScanUiState {
    object Idle : ScanUiState
    data class Scanning(
        val scanId: String,
        val currentPath: String,
        val scannedBytes: Long,
        val scannedFiles: Int,
        val isPaused: Boolean,
    ) : ScanUiState
    data class Done(val summary: ScanSummary) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

sealed interface ScanNavigationEvent {
    data class NavigateToProgress(val scanId: String) : ScanNavigationEvent
    data class NavigateToResult(val scanId: String) : ScanNavigationEvent
    data class NavigateToDiff(val olderId: String, val newerId: String) : ScanNavigationEvent
}

data class MediaConsentAction(
    val intentSender: android.content.IntentSender,
    val pendingNodeIds: List<UInt>,
    val freedBytes: Long,
    val onFinished: (successCount: Int, freedBytes: Long) -> Unit,
)

class StorageScanViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>()
    private val contentResolver: ContentResolver get() = context.contentResolver

    val engine: CoreEngine? = CompressSession.engine(app)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _navEvents = Channel<ScanNavigationEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    private val _deviceStorage = MutableStateFlow(StorageStatsProvider.queryStorage(app))
    val deviceStorage: StateFlow<DeviceStorageInfo> = _deviceStorage.asStateFlow()

    private val _currentSummary = MutableStateFlow<ScanSummary?>(null)
    val currentSummary: StateFlow<ScanSummary?> = _currentSummary.asStateFlow()

    private val _currentNodeId = MutableStateFlow(0u)
    val currentNodeId: StateFlow<UInt> = _currentNodeId.asStateFlow()

    private val _currentNode = MutableStateFlow<NodeView?>(null)
    val currentNode: StateFlow<NodeView?> = _currentNode.asStateFlow()

    private val _breadcrumbTrail = MutableStateFlow<List<NodeView>>(emptyList())
    val breadcrumbTrail: StateFlow<List<NodeView>> = _breadcrumbTrail.asStateFlow()

    private val _children = MutableStateFlow<List<NodeView>>(emptyList())
    val children: StateFlow<List<NodeView>> = _children.asStateFlow()

    private val _treemapResult = MutableStateFlow<TreemapViewResult?>(null)
    val treemapResult: StateFlow<TreemapViewResult?> = _treemapResult.asStateFlow()

    private val _categoryStats = MutableStateFlow<List<CategoryStatView>>(emptyList())
    val categoryStats: StateFlow<List<CategoryStatView>> = _categoryStats.asStateFlow()

    private val _topFiles = MutableStateFlow<List<NodeView>>(emptyList())
    val topFiles: StateFlow<List<NodeView>> = _topFiles.asStateFlow()

    private val _duplicates = MutableStateFlow<List<DuplicateGroupView>>(emptyList())
    val duplicates: StateFlow<List<DuplicateGroupView>> = _duplicates.asStateFlow()

    private val _historicalScans = MutableStateFlow<List<ScanSummary>>(emptyList())
    val historicalScans: StateFlow<List<ScanSummary>> = _historicalScans.asStateFlow()

    private val _diffResult = MutableStateFlow<List<ScanDiffView>?>(null)
    val diffResult: StateFlow<List<ScanDiffView>?> = _diffResult.asStateFlow()

    private val _selectedNodeIds = MutableStateFlow<Set<UInt>>(emptySet())
    val selectedNodeIds: StateFlow<Set<UInt>> = _selectedNodeIds.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    private val _mediaConsentAction = MutableStateFlow<MediaConsentAction?>(null)
    val mediaConsentAction: StateFlow<MediaConsentAction?> = _mediaConsentAction.asStateFlow()

    fun onMediaConsentResult(success: Boolean) {
        val action = _mediaConsentAction.value ?: return
        _mediaConsentAction.value = null
        if (success) {
            val scanId = activeScanId ?: return
            viewModelScope.launch(Dispatchers.IO) {
                engine?.applyScanRemoved(scanId, action.pendingNodeIds)
                refreshCurrentNodeData(scanId, _currentNodeId.value)
                _currentNode.value = engine?.getScanNode(scanId, _currentNodeId.value)
                clearSelection()
                withContext(Dispatchers.Main) {
                    action.onFinished(action.pendingNodeIds.size, action.freedBytes)
                }
            }
        }
    }

    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var activeScanId: String? = null
    private var lastTreemapDims: Pair<Float, Float> = Pair(1000f, 1000f)

    init {
        refreshStorageMetrics()
        loadHistoricalScans()
    }

    fun refreshStorageMetrics() {
        _deviceStorage.value = StorageStatsProvider.queryStorage(getApplication())
    }

    fun resetToIdle() {
        _uiState.value = ScanUiState.Idle
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun startScan(
        scopeType: String = "FullDisk",
        rootPath: String = Environment.getExternalStorageDirectory().absolutePath,
        selectedCategories: List<String> = emptyList(),
        selectedFolders: List<String> = emptyList(),
        isSaf: Boolean = false,
    ) {
        val eng = engine ?: run {
            _uiState.value = ScanUiState.Error("CoreEngine is not available")
            return
        }

        refreshStorageMetrics()
        val metrics = _deviceStorage.value

        val sourceLevel = when {
            isSaf -> "L2"
            hasAllFilesAccess() -> "L3"
            else -> "L1"
        }

        val req = ScanRequest(
            rootPath = rootPath,
            scopeType = scopeType,
            selectedCategories = selectedCategories,
            selectedFolders = selectedFolders,
            collapseThresholdBytes = 256uL * 1024uL,
            includeHidden = false,
            minFileSize = 0uL,
            sourceLevel = sourceLevel,
            deviceTotalBytes = metrics.totalBytes.toULong(),
            deviceFreeBytes = metrics.freeBytes.toULong(),
            deviceAppsBytes = null,
        )

        val newScanId = "scan-" + UUID.randomUUID().toString()
        activeScanId = newScanId

        ScanForegroundService.start(getApplication())
        _uiState.value = ScanUiState.Scanning(
            scanId = newScanId,
            currentPath = rootPath,
            scannedBytes = 0L,
            scannedFiles = 0,
            isPaused = false,
        )
        viewModelScope.launch {
            _navEvents.send(ScanNavigationEvent.NavigateToProgress(newScanId))
        }

        viewModelScope.launch(Dispatchers.IO) {
            val observer = object : ScanObserver {
                override fun onProgress(progress: ScanProgressView) {
                    val currentId = activeScanId ?: newScanId
                    _uiState.value = ScanUiState.Scanning(
                        scanId = currentId,
                        currentPath = progress.currentPath,
                        scannedBytes = progress.scannedBytes.toLong(),
                        scannedFiles = progress.scannedFiles.toInt(),
                        isPaused = false,
                    )
                    ScanForegroundService.updateProgress(
                        getApplication(),
                        progress.scannedBytes.toLong(),
                        progress.scannedFiles.toInt(),
                        progress.currentPath,
                    )
                }

                override fun onDone(summary: ScanSummary) {
                    ScanForegroundService.stop(getApplication())
                    _uiState.value = ScanUiState.Done(summary)
                    _currentSummary.value = summary
                    activeScanId = summary.id
                    loadTreeRoot(summary.id)
                    loadHistoricalScans()
                    viewModelScope.launch {
                        _navEvents.send(ScanNavigationEvent.NavigateToResult(summary.id))
                    }
                }

                override fun onError(message: String) {
                    ScanForegroundService.stop(getApplication())
                    _uiState.value = ScanUiState.Error(message)
                }
            }

            val lister = when {
                isSaf -> SafDirLister(getApplication())
                sourceLevel == "L1" -> MediaStoreDirLister(getApplication(), selectedCategories)
                else -> null
            }

            try {
                val returnedScanId = eng.startScan(req, observer, lister)
                activeScanId = returnedScanId
            } catch (t: Throwable) {
                ScanForegroundService.stop(getApplication())
                _uiState.value = ScanUiState.Error(t.message ?: "Failed to start scan")
            }
        }
    }

    fun pauseScan() {
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine?.pauseScan(scanId)
                val curr = _uiState.value
                if (curr is ScanUiState.Scanning) {
                    _uiState.value = curr.copy(isPaused = true)
                }
            }
        }
    }

    fun resumeScan() {
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine?.resumeScan(scanId)
                val curr = _uiState.value
                if (curr is ScanUiState.Scanning) {
                    _uiState.value = curr.copy(isPaused = false)
                }
            }
        }
    }

    fun cancelScan() {
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine?.cancelScan(scanId)
                ScanForegroundService.stop(getApplication())
                _uiState.value = ScanUiState.Idle
            }
        }
    }

    fun loadTreeRoot(scanId: String) {
        activeScanId = scanId
        viewModelScope.launch(Dispatchers.IO) {
            val eng = engine ?: return@launch
            runCatching {
                val rootNode = eng.getScanNode(scanId, 0u)
                _currentNodeId.value = 0u
                _currentNode.value = rootNode
                _breadcrumbTrail.value = listOf(rootNode)
                refreshCurrentNodeData(scanId, 0u)
            }
        }
    }

    fun drillDown(node: NodeView) {
        if (!node.isDir) return
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _currentNodeId.value = node.id
            _currentNode.value = node
            val trail = _breadcrumbTrail.value.toMutableList()
            trail.add(node)
            _breadcrumbTrail.value = trail
            refreshCurrentNodeData(scanId, node.id)
        }
    }

    fun navigateUp() {
        val trail = _breadcrumbTrail.value
        if (trail.size <= 1) return
        val newTrail = trail.dropLast(1)
        val target = newTrail.last()
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _breadcrumbTrail.value = newTrail
            _currentNodeId.value = target.id
            _currentNode.value = target
            refreshCurrentNodeData(scanId, target.id)
        }
    }

    fun drillUp() = navigateUp()

    fun navigateToBreadcrumb(target: NodeView) {
        val trail = _breadcrumbTrail.value
        val idx = trail.indexOfFirst { it.id == target.id }
        if (idx < 0) return
        val newTrail = trail.take(idx + 1)
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _breadcrumbTrail.value = newTrail
            _currentNodeId.value = target.id
            _currentNode.value = target
            refreshCurrentNodeData(scanId, target.id)
        }
    }

    fun jumpToBreadcrumb(index: Int) {
        val trail = _breadcrumbTrail.value
        if (index in trail.indices) {
            navigateToBreadcrumb(trail[index])
        }
    }

    fun refreshTreemap(width: Float, height: Float) {
        lastTreemapDims = Pair(width, height)
        val scanId = activeScanId ?: return
        val nodeId = _currentNodeId.value
        viewModelScope.launch(Dispatchers.IO) {
            val eng = engine ?: return@launch
            runCatching {
                val res = eng.getTreemapLayout(
                    scanId = scanId,
                    nodeId = nodeId,
                    width = width,
                    height = height,
                    minRectPx = 8f,
                    maxItems = 200u,
                )
                _treemapResult.value = res
            }
        }
    }

    fun updateTreemapDimensions(width: Float, height: Float) = refreshTreemap(width, height)

    private suspend fun refreshCurrentNodeData(scanId: String, nodeId: UInt) {
        val eng = engine ?: return
        runCatching {
            _children.value = eng.getScanChildren(scanId, nodeId, 0u, 500u)
            _categoryStats.value = eng.getCategoryStats(scanId, nodeId)
            _topFiles.value = eng.getTopFiles(scanId, nodeId, 1024uL * 1024uL, 20u)
            val dims = lastTreemapDims
            _treemapResult.value = eng.getTreemapLayout(
                scanId = scanId,
                nodeId = nodeId,
                width = dims.first,
                height = dims.second,
                minRectPx = 8f,
                maxItems = 200u,
            )
        }
    }

    fun loadDuplicates() {
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val eng = engine
            if (eng != null) {
                runCatching {
                    val dupes = eng.findDuplicates(
                        scanId = scanId,
                        nodeId = _currentNodeId.value,
                        minSize = 100uL * 1024uL, // 100 KB
                        maxGroups = 100u,
                    )
                    _duplicates.value = dupes
                }
            }
            _isBusy.value = false
        }
    }

    fun toggleSelectNode(nodeId: UInt) {
        val current = _selectedNodeIds.value.toMutableSet()
        if (current.contains(nodeId)) {
            current.remove(nodeId)
        } else {
            current.add(nodeId)
        }
        _selectedNodeIds.value = current
    }

    fun selectAllNodes(nodeIds: Collection<UInt>) {
        _selectedNodeIds.value = nodeIds.toSet()
    }

    fun deselectAllNodes(nodeIds: Collection<UInt>) {
        _selectedNodeIds.value = _selectedNodeIds.value - nodeIds.toSet()
    }

    fun clearSelection() {
        _selectedNodeIds.value = emptySet()
    }

    fun deleteSelectedNodes(
        nodes: List<NodeView>,
        moveToVault: Boolean = false,
        onComplete: (successCount: Int, freedBytes: Long) -> Unit,
    ) {
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            var count = 0
            var bytes = 0L
            val removedIds = mutableListOf<UInt>()
            val pendingConsentUris = mutableListOf<Uri>()
            val pendingConsentIds = mutableListOf<UInt>()
            var pendingConsentBytes = 0L
            val eng = engine

            for (node in nodes) {
                if (node.isProtected) continue // Protected system files are NEVER deleted

                val path = node.path
                var sz = node.sizePhysical.toLong()
                var deleted = false

                val safUri = SafDirLister.getDocumentUri(path)
                val mediaUri = MediaStoreDirLister.getContentUri(path)

                if (safUri != null || path.startsWith("content://")) {
                    val uriToDelete = safUri ?: Uri.parse(path)
                    try {
                        if (moveToVault && eng != null) {
                            val jobId = "scan_del_" + UUID.randomUUID().toString().take(8)
                            val vaultResult = runCatching {
                                VaultManager.copyToVault(
                                    context = context,
                                    engine = eng,
                                    sourceUri = uriToDelete.toString(),
                                    localPath = "",
                                    jobId = jobId,
                                    vaultDays = 30u,
                                )
                            }
                            if (vaultResult.isFailure) {
                                continue
                            }
                        }
                        deleted = DocumentsContract.deleteDocument(contentResolver, uriToDelete)
                    } catch (_: Throwable) {}
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        sz = file.length()
                        if (moveToVault && eng != null) {
                            val jobId = "scan_del_" + UUID.randomUUID().toString().take(8)
                            val vaultResult = runCatching {
                                VaultManager.copyToVault(
                                    context = context,
                                    engine = eng,
                                    sourceUri = Uri.fromFile(file).toString(),
                                    localPath = file.absolutePath,
                                    jobId = jobId,
                                    vaultDays = 30u,
                                )
                            }
                            if (vaultResult.isFailure) {
                                continue
                            }
                        }
                        deleted = file.delete()
                        if (!deleted && mediaUri != null) {
                            try {
                                deleted = contentResolver.delete(mediaUri, null, null) > 0
                            } catch (_: Throwable) {
                                pendingConsentUris.add(mediaUri)
                                pendingConsentIds.add(node.id)
                                pendingConsentBytes += sz
                            }
                        }
                    } else if (mediaUri != null) {
                        try {
                            if (moveToVault && eng != null) {
                                val jobId = "scan_del_" + UUID.randomUUID().toString().take(8)
                                val vaultResult = runCatching {
                                    VaultManager.copyToVault(
                                        context = context,
                                        engine = eng,
                                        sourceUri = mediaUri.toString(),
                                        localPath = "",
                                        jobId = jobId,
                                        vaultDays = 30u,
                                    )
                                }
                                if (vaultResult.isFailure) {
                                    continue
                                }
                            }
                            deleted = contentResolver.delete(mediaUri, null, null) > 0
                        } catch (_: Throwable) {
                            pendingConsentUris.add(mediaUri)
                            pendingConsentIds.add(node.id)
                            pendingConsentBytes += sz
                        }
                    } else {
                        deleted = true
                    }
                }

                if (deleted) {
                    count++
                    bytes += sz
                    removedIds.add(node.id)
                }
            }

            if (removedIds.isNotEmpty()) {
                engine?.applyScanRemoved(scanId, removedIds)
                refreshCurrentNodeData(scanId, _currentNodeId.value)
                _currentNode.value = engine?.getScanNode(scanId, _currentNodeId.value)
            }

            clearSelection()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pendingConsentUris.isNotEmpty()) {
                val trashRequest = try {
                    MediaStore.createTrashRequest(contentResolver, pendingConsentUris, true)
                } catch (_: Throwable) {
                    try {
                        MediaStore.createDeleteRequest(contentResolver, pendingConsentUris)
                    } catch (_: Throwable) { null }
                }
                if (trashRequest != null) {
                    _mediaConsentAction.value = MediaConsentAction(
                        intentSender = trashRequest.intentSender,
                        pendingNodeIds = pendingConsentIds,
                        freedBytes = pendingConsentBytes,
                        onFinished = { c, b ->
                            onComplete(count + c, bytes + b)
                        }
                    )
                    _isBusy.value = false
                    return@launch
                }
            }

            _isBusy.value = false
            withContext(Dispatchers.Main) {
                onComplete(count, bytes)
            }
        }
    }

    fun cleanAllDuplicates(
        moveToVault: Boolean = false,
        onComplete: (cleanedCount: Int, freedBytes: Long) -> Unit,
    ) {
        val scanId = activeScanId ?: return
        val currentDupes = _duplicates.value
        if (currentDupes.isEmpty()) {
            onComplete(0, 0L)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            var count = 0
            var bytes = 0L
            val removedIds = mutableListOf<UInt>()
            val pendingConsentUris = mutableListOf<Uri>()
            val pendingConsentIds = mutableListOf<UInt>()
            var pendingConsentBytes = 0L
            val eng = engine

            for (group in currentDupes) {
                for (item in group.items) {
                    if (!item.isSuggestedKeep) {
                        val path = item.path
                        val safUri = SafDirLister.getDocumentUri(path)
                        val mediaUri = MediaStoreDirLister.getContentUri(path)
                        var deleted = false
                        var sz = 0L

                        if (safUri != null || path.startsWith("content://")) {
                            val uriToDelete = safUri ?: Uri.parse(path)
                            try {
                                if (moveToVault && eng != null) {
                                    val jobId = "scan_dupe_" + UUID.randomUUID().toString().take(8)
                                    val vaultResult = runCatching {
                                        VaultManager.copyToVault(
                                            context = context,
                                            engine = eng,
                                            sourceUri = uriToDelete.toString(),
                                            localPath = "",
                                            jobId = jobId,
                                            vaultDays = 30u,
                                        )
                                    }
                                    if (vaultResult.isFailure) continue
                                }
                                deleted = DocumentsContract.deleteDocument(contentResolver, uriToDelete)
                            } catch (_: Throwable) {}
                        } else {
                            val file = File(path)
                            if (file.exists()) {
                                sz = file.length()
                                if (moveToVault && eng != null) {
                                    val jobId = "scan_dupe_" + UUID.randomUUID().toString().take(8)
                                    val vaultResult = runCatching {
                                        VaultManager.copyToVault(
                                            context = context,
                                            engine = eng,
                                            sourceUri = Uri.fromFile(file).toString(),
                                            localPath = file.absolutePath,
                                            jobId = jobId,
                                            vaultDays = 30u,
                                        )
                                    }
                                    if (vaultResult.isFailure) continue
                                }
                                deleted = file.delete()
                                if (!deleted && mediaUri != null) {
                                    try {
                                        deleted = contentResolver.delete(mediaUri, null, null) > 0
                                    } catch (_: Throwable) {
                                        pendingConsentUris.add(mediaUri)
                                        pendingConsentIds.add(item.id)
                                        pendingConsentBytes += sz
                                    }
                                }
                            } else if (mediaUri != null) {
                                try {
                                    if (moveToVault && eng != null) {
                                        val jobId = "scan_dupe_" + UUID.randomUUID().toString().take(8)
                                        val vaultResult = runCatching {
                                            VaultManager.copyToVault(
                                                context = context,
                                                engine = eng,
                                                sourceUri = mediaUri.toString(),
                                                localPath = "",
                                                jobId = jobId,
                                                vaultDays = 30u,
                                            )
                                        }
                                        if (vaultResult.isFailure) continue
                                    }
                                    deleted = contentResolver.delete(mediaUri, null, null) > 0
                                } catch (_: Throwable) {
                                    pendingConsentUris.add(mediaUri)
                                    pendingConsentIds.add(item.id)
                                    pendingConsentBytes += sz
                                }
                            } else {
                                deleted = true
                            }
                        }

                        if (deleted) {
                            count++
                            bytes += sz
                            removedIds.add(item.id)
                        }
                    }
                }
            }

            if (removedIds.isNotEmpty()) {
                engine?.applyScanRemoved(scanId, removedIds)
                loadDuplicates()
                refreshCurrentNodeData(scanId, _currentNodeId.value)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pendingConsentUris.isNotEmpty()) {
                val trashRequest = try {
                    MediaStore.createTrashRequest(contentResolver, pendingConsentUris, true)
                } catch (_: Throwable) {
                    try {
                        MediaStore.createDeleteRequest(contentResolver, pendingConsentUris)
                    } catch (_: Throwable) { null }
                }
                if (trashRequest != null) {
                    _mediaConsentAction.value = MediaConsentAction(
                        intentSender = trashRequest.intentSender,
                        pendingNodeIds = pendingConsentIds,
                        freedBytes = pendingConsentBytes,
                        onFinished = { c, b ->
                            onComplete(count + c, bytes + b)
                        }
                    )
                    _isBusy.value = false
                    return@launch
                }
            }

            _isBusy.value = false
            withContext(Dispatchers.Main) {
                onComplete(count, bytes)
            }
        }
    }

    fun deleteDuplicateItem(
        item: DuplicateItemView,
        moveToVault: Boolean = false,
        onComplete: (Boolean) -> Unit,
    ) {
        val scanId = activeScanId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            val path = item.path
            val safUri = SafDirLister.getDocumentUri(path)
            val mediaUri = MediaStoreDirLister.getContentUri(path)
            val eng = engine

            if (safUri != null || path.startsWith("content://")) {
                val uriToDelete = safUri ?: Uri.parse(path)
                try {
                    if (moveToVault && eng != null) {
                        val jobId = "scan_dupe_item_" + UUID.randomUUID().toString().take(8)
                        val vaultResult = runCatching {
                            VaultManager.copyToVault(
                                context = context,
                                engine = eng,
                                sourceUri = uriToDelete.toString(),
                                localPath = "",
                                jobId = jobId,
                                vaultDays = 30u,
                            )
                        }
                        if (vaultResult.isSuccess) {
                            success = DocumentsContract.deleteDocument(contentResolver, uriToDelete)
                        }
                    } else {
                        success = DocumentsContract.deleteDocument(contentResolver, uriToDelete)
                    }
                } catch (_: Throwable) {}
            } else {
                val file = File(path)
                if (file.exists()) {
                    if (moveToVault && eng != null) {
                        val jobId = "scan_dupe_item_" + UUID.randomUUID().toString().take(8)
                        val vaultResult = runCatching {
                            VaultManager.copyToVault(
                                context = context,
                                engine = eng,
                                sourceUri = Uri.fromFile(file).toString(),
                                localPath = file.absolutePath,
                                jobId = jobId,
                                vaultDays = 30u,
                            )
                        }
                        if (vaultResult.isSuccess) {
                            success = file.delete()
                            if (!success && mediaUri != null) {
                                try {
                                    success = contentResolver.delete(mediaUri, null, null) > 0
                                } catch (_: Throwable) {}
                            }
                        }
                    } else {
                        success = file.delete()
                        if (!success && mediaUri != null) {
                            try {
                                success = contentResolver.delete(mediaUri, null, null) > 0
                            } catch (_: Throwable) {}
                        }
                    }
                } else if (mediaUri != null) {
                    try {
                        success = contentResolver.delete(mediaUri, null, null) > 0
                    } catch (_: Throwable) {}
                } else {
                    success = true
                }
            }

            if (success) {
                engine?.applyScanRemoved(scanId, listOf(item.id))
                loadDuplicates()
                refreshCurrentNodeData(scanId, _currentNodeId.value)
            } else if (mediaUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val trashRequest = try {
                    MediaStore.createTrashRequest(contentResolver, listOf(mediaUri), true)
                } catch (_: Throwable) {
                    try {
                        MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                    } catch (_: Throwable) { null }
                }
                if (trashRequest != null) {
                    _mediaConsentAction.value = MediaConsentAction(
                        intentSender = trashRequest.intentSender,
                        pendingNodeIds = listOf(item.id),
                        freedBytes = 0L,
                        onFinished = { _, _ -> onComplete(true) }
                    )
                    return@launch
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    private val _latestOverviewStats = MutableStateFlow<List<CategoryStatView>>(emptyList())
    val latestOverviewStats: StateFlow<List<CategoryStatView>> = _latestOverviewStats.asStateFlow()

    fun loadHistoricalScans() {
        val eng = engine ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val list = eng.listHistoricalScans(20u)
                _historicalScans.value = list
                val latest = list.firstOrNull()
                if (latest != null) {
                    _latestOverviewStats.value = eng.getCategoryStats(latest.id, 0u)
                }
            }
        }
    }

    fun openHistoricalScan(scanId: String) {
        val eng = engine ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            runCatching {
                val summary = eng.loadHistoricalScan(scanId)
                activeScanId = scanId
                _currentSummary.value = summary
                _uiState.value = ScanUiState.Done(summary)
                loadTreeRoot(scanId)
                _navEvents.send(ScanNavigationEvent.NavigateToResult(scanId))
            }
            _isBusy.value = false
        }
    }

    fun deleteHistoricalScan(scanId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine?.deleteHistoricalScan(scanId)
                loadHistoricalScans()
            }
        }
    }

    fun compareScans(olderId: String, newerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            runCatching {
                val diff = engine?.compareScans(olderId, newerId)
                _diffResult.value = diff
                _navEvents.send(ScanNavigationEvent.NavigateToDiff(olderId, newerId))
            }
            _isBusy.value = false
        }
    }

    fun exportCsv(context: android.content.Context, scanId: String): File? {
        val eng = engine ?: return null
        return try {
            val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val csvFile = File(exportDir, "SpaceSlimmer_Scan_${scanId.take(8)}.csv")
            eng.exportScanCsv(scanId, csvFile.absolutePath)
            csvFile
        } catch (_: Throwable) {
            null
        }
    }

    fun exportJson(context: android.content.Context, scanId: String): File? {
        val eng = engine ?: return null
        return try {
            val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val jsonFile = File(exportDir, "SpaceSlimmer_Scan_${scanId.take(8)}.json")
            val summary = eng.getScanSummary(scanId)
            val categoryStats: List<CategoryStatView> = eng.getCategoryStats(scanId, 0u)
            val topFiles: List<NodeView> = eng.getTopFiles(scanId, 0u, 0uL, 100u)

            val jsonObject = org.json.JSONObject().apply {
                put("scanId", summary.id)
                put("createdAt", summary.createdAt)
                put("sourceLevel", summary.sourceLevel)
                put("rootPath", summary.rootPath)
                put("totalBytes", summary.totalBytes.toLong())
                put("usedBytes", summary.usedBytes.toLong())
                put("freeBytes", summary.freeBytes.toLong())
                put("scannedBytes", summary.scannedBytes.toLong())
                put("fileCount", summary.fileCount.toLong())
                put("durationMs", summary.durationMs.toLong())

                val catArray = org.json.JSONArray()
                for (cat in categoryStats) {
                    val catObj = org.json.JSONObject().apply {
                        put("categoryKey", cat.categoryKey)
                        put("displayName", cat.displayName)
                        put("bytes", cat.bytes.toLong())
                        put("fileCount", cat.fileCount.toLong())
                        put("isCompressible", cat.isCompressible)
                    }
                    catArray.put(catObj)
                }
                put("categories", catArray)

                val topArray = org.json.JSONArray()
                for (f in topFiles) {
                    val fObj = org.json.JSONObject().apply {
                        put("id", f.id.toLong())
                        put("name", f.name)
                        put("path", f.path)
                        put("categoryKey", f.categoryKey)
                        put("sizeLogical", f.sizeLogical.toLong())
                        put("sizePhysical", f.sizePhysical.toLong())
                    }
                    topArray.put(fObj)
                }
                put("topLargeFiles", topArray)
            }

            jsonFile.writeText(jsonObject.toString(2), Charsets.UTF_8)
            jsonFile
        } catch (_: Throwable) {
            null
        }
    }
}
