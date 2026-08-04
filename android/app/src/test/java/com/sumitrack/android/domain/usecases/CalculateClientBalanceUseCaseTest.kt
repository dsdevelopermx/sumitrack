package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.entities.PaymentEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateClientBalanceUseCaseTest {

    private lateinit var fakeDao: FakeSaleDao
    private lateinit var fakePaymentDao: FakePaymentDao
    private lateinit var useCase: CalculateClientBalanceUseCase

    @Before
    fun setUp() {
        fakeDao = FakeSaleDao()
        fakePaymentDao = FakePaymentDao()
        useCase = CalculateClientBalanceUseCase(
            SaleRepository(FakeTransactionRunner(), fakeDao, FakeSaleItemDao(), FakeInstallmentDao(), fakePaymentDao, FakeCreditBalanceDao())
        )
    }

    private fun sale(id: String, clientId: String, total: BigDecimal, status: String) = SaleEntity(
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

    @Test
    fun `invoke sums pending and partial sales for the client`() = runTest {
        fakeDao.setSales(
            listOf(
                sale("1", "client-1", BigDecimal("100.50"), "pending"),
                sale("2", "client-1", BigDecimal("200.25"), "partial"),
            )
        )

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal("300.75"), result)
    }

    @Test
    fun `invoke excludes paid and cancelled sales`() = runTest {
        fakeDao.setSales(
            listOf(
                sale("1", "client-1", BigDecimal("100.00"), "pending"),
                sale("2", "client-1", BigDecimal("500.00"), "paid"),
                sale("3", "client-1", BigDecimal("999.00"), "cancelled"),
            )
        )

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal("100.00"), result)
    }

    @Test
    fun `invoke excludes sales from other clients`() = runTest {
        fakeDao.setSales(
            listOf(
                sale("1", "client-1", BigDecimal("100.00"), "pending"),
                sale("2", "client-2", BigDecimal("400.00"), "pending"),
            )
        )

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal("100.00"), result)
    }

    @Test
    fun `invoke excludes sales from other tenants`() = runTest {
        fakeDao.setSales(
            listOf(
                SaleEntity(
                    id = "1", fkTenant = "tenant-2", fkClient = "client-1", folio = "A1",
                    total = BigDecimal("999.00"), status = "pending",
                    createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "synced",
                )
            )
        )

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `invoke returns zero for client with no sales`() = runTest {
        val result = useCase("client-without-sales", "tenant-1")

        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `invoke subtracts payments already received on a partially-paid sale`() = runTest {
        fakeDao.setSales(listOf(sale("1", "client-1", BigDecimal("300.00"), "partial")))
        fakePaymentDao.upsertAll(
            listOf(
                PaymentEntity(
                    id = "p1", fkTenant = "tenant-1", fkSale = "1", fkInstallment = "i1", method = "efectivo",
                    amount = BigDecimal("150.00"), paidAt = Instant.now(), createdAt = Instant.now(), updatedAt = Instant.now(),
                    syncStatus = "pending",
                )
            )
        )

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal("150.00"), result)
    }
}
