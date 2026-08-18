package com.sumitrack.android.sync

import com.sumitrack.android.data.repositories.FolioBaselineStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFolioBaselineStore : FolioBaselineStore {

    private val baselineFlow = MutableStateFlow<Int?>(null)

    override val folioBaseline = baselineFlow

    override suspend fun saveFolioBaseline(count: Int) {
        baselineFlow.value = count
    }
}
