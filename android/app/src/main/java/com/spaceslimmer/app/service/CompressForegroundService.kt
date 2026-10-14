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
import uniffi.ss_core.GroupView

class CompressForegroundService : Service() {
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "SpaceSlimmer::CompressWakeLock",
        )?.apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)
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
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Companion.running = false
                Companion.starting = false
                Companion.stopping = false
            }
            else -> {
                val incomingSession = intent?.getIntExtra(EXTRA_SESSION, -1) ?: -1
                val text = Companion.pendingText ?: getString(R.string.notify_idle)
                val ratio = Companion.pendingRatio
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(text, ratio))
                    acquireWakeLock()
                } catch (_: Throwable) {
                    Companion.running = false
                    Companion.starting = false
                    Companion.stopping = false
                    releaseWakeLock()
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (Companion.stopping || incomingSession <= Companion.stopSession) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    Companion.running = false
                    Companion.starting = false
                    Companion.stopping = false
                    return START_NOT_STICKY
                }
                Companion.running = true
                Companion.starting = false
                Companion.pendingText = null
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "compress_jobs"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "stop"
        private const val EXTRA_SESSION = "session"
        private var running = false
        private var starting = false
        private var stopping = false
        private var session = 0
        private var stopSession = -1
        private var pendingText: String? = null
        private var pendingRatio: Float = 0f

        fun isRunning(): Boolean = running || starting

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            if (running || starting || stopping) return
            ensureChannel(context)
            val intent = Intent(context, CompressForegroundService::class.java)
            try {
                starting = true
                session += 1
                intent.putExtra(EXTRA_SESSION, session)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                running = false
                starting = false
            }
        }

        fun stop(context: Context) {
            if (!running && !starting && !stopping) return
            stopping = true
            stopSession = session
            val intent = Intent(context, CompressForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun update(context: Context, group: GroupView) {
            runCatching {
                val total = group.jobs.size
                val done = (group.done + group.failed + group.skipped).toInt()
                val ratio = if (total == 0) 0f else done.toFloat() / total
                val text = context.getString(
                    R.string.notify_progress,
                    done,
                    total,
                    com.spaceslimmer.app.ui.shared.formatBytes(context, group.savedBytes),
                )
                notify(context, text, ratio)
            }.onFailure { err ->
                com.spaceslimmer.core.AppLogger.e("CompressForegroundService", "update error: ${err.message}", err)
            }
        }

        fun notify(context: Context, text: String, ratio: Float) {
            if (stopping) {
                pendingText = text
                pendingRatio = ratio
                return
            }
            ensureChannel(context)
            if (running) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(context, text, ratio))
            } else {
                pendingText = text
                pendingRatio = ratio
                start(context)
            }
        }

        private fun buildNotification(context: Context, text: String, ratio: Float): Notification {
            val launch = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentTitle(context.getString(R.string.notify_title))
                .setContentText(text)
                .setProgress(100, (ratio * 100).toInt(), ratio <= 0f)
                .setContentIntent(launch)
                .setOngoing(ratio < 1f)
                .build()
        }
    }

    private fun buildNotification(text: String, ratio: Float): Notification =
        buildNotification(this, text, ratio)
}
