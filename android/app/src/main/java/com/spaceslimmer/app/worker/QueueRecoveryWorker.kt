package com.spaceslimmer.app.worker



import android.app.NotificationChannel

import android.app.NotificationManager

import android.app.PendingIntent

import android.content.Context

import android.content.Intent

import android.os.Build

import androidx.core.app.NotificationCompat

import androidx.core.app.NotificationManagerCompat

import androidx.work.CoroutineWorker

import androidx.work.ExistingWorkPolicy

import androidx.work.OneTimeWorkRequestBuilder

import androidx.work.WorkManager

import androidx.work.WorkerParameters

import com.spaceslimmer.app.MainActivity

import com.spaceslimmer.app.R

import com.spaceslimmer.app.SpaceSlimmerApp

import com.spaceslimmer.app.viewmodel.CompressSession

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import java.util.concurrent.TimeUnit



/**

 * Notifies the user when unfinished compression groups exist (e.g. after reboot).

 * Dispatches queued work unless idle-charge-only is enabled.

 */

class QueueRecoveryWorker(

    appContext: Context,

    params: WorkerParameters,

) : CoroutineWorker(appContext, params) {



    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val engine = (applicationContext as? SpaceSlimmerApp)?.engine

            ?: return@withContext Result.success()

        if (engine.settings().idleChargeOnly) {

            runCatching { engine.dispatchPendingWork() }

            runCatching {

                engine.idleChargeRecoveryGroupIds().forEach { groupId ->

                    IdleChargeWorker.enqueue(applicationContext, groupId)

                }

            }

        } else {

            runCatching { engine.dispatchPendingWork() }

        }

        val unfinished = runCatching { engine.listGroups() }

            .getOrDefault(emptyList())

            .count { group -> !CompressSession.isGroupFinished(group) }

        if (unfinished > 0) {

            ensureChannel(applicationContext)

            notifyUnfinished(applicationContext, unfinished)

        }

        Result.success()

    }



    companion object {

        private const val WORK_NAME = "queue_recovery"

        private const val CHANNEL_ID = "queue_recovery"

        private const val NOTIFICATION_ID = 1003



        fun schedule(context: Context) {

            val request = OneTimeWorkRequestBuilder<QueueRecoveryWorker>()

                .setInitialDelay(30, TimeUnit.SECONDS)

                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(

                WORK_NAME,

                ExistingWorkPolicy.KEEP,

                request,

            )

        }



        private fun ensureChannel(context: Context) {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channel = NotificationChannel(

                CHANNEL_ID,

                context.getString(R.string.queue_recovery_channel),

                NotificationManager.IMPORTANCE_DEFAULT,

            )

            context.getSystemService(NotificationManager::class.java)

                .createNotificationChannel(channel)

        }



        private fun notifyUnfinished(context: Context, count: Int) {

            ensureChannel(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {

                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_HISTORY)

            }

            val pendingIntent = PendingIntent.getActivity(

                context,

                NOTIFICATION_ID,

                launchIntent,

                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,

            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)

                .setSmallIcon(android.R.drawable.ic_dialog_info)

                .setContentTitle(context.getString(R.string.queue_recovery_title))

                .setContentText(context.getString(R.string.queue_recovery_body, count))

                .setContentIntent(pendingIntent)

                .setAutoCancel(true)

                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

        }

    }

}


