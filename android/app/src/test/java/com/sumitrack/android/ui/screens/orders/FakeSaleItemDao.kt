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
}
