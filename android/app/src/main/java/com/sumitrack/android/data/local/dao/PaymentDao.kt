package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.PaymentEntity

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments WHERE fk_sale = :saleId AND fk_tenant = :tenantId")
    suspend fun getForSale(saleId: String, tenantId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: String): PaymentEntity?

    @Upsert
    suspend fun upsertAll(payments: List<PaymentEntity>)

    @Query("SELECT * FROM payments WHERE fk_tenant = :tenantId AND sync_status = 'pending'")
    suspend fun getPending(tenantId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE fk_tenant = :tenantId AND sync_status = 'conflict'")
    suspend fun getConflicted(tenantId: String): List<PaymentEntity>

    @Query("UPDATE payments SET sync_status = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE payments SET sync_status = 'conflict' WHERE id IN (:ids)")
    suspend fun markConflict(ids: List<String>)
}
