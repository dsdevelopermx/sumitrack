package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import java.math.BigDecimal
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
class OrderListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeSaleDao
    private lateinit var repository: SaleRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeSaleDao()
        repository = SaleRepository(fakeDao)
        fakeDao.setClientNames(mapOf("client-1" to "Ana López", "client-2" to "Bernardo Ruiz"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(tenantId: String? = "tenant-1") =
        OrderListViewModel(repository, flowOf(tenantId))

    private fun sale(
        id: String,
        clientId: String = "client-1",
        folio: String = "A1",
        status: String = "pending",
        tenantId: String = "tenant-1",
    ) = SaleEntity(
        id = id,
        fkTenant = tenantId,
        fkClient = clientId,
        folio = folio,
        total = BigDecimal("100.00"),
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = "synced",
    )

    @Test
    fun `orders emits empty list when no sales exist`() = runTest {
        val vm = viewModel()
        val job = launch { vm.orders.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.orders.value)
        job.cancel()
    }

    @Test
    fun `orders emits all sales for the active tenant`() = runTest {
        val vm = viewModel()
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1"), sale(id = "s2", clientId = "client-2")))
        advanceUntilIdle()
        assertEquals(2, vm.orders.value.size)
        job.cancel()
    }

    @Test
    fun `orders excludes sales from a different tenant`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1", tenantId = "tenant-1"), sale(id = "s2", tenantId = "tenant-2")))
        advanceUntilIdle()
        assertEquals(listOf("s1"), vm.orders.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `orders emits empty list when tenantId is null`() = runTest {
        val vm = viewModel(tenantId = null)
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1")))
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.orders.value)
        job.cancel()
    }

    @Test
    fun `onStatusFilterSelected filters orders by status`() = runTest {
        val vm = viewModel()
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1", status = "pending"), sale(id = "s2", status = "paid")))
        advanceUntilIdle()

        vm.onStatusFilterSelected(SaleStatus.PAID)
        advanceUntilIdle()

        assertEquals(listOf("s2"), vm.orders.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `onStatusFilterSelected with null clears the filter`() = runTest {
        val vm = viewModel()
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1", status = "pending"), sale(id = "s2", status = "paid")))
        advanceUntilIdle()

        vm.onStatusFilterSelected(SaleStatus.PAID)
        advanceUntilIdle()
        vm.onStatusFilterSelected(null)
        advanceUntilIdle()

        assertEquals(2, vm.orders.value.size)
        job.cancel()
    }

    @Test
    fun `onSearchQueryChange filters by folio`() = runTest {
        val vm = viewModel()
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1", folio = "A1"), sale(id = "s2", folio = "A2")))
        advanceUntilIdle()

        vm.onSearchQueryChange("A1")
        advanceUntilIdle()

        assertEquals(listOf("s1"), vm.orders.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `onSearchQueryChange filters by client name`() = runTest {
        val vm = viewModel()
        val job = launch { vm.orders.collect {} }
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1"), sale(id = "s2", clientId = "client-2")))
        advanceUntilIdle()

        vm.onSearchQueryChange("Bernardo")
        advanceUntilIdle()

        assertEquals(listOf("s2"), vm.orders.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `onSearchClear resets the search query`() = runTest {
        val vm = viewModel()
        vm.onSearchQueryChange("algo")
        vm.onSearchClear()
        assertEquals("", vm.searchQuery.value)
    }
}
