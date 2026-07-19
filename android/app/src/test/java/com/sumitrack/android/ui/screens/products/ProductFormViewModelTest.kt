package com.sumitrack.android.ui.screens.products

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.repositories.ProductRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeProductDao: FakeProductDao
    private lateinit var repository: ProductRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductDao = FakeProductDao()
        repository = ProductRepository(FakeTransactionRunner(), fakeProductDao, FakeProductVariantDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(productId: String? = null, tenantId: String? = "tenant-1") = ProductFormViewModel(
        savedStateHandle = SavedStateHandle(mapOf("productId" to productId)),
        productRepository = repository,
        tenantId = flowOf(tenantId),
    )

    @Test
    fun `alta mode starts with isEditMode false and empty fields`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.isEditMode)
        assertEquals("", vm.uiState.value.name)
        assertEquals(emptyList<String>(), vm.uiState.value.variantNames)
    }

    @Test
    fun `blank productId is treated as alta mode, not edit mode`() {
        val vm = viewModel(productId = "")
        assertFalse(vm.uiState.value.isEditMode)
    }

    @Test
    fun `onSaveClick with blank name marks nameError`() {
        val vm = viewModel()
        vm.onPriceChange("10.00")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.nameError)
        assertEquals("El nombre es obligatorio", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onSaveClick with blank price marks priceError`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.priceError)
        assertEquals("Ingresa un precio válido", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onSaveClick with non-numeric price marks priceError`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")
        vm.onPriceChange("no-es-numero")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.priceError)
    }

    @Test
    fun `onSaveClick with scientific notation price marks priceError`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")
        vm.onPriceChange("1e10")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.priceError)
    }

    @Test
    fun `onSaveClick with negative price marks priceError`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")
        vm.onPriceChange("-5.00")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.priceError)
    }

    @Test
    fun `onSaveClick with price exceeding NUMERIC(18,6) scale marks priceError`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")
        vm.onPriceChange("10.1234567")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.priceError)
    }

    @Test
    fun `onSaveClick with non-numeric tax rate shows generic errorMessage`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")
        vm.onPriceChange("10.00")
        vm.onTaxRateChange("no-es-numero")

        vm.onSaveClick()

        assertEquals("Ingresa un impuesto válido (0-100%)", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onSaveClick with tax rate over 100 shows errorMessage`() {
        val vm = viewModel()
        vm.onNameChange("Refresco")
        vm.onPriceChange("10.00")
        vm.onTaxRateChange("150")

        vm.onSaveClick()

        assertEquals("Ingresa un impuesto válido (0-100%)", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onSaveClick with tax rate of exactly 100 is accepted`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        val navEvents = mutableListOf<Unit>()
        val job = launch { vm.navEvent.collect { navEvents.add(it) } }

        vm.onNameChange("Refresco")
        vm.onPriceChange("10.00")
        vm.onTaxRateChange("100")
        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals(1, navEvents.size)
        job.cancel()
    }

    @Test
    fun `onAddVariantClick appends trimmed name without persisting anything`() = runTest {
        val vm = viewModel()
        vm.onNewVariantNameChange("  600ml  ")

        vm.onAddVariantClick()
        advanceUntilIdle()

        assertEquals(listOf("600ml"), vm.uiState.value.variantNames)
        assertEquals("", vm.uiState.value.newVariantName)

        val persistedProducts = mutableListOf<Any>()
        val job = launch { repository.getAllProducts("tenant-1").collect { persistedProducts.addAll(it) } }
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), persistedProducts)
        job.cancel()
    }

    @Test
    fun `onAddVariantClick with blank name does nothing`() {
        val vm = viewModel()
        vm.onNewVariantNameChange("   ")

        vm.onAddVariantClick()

        assertEquals(emptyList<String>(), vm.uiState.value.variantNames)
    }

    @Test
    fun `onRemoveVariantClick removes the variant at that index`() {
        val vm = viewModel()
        vm.onNewVariantNameChange("600ml")
        vm.onAddVariantClick()
        vm.onNewVariantNameChange("1L")
        vm.onAddVariantClick()

        vm.onRemoveVariantClick(0)

        assertEquals(listOf("1L"), vm.uiState.value.variantNames)
    }

    @Test
    fun `alta mode creates product with variants and emits navEvent`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        val navEvents = mutableListOf<Unit>()
        val job = launch { vm.navEvent.collect { navEvents.add(it) } }

        vm.onNameChange("Refresco")
        vm.onPriceChange("15.50")
        vm.onTaxRateChange("16.00")
        vm.onNewVariantNameChange("600ml")
        vm.onAddVariantClick()
        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals(1, navEvents.size)
        job.cancel()
    }

    @Test
    fun `alta mode with null tenantId shows error and does not save`() = runTest {
        val vm = viewModel(tenantId = null)
        vm.onNameChange("Refresco")
        vm.onPriceChange("15.50")

        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals("No se pudo determinar tu negocio. Vuelve a iniciar sesión.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `edit mode preloads fields and variants from existing product`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal("16.00"), listOf("600ml", "1L"), "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(productId = id)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isEditMode)
        assertEquals("Refresco", state.name)
        assertEquals("15.50", state.price)
        assertEquals("16.00", state.taxRate)
        assertEquals(setOf("600ml", "1L"), state.variantNames.toSet())
    }

    @Test
    fun `edit mode with null tenantId shows error while loading`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal("16.00"), emptyList(), "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(productId = id, tenantId = null)
        advanceUntilIdle()

        assertEquals("No se pudo determinar tu negocio. Vuelve a iniciar sesión.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `edit mode on a product from another tenant shows an error, same as nonexistent`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal("16.00"), emptyList(), "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(productId = id, tenantId = "tenant-2")
        advanceUntilIdle()

        assertEquals("No pudimos cargar los datos del producto.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `edit mode on a deleted product shows an error and does not emit navEvent`() = runTest {
        val vm = viewModel(productId = "ghost-id")
        advanceUntilIdle()
        assertEquals("No pudimos cargar los datos del producto.", vm.uiState.value.errorMessage)

        val navEvents = mutableListOf<Unit>()
        val job = launch { vm.navEvent.collect { navEvents.add(it) } }

        vm.onNameChange("Refresco")
        vm.onPriceChange("15.50")
        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals(0, navEvents.size)
        assertEquals("Este producto ya no existe. Puede que se haya eliminado en otro dispositivo.", vm.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `edit mode saves updated fields and replaces variants`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal("16.00"), listOf("600ml"), "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(productId = id)
        advanceUntilIdle()

        vm.onNameChange("Refresco Grande")
        vm.onRemoveVariantClick(0)
        vm.onNewVariantNameChange("2L")
        vm.onAddVariantClick()
        vm.onSaveClick()
        advanceUntilIdle()

        val updated = repository.getProductById(id, "tenant-1")
        assertEquals("Refresco Grande", updated?.name)
        val variants = repository.getVariantsForProduct(id, "tenant-1")
        assertEquals(listOf("2L"), variants.map { it.name })
    }
}
