package com.sumitrack.android.ui.screens.agenda

import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("America/Mexico_City")

    // Lunes 21 de septiembre de 2026, 09:00 hora local.
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, zone).toInstant(), zone)

    private fun at(month: Int, day: Int, hour: Int = 12): Instant =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant()

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    private lateinit var installmentDao: FakeInstallmentDao
    private lateinit var settingsDao: FakeSettingsDao
    private lateinit var tenant: MutableStateFlow<String?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        installmentDao = FakeInstallmentDao()
        settingsDao = FakeSettingsDao()
        tenant = MutableStateFlow("t1")
        installmentDao.setSaleContext("s1", "A1", "Juan Pérez")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MutableClock(var current: Instant, private val zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
        override fun instant(): Instant = current
    }

    private fun viewModel(clock: Clock = this.clock) = AgendaViewModel(
        saleRepository = SaleRepository(
            FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), installmentDao, FakePaymentDao(), FakeCreditBalanceDao(),
        ),
        settingsRepository = SettingsRepository(settingsDao, noOpApiService),
        tenantId = tenant,
        clock = clock,
    )

    private fun installment(id: String, due: Instant, status: String = "pending", tenantId: String = "t1") = InstallmentEntity(
        id = id,
        fkTenant = tenantId,
        fkSale = "s1",
        amount = BigDecimal("100.00"),
        dueDate = due,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun AgendaUiState.cell(date: LocalDate) = cells.filterNotNull().first { it.date == date }

    @Test
    fun `el mes inicial es el del reloj`() = runTest {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        assertEquals("Septiembre 2026", vm.uiState.value.monthLabel)
        job.cancel()
    }

    @Test
    fun `sin parcialidades hasAnyEntries es falso y deja de cargar`() = runTest {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasAnyEntries)
        job.cancel()
    }

    @Test
    fun `cuenta cobros por dia y marca la campana segun el umbral por defecto`() = runTest {
        installmentDao.upsertAll(
            listOf(
                installment("i1", at(9, 21)), installment("i2", at(9, 21, 15)),
                installment("i3", at(9, 23)), installment("i4", at(9, 30)),
            ),
        )
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hasAnyEntries)
        assertEquals(2, state.cell(day(9, 21)).count)
        assertTrue(state.cell(day(9, 21)).hasAlert)
        assertTrue(state.cell(day(9, 23)).hasAlert)
        assertFalse(state.cell(day(9, 30)).hasAlert)
        job.cancel()
    }

    @Test
    fun `cambiar dias_anticipacion_recordatorio actualiza la campana`() = runTest {
        installmentDao.upsertAll(listOf(installment("i4", at(9, 30))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.cell(day(9, 30)).hasAlert)

        settingsDao.upsertAll(listOf(SettingsEntity(key = "dias_anticipacion_recordatorio", value = "10", syncStatus = "pending")))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cell(day(9, 30)).hasAlert)
        job.cancel()
    }

    @Test
    fun `onNextMonth y onPreviousMonth cambian de mes y limpian la seleccion`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onDayClick(day(9, 21))
        advanceUntilIdle()
        assertEquals(day(9, 21), vm.uiState.value.selectedDate)

        vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
        assertNull(vm.uiState.value.selectedDate)

        vm.onPreviousMonth()
        vm.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.month)
        job.cancel()
    }

    @Test
    fun `onPreviousMonth tambien limpia la seleccion`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onDayClick(day(9, 21))
        advanceUntilIdle()
        assertEquals(day(9, 21), vm.uiState.value.selectedDate)

        vm.onPreviousMonth()
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedDate)
        job.cancel()
    }

    @Test
    fun `si los cobros del dia seleccionado desaparecen la seleccion se limpia`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onDayClick(day(9, 21))
        advanceUntilIdle()
        assertEquals(day(9, 21), vm.uiState.value.selectedDate)

        installmentDao.upsertAll(listOf(installment("i1", at(9, 21), status = "paid")))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedDate)
        assertTrue(vm.uiState.value.selectedEntries.isEmpty())
        job.cancel()
    }

    @Test
    fun `onResume recalcula Vencida al avanzar la hora`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21, 12))))
        val mutableClock = MutableClock(at(9, 21, 9), zone)
        val vm = viewModel(mutableClock)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onDayClick(day(9, 21))
        advanceUntilIdle()
        assertEquals(listOf(false), vm.uiState.value.selectedEntries.map { it.isOverdue })

        mutableClock.current = at(9, 21, 13)
        vm.onResume()
        advanceUntilIdle()

        assertEquals(listOf(true), vm.uiState.value.selectedEntries.map { it.isOverdue })
        job.cancel()
    }

    @Test
    fun `onResume tras cambiar el dia regresa al mes actual y limpia la seleccion`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val mutableClock = MutableClock(at(9, 21, 9), zone)
        val vm = viewModel(mutableClock)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onNextMonth()
        vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), vm.uiState.value.month)

        mutableClock.current = at(9, 22, 9)
        vm.onResume()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        assertNull(vm.uiState.value.selectedDate)
        job.cancel()
    }

    @Test
    fun `onResume el mismo dia no mueve el mes que el usuario esta viendo`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onNextMonth()
        advanceUntilIdle()

        vm.onResume()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
        job.cancel()
    }

    @Test
    fun `avisa de cobros vencidos en meses anteriores y al tocarlo salta al mes del mas antiguo`() = runTest {
        installmentDao.upsertAll(
            listOf(installment("julio", at(7, 10)), installment("agosto", at(8, 15)), installment("septiembre", at(9, 30))),
        )
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.overdueBeforeMonthCount)
        assertEquals(YearMonth.of(2026, 7), vm.uiState.value.earliestOverdueMonth)

        vm.onOverdueNoticeClick()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 7), vm.uiState.value.month)
        assertEquals(0, vm.uiState.value.overdueBeforeMonthCount)
        job.cancel()
    }

    @Test
    fun `sin vencidos previos no hay aviso y tocarlo no hace nada`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 30))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onOverdueNoticeClick()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.overdueBeforeMonthCount)
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        job.cancel()
    }

    @Test
    fun `onDayClick selecciona el dia y lista sus cobros con isOverdue segun el reloj`() = runTest {
        installmentDao.upsertAll(
            listOf(installment("ayer", at(9, 20)), installment("manana-b", at(9, 22, 15)), installment("manana-a", at(9, 22, 9))),
        )
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDayClick(day(9, 20))
        advanceUntilIdle()
        assertEquals(listOf(true), vm.uiState.value.selectedEntries.map { it.isOverdue })

        vm.onDayClick(day(9, 22))
        advanceUntilIdle()
        assertEquals(listOf("manana-a", "manana-b"), vm.uiState.value.selectedEntries.map { it.entry.installmentId })
        assertEquals(listOf(false, false), vm.uiState.value.selectedEntries.map { it.isOverdue })
        assertEquals("A1", vm.uiState.value.selectedEntries.first().entry.folio)
        assertEquals("Juan Pérez", vm.uiState.value.selectedEntries.first().entry.clientName)
        job.cancel()
    }

    @Test
    fun `tocar el mismo dia otra vez lo deselecciona`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDayClick(day(9, 21))
        advanceUntilIdle()
        vm.onDayClick(day(9, 21))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedDate)
        assertTrue(vm.uiState.value.selectedEntries.isEmpty())
        job.cancel()
    }

    @Test
    fun `tocar un dia sin cobros no lo selecciona`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDayClick(day(9, 25))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedDate)
        job.cancel()
    }

    @Test
    fun `parcialidades paid o de otro tenant no aparecen`() = runTest {
        installmentDao.upsertAll(
            listOf(installment("pagada", at(9, 21), status = "paid"), installment("ajena", at(9, 21), tenantId = "t2")),
        )
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasAnyEntries)
        job.cancel()
    }

    @Test
    fun `sin tenant (sesion cerrada) la agenda queda vacia`() = runTest {
        installmentDao.upsertAll(listOf(installment("i1", at(9, 21))))
        tenant.value = null
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasAnyEntries)
        job.cancel()
    }
}
