package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.SaleItemEntity

@Dao
interface SaleItemDao {

    @Query("SELECT * FROM sale_items WHERE fk_sale = :saleId AND fk_tenant = :tenantId")
    suspend fun getForSale(saleId: String, tenantId: String): List<SaleItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<SaleItemEntity>)
}
