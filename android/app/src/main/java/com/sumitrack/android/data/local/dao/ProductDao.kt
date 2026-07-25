package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE fk_tenant = :tenantId ORDER BY name ASC")
    fun getAllAsFlow(tenantId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE fk_tenant = :tenantId AND is_active = 1 ORDER BY name ASC")
    fun getActiveAsFlow(tenantId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id AND fk_tenant = :tenantId LIMIT 1")
    suspend fun getById(id: String, tenantId: String): ProductEntity?

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)
}
