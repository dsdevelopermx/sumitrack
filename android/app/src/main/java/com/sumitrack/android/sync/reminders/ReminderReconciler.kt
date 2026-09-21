package com.sumitrack.android.sync.reminders

import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.di.TenantId
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// Observa Room (parcialidades abiertas del tenant) + dias_anticipacion_recordatorio y mantiene el
// scheduler sincronizado. Al ser reactivo cubre TODOS los puntos de escritura que afectan a una
// parcialidad (alta, cobro, cancelación, pull de otro dispositivo, resolución de conflictos) y el
// cambio de Settings sin engancharse a ninguno. Sin sesión (tenant null) la lista queda vacía, así
// que replaceAll cancela todo — cierre de sesión (AC-6).
@Singleton
class ReminderReconciler @Inject constructor(
    private val installmentDao: InstallmentDao,
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    @TenantId private val tenantId: Flow<String?>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(
        scope: CoroutineScope,
        now: () -> Instant = { Instant.now() },
        // Proveedor (no valor fijo): la zona se lee en cada re-plan, así un cambio de zona horaria
        // con el proceso vivo no deja el cálculo del día/hora con la zona vieja.
        zone: () -> ZoneId = { ZoneId.systemDefault() },
    ): Job = scope.launch {
        combine(
            tenantId.flatMapLatest { tenant ->
                if (tenant == null) flowOf(emptyList()) else installmentDao.observeOpenForTenant(tenant)
            },
            settingsRepository.observeDiasAnticipacion(),
        ) { installments, dias ->
            ReminderPlanner.plan(installments, dias, now(), zone()) to installments.map { it.id }.toSet()
        }
            // Room re-emite la consulta ante CUALQUIER escritura a `installments` (p. ej. markSynced tras
            // cada push) aunque el plan resultante sea idéntico; sin esto cada emisión re-encolaría
            // (REPLACE) todos los recordatorios.
            .distinctUntilChanged()
            // Los recordatorios no son críticos y esto corre en un scope de aplicación: un fallo de Room
            // no debe tumbar el proceso. Un fallo detiene el reconciliador hasta el próximo arranque.
            .catch { }
            .collectLatest { (plans, openIds) -> scheduler.replaceAll(plans, openIds) }
    }
}
