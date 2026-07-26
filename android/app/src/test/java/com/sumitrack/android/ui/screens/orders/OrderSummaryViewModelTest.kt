package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.ui.screens.clients.FakeClientDao
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderSummaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var clientRepository: ClientRepository
    private lateinit var productRepository: ProductRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        clientRepository = ClientRepository(
            FakeClientDao(),
            CalculateClientBalanceUseCase(
                SaleRepository(FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao())
            ),
        )
        productRepository = ProductRepository(FakeTransactionRunner(), FakeProductDao(), FakeProductVariantDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clientId: String, cart: String, tenantId: String? = "tenant-1") = OrderSummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("clientId" to clientId, "cart" to cart)),
        clientRepository = clientRepository,
        productRepository = productRepository,
        tenantId = flowOf(tenantId),
    )

    @Test
    fun `resolves clientName and items from the encoded cart`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val productId = productRepository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, emptyList(), "tenant-1")
        advanceUntilIdle()

        val cart = CartRouteCodec.encode(
            listOf(OrderDraftItem(productRepository.getProductById(productId, "tenant-1")!!, null, 2))
        )
        val vm = viewModel(clientId, cart)
        advanceUntilIdle()

        assertEquals("Ana López", vm.uiState.value.clientName)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(2, vm.uiState.value.items.first().quantity)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `subtotal tax and total are computed correctly across items`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        val productId = productRepository.createProduct("Refresco", BigDecimal("100.00"), BigDecimal("16"), emptyList(), "tenant-1")
        advanceUntilIdle()

        val cart = CartRouteCodec.encode(
            listOf(OrderDraftItem(productRepository.getProductById(productId, "tenant-1")!!, null, 2))
        )
        val vm = viewModel(clientId, cart)
        advanceUntilIdle()

        assertEquals(BigDecimal("200.00"), vm.uiState.value.subtotal)
        assertEquals(BigDecimal("32.00"), vm.uiState.value.tax)
        assertEquals(BigDecimal("232.00"), vm.uiState.value.total)
    }

    @Test
    fun `empty cart yields empty items and zero totals`() = runTest {
        val clientId = clientRepository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(clientId, "")
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.uiState.value.items)
        assertEquals(0, BigDecimal.ZERO.compareTo(vm.uiState.value.total))
    }
}
