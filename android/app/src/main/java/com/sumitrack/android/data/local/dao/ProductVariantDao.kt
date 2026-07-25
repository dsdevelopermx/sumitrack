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
}
