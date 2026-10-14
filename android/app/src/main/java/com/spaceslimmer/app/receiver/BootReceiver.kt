package com.spaceslimmer.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spaceslimmer.app.worker.QueueRecoveryWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            QueueRecoveryWorker.schedule(context.applicationContext)
        }
    }
}
