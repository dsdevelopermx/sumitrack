package com.sumitrack.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.sumitrack.android.sync.workers.PushWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SumitrackApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Único punto de encolado, una vez por proceso — 15 min es el mínimo que WorkManager
        // permite para trabajo periódico. Cubre AC-1/AC-3 (sube mientras hay conexión, retoma
        // la cola al reconectar) sin necesitar triggers oportunistas por cada escritura local.
        val request = PeriodicWorkRequestBuilder<PushWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        // UPDATE (no KEEP) para que cambios futuros al intervalo/constraints/backoff se apliquen
        // a instalaciones ya existentes en vez de quedar congelados con la primera versión.
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("push-sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
