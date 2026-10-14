package com.spaceslimmer.app.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.components.SsScreen
import uniffi.ss_core.CoreEngine

enum class MainTab { Home, History, Vault, Settings }

@Composable
fun MainShell(
    engine: CoreEngine?,
    engineError: String?,
    onNavigate: (String) -> Unit,
    homeContent: @Composable (Modifier) -> Unit,
    startTab: MainTab = MainTab.Home,
) {
    var tabName by rememberSaveable { mutableStateOf(startTab.name) }
    LaunchedEffect(startTab) {
        tabName = startTab.name
    }
    val tab = MainTab.entries.firstOrNull { it.name == tabName } ?: MainTab.Home

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SsScreen(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = tab == MainTab.Home,
                    onClick = { tabName = MainTab.Home.name },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.tab_home)) },
                    label = { Text(stringResource(R.string.tab_home)) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = tab == MainTab.History,
                    onClick = { tabName = MainTab.History.name },
                    icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.tab_history)) },
                    label = { Text(stringResource(R.string.tab_history)) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = tab == MainTab.Vault,
                    onClick = { tabName = MainTab.Vault.name },
                    icon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.tab_vault)) },
                    label = { Text(stringResource(R.string.tab_vault)) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = tab == MainTab.Settings,
                    onClick = { tabName = MainTab.Settings.name },
                    icon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.tab_settings)) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    colors = navItemColors,
                )
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.Home -> homeContent(Modifier.padding(padding))
            MainTab.History -> HistoryScreen(
                engine = engine,
                modifier = Modifier.padding(padding),
                onOpenReport = { onNavigate("report") },
                onOpenJob = { groupId, jobId -> onNavigate("history/job/$groupId/$jobId") },
                onOpenInProgressJob = { kind, groupId ->
                    val route = when (kind) {
                        uniffi.ss_core.MediaKind.IMAGE -> "photo/jobs/$groupId"
                        uniffi.ss_core.MediaKind.VIDEO -> "video/jobs/$groupId"
                        uniffi.ss_core.MediaKind.AUDIO -> "audio/jobs/$groupId"
                    }
                    onNavigate(route)
                },
                onGoHome = { tabName = MainTab.Home.name },
            )
            MainTab.Vault -> VaultScreen(
                engine = engine,
                modifier = Modifier.padding(padding),
                onGoHome = { tabName = MainTab.Home.name },
            )
            MainTab.Settings -> SettingsScreen(
                engine = engine,
                engineError = engineError,
                modifier = Modifier.padding(padding),
                onOpenLogs = { onNavigate("settings/logs") },
            )
        }
    }
}
