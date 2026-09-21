package com.sumitrack.android.sync.reminders

import com.sumitrack.android.data.local.entities.InstallmentEntity
import java.time.Instant
import java.time.ZoneId

data class ReminderPlan(val installmentId: String, val triggerAt: Instant)

// Lógica pura de "cuándo avisar" — `now` y `zone` son parámetros (mismo criterio que
// CalculateInstallmentsUseCase/toUiStatus) para tests deterministas sin reloj de sistema.
object ReminderPlanner {

    const val REMINDER_HOUR = 9

    // Solo devuelve disparos FUTUROS de parcialidades pending: una vez pasado el momento, la
    // parcialidad sale del plan y nunca se reprograma, lo que evita duplicados sin persistir "ya se
    // notificó". El día se calcula en la zona del dispositivo — la misma con la que la UI muestra
    // dueDate — para que coincida con la fecha que ve el proveedor.
    fun plan(
        installments: List<InstallmentEntity>,
        diasAnticipacion: Int,
        now: Instant,
        zone: ZoneId,
    ): List<ReminderPlan> =
        installments
            .filter { it.status == "pending" }
            .mapNotNull { installment ->
                val triggerAt = installment.dueDate
                    .atZone(zone)
                    .toLocalDate()
                    .minusDays(diasAnticipacion.toLong())
                    .atTime(REMINDER_HOUR, 0)
                    .atZone(zone)
                    .toInstant()
                if (triggerAt.isAfter(now)) ReminderPlan(installment.id, triggerAt) else null
            }
}
