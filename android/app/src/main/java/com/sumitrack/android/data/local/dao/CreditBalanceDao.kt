package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.CreditBalanceEntity

@Dao
interface CreditBalanceDao {

    @Query("SELECT * FROM credit_balances WHERE fk_client = :clientId AND fk_tenant = :tenantId")
    suspend fun getForClient(clientId: String, tenantId: String): List<CreditBalanceEntity>

    @Upsert
    suspend fun upsertAll(rows: List<CreditBalanceEntity>)

    @Query("SELECT * FROM credit_balances WHERE fk_tenant = :tenantId AND sync_status = 'pending'")
    suspend fun getPending(tenantId: String): List<CreditBalanceEntity>

    @Query("UPDATE credit_balances SET sync_status = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
