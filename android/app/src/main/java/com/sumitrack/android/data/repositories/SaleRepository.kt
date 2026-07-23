package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.dao.OrderSummaryRow
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.domain.models.OrderSummary
import com.sumitrack.android.domain.models.Sale
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
) {

    suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<Sale> =
        saleDao.getOpenSalesForClient(clientId, tenantId).map { it.toDomain() }

    fun getOrdersForTenant(tenantId: String, statusFilter: SaleStatus?, searchQuery: String): Flow<List<OrderSummary>> =
        saleDao.getOrdersForTenantAsFlow(
            tenantId = tenantId,
            statusFilter = statusFilter?.name?.lowercase(Locale.ROOT),
            normalizedQuery = SearchNormalizer.toLikePattern(searchQuery.trim()),
        ).map { rows -> rows.map { it.toDomain() } }

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

    private fun OrderSummaryRow.toDomain() = OrderSummary(
        id = id,
        folio = folio,
        clientName = clientName,
        total = total,
        status = SaleStatus.fromString(status),
        createdAt = createdAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )
}
