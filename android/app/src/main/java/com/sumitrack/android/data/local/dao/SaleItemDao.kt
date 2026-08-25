package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.SaleItemEntity

@Dao
interface SaleItemDao {

    @Query("SELECT * FROM sale_items WHERE fk_sale = :saleId AND fk_tenant = :tenantId")
    suspend fun getForSale(saleId: String, tenantId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE id = :id")
    suspend fun getById(id: String): SaleItemEntity?

    @Upsert
    suspend fun upsertAll(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sale_items WHERE fk_tenant = :tenantId AND sync_status = 'pending'")
    suspend fun getPending(tenantId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE fk_tenant = :tenantId AND sync_status = 'conflict'")
    suspend fun getConflicted(tenantId: String): List<SaleItemEntity>

    @Query("UPDATE sale_items SET sync_status = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE sale_items SET sync_status = 'conflict' WHERE id IN (:ids)")
    suspend fun markConflict(ids: List<String>)
}
