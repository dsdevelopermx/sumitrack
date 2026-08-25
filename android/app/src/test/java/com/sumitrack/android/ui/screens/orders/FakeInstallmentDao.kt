package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.entities.InstallmentEntity

class FakeInstallmentDao : InstallmentDao {

    private val installments = mutableMapOf<String, InstallmentEntity>()

    override suspend fun getForSale(saleId: String, tenantId: String): List<InstallmentEntity> =
        installments.values
            .filter { it.fkSale == saleId && it.fkTenant == tenantId }
            .sortedBy { it.dueDate }

    override suspend fun upsertAll(installments: List<InstallmentEntity>) {
        installments.forEach { this.installments[it.id] = it }
    }

    override suspend fun getPending(tenantId: String): List<InstallmentEntity> =
        installments.values.filter { it.fkTenant == tenantId && it.syncStatus == "pending" }

    override suspend fun getConflicted(tenantId: String): List<InstallmentEntity> =
        installments.values.filter { it.fkTenant == tenantId && it.syncStatus == "conflict" }

    override suspend fun markSynced(ids: List<String>) {
        ids.forEach { id -> installments[id]?.let { installments[id] = it.copy(syncStatus = "synced") } }
    }

    override suspend fun markConflict(ids: List<String>) {
        ids.forEach { id -> installments[id]?.let { installments[id] = it.copy(syncStatus = "conflict") } }
    }

    override suspend fun getById(id: String): InstallmentEntity? = installments[id]
}
