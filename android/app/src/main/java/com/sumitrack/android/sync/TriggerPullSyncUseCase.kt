package com.sumitrack.android.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.sumitrack.android.sync.workers.PullWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Interfaz angosta — mismo patrón que PushSyncTrigger (Historia 4.3): permite que ViewModels
// consumidores (SettingsViewModel) sean testeables en JVM puro sin necesitar un WorkManager real.
fun interface PullSyncTrigger {
    operator fun invoke()
}

// Encola un PullWorker de un solo disparo bajo el mismo nombre único "pull-sync" que
// AuthRepository ya usa al hacer login (Historia 4.2) — así SyncStatusViewModel.isSyncing, que ya
// observa "pull-sync" como trabajo one-time, refleja el pull manual sin cambios adicionales.
// Usado por el botón "Sincronizar ahora" de S-14 (Historia 5.1).
class TriggerPullSyncUseCase @Inject constructor(
    private val workManager: WorkManager,
) : PullSyncTrigger {
    override operator fun invoke() {
        val request = OneTimeWorkRequestBuilder<PullWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork("pull-sync", ExistingWorkPolicy.REPLACE, request)
    }
}
