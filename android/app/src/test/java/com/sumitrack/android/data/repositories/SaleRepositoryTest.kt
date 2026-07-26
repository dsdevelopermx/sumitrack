package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.OrderSummary
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.ProductVariant
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.usecases.InstallmentSuggestion
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaleRepositoryTest {

    private lateinit var fakeDao: FakeSaleDao
    private lateinit var fakeSaleItemDao: FakeSaleItemDao
    private lateinit var fakeInstallmentDao: FakeInstallmentDao
    private lateinit var fakePaymentDao: FakePaymentDao
    private lateinit var repository: SaleRepository

    @Before
    fun setUp() {
        fakeDao = FakeSaleDao()
        fakeSaleItemDao = FakeSaleItemDao()
        fakeInstallmentDao = FakeInstallmentDao()
        fakePaymentDao = FakePaymentDao()
        repository = SaleRepository(FakeTransactionRunner(), fakeDao, fakeSaleItemDao, fakeInstallmentDao, fakePaymentDao)
    }

    private fun product(
        id: String = "product-1",
        price: BigDecimal = BigDecimal("50.00"),
        taxRate: BigDecimal = BigDecimal.ZERO,
        name: String = "Refresco",
    ) = Product(
        id = id,
        fkTenant = "tenant-1",
        name = name,
        price = price,
        taxRate = taxRate,
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = SyncStatus.SYNCED,
    )

    private fun variant(id: String = "variant-1", productId: String = "product-1", name: String = "Chico") = ProductVariant(
        id = id,
        fkTenant = "tenant-1",
        fkProduct = productId,
        name = name,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = SyncStatus.SYNCED,
    )

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

    @Test
    fun `createSale in Immediate mode persists sale with paid status`() = runTest {
        val items = listOf(OrderDraftItem(product(price = BigDecimal("100.00")), null, 2))

        val saleId = repository.createSale(
            tenantId = "tenant-1",
            clientId = "client-1",
            folio = "A1",
            items = items,
            paymentConfig = PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("200.00"))),
        )

        val saved = fakeDao.getOpenSalesForClient("client-1", "tenant-1")
        assertEquals(emptyList<Any>(), saved) // "paid" no es un estatus abierto

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, orders.size)
        assertEquals(saleId, orders.first().id)
        assertEquals(SaleStatus.PAID, orders.first().status)
        assertEquals(BigDecimal("200.00"), orders.first().total)
    }

    @Test
    fun `createSale in Immediate mode persists a SaleItem snapshot per cart item`() = runTest {
        val prod = product(id = "p1", price = BigDecimal("100.00"), taxRate = BigDecimal("16"), name = "Refresco")
        val vari = variant(id = "v1", productId = "p1", name = "Chico")
        val items = listOf(OrderDraftItem(prod, vari, 3))

        val saleId = repository.createSale(
            tenantId = "tenant-1",
            clientId = "client-1",
            folio = "A1",
            items = items,
            paymentConfig = PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("348.00"))),
        )

        val savedItems = fakeSaleItemDao.getForSale(saleId, "tenant-1")
        assertEquals(1, savedItems.size)
        val savedItem = savedItems.first()
        assertEquals("p1", savedItem.fkProduct)
        assertEquals("v1", savedItem.fkVariant)
        assertEquals("Refresco", savedItem.productName)
        assertEquals("Chico", savedItem.variantName)
        assertEquals(3, savedItem.quantity)
        assertEquals(BigDecimal("100.00"), savedItem.unitPrice)
        assertEquals(BigDecimal("16"), savedItem.taxRate)
    }

    @Test
    fun `createSale in Immediate mode persists one Payment per method with amount greater than zero`() = runTest {
        val items = listOf(OrderDraftItem(product(price = BigDecimal("100.00")), null, 1))

        val saleId = repository.createSale(
            tenantId = "tenant-1",
            clientId = "client-1",
            folio = "A1",
            items = items,
            paymentConfig = PaymentConfig.Immediate(
                listOf(
                    PaymentMethodType.EFECTIVO to BigDecimal("60.00"),
                    PaymentMethodType.TRANSFERENCIA to BigDecimal("40.00"),
                )
            ),
        )

        val payments = fakePaymentDao.getForSale(saleId, "tenant-1")
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.fkInstallment == null })
        assertEquals(BigDecimal("100.00"), payments.sumOf { it.amount })
        assertEquals(emptyList<Any>(), fakeInstallmentDao.getForSale(saleId, "tenant-1"))
    }

    @Test
    fun `createSale in Installments mode persists sale with pending status and no Payment`() = runTest {
        val items = listOf(OrderDraftItem(product(price = BigDecimal("300.00")), null, 1))
        val installments = listOf(
            InstallmentSuggestion(BigDecimal("150.00"), Instant.now()),
            InstallmentSuggestion(BigDecimal("150.00"), Instant.now()),
        )

        val saleId = repository.createSale(
            tenantId = "tenant-1",
            clientId = "client-1",
            folio = "A1",
            items = items,
            paymentConfig = PaymentConfig.Installments(installments),
        )

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()
        assertEquals(SaleStatus.PENDING, orders.first().status)

        val savedInstallments = fakeInstallmentDao.getForSale(saleId, "tenant-1")
        assertEquals(2, savedInstallments.size)
        assertEquals(emptyList<Any>(), fakePaymentDao.getForSale(saleId, "tenant-1"))
    }

    @Test
    fun `createSale computes subtotal, tax and total across multiple items with different tax rates`() = runTest {
        val items = listOf(
            OrderDraftItem(product(id = "p1", price = BigDecimal("100.00"), taxRate = BigDecimal("16")), null, 1),
            OrderDraftItem(product(id = "p2", price = BigDecimal("50.00"), taxRate = BigDecimal.ZERO), null, 2),
        )

        repository.createSale(
            tenantId = "tenant-1",
            clientId = "client-1",
            folio = "A1",
            items = items,
            paymentConfig = PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("216.00"))),
        )

        val orders = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        // subtotal = 100 + 100 = 200; tax = 100*0.16 = 16; total = 216
        assertEquals(BigDecimal("216.00"), orders.first().total)
    }

    @Test
    fun `createSale isolates by tenant`() = runTest {
        val items = listOf(OrderDraftItem(product(price = BigDecimal("10.00")), null, 1))

        repository.createSale(
            tenantId = "tenant-1",
            clientId = "client-1",
            folio = "A1",
            items = items,
            paymentConfig = PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("10.00"))),
        )

        val ordersTenant2 = mutableListOf<OrderSummary>()
        val job = launch { repository.getOrdersForTenant("tenant-2", null, "").collect { ordersTenant2.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Any>(), ordersTenant2)
        assertNull(fakeDao.getOpenSalesForClient("client-1", "tenant-2").firstOrNull())
    }
}
