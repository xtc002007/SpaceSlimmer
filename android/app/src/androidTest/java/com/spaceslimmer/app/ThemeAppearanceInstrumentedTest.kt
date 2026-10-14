package com.spaceslimmer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeAppearanceInstrumentedTest {
    @Test
    fun themeModePersistsAcrossWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppPrefs.setThemeMode(context, ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, AppPrefs.getThemeMode(context))
        AppPrefs.setThemeMode(context, ThemeMode.Light)
        assertEquals(ThemeMode.Light, AppPrefs.getThemeMode(context))
        AppPrefs.setThemeMode(context, ThemeMode.System)
        assertEquals(ThemeMode.System, AppPrefs.getThemeMode(context))
    }

    @Test
    fun themeModeFromPrefRoundTrip() {
        assertEquals(ThemeMode.Light, ThemeMode.fromPref("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromPref("dark"))
        assertEquals(ThemeMode.System, ThemeMode.fromPref("system"))
        assertEquals(ThemeMode.System, ThemeMode.fromPref("unknown"))
    }

    @Test
    fun nightColorsExistForWidget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lightSaved = context.getColor(com.spaceslimmer.app.R.color.ss_saved)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val nightContext = context.createConfigurationContext(config)
        val darkSaved = nightContext.getColor(com.spaceslimmer.app.R.color.ss_saved)
        assert(lightSaved != darkSaved)
    }
}
