package com.sumitrack.android.ui.screens.products

import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.entities.ProductVariantEntity

class FakeProductVariantDao : ProductVariantDao {

    private val variants = mutableMapOf<String, ProductVariantEntity>()

    override suspend fun getForProduct(productId: String, tenantId: String): List<ProductVariantEntity> =
        variants.values
            .filter { it.fkProduct == productId && it.fkTenant == tenantId }
            .sortedBy { it.name }

    override suspend fun upsertAll(variants: List<ProductVariantEntity>) {
        variants.forEach { this.variants[it.id] = it }
    }

    override suspend fun deleteAllForProduct(productId: String, tenantId: String) {
        variants.values
            .filter { it.fkProduct == productId && it.fkTenant == tenantId }
            .forEach { variants.remove(it.id) }
    }

    override suspend fun getProductIdsWithVariants(tenantId: String): List<String> =
        variants.values.filter { it.fkTenant == tenantId }.map { it.fkProduct }.distinct()

    override suspend fun getPending(tenantId: String): List<ProductVariantEntity> =
        variants.values.filter { it.fkTenant == tenantId && it.syncStatus == "pending" }

    override suspend fun getConflicted(tenantId: String): List<ProductVariantEntity> =
        variants.values.filter { it.fkTenant == tenantId && it.syncStatus == "conflict" }

    override suspend fun markSynced(ids: List<String>) {
        ids.forEach { id -> variants[id]?.let { variants[id] = it.copy(syncStatus = "synced") } }
    }

    override suspend fun markConflict(ids: List<String>) {
        ids.forEach { id -> variants[id]?.let { variants[id] = it.copy(syncStatus = "conflict") } }
    }

    override suspend fun getById(id: String): ProductVariantEntity? = variants[id]
}
