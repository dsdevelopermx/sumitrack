package com.sumitrack.android.sync

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSyncWorkObserver : SyncWorkObserver {
    private val flows = mutableMapOf<String, MutableStateFlow<List<WorkInfo>>>()

    private fun flowFor(uniqueWorkName: String) =
        flows.getOrPut(uniqueWorkName) { MutableStateFlow(emptyList()) }

    override fun observe(uniqueWorkName: String): StateFlow<List<WorkInfo>> = flowFor(uniqueWorkName)

    fun setState(uniqueWorkName: String, state: WorkInfo.State) {
        flowFor(uniqueWorkName).value = listOf(fakeWorkInfo(state))
    }

    private fun fakeWorkInfo(state: WorkInfo.State) = WorkInfo(
        id = java.util.UUID.randomUUID(),
        state = state,
        tags = emptySet(),
        outputData = androidx.work.Data.EMPTY,
        progress = androidx.work.Data.EMPTY,
        runAttemptCount = 0,
        generation = 0,
    )
}
