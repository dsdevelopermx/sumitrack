package com.sumitrack.android.ui.screens.products

import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.repositories.ProductRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProductListViewModelTest {

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

    private fun viewModel(tenantId: String? = "tenant-1") =
        ProductListViewModel(repository, flowOf(tenantId))

    @Test
    fun `products emits empty list when no products exist`() = runTest {
        val vm = viewModel()
        val job = launch { vm.products.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.products.value)
        job.cancel()
    }

    @Test
    fun `products includes active and inactive products with isActive preserved`() = runTest {
        val vm = viewModel()
        val job = launch { vm.products.collect {} }
        fakeProductDao.setProducts(
            listOf(
                makeEntity("1", "Activo", isActive = true),
                makeEntity("2", "Inactivo", isActive = false),
            )
        )
        advanceUntilIdle()
        val result = vm.products.value
        assertEquals(2, result.size)
        assertTrue(result.first { it.id == "1" }.isActive)
        assertTrue(!result.first { it.id == "2" }.isActive)
        job.cancel()
    }

    @Test
    fun `products excludes products from a different tenant`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        val job = launch { vm.products.collect {} }
        fakeProductDao.setProducts(
            listOf(
                makeEntity("1", "Mío", isActive = true, fkTenant = "tenant-1"),
                makeEntity("2", "De otro tenant", isActive = true, fkTenant = "tenant-2"),
            )
        )
        advanceUntilIdle()
        assertEquals(listOf("1"), vm.products.value.map { it.id })
        job.cancel()
    }

    @Test
    fun `products emits empty list when tenantId is null`() = runTest {
        val vm = viewModel(tenantId = null)
        val job = launch { vm.products.collect {} }
        fakeProductDao.setProducts(listOf(makeEntity("1", "Activo", isActive = true)))
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), vm.products.value)
        job.cancel()
    }

    private fun makeEntity(id: String, name: String, isActive: Boolean, fkTenant: String = "tenant-1") = ProductEntity(
        id = id,
        fkTenant = fkTenant,
        name = name,
        price = BigDecimal("10.00"),
        taxRate = BigDecimal.ZERO,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(1_000_000L),
        updatedAt = Instant.ofEpochMilli(1_000_000L),
        syncStatus = "synced",
    )
}
