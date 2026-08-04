package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.entities.ClientEntity
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientSelectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeClientDao: FakeClientDao
    private lateinit var repository: ClientRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClientDao = FakeClientDao()
        repository = ClientRepository(
            fakeClientDao,
            CalculateClientBalanceUseCase(
                SaleRepository(FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), FakeCreditBalanceDao())
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(tenantId: String? = "tenant-1") =
        ClientSelectViewModel(repository, flowOf(tenantId))

    private fun entity(id: String, name: String, tenantId: String = "tenant-1") = ClientEntity(
        id = id,
        fkTenant = tenantId,
        name = name,
        phone = "555-0000",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = "synced",
    )

    @Test
    fun `results emits empty list when no clients exist`() = runTest {
        val vm = viewModel()
        val job = launch { vm.results.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.results.value)
        job.cancel()
    }

    @Test
    fun `results reflects clients for the active tenant`() = runTest {
        val vm = viewModel()
        val job = launch { vm.results.collect {} }
        fakeClientDao.setClients(listOf(entity("c1", "Ana López"), entity("c2", "Bernardo Ruiz")))
        advanceUntilIdle()
        assertEquals(2, vm.results.value.size)
        job.cancel()
    }

    @Test
    fun `results excludes clients from a different tenant`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        val job = launch { vm.results.collect {} }
        fakeClientDao.setClients(listOf(entity("c1", "Ana López", tenantId = "tenant-1"), entity("c2", "Otro", tenantId = "tenant-2")))
        advanceUntilIdle()
        assertEquals(listOf("c1"), vm.results.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `onSearchQueryChange filters results by name`() = runTest {
        val vm = viewModel()
        val job = launch { vm.results.collect {} }
        fakeClientDao.setClients(listOf(entity("c1", "Ana López"), entity("c2", "Bernardo Ruiz")))
        advanceUntilIdle()

        vm.onSearchQueryChange("Ana")
        advanceUntilIdle()

        assertEquals(listOf("c1"), vm.results.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `results emits empty list when tenantId is null`() = runTest {
        val vm = viewModel(tenantId = null)
        val job = launch { vm.results.collect {} }
        fakeClientDao.setClients(listOf(entity("c1", "Ana López")))
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.results.value)
        job.cancel()
    }

    @Test
    fun `onSearchClear resets the search query`() {
        val vm = viewModel()
        vm.onSearchQueryChange("algo")
        vm.onSearchClear()
        assertEquals("", vm.searchQuery.value)
    }
}
