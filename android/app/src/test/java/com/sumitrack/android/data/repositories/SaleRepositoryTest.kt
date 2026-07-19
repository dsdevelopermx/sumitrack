package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
}
