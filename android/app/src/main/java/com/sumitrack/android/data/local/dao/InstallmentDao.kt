package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.InstallmentEntity

@Dao
interface InstallmentDao {

    @Query("SELECT * FROM installments WHERE fk_sale = :saleId AND fk_tenant = :tenantId ORDER BY due_date ASC")
    suspend fun getForSale(saleId: String, tenantId: String): List<InstallmentEntity>

    @Upsert
    suspend fun upsertAll(installments: List<InstallmentEntity>)
}
