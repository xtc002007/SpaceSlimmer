package com.spaceslimmer.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spaceslimmer.app.SpaceSlimmerApp
import com.spaceslimmer.app.service.CompressForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdleChargeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val engine = (applicationContext as? SpaceSlimmerApp)?.engine
            ?: return@withContext Result.retry()
        val groupId = inputData.getString(KEY_GROUP_ID).orEmpty()
        if (groupId.isBlank()) return@withContext Result.success()
        runCatching {
            CompressForegroundService.start(applicationContext)
            engine.resumeGroup(groupId)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val WORK_PREFIX = "idle_charge_"
        const val KEY_GROUP_ID = "group_id"

        fun enqueue(context: Context, groupId: String) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = OneTimeWorkRequestBuilder<IdleChargeWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_GROUP_ID to groupId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + groupId,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
