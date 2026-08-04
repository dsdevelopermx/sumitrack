package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.local.entities.CreditBalanceEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.usecases.CalculateAvailableCreditUseCase
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.domain.usecases.CalculateInstallmentsUseCase
import com.sumitrack.android.domain.usecases.GenerateTicketUseCase
import com.sumitrack.android.domain.usecases.ValidateFolioUseCase
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var productRepository: ProductRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fakeSettingsDao: FakeSettingsDao
    private lateinit var saleRepository: SaleRepository
    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var clientRepository: ClientRepository
    private lateinit var validateFolioUseCase: ValidateFolioUseCase
    private lateinit var calculateInstallmentsUseCase: CalculateInstallmentsUseCase
    private lateinit var calculateAvailableCreditUseCase: CalculateAvailableCreditUseCase
    private lateinit var generateTicketUseCase: GenerateTicketUseCase
    private lateinit var fakeBluetoothTicketPrinter: FakeBluetoothTicketPrinter
    private lateinit var fakeTicketFileWriter: FakeTicketFileWriter
    private lateinit var fakeCreditBalanceDao: FakeCreditBalanceDao

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        productRepository = ProductRepository(FakeTransactionRunner(), FakeProductDao(), FakeProductVariantDao())
        fakeSettingsDao = FakeSettingsDao()
        settingsRepository = SettingsRepository(fakeSettingsDao, noOpApiService)
        fakeSaleDao = FakeSaleDao()
        fakeCreditBalanceDao = FakeCreditBalanceDao()
        saleRepository = SaleRepository(FakeTransactionRunner(), fakeSaleDao, FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), fakeCreditBalanceDao)
        clientRepository = ClientRepository(FakeClientDao(), CalculateClientBalanceUseCase(saleRepository))
        validateFolioUseCase = ValidateFolioUseCase(fakeSaleDao, settingsRepository)
        calculateInstallmentsUseCase = CalculateInstallmentsUseCase()
        calculateAvailableCreditUseCase = CalculateAvailableCreditUseCase(saleRepository)
        generateTicketUseCase = GenerateTicketUseCase(saleRepository, clientRepository, settingsRepository)
        fakeBluetoothTicketPrinter = FakeBluetoothTicketPrinter()
        fakeTicketFileWriter = FakeTicketFileWriter()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clientId: String = "client-1", cart: String, tenantId: String? = "tenant-1") = PaymentViewModel(
        savedStateHandle = SavedStateHandle(mapOf("clientId" to clientId, "cart" to cart)),
        productRepository = productRepository,
        settingsRepository = settingsRepository,
        saleRepository = saleRepository,
        validateFolioUseCase = validateFolioUseCase,
        calculateInstallmentsUseCase = calculateInstallmentsUseCase,
        calculateAvailableCreditUseCase = calculateAvailableCreditUseCase,
        generateTicketUseCase = generateTicketUseCase,
        bluetoothTicketPrinter = fakeBluetoothTicketPrinter,
        ticketFileWriter = fakeTicketFileWriter,
        tenantId = flowOf(tenantId),
    )

    private suspend fun cartWithProduct(price: BigDecimal, quantity: Int = 1, taxRate: BigDecimal = BigDecimal.ZERO): String {
        val productId = productRepository.createProduct("Refresco", price, taxRate, emptyList(), "tenant-1")
        val product = productRepository.getProductById(productId, "tenant-1")!!
        return CartRouteCodec.encode(listOf(OrderDraftItem(product, null, quantity)))
    }

    @Test
    fun `onPaymentMethodTypeChange rejects a second Efectivo method`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        vm.onAddPaymentMethod()

        val secondLocalId = vm.uiState.value.paymentMethods[1].localId
        vm.onPaymentMethodTypeChange(secondLocalId, PaymentMethodType.EFECTIVO)

        assertEquals("Ya agregaste un método de pago en efectivo", vm.uiState.value.errorMessage)
        assertEquals(PaymentMethodType.TRANSFERENCIA, vm.uiState.value.paymentMethods[1].type)
    }

    @Test
    fun `remaining and isImmediateConfirmEnabled react to amount changes`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()

        val localId = vm.uiState.value.paymentMethods.first().localId
        assertEquals(BigDecimal("100.00"), vm.uiState.value.remaining)
        assertFalse(vm.uiState.value.isImmediateConfirmEnabled)

        vm.onPaymentMethodAmountChange(localId, "100.00")

        assertEquals(0, BigDecimal.ZERO.compareTo(vm.uiState.value.remaining))
        assertTrue(vm.uiState.value.isImmediateConfirmEnabled)
    }

    @Test
    fun `onInstallmentCountChange out of range sets error and does not generate installments`() = runTest {
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        assertEquals(15, vm.uiState.value.maxParcialidades)

        vm.onInstallmentCountChange("20")

        assertEquals(emptyList<Any>(), vm.uiState.value.installments)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `onInstallmentCountChange respects the tenant's maxParcialidades from Settings`() = runTest {
        fakeSettingsDao.upsertAll(listOf(SettingsEntity(key = "max_parcialidades", value = "3")))
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.maxParcialidades)

        vm.onInstallmentCountChange("4")
        assertEquals(emptyList<Any>(), vm.uiState.value.installments)

        vm.onInstallmentCountChange("3")
        assertEquals(3, vm.uiState.value.installments.size)
    }

    @Test
    fun `installmentsSum and isInstallmentsConfirmEnabled reflect suggested installments`() = runTest {
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()

        vm.onInstallmentCountChange("3")

        assertEquals(BigDecimal("300.00"), vm.uiState.value.installmentsSum)
        assertTrue(vm.uiState.value.isInstallmentsConfirmEnabled)
    }

    @Test
    fun `onConfirmClick in Immediate mode creates the sale and emits navEvent`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")

        var navEventReceived = false
        val job = launch { vm.navEvent.collect { navEventReceived = true } }

        vm.onConfirmClick()
        advanceUntilIdle()

        assertTrue(navEventReceived)
        val orders = mutableListOf<com.sumitrack.android.domain.models.OrderSummary>()
        val ordersJob = launch { saleRepository.getOrdersForTenant("tenant-1", null, "").collect { orders.addAll(it) } }
        advanceUntilIdle()
        assertEquals(1, orders.size)
        job.cancel()
        ordersJob.cancel()
    }

    @Test
    fun `onConfirmClick in Installments mode creates the sale with a pending plan`() = runTest {
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        vm.onModeChange(PaymentMode.INSTALLMENTS)
        vm.onInstallmentCountChange("3")

        var navEventReceived = false
        val job = launch { vm.navEvent.collect { navEventReceived = true } }

        vm.onConfirmClick()
        advanceUntilIdle()

        assertTrue(navEventReceived)
        assertEquals(1, fakeSaleDao.countSalesForTenant("tenant-1"))
        job.cancel()
    }

    @Test
    fun `onConfirmClick with null tenantId shows error and does not save`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart, tenantId = null)
        advanceUntilIdle()
        // Monto asignado para que isImmediateConfirmEnabled sea true y onConfirmClick realmente
        // entre a la corrutina que resuelve tenantId — de lo contrario el guard de confirmabilidad
        // (isImmediateConfirmEnabled==false por remaining!=0) devolvería antes de llegar ahí.
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")

        vm.onConfirmClick()
        advanceUntilIdle()

        assertEquals("No se pudo determinar tu negocio. Vuelve a iniciar sesión.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `onConfirmClick is a no-op when the current mode is not confirmable`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        // Sin monto asignado: remaining != 0, isImmediateConfirmEnabled == false.

        var navEventReceived = false
        val job = launch { vm.navEvent.collect { navEventReceived = true } }

        vm.onConfirmClick()
        advanceUntilIdle()

        assertFalse(navEventReceived)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(0, fakeSaleDao.countSalesForTenant("tenant-1"))
        job.cancel()
    }

    @Test
    fun `maxParcialidades from Settings is clamped to the hard limit of 15`() = runTest {
        fakeSettingsDao.upsertAll(listOf(SettingsEntity(key = "max_parcialidades", value = "20")))
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()

        assertEquals(15, vm.uiState.value.maxParcialidades)

        // Un count de 16 (dentro del valor crudo de Settings pero fuera del límite duro) no debe
        // ni intentar llegar a CalculateInstallmentsUseCase, que lanzaría IllegalArgumentException.
        vm.onInstallmentCountChange("16")
        assertEquals(emptyList<Any>(), vm.uiState.value.installments)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `negative payment method amount is excluded from assignedAmount and cannot satisfy remaining`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val firstLocalId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(firstLocalId, "150.00")
        vm.onAddPaymentMethod()
        val secondLocalId = vm.uiState.value.paymentMethods[1].localId
        vm.onPaymentMethodAmountChange(secondLocalId, "-50.00")

        // Sin filtrar el monto negativo, 150 + (-50) = 100 = remaining 0; filtrado, assignedAmount
        // solo cuenta el positivo (150), remaining = 100 - 150 = -50 != 0.
        assertEquals(0, BigDecimal("150.00").compareTo(vm.uiState.value.assignedAmount))
        assertFalse(vm.uiState.value.isImmediateConfirmEnabled)
    }

    @Test
    fun `negative installment amount is excluded from installmentsSum and disables confirm`() = runTest {
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        vm.onModeChange(PaymentMode.INSTALLMENTS)
        vm.onInstallmentCountChange("3")

        vm.onInstallmentAmountChange(0, "-10.00")

        assertFalse(vm.uiState.value.isInstallmentsConfirmEnabled)
    }

    @Test
    fun `editing installment count to an invalid value clears the previous plan`() = runTest {
        val cart = cartWithProduct(BigDecimal("300.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        vm.onModeChange(PaymentMode.INSTALLMENTS)
        vm.onInstallmentCountChange("3")
        assertTrue(vm.uiState.value.isInstallmentsConfirmEnabled)

        vm.onInstallmentCountChange("99")

        assertEquals(emptyList<Any>(), vm.uiState.value.installments)
        assertFalse(vm.uiState.value.isInstallmentsConfirmEnabled)
    }

    @Test
    fun `onConfirmClick success populates ticketData with the folio and clears isSaving`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")

        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        assertEquals("A1", vm.uiState.value.ticketData?.folio)
        assertFalse(vm.uiState.value.isSaving)
        job.cancel()
    }

    @Test
    fun `onPrintClick success clears isPrinting and printError`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        fakeBluetoothTicketPrinter.result = Result.success(Unit)
        vm.onPrintClick()
        advanceUntilIdle()

        assertEquals(1, fakeBluetoothTicketPrinter.printCallCount)
        assertFalse(vm.uiState.value.isPrinting)
        assertEquals(null, vm.uiState.value.printError)
        job.cancel()
    }

    @Test
    fun `onPrintClick failure sets the exact AC-3 error message and keeps the sheet state`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        fakeBluetoothTicketPrinter.result = Result.failure(IllegalStateException("no printer"))
        vm.onPrintClick()
        advanceUntilIdle()

        assertEquals(
            "No encontramos la impresora. La orden ya está guardada — puedes compartir el ticket después.",
            vm.uiState.value.printError,
        )
        assertFalse(vm.uiState.value.isPrinting)
        assertNotNull(vm.uiState.value.ticketData) // el sheet permanece abierto (AC-3)
        job.cancel()
    }

    @Test
    fun `onShareClick writes the ticket and emits shareEvent with the returned uri`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val navJob = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        fakeTicketFileWriter.uriToReturn = "content://sumitrack/ticket_A1.png"
        var sharedUri: String? = null
        val shareJob = launch { vm.shareEvent.collect { sharedUri = it } }

        vm.onShareClick()
        advanceUntilIdle()

        assertEquals("content://sumitrack/ticket_A1.png", sharedUri)
        assertEquals("ticket_A1.png", fakeTicketFileWriter.lastFileName)
        navJob.cancel()
        shareJob.cancel()
    }

    @Test
    fun `onTicketDismiss clears ticketData and printError`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.ticketData)

        vm.onTicketDismiss()

        assertEquals(null, vm.uiState.value.ticketData)
        assertEquals(null, vm.uiState.value.printError)
        job.cancel()
    }

    @Test
    fun `onBluetoothPermissionDenied sets a clear error message instead of attempting to print`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        vm.onBluetoothPermissionDenied()

        assertEquals(
            "Se necesita permiso de Bluetooth para imprimir. Puedes compartir el ticket en su lugar.",
            vm.uiState.value.printError,
        )
        assertEquals(0, fakeBluetoothTicketPrinter.printCallCount)
        job.cancel()
    }

    @Test
    fun `onShareClick failure sets an error message and clears isSharing`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        fakeTicketFileWriter.throwOnWrite = java.io.IOException("disk full")
        var sharedUri: String? = null
        val shareJob = launch { vm.shareEvent.collect { sharedUri = it } }

        vm.onShareClick()
        advanceUntilIdle()

        assertEquals("No se pudo preparar el ticket para compartir. Inténtalo de nuevo.", vm.uiState.value.printError)
        assertFalse(vm.uiState.value.isSharing)
        assertEquals(null, sharedUri)
        job.cancel()
        shareJob.cancel()
    }

    @Test
    fun `onShareClick is a no-op while a previous share is still in flight`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }
        vm.onConfirmClick()
        advanceUntilIdle()

        // Simula estar a mitad de una escritura: onShareClick ya marcó isSharing=true pero el
        // launch todavía no corre (runTest no avanza hasta advanceUntilIdle).
        vm.onShareClick()
        vm.onShareClick()
        advanceUntilIdle()

        // Solo la primera llamada debió llegar a escribir — la segunda debió descartarse por el
        // guard de isSharing (síncrono, igual que el de onConfirmClick/onPrintClick).
        assertEquals("ticket_A1.png", fakeTicketFileWriter.lastFileName)
        job.cancel()
    }

    @Test
    fun `when GenerateTicketUseCase returns null, ticketLoadFailed is set instead of leaving ticketData stuck`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()
        val localId = vm.uiState.value.paymentMethods.first().localId
        vm.onPaymentMethodAmountChange(localId, "100.00")
        val job = launch { vm.navEvent.collect {} }

        fakeSaleDao.forceGetByIdNull = true
        vm.onConfirmClick()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.ticketData)
        assertTrue(vm.uiState.value.ticketLoadFailed)
        assertFalse(vm.uiState.value.isSaving)
        job.cancel()
    }

    @Test
    fun `availableCredit loads for the client on init`() = runTest {
        fakeCreditBalanceDao.upsertAll(
            listOf(
                CreditBalanceEntity(
                    id = "c1", fkTenant = "tenant-1", fkClient = "client-1", amount = BigDecimal("300.00"),
                    origin = "cancellation", fkOriginSale = null, appliedAt = null,
                    createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "pending",
                )
            )
        )
        val cart = cartWithProduct(BigDecimal("100.00"))

        val vm = viewModel(cart = cart)
        advanceUntilIdle()

        assertEquals(BigDecimal("300.00"), vm.uiState.value.availableCredit)
    }

    @Test
    fun `onApplyCreditClick adds a CREDITO_A_FAVOR row capped at the remaining total, only once`() = runTest {
        fakeCreditBalanceDao.upsertAll(
            listOf(
                CreditBalanceEntity(
                    id = "c1", fkTenant = "tenant-1", fkClient = "client-1", amount = BigDecimal("300.00"),
                    origin = "cancellation", fkOriginSale = null, appliedAt = null,
                    createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = "pending",
                )
            )
        )
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()

        vm.onApplyCreditClick()
        vm.onApplyCreditClick() // segundo click no debe duplicar la fila

        val creditMethods = vm.uiState.value.paymentMethods.filter { it.type == PaymentMethodType.CREDITO_A_FAVOR }
        assertEquals(1, creditMethods.size)
        assertEquals("100.00", creditMethods.first().amountText) // capado al total, no a los 300 disponibles
        assertTrue(vm.uiState.value.isImmediateConfirmEnabled) // cubre el total exacto → Confirmar habilitado
    }

    @Test
    fun `onApplyCreditClick does nothing when there is no available credit`() = runTest {
        val cart = cartWithProduct(BigDecimal("100.00"))
        val vm = viewModel(cart = cart)
        advanceUntilIdle()

        vm.onApplyCreditClick()

        assertTrue(vm.uiState.value.paymentMethods.none { it.type == PaymentMethodType.CREDITO_A_FAVOR })
    }
}
