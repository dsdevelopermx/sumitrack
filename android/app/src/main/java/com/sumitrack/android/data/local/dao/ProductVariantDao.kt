package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.ProductVariantEntity

@Dao
interface ProductVariantDao {

    @Query("SELECT * FROM product_variants WHERE fk_product = :productId AND fk_tenant = :tenantId ORDER BY name ASC")
    suspend fun getForProduct(productId: String, tenantId: String): List<ProductVariantEntity>

    @Query("SELECT DISTINCT fk_product FROM product_variants WHERE fk_tenant = :tenantId")
    suspend fun getProductIdsWithVariants(tenantId: String): List<String>

    @Upsert
    suspend fun upsertAll(variants: List<ProductVariantEntity>)

    @Query("DELETE FROM product_variants WHERE fk_product = :productId AND fk_tenant = :tenantId")
    suspend fun deleteAllForProduct(productId: String, tenantId: String)

    @Query("SELECT * FROM product_variants WHERE fk_tenant = :tenantId AND sync_status = 'pending'")
    suspend fun getPending(tenantId: String): List<ProductVariantEntity>

    @Query("UPDATE product_variants SET sync_status = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
