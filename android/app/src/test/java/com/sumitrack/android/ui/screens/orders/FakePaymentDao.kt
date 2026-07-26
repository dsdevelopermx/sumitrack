package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.entities.PaymentEntity

class FakePaymentDao : PaymentDao {

    private val payments = mutableMapOf<String, PaymentEntity>()

    override suspend fun getForSale(saleId: String, tenantId: String): List<PaymentEntity> =
        payments.values.filter { it.fkSale == saleId && it.fkTenant == tenantId }

    override suspend fun upsertAll(payments: List<PaymentEntity>) {
        payments.forEach { this.payments[it.id] = it }
    }
}
