package com.spaceslimmer.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spaceslimmer.app.R
import com.spaceslimmer.app.SpaceSlimmerApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.ss_core.CoreEngine
import java.io.File
import java.util.concurrent.TimeUnit

class VaultExpiryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val engine = (applicationContext as? SpaceSlimmerApp)?.engine ?: return@withContext Result.success()
        ensureChannel(applicationContext)
        val settings = engine.settings()
        val now = System.currentTimeMillis() / 1000
        val soon = now + TimeUnit.DAYS.toSeconds(1)
        val items = runCatching { engine.listVaultItems() }.getOrDefault(emptyList())
        val expiring = items.filter { !it.restored && it.expiresAt <= soon }
        if (settings.autoDeleteVault) {
            items.filter { !it.restored && it.expiresAt <= now }.forEach { item ->
                runCatching {
                    File(item.vaultPath).delete()
                    engine.deleteVaultItem(item.id)
                }
            }
        } else if (expiring.isNotEmpty()) {
            notifyExpiring(applicationContext, expiring.size)
        }
        Result.success()
    }

    companion object {
        private const val WORK_NAME = "vault_expiry"
        private const val CHANNEL_ID = "vault_expiry"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VaultExpiryWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.vault_notify_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        private fun notifyExpiring(context: Context, count: Int) {
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.vault_notify_title))
                .setContentText(context.getString(R.string.vault_notify_body, count))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(1002, notification)
        }
    }
}
