package com.sumitrack.android.sync

import androidx.work.WorkInfo
import androidx.work.WorkManager
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// Interfaz angosta sobre WorkManager.getWorkInfosForUniqueWorkFlow — mismo patrón de Historia 4.2
// (FolioBaselineStore) para mantener testeable en JVM puro cualquier clase que observe trabajo en
// background, sin necesitar fakear WorkManager completo (clase abstracta con decenas de miembros).
interface SyncWorkObserver {
    fun observe(uniqueWorkName: String): Flow<List<WorkInfo>>
}

class WorkManagerSyncWorkObserver @Inject constructor(
    private val workManager: WorkManager,
) : SyncWorkObserver {
    override fun observe(uniqueWorkName: String): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
}
