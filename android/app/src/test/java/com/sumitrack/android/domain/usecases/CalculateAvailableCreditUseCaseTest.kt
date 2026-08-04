package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.entities.CreditBalanceEntity
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

class CalculateAvailableCreditUseCaseTest {

    private lateinit var fakeCreditBalanceDao: FakeCreditBalanceDao
    private lateinit var useCase: CalculateAvailableCreditUseCase

    @Before
    fun setUp() {
        fakeCreditBalanceDao = FakeCreditBalanceDao()
        val saleRepository = SaleRepository(
            FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), fakeCreditBalanceDao,
        )
        useCase = CalculateAvailableCreditUseCase(saleRepository)
    }

    private fun creditRow(id: String, clientId: String, tenantId: String, amount: BigDecimal) = CreditBalanceEntity(
        id = id, fkTenant = tenantId, fkClient = clientId, amount = amount, origin = "cancellation",
        fkOriginSale = null, appliedAt = null, createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "pending",
    )

    @Test
    fun `sums credit rows for the client`() = runTest {
        fakeCreditBalanceDao.upsertAll(
            listOf(
                creditRow("c1", "client-1", "tenant-1", BigDecimal("100.00")),
                creditRow("c2", "client-1", "tenant-1", BigDecimal("50.00")),
            )
        )

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal("150.00"), result)
    }

    @Test
    fun `returns zero for a client with no credit`() = runTest {
        val result = useCase("client-without-credit", "tenant-1")
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `isolates by tenant`() = runTest {
        fakeCreditBalanceDao.upsertAll(listOf(creditRow("c1", "client-1", "tenant-2", BigDecimal("999.00"))))

        val result = useCase("client-1", "tenant-1")

        assertEquals(BigDecimal.ZERO, result)
    }
}
