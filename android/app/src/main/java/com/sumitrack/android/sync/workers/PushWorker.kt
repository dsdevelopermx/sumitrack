package com.sumitrack.android.sync.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.sync.SyncEvent
import com.sumitrack.android.sync.SyncEventBus
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
    private val syncEventBus: SyncEventBus,
    @TenantId private val tenantId: Flow<String?>,
) : CoroutineWorker(context, params) {

    // El WorkManager de este proyecto NO expone Result.success()/failure() como estados
    // observables para trabajo periódico (confirmado en el bytecode de WorkerWrapper —
    // resetPeriodic() reinicia a ENQUEUED de inmediato en ambos casos) — por eso PushWorker emite
    // explícitamente a SyncEventBus en vez de que la UI intente inferir el resultado desde
    // WorkInfo. Result.retry()/Result.success() NO cambian respecto a Historia 4.1/4.2 — cambiar
    // eso no aportaría nada observable y arriesgaría la confiabilidad ya en producción.
    override suspend fun doWork(): Result {
        val isManualRetry = inputData.getBoolean(KEY_MANUAL_RETRY, false)
        var tenant: String? = null
        return try {
            tenant = tenantId.first() ?: return Result.success()
            val hadPendingWork = syncManager.hasPendingWork(tenant)
            if (syncManager.pushPending(tenant)) {
                if (hadPendingWork) syncEventBus.emit(SyncEvent.Success(tenant))
                Result.success()
            } else {
                if (shouldNotifyFailure(runAttemptCount, isManualRetry)) syncEventBus.emit(SyncEvent.Failure(tenant))
                Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Falla local inesperada (p.ej. DataStore corrupto) — se reintenta la corrida en vez
            // de dejar que la excepción se propague y WorkManager la trate como Result.failure().
            // Si tenant es null (la excepción ocurrió leyendo tenantId), no hay a quién notificar.
            if (shouldNotifyFailure(runAttemptCount, isManualRetry)) tenant?.let { syncEventBus.emit(SyncEvent.Failure(it)) }
            Result.retry()
        }
    }

    companion object {
        // Clave de inputData que TriggerPushSyncUseCase agrega a push-sync-now — distingue un
        // reintento manual explícito (botón "Reintentar", pull-to-refresh) de una corrida
        // periódica silenciosa, para notificar fallos con umbrales distintos (ver abajo).
        const val KEY_MANUAL_RETRY = "manual_retry"

        // runAttemptCount se reinicia en cada corrida periódica nueva (confirmado en el bytecode
        // de WorkManager) — este tope aplica dentro de cada ventana de 15 min, no acumulado.
        internal const val MAX_ATTEMPTS = 5

        // Un reintento manual es una acción explícita y deliberada del usuario — a diferencia de
        // una corrida periódica silenciosa, no tiene sentido esperar 5 intentos para avisarle que
        // falló: se notifica en el primer intento (runAttemptCount == 0).
        private const val MANUAL_RETRY_ATTEMPT_THRESHOLD = 0

        // `==` (no `>=`) — notifica exactamente una vez al cruzar el umbral. Como PushWorker
        // siempre devuelve Result.retry() en fallo (nunca deja de reintentar), runAttemptCount
        // pasa por todos los enteros en orden sin saltos, así que `==` no puede "perderse" el
        // umbral — y evita reemitir SyncEvent.Failure en cada intento posterior (spam de Snackbar).
        internal fun shouldNotifyFailure(runAttemptCount: Int, isManualRetry: Boolean): Boolean =
            runAttemptCount == if (isManualRetry) MANUAL_RETRY_ATTEMPT_THRESHOLD else MAX_ATTEMPTS
    }
}
