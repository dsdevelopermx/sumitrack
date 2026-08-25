package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.entities.SaleItemEntity

class FakeSaleItemDao : SaleItemDao {

    private val items = mutableMapOf<String, SaleItemEntity>()

    override suspend fun getForSale(saleId: String, tenantId: String): List<SaleItemEntity> =
        items.values.filter { it.fkSale == saleId && it.fkTenant == tenantId }

    override suspend fun upsertAll(items: List<SaleItemEntity>) {
        items.forEach { this.items[it.id] = it }
    }

    override suspend fun getPending(tenantId: String): List<SaleItemEntity> =
        items.values.filter { it.fkTenant == tenantId && it.syncStatus == "pending" }

    override suspend fun getConflicted(tenantId: String): List<SaleItemEntity> =
        items.values.filter { it.fkTenant == tenantId && it.syncStatus == "conflict" }

    override suspend fun markSynced(ids: List<String>) {
        ids.forEach { id -> items[id]?.let { items[id] = it.copy(syncStatus = "synced") } }
    }

    override suspend fun markConflict(ids: List<String>) {
        ids.forEach { id -> items[id]?.let { items[id] = it.copy(syncStatus = "conflict") } }
    }

    override suspend fun getById(id: String): SaleItemEntity? = items[id]
}
