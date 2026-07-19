package com.sumitrack.android.ui.screens.clients

import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.entities.SaleEntity

class FakeSaleDao : SaleDao {

    private val sales = mutableMapOf<String, SaleEntity>()

    // null = never throw. 1-based: throws starting from the N-th call (e.g. 2 lets the first
    // call through — used to simulate the client-balance lookup succeeding while a later,
    // independent open-sales fetch fails).
    var throwFromCallNumber: Int? = null
    private var callCount = 0

    override suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<SaleEntity> {
        callCount++
        throwFromCallNumber?.let { if (callCount >= it) throw IllegalStateException("simulated DB failure") }
        return sales.values
            .filter { it.fkClient == clientId && it.fkTenant == tenantId && it.status in setOf("pending", "partial") }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun upsertAll(sales: List<SaleEntity>) {
        sales.forEach { this.sales[it.id] = it }
    }

    fun setSales(entities: List<SaleEntity>) {
        sales.clear()
        entities.forEach { sales[it.id] = it }
    }
}
