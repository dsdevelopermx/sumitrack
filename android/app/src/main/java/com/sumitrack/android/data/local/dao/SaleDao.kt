package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.SaleEntity

@Dao
interface SaleDao {

    @Query(
        "SELECT * FROM sales WHERE fk_client = :clientId AND fk_tenant = :tenantId " +
            "AND status IN ('pending', 'partial') ORDER BY created_at DESC"
    )
    suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<SaleEntity>

    @Upsert
    suspend fun upsertAll(sales: List<SaleEntity>)
}
