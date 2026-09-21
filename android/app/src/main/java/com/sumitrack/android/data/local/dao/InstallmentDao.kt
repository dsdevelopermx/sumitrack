package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.InstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    // Sin join con `sales`: cancelSale marca `cancelled` las parcialidades pending de la venta y
    // registerPayment con installmentId marca `paid` la cobrada. registerPayment con installmentId = null
    // (que marca la VENTA paid sin tocar parcialidades) solo es alcanzable para ventas SIN parcialidades:
    // RegisterPaymentUseCase lo rechaza si la venta tiene alguna. Si algún día llegara una parcialidad
    // pending de una venta ya cerrada, ReminderContentBuilder no notifica (guard de venta paid/cancelled).
    @Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND status = 'pending'")
    fun observeOpenForTenant(tenantId: String): Flow<List<InstallmentEntity>>

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
