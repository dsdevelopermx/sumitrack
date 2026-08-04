package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.dao.CreditBalanceDao
import com.sumitrack.android.data.local.entities.CreditBalanceEntity

class FakeCreditBalanceDao : CreditBalanceDao {

    private val rows = mutableMapOf<String, CreditBalanceEntity>()

    override suspend fun getForClient(clientId: String, tenantId: String): List<CreditBalanceEntity> =
        rows.values.filter { it.fkClient == clientId && it.fkTenant == tenantId }

    override suspend fun upsertAll(rows: List<CreditBalanceEntity>) {
        rows.forEach { this.rows[it.id] = it }
    }
}
