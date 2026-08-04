package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.entities.SaleEntity
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

class RegisterPaymentUseCaseTest {

    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var fakeInstallmentDao: FakeInstallmentDao
    private lateinit var fakePaymentDao: FakePaymentDao
    private lateinit var saleRepository: SaleRepository
    private lateinit var useCase: RegisterPaymentUseCase

    @Before
    fun setUp() {
        fakeSaleDao = FakeSaleDao()
        fakeInstallmentDao = FakeInstallmentDao()
        fakePaymentDao = FakePaymentDao()
        saleRepository = SaleRepository(FakeTransactionRunner(), fakeSaleDao, FakeSaleItemDao(), fakeInstallmentDao, fakePaymentDao, FakeCreditBalanceDao())
        useCase = RegisterPaymentUseCase(saleRepository)
    }

    private fun product(price: BigDecimal = BigDecimal("300.00")) = Product(
        id = "p1", fkTenant = "tenant-1", name = "Refresco", price = price, taxRate = BigDecimal.ZERO, isActive = true,
        createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = SyncStatus.SYNCED,
    )

    private fun pendingSingleSale(id: String = "s1", total: BigDecimal = BigDecimal("100.00")) = SaleEntity(
        id = id, fkTenant = "tenant-1", fkClient = "client-1", folio = "A1", total = total, status = "pending",
        createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "pending",
    )

    private suspend fun installmentSale(): String {
        val items = listOf(OrderDraftItem(product(), null, 1))
        return saleRepository.createSale(
            "tenant-1", "client-1", "A1", items,
            PaymentConfig.Installments(
                listOf(
                    InstallmentSuggestion(BigDecimal("150.00"), Instant.now().plusSeconds(86_400 * 15)),
                    InstallmentSuggestion(BigDecimal("150.00"), Instant.now().plusSeconds(86_400 * 30)),
                )
            ),
        )
    }

    @Test
    fun `full payment on a single-payment sale marks it paid`() = runTest {
        fakeSaleDao.setSales(listOf(pendingSingleSale(total = BigDecimal("100.00"))))

        val result = useCase("tenant-1", "s1", null, PaymentMethodType.EFECTIVO)

        assertTrue(result)
        assertEquals("paid", fakeSaleDao.getById("s1", "tenant-1")?.status)
        assertEquals(BigDecimal("100.00"), fakePaymentDao.getForSale("s1", "tenant-1").first().amount)
    }

    @Test
    fun `marking one of several pending installments paid sets the sale to partial`() = runTest {
        val saleId = installmentSale()
        val installmentId = fakeInstallmentDao.getForSale(saleId, "tenant-1").first().id

        val result = useCase("tenant-1", saleId, installmentId, PaymentMethodType.TRANSFERENCIA)

        assertTrue(result)
        assertEquals("partial", fakeSaleDao.getById(saleId, "tenant-1")?.status)
    }

    @Test
    fun `marking the last pending installment paid sets the sale to paid`() = runTest {
        val saleId = installmentSale()
        val installments = fakeInstallmentDao.getForSale(saleId, "tenant-1")
        useCase("tenant-1", saleId, installments[0].id, PaymentMethodType.EFECTIVO)

        val result = useCase("tenant-1", saleId, installments[1].id, PaymentMethodType.EFECTIVO)

        assertTrue(result)
        assertEquals("paid", fakeSaleDao.getById(saleId, "tenant-1")?.status)
    }

    @Test
    fun `returns false for an unknown saleId`() = runTest {
        val result = useCase("tenant-1", "does-not-exist", null, PaymentMethodType.EFECTIVO)
        assertFalse(result)
    }

    @Test
    fun `returns false when the sale is already cancelled`() = runTest {
        fakeSaleDao.setSales(listOf(pendingSingleSale().copy(status = "cancelled")))
        val result = useCase("tenant-1", "s1", null, PaymentMethodType.EFECTIVO)
        assertFalse(result)
    }

    @Test
    fun `returns false when the sale is already paid`() = runTest {
        fakeSaleDao.setSales(listOf(pendingSingleSale().copy(status = "paid")))
        val result = useCase("tenant-1", "s1", null, PaymentMethodType.EFECTIVO)
        assertFalse(result)
    }

    @Test
    fun `returns false when the installment is not found`() = runTest {
        val saleId = installmentSale()
        val result = useCase("tenant-1", saleId, "does-not-exist", PaymentMethodType.EFECTIVO)
        assertFalse(result)
    }

    @Test
    fun `returns false when the installment is already paid`() = runTest {
        val saleId = installmentSale()
        val installmentId = fakeInstallmentDao.getForSale(saleId, "tenant-1").first().id
        useCase("tenant-1", saleId, installmentId, PaymentMethodType.EFECTIVO)

        val result = useCase("tenant-1", saleId, installmentId, PaymentMethodType.EFECTIVO)

        assertFalse(result)
    }

    @Test
    fun `returns false for a single-payment request against a sale that has installments`() = runTest {
        val saleId = installmentSale()
        val result = useCase("tenant-1", saleId, null, PaymentMethodType.EFECTIVO)
        assertFalse(result)
    }

    @Test
    fun `returns false and writes nothing when the sale belongs to a different tenant`() = runTest {
        fakeSaleDao.setSales(listOf(pendingSingleSale(total = BigDecimal("100.00"))))

        val result = useCase("tenant-2", "s1", null, PaymentMethodType.EFECTIVO)

        assertFalse(result)
        assertEquals(emptyList<Any>(), fakePaymentDao.getForSale("s1", "tenant-1"))
        assertEquals("pending", fakeSaleDao.getById("s1", "tenant-1")?.status)
    }
}
