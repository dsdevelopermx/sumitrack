package com.sumitrack.android.sync.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@HiltWorker
class PushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    @TenantId private val tenantId: Flow<String?>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val tenant = tenantId.first() ?: return Result.success()
            if (syncManager.pushPending(tenant)) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Falla local inesperada (p.ej. DataStore corrupto) — se reintenta la corrida en vez
            // de dejar que la excepción se propague y WorkManager la trate como Result.failure().
            Result.retry()
        }
    }
}
