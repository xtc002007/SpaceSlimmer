package com.spaceslimmer.app.ui.report



import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.spaceslimmer.app.R

import com.spaceslimmer.app.prefs.AppPrefs

import com.spaceslimmer.app.ui.components.SsAppBar

import com.spaceslimmer.app.ui.components.SsCard

import com.spaceslimmer.app.ui.components.SsHeroMetric

import com.spaceslimmer.app.ui.components.SsProgressBar

import com.spaceslimmer.app.ui.components.SsScreen

import com.spaceslimmer.app.ui.shared.formatBytes

import com.spaceslimmer.app.ui.theme.savingsColor

import com.spaceslimmer.app.viewmodel.CompressSession

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import uniffi.ss_core.CoreEngine

import uniffi.ss_core.MediaKind



@Composable

fun WatchReportScreen(

    engine: CoreEngine?,

    onBack: () -> Unit,

    modifier: Modifier = Modifier,

) {

    val context = LocalContext.current

    var videoSaved by remember { mutableStateOf(0L) }

    var imageSaved by remember { mutableStateOf(0L) }

    var audioSaved by remember { mutableStateOf(0L) }

    var groups by remember { mutableStateOf(0) }

    val radar = remember { AppPrefs.loadRadarCache(context) }

    val weekly = remember { AppPrefs.weeklySavedBytes(context) }



    LaunchedEffect(engine) {

        val loaded = withContext(Dispatchers.IO) {

            runCatching { engine?.listGroups() ?: emptyList() }.getOrDefault(emptyList())

        }.filter { CompressSession.isGroupFinished(it) }

        groups = loaded.size

        var v = 0L

        var i = 0L

        var a = 0L

        loaded.forEach { group ->

            group.jobs.forEach { job ->

                val saved = job.savedBytes ?: 0L

                when (job.spec.fingerprint.kind) {

                    MediaKind.VIDEO -> v += saved

                    MediaKind.IMAGE -> i += saved

                    MediaKind.AUDIO -> a += saved

                }

            }

        }

        videoSaved = v

        imageSaved = i

        audioSaved = a

    }



    val total = videoSaved + imageSaved + audioSaved

    val maxKind = maxOf(videoSaved, imageSaved, audioSaved, 1L)



    SsScreen(

        modifier = modifier,

        topBar = { SsAppBar(title = stringResource(R.string.watch_report_title), onBack = onBack) },

    ) { padding ->

        Column(

            modifier = Modifier

                .fillMaxSize()

                .padding(padding)

                .padding(16.dp)

                .verticalScroll(rememberScrollState()),

            verticalArrangement = Arrangement.spacedBy(16.dp),

        ) {

            SsHeroMetric(

                title = stringResource(R.string.watch_report_title),

                value = stringResource(R.string.watch_report_total, formatBytes(context, total)),

                subtitle = stringResource(R.string.watch_report_weekly, formatBytes(context, weekly)),

            )

            SsCard {

                Column(

                    modifier = Modifier.padding(20.dp),

                    verticalArrangement = Arrangement.spacedBy(16.dp),

                ) {

                    Text(

                        stringResource(R.string.watch_report_by_kind, groups),

                        style = MaterialTheme.typography.labelLarge,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                    )

                    KindBar(stringResource(R.string.radar_filter_video), videoSaved, maxKind)

                    KindBar(stringResource(R.string.radar_filter_image), imageSaved, maxKind)

                    KindBar(stringResource(R.string.radar_filter_audio), audioSaved, maxKind)

                }

            }

            radar?.let { cache ->

                SsCard {

                    Text(

                        stringResource(

                            R.string.watch_report_library,

                            formatBytes(context, cache.libraryBytes),

                            formatBytes(context, cache.estimateHigh),

                        ),

                        style = MaterialTheme.typography.bodyMedium,

                        color = savingsColor(),

                        modifier = Modifier.padding(20.dp),

                    )

                }

            }

        }

    }

}



@Composable

private fun KindBar(label: String, saved: Long, max: Long) {

    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {

        Text(

            "$label · ${formatBytes(context, saved)}",

            style = MaterialTheme.typography.bodyMedium,

            color = savingsColor(),

        )

        SsProgressBar(

            progress = (saved.toFloat() / max.toFloat()).coerceIn(0f, 1f),

            modifier = Modifier.fillMaxWidth(),

        )

    }

}


