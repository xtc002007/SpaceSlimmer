package com.spaceslimmer.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.spaceslimmer.app.ui.SpaceSlimmerRoot
import com.spaceslimmer.app.ui.theme.LocalThemeController
import com.spaceslimmer.app.ui.theme.SpaceSlimmerTheme
import com.spaceslimmer.app.ui.theme.rememberThemeController
import com.spaceslimmer.core.LocaleHelper

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN = "com.spaceslimmer.app.extra.OPEN"
        const val OPEN_RADAR = "radar"
        const val OPEN_HISTORY = "history"
        const val OPEN_COMPRESS = "compress"
        const val OPEN_REPORT = "report"
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as SpaceSlimmerApp
        setContent {
            val themeController = rememberThemeController()
            CompositionLocalProvider(LocalThemeController provides themeController) {
                SpaceSlimmerTheme(themeMode = themeController.mode) {
                    SpaceSlimmerRoot(
                        engine = app.engine,
                        engineError = app.engineError,
                        onRequestMediaPermissions = this::requestMediaPermissions,
                        onRequestNotificationPermission = this::requestNotificationPermission,
                        launchAction = intent?.getStringExtra(EXTRA_OPEN),
                    )
                }
            }
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val needed = buildList {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            }.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                mediaPermissions.launch(needed.toTypedArray())
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                mediaPermissions.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }
}
