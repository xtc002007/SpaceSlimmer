package com.spaceslimmer.app.ui.shared

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.spaceslimmer.app.R
import com.spaceslimmer.app.export.ExportNaming
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.ui.components.SsSecondaryButton
import com.spaceslimmer.core.ExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportToFolderButton(
    paths: List<String>,
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<String>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ExportHelper.saveExportTree(context, uri)
        val toExport = pending
        pending = emptyList()
        scope.launch {
            val n = exportAll(context, toExport)
            onResult(context.getString(R.string.media_files_exported, n))
        }
    }

    SsSecondaryButton(
        text = stringResource(R.string.media_export_folder),
        onClick = {
            if (paths.isEmpty()) return@SsSecondaryButton
            if (ExportHelper.getSavedExportTree(context) != null) {
                scope.launch {
                    val n = exportAll(context, paths)
                    onResult(context.getString(R.string.media_files_exported, n))
                }
            } else {
                pending = paths
                launcher.launch(null)
            }
        },
        enabled = paths.isNotEmpty(),
        modifier = modifier,
    )
}

private suspend fun exportAll(context: android.content.Context, paths: List<String>): Int =
    withContext(Dispatchers.IO) {
        var n = 0
        paths.forEachIndexed { index, path ->
            val raw = path.substringAfterLast('/').substringAfterLast('\\')
            val name = ExportNaming.displayName(context, raw, index)
            if (ExportHelper.exportFile(
                    context,
                    path,
                    name,
                    ExportHelper.mimeForFile(path),
                    AppPrefs.archiveByDateEnabled(context),
                ) != null
            ) {
                n++
            }
        }
        n
    }
