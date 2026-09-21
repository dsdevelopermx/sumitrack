package com.sumitrack.android.sync.reminders

import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
// runCurrent() (no advanceUntilIdle) a propósito: advanceUntilIdle no ejecuta las corrutinas de
// backgroundScope, que son las que corren al reconciliador.
class ReminderReconcilerTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private fun at(year: Int, month: Int, day: Int, hour: Int): Instant =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant()

    private val now = at(2026, 9, 21, 8)

    private class FakeReminderScheduler : ReminderScheduler {
        val calls = mutableListOf<Pair<List<ReminderPlan>, Set<String>>>()
        val lastPlans: List<ReminderPlan> get() = calls.last().first
        val lastOpenIds: Set<String> get() = calls.last().second

        override suspend fun replaceAll(plans: List<ReminderPlan>, openInstallmentIds: Set<String>) {
            calls += plans to openInstallmentIds
        }
    }

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    private lateinit var installmentDao: FakeInstallmentDao
    private lateinit var settingsDao: FakeSettingsDao
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var tenant: MutableStateFlow<String?>
    private lateinit var reconciler: ReminderReconciler

    @Before
    fun setUp() {
        installmentDao = FakeInstallmentDao()
        settingsDao = FakeSettingsDao()
        scheduler = FakeReminderScheduler()
        tenant = MutableStateFlow("t1")
        reconciler = ReminderReconciler(installmentDao, SettingsRepository(settingsDao, noOpApiService), scheduler, tenant)
    }

    private fun installment(id: String, dueDate: Instant, status: String = "pending", tenantId: String = "t1") = InstallmentEntity(
        id = id,
        fkTenant = tenantId,
        fkSale = "s1",
        amount = BigDecimal("100.00"),
        dueDate = dueDate,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `parcialidad futura se programa con el triggerAt esperado y queda en openIds`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(2026, 10, 1, 15))))

        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()

        assertEquals(listOf(ReminderPlan("i1", at(2026, 9, 28, 9))), scheduler.lastPlans)
        assertEquals(setOf("i1"), scheduler.lastOpenIds)
    }

    @Test
    fun `marcar la parcialidad paid la saca del plan y de openIds (AC-3)`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(2026, 10, 1, 15))))
        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()

        installmentDao.upsertAll(listOf(installment("i1", at(2026, 10, 1, 15), status = "paid")))
        runCurrent()

        assertTrue(scheduler.lastPlans.isEmpty())
        assertTrue(scheduler.lastOpenIds.isEmpty())
    }

    @Test
    fun `cambiar dias_anticipacion_recordatorio recalcula el triggerAt (AC-4)`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(2026, 10, 1, 15))))
        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()

        settingsDao.upsertAll(listOf(SettingsEntity(key = "dias_anticipacion_recordatorio", value = "5", syncStatus = "pending")))
        runCurrent()

        assertEquals(listOf(ReminderPlan("i1", at(2026, 9, 26, 9))), scheduler.lastPlans)
    }

    @Test
    fun `al cerrar sesion (tenant null) se cancelan todos los recordatorios (AC-6)`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(2026, 10, 1, 15))))
        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()

        tenant.value = null
        runCurrent()

        assertTrue(scheduler.lastPlans.isEmpty())
        assertTrue(scheduler.lastOpenIds.isEmpty())
    }

    @Test
    fun `parcialidad de otro tenant no se programa`() = runTest {
        installmentDao.upsertAll(listOf(installment("other", at(2026, 10, 1, 15), tenantId = "t2")))

        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()

        assertTrue(scheduler.lastPlans.isEmpty())
        assertTrue(scheduler.lastOpenIds.isEmpty())
    }

    @Test
    fun `parcialidad abierta con trigger ya pasado no esta en plans pero si en openIds`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(2026, 9, 22, 15))))

        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()

        assertTrue(scheduler.lastPlans.isEmpty())
        assertEquals(setOf("i1"), scheduler.lastOpenIds)
    }

    @Test
    fun `una escritura que no cambia el plan (markSynced) no vuelve a llamar al scheduler`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(2026, 10, 1, 15))))
        reconciler.start(backgroundScope, { now }, { zone })
        runCurrent()
        assertEquals(1, scheduler.calls.size)

        installmentDao.markSynced(listOf("i1"))
        runCurrent()

        assertEquals(1, scheduler.calls.size)
    }

    // Delega en un FakeInstallmentDao real salvo la observación, que siempre lanza — simula un error de Room.
    private class ThrowingInstallmentDao(delegate: FakeInstallmentDao) : InstallmentDao by delegate {
        override fun observeOpenForTenant(tenantId: String): Flow<List<InstallmentEntity>> =
            flow { throw RuntimeException("boom") }
    }

    @Test
    fun `un fallo del flujo de Room no propaga la excepcion ni llama al scheduler`() = runTest {
        val failing = ReminderReconciler(
            ThrowingInstallmentDao(installmentDao),
            SettingsRepository(settingsDao, noOpApiService),
            scheduler,
            tenant,
        )

        failing.start(backgroundScope, { now }, { zone })
        runCurrent()

        assertTrue(scheduler.calls.isEmpty())
    }
}
