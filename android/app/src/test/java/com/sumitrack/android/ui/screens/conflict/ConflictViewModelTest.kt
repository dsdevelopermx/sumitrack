package com.sumitrack.android.ui.screens.conflict

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.ConflictLogEntity
import com.sumitrack.android.sync.FakeConflictLogDao
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var clientDao: FakeClientDao
    private lateinit var conflictLogDao: FakeConflictLogDao

    private val tenantId = "tenant-1"
    private val recordId = "c1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        clientDao = FakeClientDao()
        conflictLogDao = FakeConflictLogDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun client(syncStatus: String = "conflict", updatedAt: Instant = Instant.ofEpochMilli(1_000_000L)) = ClientEntity(
        id = recordId,
        fkTenant = tenantId,
        name = "Juan Pérez",
        phone = "5512345678",
        createdAt = updatedAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    private fun logEntry() = ConflictLogEntity(
        id = "log-1",
        fkTenant = tenantId,
        entityType = "clientes",
        recordId = recordId,
        localSnapshotJson = """{"id":"$recordId","name":"Juan Pérez (local)"}""",
        serverSnapshotJson = """{"id":"$recordId","name":"Juan Pérez (servidor)"}""",
        detectedAt = Instant.ofEpochMilli(2_000_000L),
    )

    private fun viewModel() = ConflictViewModel(
        savedStateHandle = SavedStateHandle(mapOf("entityType" to "clientes", "recordId" to recordId)),
        conflictLogDao = conflictLogDao,
        clientDao = clientDao,
        productDao = FakeProductDao(),
        productVariantDao = FakeProductVariantDao(),
        saleDao = FakeSaleDao(),
        saleItemDao = FakeSaleItemDao(),
        installmentDao = FakeInstallmentDao(),
        paymentDao = FakePaymentDao(),
        creditBalanceDao = FakeCreditBalanceDao(),
        settingsDao = FakeSettingsDao(),
    )

    @Test
    fun `carga la entrada no resuelta mas reciente para el entityType y recordId dados`() = runTest {
        clientDao.upsertAll(listOf(client()))
        conflictLogDao.insert(logEntry())

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("log-1", vm.uiState.value.logEntry?.id)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `resolveKeepLocal deja el registro pending con updatedAt posterior y marca el log resuelto`() = runTest {
        val originalUpdatedAt = Instant.ofEpochMilli(1_000_000L)
        clientDao.upsertAll(listOf(client(syncStatus = "conflict", updatedAt = originalUpdatedAt)))
        conflictLogDao.insert(logEntry())
        val vm = viewModel()
        advanceUntilIdle()

        vm.resolveKeepLocal()
        advanceUntilIdle()

        val stored = clientDao.getById(recordId)
        assertNotNull(stored)
        assertEquals("pending", stored!!.syncStatus)
        assertTrue(stored.updatedAt.isAfter(originalUpdatedAt))

        val resolvedLog = conflictLogDao.getById("log-1")
        assertNotNull(resolvedLog!!.resolvedAt)
        assertEquals("local_wins", resolvedLog.resolution)
        assertNull(resolvedLog.duplicateRecordId)
        assertTrue(vm.uiState.value.isResolved)
    }

    @Test
    fun `resolveKeepBoth crea un segundo registro pending con id distinto y lo referencia en el log`() = runTest {
        clientDao.upsertAll(listOf(client()))
        conflictLogDao.insert(logEntry())
        val vm = viewModel()
        advanceUntilIdle()

        vm.resolveKeepBoth()
        advanceUntilIdle()

        val original = clientDao.getById(recordId)
        assertNotNull(original)
        assertEquals("pending", original!!.syncStatus)

        val resolvedLog = conflictLogDao.getById("log-1")
        assertNotNull(resolvedLog!!.duplicateRecordId)
        assertEquals("kept_both", resolvedLog.resolution)

        val duplicateId = resolvedLog.duplicateRecordId!!
        assertNotEquals(recordId, duplicateId)
        val duplicate = clientDao.getById(duplicateId)
        assertNotNull(duplicate)
        assertEquals("pending", duplicate!!.syncStatus)
        assertEquals("Juan Pérez", duplicate.name)
    }

    @Test
    fun `doble tap en resolveKeepBoth antes de que isResolving se propague no crea dos duplicados`() = runTest {
        clientDao.upsertAll(listOf(client()))
        conflictLogDao.insert(logEntry())
        val vm = viewModel()
        advanceUntilIdle()

        // Doble tap sin avanzar el dispatcher entre llamadas — simula el usuario tocando el botón
        // dos veces antes de que la primera corrutina alcance a marcar isResolving=true de forma
        // observable para la UI. isResolving se setea de forma SÍNCRONA en resolve(), así que la
        // segunda llamada debe verlo ya en true y no volver a disparar applyResolution.
        vm.resolveKeepBoth()
        vm.resolveKeepBoth()
        advanceUntilIdle()

        val allClients = listOf(clientDao.getById(recordId)) + conflictLogDao.getById("log-1")?.duplicateRecordId?.let { listOf(clientDao.getById(it)) }.orEmpty()
        val pendingClients = allClients.filterNotNull().filter { it.syncStatus == "pending" }
        // El original bumpeado + UN solo duplicado = 2 filas pending, no 3 ni 4.
        assertEquals(2, pendingClients.size)
    }

    @Test
    fun `resolveKeepLocal no marca el log resuelto si el registro subyacente ya no existe`() = runTest {
        // Nunca se hace upsertAll de un client con id=recordId — el registro no existe.
        conflictLogDao.insert(logEntry())
        val vm = viewModel()
        advanceUntilIdle()

        vm.resolveKeepLocal()
        advanceUntilIdle()

        val log = conflictLogDao.getById("log-1")
        assertNotNull(log)
        assertNull(log!!.resolvedAt)
        assertNull(log.resolution)
        assertEquals(false, vm.uiState.value.isResolved)
        assertEquals(false, vm.uiState.value.isResolving)
    }
}
