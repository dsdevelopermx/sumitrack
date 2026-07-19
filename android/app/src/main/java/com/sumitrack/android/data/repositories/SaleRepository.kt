package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.domain.models.Sale
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
) {

    suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<Sale> =
        saleDao.getOpenSalesForClient(clientId, tenantId).map { it.toDomain() }

    private fun SaleEntity.toDomain() = Sale(
        id = id,
        fkTenant = fkTenant,
        fkClient = fkClient,
        folio = folio,
        total = total,
        status = SaleStatus.fromString(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )
}
