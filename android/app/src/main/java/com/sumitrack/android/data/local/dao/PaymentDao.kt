package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.PaymentEntity

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments WHERE fk_sale = :saleId AND fk_tenant = :tenantId")
    suspend fun getForSale(saleId: String, tenantId: String): List<PaymentEntity>

    @Upsert
    suspend fun upsertAll(payments: List<PaymentEntity>)
}
