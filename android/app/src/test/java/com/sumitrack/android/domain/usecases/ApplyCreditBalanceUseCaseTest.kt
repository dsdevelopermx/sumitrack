package com.sumitrack.android.domain.usecases

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplyCreditBalanceUseCaseTest {

    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var fakeCreditBalanceDao: FakeCreditBalanceDao
    private lateinit var useCase: ApplyCreditBalanceUseCase

    @Before
    fun setUp() {
        fakeSaleDao = FakeSaleDao()
        fakeCreditBalanceDao = FakeCreditBalanceDao()
        val saleRepository = SaleRepository(
            FakeTransactionRunner(), fakeSaleDao, FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), fakeCreditBalanceDao,
        )
        useCase = ApplyCreditBalanceUseCase(saleRepository)
    }

    @Test
    fun `creates a CreditBalanceEntity for the amount given, delegating to cancelSale`() = runTest {
        fakeSaleDao.setSales(
            listOf(
                SaleEntity(
                    id = "s1", fkTenant = "tenant-1", fkClient = "client-1", folio = "A1", total = BigDecimal("300.00"),
                    status = "partial", createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "pending",
                )
            )
        )

        val result = useCase("tenant-1", "s1", BigDecimal("200.00"))

        assertTrue(result)
        val credit = fakeCreditBalanceDao.getForClient("client-1", "tenant-1")
        assertEquals(1, credit.size)
        assertEquals(BigDecimal("200.00"), credit.first().amount)
        assertEquals("cancelled", fakeSaleDao.getById("s1", "tenant-1")?.status)
    }
}
