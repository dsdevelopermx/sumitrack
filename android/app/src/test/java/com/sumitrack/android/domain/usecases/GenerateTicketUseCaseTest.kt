package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.PaymentConfig
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.models.TicketPaymentCondition
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateTicketUseCaseTest {

    private lateinit var saleRepository: SaleRepository
    private lateinit var clientRepository: ClientRepository
    private lateinit var fakeClientDao: FakeClientDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fakeSettingsDao: FakeSettingsDao
    private lateinit var useCase: GenerateTicketUseCase

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    @Before
    fun setUp() {
        saleRepository = SaleRepository(FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), FakeCreditBalanceDao())
        fakeClientDao = FakeClientDao()
        clientRepository = ClientRepository(fakeClientDao, CalculateClientBalanceUseCase(saleRepository))
        fakeSettingsDao = FakeSettingsDao()
        settingsRepository = SettingsRepository(fakeSettingsDao, noOpApiService)
        useCase = GenerateTicketUseCase(saleRepository, clientRepository, settingsRepository)
    }

    private fun product(price: BigDecimal = BigDecimal("100.00"), taxRate: BigDecimal = BigDecimal.ZERO) = Product(
        id = "p1", fkTenant = "tenant-1", name = "Refresco", price = price, taxRate = taxRate, isActive = true,
        createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = SyncStatus.SYNCED,
    )

    @Test
    fun `generates TicketData with SinglePayment condition for Immediate mode`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val items = listOf(OrderDraftItem(product(), null, 2))
        val saleId = saleRepository.createSale(
            "tenant-1", clientId, "A1", items,
            PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("200.00"))),
        )

        val ticket = useCase(saleId, "tenant-1")

        assertEquals("Ana López", ticket?.clientName)
        assertEquals("A1", ticket?.folio)
        assertEquals(1, ticket?.lineItems?.size)
        assertTrue(ticket?.paymentCondition is TicketPaymentCondition.SinglePayment)
    }

    @Test
    fun `generates TicketData with InstallmentPlan condition sorted by due date`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val items = listOf(OrderDraftItem(product(price = BigDecimal("300.00")), null, 1))
        val later = Instant.now().plusSeconds(86_400 * 30)
        val sooner = Instant.now().plusSeconds(86_400 * 15)
        val saleId = saleRepository.createSale(
            "tenant-1", clientId, "A1", items,
            PaymentConfig.Installments(
                listOf(
                    InstallmentSuggestion(BigDecimal("150.00"), later),
                    InstallmentSuggestion(BigDecimal("150.00"), sooner),
                )
            ),
        )

        val ticket = useCase(saleId, "tenant-1")

        val condition = ticket?.paymentCondition as TicketPaymentCondition.InstallmentPlan
        assertEquals(sooner, condition.installments.first().dueDate)
        assertEquals(later, condition.installments.last().dueDate)
    }

    @Test
    fun `fiscal data falls back to empty strings when Settings keys are missing`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val items = listOf(OrderDraftItem(product(), null, 1))
        val saleId = saleRepository.createSale(
            "tenant-1", clientId, "A1", items,
            PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("100.00"))),
        )

        val ticket = useCase(saleId, "tenant-1")

        assertEquals("", ticket?.fiscal?.businessName)
        assertEquals("", ticket?.fiscal?.rfc)
        assertEquals("", ticket?.fiscal?.address)
        assertEquals("", ticket?.fiscal?.phone)
    }

    @Test
    fun `fiscal data is read from Settings when present`() = runTest {
        fakeSettingsDao.upsertAll(
            listOf(
                SettingsEntity(key = "negocio_nombre", value = "Ferretería El Clavo"),
                SettingsEntity(key = "negocio_rfc", value = "XAXX010101000"),
            )
        )
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val items = listOf(OrderDraftItem(product(), null, 1))
        val saleId = saleRepository.createSale(
            "tenant-1", clientId, "A1", items,
            PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("100.00"))),
        )

        val ticket = useCase(saleId, "tenant-1")

        assertEquals("Ferretería El Clavo", ticket?.fiscal?.businessName)
        assertEquals("XAXX010101000", ticket?.fiscal?.rfc)
    }

    @Test
    fun `a deleted client does not prevent ticket generation, name is empty`() = runTest {
        val items = listOf(OrderDraftItem(product(), null, 1))
        val saleId = saleRepository.createSale(
            "tenant-1", "ghost-client", "A1", items,
            PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to BigDecimal("100.00"))),
        )

        val ticket = useCase(saleId, "tenant-1")

        assertEquals("", ticket?.clientName)
    }

    @Test
    fun `returns null for an unknown saleId`() = runTest {
        val ticket = useCase("does-not-exist", "tenant-1")
        assertNull(ticket)
    }
}
