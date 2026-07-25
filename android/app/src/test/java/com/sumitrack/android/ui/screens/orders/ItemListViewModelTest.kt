package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.repositories.ProductRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeProductDao: FakeProductDao
    private lateinit var fakeVariantDao: FakeProductVariantDao
    private lateinit var repository: ProductRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductDao = FakeProductDao()
        fakeVariantDao = FakeProductVariantDao()
        repository = ProductRepository(FakeTransactionRunner(), fakeProductDao, fakeVariantDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clientId: String = "client-1", tenantId: String? = "tenant-1") = ItemListViewModel(
        savedStateHandle = SavedStateHandle(mapOf("clientId" to clientId)),
        productRepository = repository,
        tenantId = flowOf(tenantId),
    )

    @Test
    fun `initial state loads active products and productIdsWithVariants`() = runTest {
        val withVariants = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml"), "tenant-1")
        repository.createProduct("Agua", BigDecimal("10.00"), BigDecimal.ZERO, emptyList(), "tenant-1")
        advanceUntilIdle()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.products.size)
        assertTrue(withVariants in vm.uiState.value.productIdsWithVariants)
    }

    @Test
    fun `onProductClick without variants adds item to cart with quantity 1`() = runTest {
        repository.createProduct("Agua", BigDecimal("10.00"), BigDecimal.ZERO, emptyList(), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val product = vm.uiState.value.products.first()
        vm.onProductClick(product)

        val cart = vm.uiState.value.cart
        assertEquals(1, cart.size)
        assertEquals(1, cart.first().quantity)
        assertEquals(null, cart.first().variant)
    }

    @Test
    fun `onProductClick without variants twice increments quantity instead of duplicating`() = runTest {
        repository.createProduct("Agua", BigDecimal("10.00"), BigDecimal.ZERO, emptyList(), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val product = vm.uiState.value.products.first()
        vm.onProductClick(product)
        vm.onProductClick(product)

        val cart = vm.uiState.value.cart
        assertEquals(1, cart.size)
        assertEquals(2, cart.first().quantity)
    }

    @Test
    fun `onProductClick cancels a pending variant load when a different product is tapped before it resolves`() = runTest {
        val productAId = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml"), "tenant-1")
        val productBId = repository.createProduct("Jugo", BigDecimal("12.00"), BigDecimal.ZERO, listOf("Chico", "Grande"), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val productA = vm.uiState.value.products.first { it.id == productAId }
        val productB = vm.uiState.value.products.first { it.id == productBId }

        // Ambos toques se disparan antes de que el dispatcher de test ejecute ninguna corrutina —
        // el segundo debe cancelar la carga de variantes del primero (Review Finding del code
        // review de esta historia: antes, la que resolvía último sobreescribía el sheet abierto).
        vm.onProductClick(productA)
        vm.onProductClick(productB)
        advanceUntilIdle()

        assertEquals(productB.id, vm.uiState.value.variantSheetProduct?.id)
        assertEquals(2, vm.uiState.value.variantSheetVariants.size)
    }

    @Test
    fun `onProductClick with variants opens the variant sheet without touching the cart`() = runTest {
        repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml", "1L"), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val product = vm.uiState.value.products.first()
        vm.onProductClick(product)
        advanceUntilIdle()

        assertEquals(product.id, vm.uiState.value.variantSheetProduct?.id)
        assertEquals(2, vm.uiState.value.variantSheetVariants.size)
        assertEquals(emptyList<Any>(), vm.uiState.value.cart)
    }

    @Test
    fun `onVariantConfirmed adds the item with the selected variant and quantity, and closes the sheet`() = runTest {
        repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml", "1L"), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val product = vm.uiState.value.products.first()
        vm.onProductClick(product)
        advanceUntilIdle()
        val variant = vm.uiState.value.variantSheetVariants.first { it.name == "1L" }

        vm.onVariantConfirmed(variant, 3)

        val cart = vm.uiState.value.cart
        assertEquals(1, cart.size)
        assertEquals("1L", cart.first().variant?.name)
        assertEquals(3, cart.first().quantity)
        assertNull(vm.uiState.value.variantSheetProduct)
    }

    @Test
    fun `onVariantSheetDismiss closes the sheet without modifying the cart`() = runTest {
        repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml"), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onProductClick(vm.uiState.value.products.first())
        advanceUntilIdle()

        vm.onVariantSheetDismiss()

        assertNull(vm.uiState.value.variantSheetProduct)
        assertEquals(emptyList<Any>(), vm.uiState.value.cart)
    }

    @Test
    fun `subtotal sums price times quantity across cart items`() = runTest {
        repository.createProduct("Agua", BigDecimal("10.00"), BigDecimal.ZERO, emptyList(), "tenant-1")
        repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, emptyList(), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val products = vm.uiState.value.products
        vm.onProductClick(products[0])
        vm.onProductClick(products[0])
        vm.onProductClick(products[1])

        val expected = products[0].price.multiply(BigDecimal(2)).add(products[1].price)
        assertEquals(expected, vm.uiState.value.subtotal)
    }

    @Test
    fun `quantityByProductId sums quantities across different variants of the same product`() = runTest {
        repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml", "1L"), "tenant-1")
        advanceUntilIdle()
        val vm = viewModel()
        advanceUntilIdle()

        val product = vm.uiState.value.products.first()
        vm.onProductClick(product)
        advanceUntilIdle()
        val variant600 = vm.uiState.value.variantSheetVariants.first { it.name == "600ml" }
        vm.onVariantConfirmed(variant600, 2)

        vm.onProductClick(product)
        advanceUntilIdle()
        val variant1L = vm.uiState.value.variantSheetVariants.first { it.name == "1L" }
        vm.onVariantConfirmed(variant1L, 1)

        assertEquals(3, vm.uiState.value.quantityByProductId[product.id])
    }
}
