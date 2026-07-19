package com.sumitrack.android.data.repositories

import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.ui.screens.products.FakeProductDao
import com.sumitrack.android.ui.screens.products.FakeProductVariantDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductRepositoryTest {

    private lateinit var fakeProductDao: FakeProductDao
    private lateinit var fakeVariantDao: FakeProductVariantDao
    private lateinit var repository: ProductRepository

    @Before
    fun setUp() {
        fakeProductDao = FakeProductDao()
        fakeVariantDao = FakeProductVariantDao()
        repository = ProductRepository(FakeTransactionRunner(), fakeProductDao, fakeVariantDao)
    }

    @Test
    fun `createProduct persists product with generated id and variants with their own ids`() = runTest {
        val id = repository.createProduct(
            name = "Refresco",
            price = BigDecimal("15.50"),
            taxRate = BigDecimal("16.00"),
            variantNames = listOf("600ml", "1L"),
            fkTenant = "tenant-1",
        )

        val product = repository.getProductById(id, "tenant-1")
        assertEquals("Refresco", product?.name)
        assertEquals(BigDecimal("15.50"), product?.price)
        assertEquals(BigDecimal("16.00"), product?.taxRate)
        assertTrue(product?.isActive == true)

        val variants = repository.getVariantsForProduct(id, "tenant-1")
        assertEquals(2, variants.size)
        assertTrue(variants.all { it.fkProduct == id })
        assertTrue(variants.map { it.id }.toSet().size == 2)
    }

    @Test
    fun `createProduct without variants persists product with empty variant list`() = runTest {
        val id = repository.createProduct(
            name = "Agua",
            price = BigDecimal("10.00"),
            taxRate = BigDecimal.ZERO,
            variantNames = emptyList(),
            fkTenant = "tenant-1",
        )

        assertEquals(emptyList<Any>(), repository.getVariantsForProduct(id, "tenant-1"))
    }

    @Test
    fun `createProduct sanitizes blank and duplicate variant names`() = runTest {
        val id = repository.createProduct(
            name = "Refresco",
            price = BigDecimal("15.50"),
            taxRate = BigDecimal.ZERO,
            variantNames = listOf("600ml", "  ", "600ml", "1L"),
            fkTenant = "tenant-1",
        )

        val variants = repository.getVariantsForProduct(id, "tenant-1")
        assertEquals(setOf("600ml", "1L"), variants.map { it.name }.toSet())
    }

    @Test
    fun `updateProduct replaces the variant set entirely`() = runTest {
        val id = repository.createProduct(
            name = "Refresco",
            price = BigDecimal("15.50"),
            taxRate = BigDecimal("16.00"),
            variantNames = listOf("600ml", "1L"),
            fkTenant = "tenant-1",
        )

        repository.updateProduct(
            id = id,
            tenantId = "tenant-1",
            name = "Refresco",
            price = BigDecimal("16.00"),
            taxRate = BigDecimal("16.00"),
            isActive = true,
            variantNames = listOf("2L"),
        )

        val variants = repository.getVariantsForProduct(id, "tenant-1")
        assertEquals(1, variants.size)
        assertEquals("2L", variants.first().name)
    }

    @Test
    fun `updateProduct toggles isActive`() = runTest {
        val id = repository.createProduct(
            name = "Refresco",
            price = BigDecimal("15.50"),
            taxRate = BigDecimal("16.00"),
            variantNames = emptyList(),
            fkTenant = "tenant-1",
        )

        repository.updateProduct(
            id = id,
            tenantId = "tenant-1",
            name = "Refresco",
            price = BigDecimal("15.50"),
            taxRate = BigDecimal("16.00"),
            isActive = false,
            variantNames = emptyList(),
        )

        assertFalse(repository.getProductById(id, "tenant-1")?.isActive == true)
    }

    @Test
    fun `updateProduct on a nonexistent id returns false`() = runTest {
        val result = repository.updateProduct(
            id = "ghost-id",
            tenantId = "tenant-1",
            name = "X",
            price = BigDecimal.ZERO,
            taxRate = BigDecimal.ZERO,
            isActive = true,
            variantNames = emptyList(),
        )
        assertFalse(result)
    }

    @Test
    fun `updateProduct on a product belonging to another tenant returns false`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, emptyList(), "tenant-1")

        val result = repository.updateProduct(
            id = id,
            tenantId = "tenant-2",
            name = "Hackeado",
            price = BigDecimal.ZERO,
            taxRate = BigDecimal.ZERO,
            isActive = false,
            variantNames = emptyList(),
        )

        assertFalse(result)
        assertEquals("Refresco", repository.getProductById(id, "tenant-1")?.name)
    }

    @Test
    fun `getProductById returns null when product does not exist`() = runTest {
        assertNull(repository.getProductById("ghost-id", "tenant-1"))
    }

    @Test
    fun `getProductById excludes a product from a different tenant`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, emptyList(), "tenant-1")
        assertNull(repository.getProductById(id, "tenant-2"))
    }

    @Test
    fun `getVariantsForProduct excludes variants from a different tenant`() = runTest {
        val id = repository.createProduct("Refresco", BigDecimal("15.50"), BigDecimal.ZERO, listOf("600ml"), "tenant-1")
        assertEquals(emptyList<Any>(), repository.getVariantsForProduct(id, "tenant-2"))
    }

    @Test
    fun `getAllProducts includes both active and inactive products`() = runTest {
        val activeId = repository.createProduct("Activo", BigDecimal("1.00"), BigDecimal.ZERO, emptyList(), "tenant-1")
        val inactiveId = repository.createProduct("Inactivo", BigDecimal("1.00"), BigDecimal.ZERO, emptyList(), "tenant-1")
        repository.updateProduct(inactiveId, "tenant-1", "Inactivo", BigDecimal("1.00"), BigDecimal.ZERO, isActive = false, variantNames = emptyList())

        val all = mutableListOf<Product>()
        val job = launch { repository.getAllProducts("tenant-1").collect { all.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        val ids = all.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf(activeId, inactiveId)))
        assertTrue(all.first { it.id == inactiveId }.isActive.not())
    }

    @Test
    fun `getAllProducts excludes products from a different tenant`() = runTest {
        repository.createProduct("De otro tenant", BigDecimal("1.00"), BigDecimal.ZERO, emptyList(), "tenant-2")

        val all = mutableListOf<Product>()
        val job = launch { repository.getAllProducts("tenant-1").collect { all.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Product>(), all)
    }
}
