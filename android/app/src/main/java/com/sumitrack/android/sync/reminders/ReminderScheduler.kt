package com.sumitrack.android.sync.reminders

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sumitrack.android.sync.workers.ReminderWorker
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

// Interfaz angosta (mismo patrón que PushSyncTrigger/SyncWorkObserver): el reconciliador se prueba en
// JVM puro con un fake, sin WorkManager real.
//
// `plans` = disparos FUTUROS a (re)encolar. `openInstallmentIds` = TODAS las parcialidades pending —
// se necesita para no cancelar un recordatorio ya encolado cuya hora pasó con el dispositivo apagado
// (esa parcialidad sale de `plans` pero sigue abierta; WorkManager lo ejecutará al volver).
interface ReminderScheduler {
    suspend fun replaceAll(plans: List<ReminderPlan>, openInstallmentIds: Set<String>)
}

class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) : ReminderScheduler {

    override suspend fun replaceAll(plans: List<ReminderPlan>, openInstallmentIds: Set<String>) {
        // try/catch explícito (no runCatching) en la parte suspendible: runCatching tragaría la
        // CancellationException de collectLatest y seguiría encolando con un plan ya obsoleto.
        try {
            workManager.getWorkInfosByTagFlow(TAG_ALL).first()
                .filter { it.state == WorkInfo.State.ENQUEUED }
                .forEach { info ->
                    val id = info.tags.firstOrNull { it.startsWith(TAG_ITEM_PREFIX) }?.removePrefix(TAG_ITEM_PREFIX)
                        ?: return@forEach
                    if (id !in openInstallmentIds) workManager.cancelUniqueWork(uniqueName(id))
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo de WorkManager nunca debe tumbar al reconciliador.
        }

        val now = Instant.now()
        plans.forEach { plan ->
            runCatching {
                // SIN setConstraints de red (AC-5): a diferencia de PushWorker/PullWorker, este trabajo
                // debe dispararse también sin conexión.
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now, plan.triggerAt).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ReminderWorker.KEY_INSTALLMENT_ID to plan.installmentId))
                    .addTag(TAG_ALL)
                    .addTag("$TAG_ITEM_PREFIX${plan.installmentId}")
                    .build()
                workManager.enqueueUniqueWork(uniqueName(plan.installmentId), ExistingWorkPolicy.REPLACE, request)
            }
        }
    }

    private fun uniqueName(installmentId: String) = "reminder-$installmentId"

    private companion object {
        const val TAG_ALL = "payment-reminder"
        const val TAG_ITEM_PREFIX = "reminder-installment:"
    }
}
