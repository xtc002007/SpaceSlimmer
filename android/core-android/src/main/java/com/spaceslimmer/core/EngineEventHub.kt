package com.spaceslimmer.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uniffi.ss_core.GroupView
import uniffi.ss_core.JobView

object EngineEventHub {
    private val _groups = MutableSharedFlow<GroupView>(extraBufferCapacity = 32)
    private val _jobs = MutableSharedFlow<JobView>(extraBufferCapacity = 64)
    private val _notifications = MutableSharedFlow<Pair<String, Float>>(extraBufferCapacity = 8)
    private val _historyRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    val groups: SharedFlow<GroupView> = _groups.asSharedFlow()
    val jobs: SharedFlow<JobView> = _jobs.asSharedFlow()
    val notifications: SharedFlow<Pair<String, Float>> = _notifications.asSharedFlow()
    val historyRefresh: SharedFlow<Unit> = _historyRefresh.asSharedFlow()

    fun emitGroup(group: GroupView) {
        _groups.tryEmit(group)
    }

    fun emitJob(job: JobView) {
        _jobs.tryEmit(job)
    }

    fun emitNotification(key: String, ratio: Float) {
        _notifications.tryEmit(key to ratio)
    }

    /** Ask history UI to reload from the engine (e.g. after deleting a record). */
    fun requestHistoryRefresh() {
        _historyRefresh.tryEmit(Unit)
    }
}
