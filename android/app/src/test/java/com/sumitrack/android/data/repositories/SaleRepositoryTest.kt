package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.domain.models.OrderSummary
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaleRepositoryTest {

    private lateinit var fakeDao: FakeSaleDao
    private lateinit var repository: SaleRepository

    @Before
    fun setUp() {
        fakeDao = FakeSaleDao()
        repository = SaleRepository(fakeDao)
    }

    private fun sale(
        id: String,
        clientId: String = "client-1",
        folio: String = "A1",
        total: BigDecimal = BigDecimal("100.00"),
        status: String = "pending",
        syncStatus: String = "synced",
    ) = SaleEntity(
        id = id,
        fkTenant = "tenant-1",
        fkClient = clientId,
        folio = folio,
        total = total,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = syncStatus,
    )

    @Test
    fun `getOpenSalesForClient maps SaleEntity to domain Sale`() = runTest {
        fakeDao.setSales(listOf(sale(id = "s1", folio = "A1", total = BigDecimal("250.00"), status = "partial", syncStatus = "pending")))

        val result = repository.getOpenSalesForClient("client-1", "tenant-1")

        assertEquals(1, result.size)
        val mapped = result.first()
        assertEquals("s1", mapped.id)
        assertEquals("A1", mapped.folio)
        assertEquals(BigDecimal("250.00"), mapped.total)
        assertEquals(SaleStatus.PARTIAL, mapped.status)
        assertEquals(SyncStatus.PENDING, mapped.syncStatus)
    }

    @Test
    fun `getOpenSalesForClient returns empty list when client has no sales`() = runTest {
        val result = repository.getOpenSalesForClient("client-without-sales", "tenant-1")
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `getOpenSalesForClient excludes sales from a different tenant`() = runTest {
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1")))

        val result = repository.getOpenSalesForClient("client-1", "tenant-2")

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `getOrdersForTenant maps SaleEntity to OrderSummary including clientName`() = runTest {
        fakeDao.setClientNames(mapOf("client-1" to "Ana López"))
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1", folio = "A1", total = BigDecimal("250.00"), status = "partial")))

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, orders.size)
        val order = orders.first()
        assertEquals("s1", order.id)
        assertEquals("A1", order.folio)
        assertEquals("Ana López", order.clientName)
        assertEquals(BigDecimal("250.00"), order.total)
        assertEquals(SaleStatus.PARTIAL, order.status)
    }

    @Test
    fun `getOrdersForTenant filters by statusFilter`() = runTest {
        fakeDao.setClientNames(mapOf("client-1" to "Ana López"))
        fakeDao.setSales(
            listOf(
                sale(id = "s1", clientId = "client-1", status = "pending"),
                sale(id = "s2", clientId = "client-1", status = "paid"),
            )
        )

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", SaleStatus.PAID, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("s2"), orders.map { it.id })
    }

    @Test
    fun `getOrdersForTenant excludes orders from a different tenant`() = runTest {
        fakeDao.setClientNames(mapOf("client-1" to "Ana López"))
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1")))

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-2", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Any>(), orders)
    }

    @Test
    fun `getOrdersForTenant search matches folio and excludes non-matching sales`() = runTest {
        fakeDao.setClientNames(mapOf("client-1" to "Ana López"))
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1", folio = "A1"), sale(id = "s2", clientId = "client-1", folio = "B2")))

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "a1").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("s1"), orders.map { it.id })
    }

    @Test
    fun `getOrdersForTenant search matches client name ignoring accents and excludes non-matching sales`() = runTest {
        fakeDao.setClientNames(mapOf("client-1" to "Pérez", "client-2" to "Ruiz"))
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1", folio = "A1"), sale(id = "s2", clientId = "client-2", folio = "A2")))

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "perez").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("s1"), orders.map { it.id })
    }

    @Test
    fun `getOrdersForTenant combines statusFilter and searchQuery`() = runTest {
        fakeDao.setClientNames(mapOf("client-1" to "Ana López"))
        fakeDao.setSales(
            listOf(
                sale(id = "s1", clientId = "client-1", folio = "A1", status = "pending"),
                sale(id = "s2", clientId = "client-1", folio = "A2", status = "paid"),
            )
        )

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", SaleStatus.PENDING, "a1").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("s1"), orders.map { it.id })
    }

    @Test
    fun `getOrdersForTenant falls back to a placeholder name for an orphaned client`() = runTest {
        // No se llama setClientNames para "client-1" — simula fk_client sin fila correspondiente.
        fakeDao.setSales(listOf(sale(id = "s1", clientId = "client-1", folio = "A1")))

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, orders.size)
        assertEquals("(cliente eliminado)", orders.first().clientName)
    }

    @Test
    fun `getOrdersForTenant returns empty list when no sales exist`() = runTest {
        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Any>(), orders)
    }
}
