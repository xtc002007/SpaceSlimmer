package com.spaceslimmer.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.widget.WeeklySavingsWidget

@Stable
class ThemeController(
    initialMode: ThemeMode,
    private val context: Context,
) {
    var themeMode by mutableStateOf(initialMode)
        private set

    /** Current appearance mode (alias for [themeMode]). */
    val mode: ThemeMode get() = themeMode

    fun updateThemeMode(newMode: ThemeMode) {
        if (themeMode == newMode) return
        themeMode = newMode
        AppPrefs.setThemeMode(context, newMode)
        WeeklySavingsWidget.refresh(context)
    }
}

val LocalThemeController = compositionLocalOf<ThemeController> {
    error("ThemeController not provided")
}

@Composable
fun rememberThemeController(): ThemeController {
    val context = LocalContext.current.applicationContext
    return remember {
        ThemeController(AppPrefs.getThemeMode(context), context)
    }
}
