package com.sumitrack.android.ui.screens.clients

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import java.math.BigDecimal
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeClientDao: FakeClientDao
    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var clientRepository: ClientRepository
    private lateinit var saleRepository: SaleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClientDao = FakeClientDao()
        fakeSaleDao = FakeSaleDao()
        saleRepository = SaleRepository(fakeSaleDao)
        clientRepository = ClientRepository(fakeClientDao, CalculateClientBalanceUseCase(saleRepository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun clientEntity(id: String, name: String = "Ferretería El Clavo") = ClientEntity(
        id = id,
        fkTenant = "tenant-1",
        name = name,
        phone = "555-0000",
        createdAt = Instant.ofEpochMilli(1_000_000L),
        updatedAt = Instant.ofEpochMilli(1_000_000L),
        syncStatus = "synced",
    )

    private fun saleEntity(id: String, clientId: String, total: BigDecimal, status: String) = SaleEntity(
        id = id,
        fkTenant = "tenant-1",
        fkClient = clientId,
        folio = "A$id",
        total = total,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = "synced",
    )

    private fun viewModel(clientId: String) = ClientProfileViewModel(
        SavedStateHandle(mapOf("clientId" to clientId)),
        clientRepository,
        saleRepository,
    )

    @Test
    fun `initial load populates client and open sales`() = runTest {
        fakeClientDao.setClients(listOf(clientEntity("client-1")))
        fakeSaleDao.setSales(
            listOf(
                saleEntity("1", "client-1", BigDecimal("100.00"), "pending"),
                saleEntity("2", "client-1", BigDecimal("50.00"), "paid"),
            )
        )

        val vm = viewModel("client-1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(!state.isLoading)
        assertNotNull(state.client)
        assertEquals("Ferretería El Clavo", state.client!!.name)
        assertEquals(BigDecimal("100.00"), state.client!!.balance)
        assertEquals(1, state.openSales.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun `client not found sets errorMessage without crashing`() = runTest {
        val vm = viewModel("missing-client")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(!state.isLoading)
        assertNull(state.client)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `client with no open sales returns empty list and zero balance`() = runTest {
        fakeClientDao.setClients(listOf(clientEntity("client-1")))

        val vm = viewModel("client-1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(emptyList<Any>(), state.openSales)
        assertEquals(BigDecimal.ZERO, state.client!!.balance)
    }

    @Test
    fun `sales fetch failure sets distinct errorMessage while keeping client loaded`() = runTest {
        fakeClientDao.setClients(listOf(clientEntity("client-1")))
        // Deja pasar la 1ra llamada (cálculo de saldo dentro de getClientById) y falla desde
        // la 2da (el fetch explícito de openSales en el ViewModel).
        fakeSaleDao.throwFromCallNumber = 2

        val vm = viewModel("client-1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(!state.isLoading)
        assertNotNull(state.client)
        assertEquals(emptyList<Any>(), state.openSales)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `load can be called again to refresh state`() = runTest {
        fakeClientDao.setClients(listOf(clientEntity("client-1", name = "Nombre Original")))
        val vm = viewModel("client-1")
        advanceUntilIdle()
        assertEquals("Nombre Original", vm.uiState.value.client!!.name)

        fakeClientDao.setClients(listOf(clientEntity("client-1", name = "Nombre Actualizado")))
        vm.load()
        advanceUntilIdle()

        assertEquals("Nombre Actualizado", vm.uiState.value.client!!.name)
    }
}
