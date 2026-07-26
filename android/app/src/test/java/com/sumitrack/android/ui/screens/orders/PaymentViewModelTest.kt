package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.usecases.CalculateInstallmentsUseCase
import com.sumitrack.android.domain.usecases.ValidateFolioUseCase
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
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
    private lateinit var validateFolioUseCase: ValidateFolioUseCase
    private lateinit var calculateInstallmentsUseCase: CalculateInstallmentsUseCase

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
        saleRepository = SaleRepository(FakeTransactionRunner(), fakeSaleDao, FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao())
        validateFolioUseCase = ValidateFolioUseCase(fakeSaleDao, settingsRepository)
        calculateInstallmentsUseCase = CalculateInstallmentsUseCase()
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
}
