package com.sumitrack.android.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.sumitrack.android.sync.workers.PushWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Interfaz angosta — permite que ViewModels consumidores (OrderListViewModel, ClientListViewModel)
// sean testeables en JVM puro sin necesitar un WorkManager real; un fake SAM basta (fun interface).
fun interface PushSyncTrigger {
    operator fun invoke()
}

// Encola un PushWorker de un solo disparo — mismo patrón que PullWorker usa desde AuthRepository
// (Historia 4.2), con un nombre de trabajo único DISTINTO del periódico ("push-sync") para no
// colisionar/reemplazarlo. Usado por el botón "Reintentar" del Snackbar de error y por el gesto
// de pull-to-refresh de las listas de Órdenes/Clientes.
class TriggerPushSyncUseCase @Inject constructor(
    private val workManager: WorkManager,
) : PushSyncTrigger {
    override operator fun invoke() {
        val request = OneTimeWorkRequestBuilder<PushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(PushWorker.KEY_MANUAL_RETRY to true))
            .build()
        workManager.enqueueUniqueWork("push-sync-now", ExistingWorkPolicy.REPLACE, request)
    }
}
