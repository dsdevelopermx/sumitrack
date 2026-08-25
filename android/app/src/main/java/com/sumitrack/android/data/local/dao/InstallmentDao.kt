package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.InstallmentEntity

@Dao
interface InstallmentDao {

    @Query("SELECT * FROM installments WHERE fk_sale = :saleId AND fk_tenant = :tenantId ORDER BY due_date ASC")
    suspend fun getForSale(saleId: String, tenantId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun getById(id: String): InstallmentEntity?

    @Upsert
    suspend fun upsertAll(installments: List<InstallmentEntity>)

    @Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND sync_status = 'pending'")
    suspend fun getPending(tenantId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND sync_status = 'conflict'")
    suspend fun getConflicted(tenantId: String): List<InstallmentEntity>

    @Query("UPDATE installments SET sync_status = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE installments SET sync_status = 'conflict' WHERE id IN (:ids)")
    suspend fun markConflict(ids: List<String>)
}
