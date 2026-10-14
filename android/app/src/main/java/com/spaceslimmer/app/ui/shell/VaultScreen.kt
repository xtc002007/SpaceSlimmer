package com.spaceslimmer.app.ui.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsEmptyState
import com.spaceslimmer.app.ui.components.SsLargeTitle
import com.spaceslimmer.app.ui.components.SsPrimaryButton
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.app.ui.components.SsSection
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.core.GalleryWriter
import com.spaceslimmer.core.VaultManager
import com.spaceslimmer.core.WriteConsentRequired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ss_core.CoreEngine
import uniffi.ss_core.VaultItemView
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun VaultScreen(
    engine: CoreEngine?,
    modifier: Modifier = Modifier,
    onGoHome: () -> Unit = {},
) {
    var items by remember { mutableStateOf<List<VaultItemView>>(emptyList()) }
    var usage by remember { mutableStateOf(0UL) }
    var message by remember { mutableStateOf<String?>(null) }
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val nowSec = System.currentTimeMillis() / 1000
    val expiringSoon = items.filter { !it.restored && it.expiresAt <= nowSec + TimeUnit.DAYS.toSeconds(3) }
    val expiredCount = items.count { !it.restored && it.expiresAt <= nowSec }

    suspend fun reload() {
        val rawList = withContext(Dispatchers.IO) {
            runCatching { engine?.listVaultItems() ?: emptyList() }.getOrDefault(emptyList())
        }
        // 3. 保险箱排序：按操作时间倒序排列，最新压缩/备份的操作显示在最上面
        items = rawList.sortedByDescending { item ->
            val f = File(item.vaultPath)
            if (f.exists()) f.lastModified() else (item.expiresAt - 7 * 86400L) * 1000L
        }
        usage = withContext(Dispatchers.IO) {
            runCatching { engine?.vaultUsage() ?: 0u }.getOrDefault(0u)
        }
        selectedIds = selectedIds.intersect(items.map { it.id }.toSet())
    }

    LaunchedEffect(engine) {
        reload()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
    ) {
        SsLargeTitle(title = stringResource(R.string.vault_title))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.vault_usage, formatBytes(context, usage.toLong())),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.vault_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 批量管理与一键清空操作栏
            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isBatchMode) {
                        OutlinedButton(
                            onClick = {
                                isBatchMode = true
                                selectedIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(stringResource(R.string.batch_manage), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showClearAllConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.vault_clear_all_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.batch_selected_ratio, selectedIds.size, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == items.size) emptySet() else items.map { it.id }.toSet()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(if (selectedIds.size == items.size) stringResource(R.string.batch_deselect_all) else stringResource(R.string.batch_select_all), fontSize = 12.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { showBatchDeleteConfirm = true },
                                enabled = selectedIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(stringResource(R.string.batch_delete_selected, selectedIds.size), fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    isBatchMode = false
                                    selectedIds = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(stringResource(R.string.batch_exit), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (expiringSoon.isNotEmpty()) {
                Text(
                    stringResource(R.string.vault_expiring_soon, expiringSoon.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (expiredCount > 0 && !isBatchMode) {
                SsSecondaryButton(
                    text = stringResource(R.string.vault_purge_expired, expiredCount),
                    onClick = {
                        scope.launch {
                            runCatching {
                                val purged = withContext(Dispatchers.IO) {
                                    val count = engine?.purgeExpiredVault()?.toInt() ?: 0
                                    engine?.flush()
                                    count
                                }
                                message = context.getString(R.string.vault_purged_expired, purged)
                                reload()
                            }.onFailure { message = it.message }
                        }
                    },
                )
            }

            if (message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            if (items.isEmpty()) {
                SsEmptyState(
                    title = stringResource(R.string.vault_empty),
                    body = stringResource(R.string.vault_empty_body),
                    action = {
                        SsPrimaryButton(
                            text = stringResource(R.string.vault_empty_cta),
                            onClick = onGoHome,
                        )
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SsSection(title = stringResource(R.string.vault_items)) {
                            items.forEachIndexed { index, item ->
                                VaultItemContent(
                                    item = item,
                                    engine = engine,
                                    isBatchMode = isBatchMode,
                                    isSelected = selectedIds.contains(item.id),
                                    onToggleSelect = {
                                        selectedIds = if (selectedIds.contains(item.id)) {
                                            selectedIds - item.id
                                        } else {
                                            selectedIds + item.id
                                        }
                                    },
                                    onMessage = { message = it },
                                    onReload = { scope.launch { reload() } },
                                )
                                if (index < items.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 一键清空保险箱：醒目的高风险提示二次确认弹窗
    // 一键清空保险箱：醒目的高风险提示二次确认弹窗
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.vault_clear_all_title),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.vault_clear_all_warning),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.vault_clear_all_body, items.size, formatBytes(context, usage.toLong())),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllConfirm = false
                        val toDelete = items
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                toDelete.forEach { item ->
                                    runCatching { File(item.vaultPath).delete() }
                                    runCatching {
                                        val f = File(item.vaultPath)
                                        File(f.parentFile, "${f.nameWithoutExtension}.meta").delete()
                                    }
                                    runCatching { engine?.deleteVaultItem(item.id) }
                                }
                                engine?.flush()
                            }
                            message = context.getString(R.string.vault_cleared_all)
                            reload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.vault_clear_all_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 批量删除：高风险提示二次确认弹窗
    if (showBatchDeleteConfirm) {
        val targetItems = items.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.vault_batch_delete_title),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.vault_batch_delete_warning, selectedIds.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.vault_batch_delete_body),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirm = false
                        val toDelete = targetItems
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                toDelete.forEach { item ->
                                    runCatching { File(item.vaultPath).delete() }
                                    runCatching {
                                        val f = File(item.vaultPath)
                                        File(f.parentFile, "${f.nameWithoutExtension}.meta").delete()
                                    }
                                    runCatching { engine?.deleteVaultItem(item.id) }
                                }
                                engine?.flush()
                            }
                            message = context.getString(R.string.vault_batch_deleted, toDelete.size)
                            selectedIds = emptySet()
                            isBatchMode = false
                            reload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.batch_delete_selected, selectedIds.size), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun VaultThumbnail(
    vaultPath: String,
    modifier: Modifier = Modifier,
) {
    val file = remember(vaultPath) { File(vaultPath) }
    var bitmap by remember(vaultPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(vaultPath) {
        if (!file.isFile) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            // 1. Try video frame extraction via FileDescriptor (most reliable for video .bak files!)
            val videoBm = runCatching {
                FileInputStream(file).use { fis ->
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(fis.fd)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }.getOrNull()

            if (videoBm != null) {
                bitmap = videoBm
                return@withContext
            }

            // 2. Try ThumbnailUtils
            val thumbBm = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(file, android.util.Size(160, 160), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
                }
            }.getOrNull()

            if (thumbBm != null) {
                bitmap = thumbBm
                return@withContext
            }

            // 3. Try BitmapFactory for images (.jpg / .png backed up in vault)
            runCatching {
                FileInputStream(file).use { fis ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fis.fd, null, opts)
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 160)
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        FileInputStream(file).use { fis2 ->
                            bitmap = BitmapFactory.decodeFileDescriptor(fis2.fd, null, decodeOpts)
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun VaultItemContent(
    item: VaultItemView,
    engine: CoreEngine?,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onMessage: (String) -> Unit,
    onReload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = remember(item.vaultPath, item.originalUri) {
        VaultManager.readMeta(context, item.vaultPath, item.originalUri)
    }

    // 单项确认防误触弹窗状态
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showExtendConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val displayName = meta.displayName ?: item.originalUri.substringAfterLast('/')
    val originalPath = meta.originalPath ?: stringResource(R.string.vault_original_path_default)
    val timeText = remember(meta.dateTakenMs, item.expiresAt) {
        val captureMs = meta.dateTakenMs
        if (captureMs != null && captureMs > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(captureMs))
        } else {
            val backupSec = item.expiresAt - 7 * 86400
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(backupSec * 1000))
        }
    }
    val sizeText = remember(meta.size, item.originalBytes) {
        val b = if (meta.size > 0) meta.size else item.originalBytes.toLong()
        formatBytes(context, b)
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                runCatching {
                    val restore = withContext(Dispatchers.IO) {
                        engine?.restoreVaultItem(item.id)
                    } ?: error(context.getString(R.string.err_backup_engine_not_found))
                    withContext(Dispatchers.IO) {
                        GalleryWriter.restoreToUri(
                            context = context,
                            targetUri = restore.originalUri,
                            vaultPath = restore.vaultPath,
                        )
                    }
                    withContext(Dispatchers.IO) {
                        engine?.confirmVaultRestored(item.id)
                        engine?.flush()
                    }
                    onMessage(context.getString(R.string.vault_restored_to_gallery))
                    onReload()
                }.onFailure { err ->
                    onMessage(context.getString(R.string.vault_restore_failed, err.message ?: ""))
                }
            }
        } else {
            onMessage(context.getString(R.string.vault_permission_needed))
        }
    }

    fun openCompressed() {
        val targetUri = runCatching {
            GalleryWriter.resolveReplaceUri(context, item.compressedUri)
        }.getOrNull() ?: runCatching { Uri.parse(item.compressedUri) }.getOrNull()

        if (targetUri == null) {
            onMessage(context.getString(R.string.vault_compressed_not_found))
            return
        }

        val mime = runCatching { context.contentResolver.getType(targetUri) }.getOrNull()
            ?: if (item.vaultPath.endsWith(".mp4", ignoreCase = true) || displayName.endsWith(".mp4", ignoreCase = true)) "video/*"
            else if (item.vaultPath.endsWith(".mp3", ignoreCase = true) || displayName.endsWith(".mp3", ignoreCase = true)) "audio/*"
            else "image/*"

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(viewIntent)
        }.onFailure {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(genericIntent)
            }.onFailure { err ->
                onMessage(context.getString(R.string.vault_open_failed, err.message ?: ""))
            }
        }
    }

    fun restoreBackup() {
        scope.launch {
            runCatching {
                val restore = withContext(Dispatchers.IO) {
                    engine?.restoreVaultItem(item.id)
                } ?: error(context.getString(R.string.err_backup_engine_not_found))
                val uri = withContext(Dispatchers.IO) {
                    GalleryWriter.restoreToUri(
                        context = context,
                        targetUri = restore.originalUri,
                        vaultPath = restore.vaultPath,
                    )
                }
                withContext(Dispatchers.IO) {
                    engine?.confirmVaultRestored(item.id)
                    engine?.flush()
                }
                onMessage(context.getString(R.string.vault_restored_to_gallery))
                onReload()
            }.onFailure { err ->
                if (err is WriteConsentRequired) {
                    consentLauncher.launch(
                        IntentSenderRequest.Builder(err.pendingIntent.intentSender).build()
                    )
                } else {
                    onMessage(context.getString(R.string.vault_restore_failed, err.message ?: ""))
                }
            }
        }
    }

    fun extendVault() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    engine?.extendVaultItem(item.id, 7u)
                    engine?.flush()
                }
                onMessage(context.getString(R.string.vault_extended))
                onReload()
            }.onFailure { err ->
                onMessage(context.getString(R.string.vault_extend_failed, err.message ?: ""))
            }
        }
    }

    fun deleteVault() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    runCatching { File(item.vaultPath).delete() }
                    runCatching {
                        val f = File(item.vaultPath)
                        File(f.parentFile, "${f.nameWithoutExtension}.meta").delete()
                    }
                    engine?.deleteVaultItem(item.id)
                    engine?.flush()
                }
                onMessage(context.getString(R.string.vault_deleted))
                onReload()
            }.onFailure { err ->
                onMessage(context.getString(R.string.vault_delete_failed, err.message ?: ""))
            }
        }
    }

    val itemModifier = if (isBatchMode) {
        Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .padding(vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }

    Column(modifier = itemModifier) {
        // Upper section: Checkbox (if batch) + Thumbnail on left, Name / Original Path / Time on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                )
            }

            VaultThumbnail(vaultPath = item.vaultPath)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 名称
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 原始路径
                Text(
                    text = stringResource(R.string.vault_item_path, originalPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 时间 & 大小
                Text(
                    text = stringResource(R.string.vault_item_time_size, timeText, sizeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Lower section: Action buttons strictly in a SINGLE ROW (only shown in non-batch mode)
        if (!isBatchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.compressedUri.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { openCompressed() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.vault_open_compressed),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Button(
                    onClick = { showRestoreConfirm = true },
                    modifier = Modifier.weight(1f),
                    enabled = !item.restored,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (item.restored) stringResource(R.string.vault_already_restored) else stringResource(R.string.vault_restore),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = { showExtendConfirm = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_extend),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_delete),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    // 1. 单项恢复二次确认弹窗
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.vault_restore_confirm_title)) },
            text = { Text(stringResource(R.string.vault_restore_confirm_body, displayName)) },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    restoreBackup()
                }) {
                    Text(stringResource(R.string.vault_restore_confirm_ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 2. 单项延长7天二次确认弹窗
    if (showExtendConfirm) {
        AlertDialog(
            onDismissRequest = { showExtendConfirm = false },
            title = { Text(stringResource(R.string.vault_extend_confirm_title)) },
            text = { Text(stringResource(R.string.vault_extend_confirm_body, displayName)) },
            confirmButton = {
                Button(onClick = {
                    showExtendConfirm = false
                    extendVault()
                }) {
                    Text(stringResource(R.string.vault_extend_confirm_ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExtendConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 3. 单项删除二次确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.vault_delete_confirm_title)) },
            text = { Text(stringResource(R.string.vault_delete_confirm_body, displayName)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        deleteVault()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.vault_delete_confirm_ok), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
