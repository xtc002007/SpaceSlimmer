package com.spaceslimmer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.spaceslimmer.app.MainActivity
import com.spaceslimmer.app.R
import com.spaceslimmer.app.prefs.AppPrefs
import com.spaceslimmer.app.ui.shared.formatBytes
import com.spaceslimmer.app.ui.theme.ThemeMode

class WeeklySavingsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeeklySavingsWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
        }

        private fun themedContext(context: Context, mode: ThemeMode): Context {
            val config = Configuration(context.resources.configuration)
            when (mode) {
                ThemeMode.Light -> {
                    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        Configuration.UI_MODE_NIGHT_NO
                }
                ThemeMode.Dark -> {
                    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        Configuration.UI_MODE_NIGHT_YES
                }
                ThemeMode.System -> Unit
            }
            return context.createConfigurationContext(config)
        }

        private fun backgroundDrawable(themeMode: ThemeMode): Int = when (themeMode) {
            ThemeMode.Light -> R.drawable.widget_background_light
            ThemeMode.Dark -> R.drawable.widget_background_dark
            ThemeMode.System -> R.drawable.widget_background
        }

        private fun buildViews(context: Context): RemoteViews {
            val saved = AppPrefs.weeklySavedBytes(context)
            val themeMode = AppPrefs.getThemeMode(context)
            val themed = themedContext(context, themeMode)
            val views = RemoteViews(context.packageName, R.layout.weekly_savings_widget)
            views.setTextViewText(
                R.id.widget_title,
                context.getString(R.string.widget_weekly_title),
            )
            views.setTextViewText(R.id.widget_value, formatBytes(context, saved))
            views.setInt(R.id.widget_root, "setBackgroundResource", backgroundDrawable(themeMode))
            views.setTextColor(R.id.widget_title, themed.getColor(R.color.ss_on_surface_variant))
            views.setTextColor(R.id.widget_value, themed.getColor(R.color.ss_saved))
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_HISTORY)
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
