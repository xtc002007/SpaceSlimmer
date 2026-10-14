package com.spaceslimmer.app.ui.shell

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsAppBar
import com.spaceslimmer.app.ui.components.SsCard
import com.spaceslimmer.app.ui.components.SsEmptyState
import com.spaceslimmer.app.ui.components.SsScreen
import com.spaceslimmer.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<AppLogger.Level?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun loadLogs() {
        scope.launch(Dispatchers.IO) {
            val list = AppLogger.readRecentLogs(context, maxLines = 2000)
            withContext(Dispatchers.Main) {
                logs = list
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    val filteredLogs = remember(logs, searchQuery, selectedLevel) {
        logs.filter { line ->
            val matchesSearch = searchQuery.isBlank() || line.contains(searchQuery, ignoreCase = true)
            val matchesLevel = when (selectedLevel) {
                null -> true
                AppLogger.Level.ERROR -> line.contains(" [E] ") || line.contains("[ERROR]")
                AppLogger.Level.WARN -> line.contains(" [W] ") || line.contains("[WARN]")
                AppLogger.Level.INFO -> line.contains(" [I] ") || line.contains("[INFO]")
                AppLogger.Level.DEBUG -> line.contains(" [D] ") || line.contains("[DEBUG]")
                AppLogger.Level.VERBOSE -> line.contains(" [V] ") || line.contains("[VERBOSE]")
            }
            matchesSearch && matchesLevel
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.logs_clear)) },
            text = { Text(stringResource(R.string.logs_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        AppLogger.clearLogs(context)
                        logs = emptyList()
                    },
                ) {
                    Text(stringResource(R.string.history_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SsScreen(
        topBar = {
            SsAppBar(
                title = stringResource(R.string.logs_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { loadLogs() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_retry),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { shareLogs(context) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.logs_export),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.logs_clear),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.logs_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text(stringResource(R.string.logs_filter_all)) },
                )
                FilterChip(
                    selected = selectedLevel == AppLogger.Level.INFO,
                    onClick = { selectedLevel = if (selectedLevel == AppLogger.Level.INFO) null else AppLogger.Level.INFO },
                    label = { Text(stringResource(R.string.logs_filter_info)) },
                )
                FilterChip(
                    selected = selectedLevel == AppLogger.Level.WARN,
                    onClick = { selectedLevel = if (selectedLevel == AppLogger.Level.WARN) null else AppLogger.Level.WARN },
                    label = { Text(stringResource(R.string.logs_filter_warn)) },
                )
                FilterChip(
                    selected = selectedLevel == AppLogger.Level.ERROR,
                    onClick = { selectedLevel = if (selectedLevel == AppLogger.Level.ERROR) null else AppLogger.Level.ERROR },
                    label = { Text(stringResource(R.string.logs_filter_error)) },
                )
            }

            Text(
                text = stringResource(R.string.logs_count_ratio, filteredLogs.size, logs.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    SsEmptyState(
                        title = stringResource(R.string.logs_empty),
                        body = "",
                    )
                }
            } else {
                SsCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredLogs) { line ->
                            val color = when {
                                line.contains(" [E] ") || line.contains("[ERROR]") -> Color(0xFFD32F2F)
                                line.contains(" [W] ") || line.contains("[WARN]") -> Color(0xFFF57C00)
                                line.contains(" [I] ") || line.contains("[INFO]") -> Color(0xFF1976D2)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                ),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareLogs(context: Context) {
    val files = AppLogger.getLogFiles(context)
    if (files.isEmpty()) {
        AppLogger.i("LogViewer", "No log files to share")
        return
    }
    val file = files.first()
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SpaceSlimmer Log Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.logs_export)))
    } catch (_: Throwable) {
        // Fallback: share text content
        val content = AppLogger.readRecentLogs(context, 500).joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
            putExtra(Intent.EXTRA_SUBJECT, "SpaceSlimmer Log Export")
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.logs_export)))
    }
}
