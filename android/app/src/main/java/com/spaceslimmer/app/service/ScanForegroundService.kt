package com.spaceslimmer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.spaceslimmer.app.MainActivity
import com.spaceslimmer.app.R
import com.spaceslimmer.app.ui.shared.formatBytes

class ScanForegroundService : Service() {

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "SpaceSlimmer::ScanWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // 30 minutes max
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        isScanning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isScanning = false
            }
            ACTION_UPDATE_PROGRESS -> {
                val scannedBytes = intent.getLongExtra(EXTRA_SCANNED_BYTES, 0L)
                val scannedFiles = intent.getIntExtra(EXTRA_SCANNED_FILES, 0)
                val currentPath = intent.getStringExtra(EXTRA_CURRENT_PATH) ?: ""
                updateNotification(scannedBytes, scannedFiles, currentPath)
            }
            ACTION_START -> {
                acquireWakeLock()
                isScanning = true
                val initialNotif = buildNotification(0L, 0, "")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initialNotif,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotif)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(scannedBytes: Long, scannedFiles: Int, currentPath: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(scannedBytes, scannedFiles, currentPath))
    }

    private fun buildNotification(scannedBytes: Long, scannedFiles: Int, currentPath: String): Notification {
        ensureChannel()

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = getString(R.string.scan_service_title)
        val content = if (scannedFiles > 0) {
            "${formatBytes(scannedBytes)} · $scannedFiles items"
        } else {
            getString(R.string.scan_service_preparing)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(currentPath.takeLast(35))
            .setContentIntent(pIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.scan_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.scan_channel_desc)
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "spaceslimmer_scan"
        const val ACTION_START = "com.spaceslimmer.app.service.ACTION_START_SCAN"
        const val ACTION_STOP = "com.spaceslimmer.app.service.ACTION_STOP_SCAN"
        const val ACTION_UPDATE_PROGRESS = "com.spaceslimmer.app.service.ACTION_UPDATE_SCAN_PROGRESS"

        const val EXTRA_SCANNED_BYTES = "scanned_bytes"
        const val EXTRA_SCANNED_FILES = "scanned_files"
        const val EXTRA_CURRENT_PATH = "current_path"

        @Volatile
        var isScanning: Boolean = false

        fun start(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, bytes: Long, files: Int, path: String) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_SCANNED_BYTES, bytes)
                putExtra(EXTRA_SCANNED_FILES, files)
                putExtra(EXTRA_CURRENT_PATH, path)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
