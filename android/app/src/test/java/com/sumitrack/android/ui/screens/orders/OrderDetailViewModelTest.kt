package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.PaymentConfig
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.models.TicketPaymentCondition
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.domain.usecases.CancelSaleUseCase
import com.sumitrack.android.domain.usecases.GenerateTicketUseCase
import com.sumitrack.android.domain.usecases.InstallmentSuggestion
import com.sumitrack.android.domain.usecases.RegisterPaymentUseCase
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var saleRepository: SaleRepository
    private lateinit var fakeClientDao: FakeClientDao
    private lateinit var clientRepository: ClientRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var generateTicketUseCase: GenerateTicketUseCase
    private lateinit var registerPaymentUseCase: RegisterPaymentUseCase
    private lateinit var cancelSaleUseCase: CancelSaleUseCase
    private lateinit var fakeBluetoothTicketPrinter: FakeBluetoothTicketPrinter
    private lateinit var fakeTicketFileWriter: FakeTicketFileWriter

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSaleDao = FakeSaleDao()
        saleRepository = SaleRepository(FakeTransactionRunner(), fakeSaleDao, FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), FakeCreditBalanceDao())
        fakeClientDao = FakeClientDao()
        clientRepository = ClientRepository(fakeClientDao, CalculateClientBalanceUseCase(saleRepository))
        settingsRepository = SettingsRepository(FakeSettingsDao(), noOpApiService)
        generateTicketUseCase = GenerateTicketUseCase(saleRepository, clientRepository, settingsRepository)
        registerPaymentUseCase = RegisterPaymentUseCase(saleRepository)
        cancelSaleUseCase = CancelSaleUseCase(saleRepository)
        fakeBluetoothTicketPrinter = FakeBluetoothTicketPrinter()
        fakeTicketFileWriter = FakeTicketFileWriter()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(saleId: String, tenantId: String? = "tenant-1") = OrderDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("saleId" to saleId)),
        saleRepository = saleRepository,
        clientRepository = clientRepository,
        generateTicketUseCase = generateTicketUseCase,
        registerPaymentUseCase = registerPaymentUseCase,
        cancelSaleUseCase = cancelSaleUseCase,
        bluetoothTicketPrinter = fakeBluetoothTicketPrinter,
        ticketFileWriter = fakeTicketFileWriter,
        tenantId = flowOf(tenantId),
    )

    private fun seedPendingSingleSale(saleId: String = "s1", total: BigDecimal = BigDecimal("100.00")) {
        fakeSaleDao.setSales(
            listOf(
                SaleEntity(
                    id = saleId, fkTenant = "tenant-1", fkClient = "client-1", folio = "A1", total = total,
                    status = "pending", createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "pending",
                )
            )
        )
    }

    private fun product(price: BigDecimal = BigDecimal("100.00"), taxRate: BigDecimal = BigDecimal.ZERO) = Product(
        id = "p1", fkTenant = "tenant-1", name = "Refresco", price = price, taxRate = taxRate, isActive = true,
        createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = SyncStatus.SYNCED,
    )

    private suspend fun createImmediateSale(clientId: String = "client-1", price: BigDecimal = BigDecimal("100.00")): String {
        val items = listOf(OrderDraftItem(product(price = price), null, 1))
        return saleRepository.createSale(
            "tenant-1", clientId, "A1", items,
            PaymentConfig.Immediate(listOf(PaymentMethodType.EFECTIVO to price)),
        )
    }

    private suspend fun createInstallmentSale(clientId: String = "client-1"): String {
        val items = listOf(OrderDraftItem(product(price = BigDecimal("300.00")), null, 1))
        return saleRepository.createSale(
            "tenant-1", clientId, "A1", items,
            PaymentConfig.Installments(
                listOf(
                    InstallmentSuggestion(BigDecimal("150.00"), Instant.now().plusSeconds(86_400 * 15)),
                    InstallmentSuggestion(BigDecimal("150.00"), Instant.now().plusSeconds(86_400 * 30)),
                )
            ),
        )
    }

    @Test
    fun `loads folio date client status items and totals from SaleDetail`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)

        val vm = viewModel(saleId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.notFound)
        assertEquals("A1", state.folio)
        assertEquals("Ana López", state.clientName)
        assertEquals(SaleStatus.PAID, state.status)
        assertEquals(1, state.items.size)
        assertEquals(BigDecimal("100.00"), state.total)
    }

    @Test
    fun `payment condition is SinglePayment for a sale without installments`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)

        val vm = viewModel(saleId)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.paymentCondition is TicketPaymentCondition.SinglePayment)
        assertEquals(1, vm.uiState.value.paymentHistory.size)
    }

    @Test
    fun `payment condition is InstallmentPlan sorted by due date for a sale with installments`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createInstallmentSale(clientId)

        val vm = viewModel(saleId)
        advanceUntilIdle()

        val condition = vm.uiState.value.paymentCondition as TicketPaymentCondition.InstallmentPlan
        assertEquals(2, condition.installments.size)
        assertTrue(condition.installments[0].dueDate.isBefore(condition.installments[1].dueDate))
        assertEquals(emptyList<Any>(), vm.uiState.value.paymentHistory) // sin cobros (Historia 3.6 aún no existe)
    }

    @Test
    fun `notFound is true for an unknown saleId`() = runTest {
        val vm = viewModel("does-not-exist")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `notFound is true when the sale belongs to a different tenant`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)

        // El ViewModel resuelve tenantId="tenant-2" (distinto del que creó la venta).
        val vm = OrderDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("saleId" to saleId)),
            saleRepository = saleRepository,
            clientRepository = clientRepository,
            generateTicketUseCase = generateTicketUseCase,
            registerPaymentUseCase = registerPaymentUseCase,
            cancelSaleUseCase = cancelSaleUseCase,
            bluetoothTicketPrinter = fakeBluetoothTicketPrinter,
            ticketFileWriter = fakeTicketFileWriter,
            tenantId = flowOf("tenant-2"),
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
    }

    @Test
    fun `notFound is true when tenantId is null`() = runTest {
        val vm = viewModel(saleId = "any", tenantId = null)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
    }

    @Test
    fun `a deleted client does not prevent loading, clientName is empty`() = runTest {
        val saleId = createImmediateSale(clientId = "ghost-client")

        val vm = viewModel(saleId)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.notFound)
        assertEquals("", vm.uiState.value.clientName)
    }

    @Test
    fun `onShareTicketClick generates the ticket once and reuses it on subsequent calls`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)
        val vm = viewModel(saleId)
        advanceUntilIdle()

        var shareCount = 0
        val job = launch { vm.shareEvent.collect { shareCount++ } }

        vm.onShareTicketClick()
        advanceUntilIdle()
        val ticketAfterFirstShare = vm.uiState.value.ticketData

        vm.onShareTicketClick()
        advanceUntilIdle()

        assertEquals(ticketAfterFirstShare, vm.uiState.value.ticketData)
        assertEquals(2, shareCount)
        job.cancel()
    }

    @Test
    fun `onShareTicketClick sets an error message when ticket generation fails`() = runTest {
        // Sin cliente creado: GenerateTicketUseCase seguirá funcionando (fiscal data cae a ""),
        // así que forzamos el fallo simulando un tenant desconocido vía una venta inexistente.
        val vm = viewModel(saleId = "does-not-exist")
        advanceUntilIdle()

        vm.onShareTicketClick()
        advanceUntilIdle()

        assertEquals("No se pudo generar el ticket. Inténtalo de nuevo.", vm.uiState.value.printError)
        assertFalse(vm.uiState.value.isSharing)
        assertEquals(null, vm.uiState.value.ticketData)
    }

    @Test
    fun `onPrintClick success clears isPrinting and printError`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)
        val vm = viewModel(saleId)
        advanceUntilIdle()
        vm.onShareTicketClick()
        advanceUntilIdle()

        fakeBluetoothTicketPrinter.result = Result.success(Unit)
        vm.onPrintClick()
        advanceUntilIdle()

        assertEquals(1, fakeBluetoothTicketPrinter.printCallCount)
        assertFalse(vm.uiState.value.isPrinting)
        assertEquals(null, vm.uiState.value.printError)
    }

    @Test
    fun `onPrintClick failure sets the exact AC-3 error message`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)
        val vm = viewModel(saleId)
        advanceUntilIdle()
        vm.onShareTicketClick()
        advanceUntilIdle()

        fakeBluetoothTicketPrinter.result = Result.failure(IllegalStateException("no printer"))
        vm.onPrintClick()
        advanceUntilIdle()

        assertEquals(
            "No encontramos la impresora. La orden ya está guardada — puedes compartir el ticket después.",
            vm.uiState.value.printError,
        )
        assertFalse(vm.uiState.value.isPrinting)
    }

    @Test
    fun `onBluetoothPermissionDenied sets a clear error message instead of attempting to print`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)
        val vm = viewModel(saleId)
        advanceUntilIdle()

        vm.onBluetoothPermissionDenied()

        assertEquals(
            "Se necesita permiso de Bluetooth para imprimir. Puedes compartir el ticket en su lugar.",
            vm.uiState.value.printError,
        )
        assertEquals(0, fakeBluetoothTicketPrinter.printCallCount)
    }

    @Test
    fun `onCancelOrderConfirm cancels directly when the sale has no payments`() = runTest {
        val saleId = createInstallmentSale() // Installments mode: sin Payment todavía
        val vm = viewModel(saleId)
        advanceUntilIdle()

        vm.onCancelOrderConfirm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showCreditChoiceDialog)
        assertEquals(SaleStatus.CANCELLED, vm.uiState.value.status)
        assertEquals("Orden cancelada.", vm.uiState.value.cancelSuccessMessage)
    }

    @Test
    fun `onCancelOrderConfirm shows the credit-choice dialog when the sale has payments`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId) // Immediate: siempre paid, con Payment
        val vm = viewModel(saleId)
        advanceUntilIdle()

        vm.onCancelOrderConfirm()

        assertTrue(vm.uiState.value.showCreditChoiceDialog)
        assertEquals(SaleStatus.PAID, vm.uiState.value.status) // aún no cancelada, solo se mostró el dialog
    }

    @Test
    fun `onCreditChoiceDialogDismiss closes the dialog without cancelling`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId)
        val vm = viewModel(saleId)
        advanceUntilIdle()
        vm.onCancelOrderConfirm()

        vm.onCreditChoiceDialogDismiss()

        assertFalse(vm.uiState.value.showCreditChoiceDialog)
        assertEquals(SaleStatus.PAID, vm.uiState.value.status)
    }

    @Test
    fun `onCancelKeepingPayments (Opcion A) cancels without generating credit`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId, price = BigDecimal("100.00"))
        val vm = viewModel(saleId)
        advanceUntilIdle()
        vm.onCancelOrderConfirm()

        vm.onCancelKeepingPayments()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showCreditChoiceDialog)
        assertEquals(SaleStatus.CANCELLED, vm.uiState.value.status)
        assertEquals(BigDecimal.ZERO, saleRepository.getAvailableCredit(clientId, "tenant-1"))
    }

    @Test
    fun `onCancelWithCredit (Opcion B) cancels and generates credit for the total collected`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()
        val saleId = createImmediateSale(clientId, price = BigDecimal("100.00"))
        val vm = viewModel(saleId)
        advanceUntilIdle()
        vm.onCancelOrderConfirm()

        vm.onCancelWithCredit()
        advanceUntilIdle()

        assertEquals(SaleStatus.CANCELLED, vm.uiState.value.status)
        assertEquals(BigDecimal("100.00"), saleRepository.getAvailableCredit(clientId, "tenant-1"))
        assertEquals("Orden cancelada. Se generó Crédito a Favor para el cliente.", vm.uiState.value.cancelSuccessMessage)
    }

    @Test
    fun `cancelling a sale with installments marks the pending ones CANCELLED, not the paid ones`() = runTest {
        val saleId = createInstallmentSale()
        val vm = viewModel(saleId)
        advanceUntilIdle()
        val installmentId = vm.uiState.value.installments.first().id
        vm.onRegisterPaymentClick(installmentId)
        vm.onConfirmRegisterPayment(PaymentMethodType.EFECTIVO)
        advanceUntilIdle()

        vm.onCancelOrderConfirm() // tiene 1 cobro → muestra el dialog
        vm.onCancelKeepingPayments()
        advanceUntilIdle()

        val statuses = vm.uiState.value.installments.map { it.toUiStatus() }
        assertTrue(InstallmentUiStatus.PAID in statuses)
        assertTrue(InstallmentUiStatus.CANCELLED in statuses)
    }

    @Test
    fun `performCancel failure sets cancelError and does not leave isCancelling stuck`() = runTest {
        val vm = viewModel("does-not-exist")
        advanceUntilIdle()

        vm.onCancelOrderConfirm() // notFound: paymentHistory vacío → cancela directo, use case falla
        advanceUntilIdle()

        assertEquals("No se pudo cancelar la orden. Inténtalo de nuevo.", vm.uiState.value.cancelError)
        assertFalse(vm.uiState.value.isCancelling)
    }

    @Test
    fun `onCancelSuccessMessageShown clears the success message`() = runTest {
        val saleId = createInstallmentSale()
        val vm = viewModel(saleId)
        advanceUntilIdle()
        vm.onCancelOrderConfirm()
        advanceUntilIdle()

        vm.onCancelSuccessMessageShown()

        assertEquals(null, vm.uiState.value.cancelSuccessMessage)
    }

    @Test
    fun `onRegisterPaymentClick with null opens the dialog targeting the single payment`() = runTest {
        seedPendingSingleSale()
        val vm = viewModel("s1")
        advanceUntilIdle()

        vm.onRegisterPaymentClick(null)

        assertTrue(vm.uiState.value.showRegisterPaymentDialog)
        assertEquals(null, vm.uiState.value.paymentTargetInstallmentId)
    }

    @Test
    fun `onRegisterPaymentClick with an installmentId opens the dialog targeting that installment`() = runTest {
        val saleId = createInstallmentSale()
        val vm = viewModel(saleId)
        advanceUntilIdle()
        val installmentId = vm.uiState.value.installments.first().id

        vm.onRegisterPaymentClick(installmentId)

        assertTrue(vm.uiState.value.showRegisterPaymentDialog)
        assertEquals(installmentId, vm.uiState.value.paymentTargetInstallmentId)
    }

    @Test
    fun `onRegisterPaymentDialogDismiss closes the dialog and clears the target`() = runTest {
        seedPendingSingleSale()
        val vm = viewModel("s1")
        advanceUntilIdle()
        vm.onRegisterPaymentClick(null)

        vm.onRegisterPaymentDialogDismiss()

        assertFalse(vm.uiState.value.showRegisterPaymentDialog)
        assertEquals(null, vm.uiState.value.paymentTargetInstallmentId)
    }

    @Test
    fun `onConfirmRegisterPayment success for a single-payment sale reloads status to PAID and closes the dialog`() = runTest {
        seedPendingSingleSale(total = BigDecimal("100.00"))
        val vm = viewModel("s1")
        advanceUntilIdle()
        vm.onRegisterPaymentClick(null)

        vm.onConfirmRegisterPayment(PaymentMethodType.EFECTIVO)
        advanceUntilIdle()

        assertEquals(SaleStatus.PAID, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.paymentHistory.size)
        assertFalse(vm.uiState.value.showRegisterPaymentDialog)
        assertFalse(vm.uiState.value.isRegisteringPayment)
    }

    @Test
    fun `onConfirmRegisterPayment success for an installment reloads its status and the sale status`() = runTest {
        val saleId = createInstallmentSale()
        val vm = viewModel(saleId)
        advanceUntilIdle()
        val installmentId = vm.uiState.value.installments.first().id
        vm.onRegisterPaymentClick(installmentId)

        vm.onConfirmRegisterPayment(PaymentMethodType.TRANSFERENCIA)
        advanceUntilIdle()

        assertEquals(SaleStatus.PARTIAL, vm.uiState.value.status)
        assertEquals(InstallmentUiStatus.PAID, vm.uiState.value.installments.first { it.id == installmentId }.toUiStatus())
        assertFalse(vm.uiState.value.showRegisterPaymentDialog)
    }

    @Test
    fun `onConfirmRegisterPayment failure sets an error message and keeps the dialog open`() = runTest {
        seedPendingSingleSale()
        val vm = viewModel("s1")
        advanceUntilIdle()
        vm.onRegisterPaymentClick(null)
        // Cancelamos la venta directamente en el DAO para forzar el fallo de validación del use case.
        fakeSaleDao.setSales(listOf(fakeSaleDao.getById("s1", "tenant-1")!!.copy(status = "cancelled")))

        vm.onConfirmRegisterPayment(PaymentMethodType.EFECTIVO)
        advanceUntilIdle()

        assertEquals("No se pudo registrar el cobro. Inténtalo de nuevo.", vm.uiState.value.registerPaymentError)
        assertTrue(vm.uiState.value.showRegisterPaymentDialog)
        assertFalse(vm.uiState.value.isRegisteringPayment)
    }

    @Test
    fun `onConfirmRegisterPayment is protected against double-tap by isRegisteringPayment`() = runTest {
        seedPendingSingleSale()
        val vm = viewModel("s1")
        advanceUntilIdle()
        vm.onRegisterPaymentClick(null)

        vm.onConfirmRegisterPayment(PaymentMethodType.EFECTIVO)
        vm.onConfirmRegisterPayment(PaymentMethodType.EFECTIVO)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.paymentHistory.size)
    }
}
