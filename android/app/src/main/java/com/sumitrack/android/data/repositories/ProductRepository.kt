package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.TransactionRunner
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.entities.ProductEntity
import com.sumitrack.android.data.local.entities.ProductVariantEntity
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.ProductVariant
import com.sumitrack.android.domain.models.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val productDao: ProductDao,
    private val productVariantDao: ProductVariantDao,
) {

    fun getAllProducts(tenantId: String): Flow<List<Product>> =
        productDao.getAllAsFlow(tenantId).map { entities -> entities.map { it.toDomain() } }

    fun getActiveProducts(tenantId: String): Flow<List<Product>> =
        productDao.getActiveAsFlow(tenantId).map { entities -> entities.map { it.toDomain() } }

    suspend fun getProductById(id: String, tenantId: String): Product? =
        productDao.getById(id, tenantId)?.toDomain()

    suspend fun getVariantsForProduct(productId: String, tenantId: String): List<ProductVariant> =
        productVariantDao.getForProduct(productId, tenantId).map { it.toDomain() }

    suspend fun getProductIdsWithVariants(tenantId: String): Set<String> =
        productVariantDao.getProductIdsWithVariants(tenantId).toSet()

    suspend fun createProduct(
        name: String,
        price: BigDecimal,
        taxRate: BigDecimal,
        variantNames: List<String>,
        fkTenant: String,
    ): String {
        require(name.isNotBlank()) { "name must not be blank" }
        require(price >= BigDecimal.ZERO) { "price must not be negative" }

        val now = Instant.now()
        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            fkTenant = fkTenant,
            name = name,
            price = price,
            taxRate = taxRate,
            isActive = true,
            createdAt = now,
            updatedAt = now,
            syncStatus = "pending",
        )
        transactionRunner.run {
            productDao.upsertAll(listOf(product))
            insertVariants(product.id, fkTenant, variantNames)
        }
        return product.id
    }

    suspend fun updateProduct(
        id: String,
        tenantId: String,
        name: String,
        price: BigDecimal,
        taxRate: BigDecimal,
        isActive: Boolean,
        variantNames: List<String>,
    ): Boolean {
        require(name.isNotBlank()) { "name must not be blank" }
        require(price >= BigDecimal.ZERO) { "price must not be negative" }

        val existing = productDao.getById(id, tenantId) ?: return false
        transactionRunner.run {
            productDao.upsertAll(
                listOf(
                    existing.copy(
                        name = name,
                        price = price,
                        taxRate = taxRate,
                        isActive = isActive,
                        updatedAt = Instant.now(),
                        syncStatus = "pending",
                    )
                )
            )
            // Reemplazo total de variantes — ver Dev Notes de la historia 2.4 ("Por qué 'reemplazar
            // todo' y no diffing"): sin motor de sync todavía (Epic 4), es la opción más simple.
            // Envuelto en la misma transacción que el upsert del producto para que un fallo a mitad
            // de camino no deje variantes borradas sin reinsertar.
            productVariantDao.deleteAllForProduct(id, tenantId)
            insertVariants(id, existing.fkTenant, variantNames)
        }
        return true
    }

    private suspend fun insertVariants(productId: String, fkTenant: String, names: List<String>) {
        val sanitized = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (sanitized.isEmpty()) return
        val now = Instant.now()
        val entities = sanitized.map { variantName ->
            ProductVariantEntity(
                id = UUID.randomUUID().toString(),
                fkTenant = fkTenant,
                fkProduct = productId,
                name = variantName,
                createdAt = now,
                updatedAt = now,
                syncStatus = "pending",
            )
        }
        productVariantDao.upsertAll(entities)
    }

    private fun ProductEntity.toDomain() = Product(
        id = id,
        fkTenant = fkTenant,
        name = name,
        price = price,
        taxRate = taxRate,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun ProductVariantEntity.toDomain() = ProductVariant(
        id = id,
        fkTenant = fkTenant,
        fkProduct = fkProduct,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )
}
