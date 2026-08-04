package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.PaymentConfig
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.SyncStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CancelSaleUseCaseTest {

    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var fakeCreditBalanceDao: FakeCreditBalanceDao
    private lateinit var saleRepository: SaleRepository
    private lateinit var useCase: CancelSaleUseCase

    @Before
    fun setUp() {
        fakeSaleDao = FakeSaleDao()
        fakeCreditBalanceDao = FakeCreditBalanceDao()
        saleRepository = SaleRepository(
            FakeTransactionRunner(), fakeSaleDao, FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), fakeCreditBalanceDao,
        )
        useCase = CancelSaleUseCase(saleRepository)
    }

    private fun product(price: BigDecimal) = Product(
        id = "p1", fkTenant = "tenant-1", name = "Refresco", price = price, taxRate = BigDecimal.ZERO, isActive = true,
        createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = SyncStatus.SYNCED,
    )

    private suspend fun installmentSaleWithOnePayment(): String {
        val items = listOf(OrderDraftItem(product(BigDecimal("300.00")), null, 1))
        val saleId = saleRepository.createSale(
            "tenant-1", "client-1", "A1", items,
            PaymentConfig.Installments(listOf(InstallmentSuggestion(BigDecimal("300.00"), Instant.now().plusSeconds(86_400)))),
        )
        val installmentId = saleRepository.getSaleDetail(saleId, "tenant-1")!!.installments.first().id
        saleRepository.registerPayment(
            "tenant-1", saleId, installmentId, PaymentMethodType.EFECTIVO, BigDecimal("300.00"), Instant.now(),
        )
        return saleId
    }

    @Test
    fun `sale without payments ignores generateCredit and does not create a CreditBalanceEntity`() = runTest {
        val items = listOf(OrderDraftItem(product(BigDecimal("100.00")), null, 1))
        val saleId = saleRepository.createSale(
            "tenant-1", "client-1", "A1", items,
            PaymentConfig.Installments(listOf(InstallmentSuggestion(BigDecimal("100.00"), Instant.now().plusSeconds(86_400)))),
        )

        val result = useCase("tenant-1", saleId, generateCredit = true)

        assertTrue(result)
        assertEquals(emptyList<Any>(), fakeCreditBalanceDao.getForClient("client-1", "tenant-1"))
    }

    @Test
    fun `Opcion A cancels the sale without creating a CreditBalanceEntity`() = runTest {
        val saleId = installmentSaleWithOnePayment()

        val result = useCase("tenant-1", saleId, generateCredit = false)

        assertTrue(result)
        assertEquals(emptyList<Any>(), fakeCreditBalanceDao.getForClient("client-1", "tenant-1"))
        assertEquals("cancelled", fakeSaleDao.getById(saleId, "tenant-1")?.status)
    }

    @Test
    fun `Opcion B cancels the sale and creates a CreditBalanceEntity for the total collected`() = runTest {
        val saleId = installmentSaleWithOnePayment()

        val result = useCase("tenant-1", saleId, generateCredit = true)

        assertTrue(result)
        val credit = fakeCreditBalanceDao.getForClient("client-1", "tenant-1")
        assertEquals(1, credit.size)
        assertEquals(BigDecimal("300.00"), credit.first().amount)
    }

    @Test
    fun `returns false for an already cancelled sale`() = runTest {
        val saleId = installmentSaleWithOnePayment()
        useCase("tenant-1", saleId, generateCredit = false)

        val result = useCase("tenant-1", saleId, generateCredit = false)

        assertFalse(result)
    }

    @Test
    fun `returns false for an unknown saleId`() = runTest {
        val result = useCase("tenant-1", "does-not-exist", generateCredit = false)
        assertFalse(result)
    }

    @Test
    fun `a fully paid sale with payments follows the same credit-choice path as a partial sale`() = runTest {
        val items = listOf(OrderDraftItem(product(BigDecimal("100.00")), null, 1))
        val saleId = saleRepository.createSale(
            "tenant-1", "client-1", "A1", items,
            PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("100.00"))),
        )

        val result = useCase("tenant-1", saleId, generateCredit = true)

        assertTrue(result)
        assertEquals(BigDecimal("100.00"), fakeCreditBalanceDao.getForClient("client-1", "tenant-1").first().amount)
        assertEquals("cancelled", fakeSaleDao.getById(saleId, "tenant-1")?.status)
    }
}
