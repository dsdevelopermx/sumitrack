package com.sumitrack.android.ui.screens.clients

import com.sumitrack.android.data.local.SearchNormalizer
import com.sumitrack.android.data.local.dao.OrderSummaryRow
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.entities.SaleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSaleDao : SaleDao {

    private val salesFlow = MutableStateFlow<List<SaleEntity>>(emptyList())
    private val clientNames = mutableMapOf<String, String>()

    // null = never throw. 1-based: throws starting from the N-th call (e.g. 2 lets the first
    // call through — used to simulate the client-balance lookup succeeding while a later,
    // independent open-sales fetch fails).
    var throwFromCallNumber: Int? = null
    private var callCount = 0

    override suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<SaleEntity> {
        callCount++
        throwFromCallNumber?.let { if (callCount >= it) throw IllegalStateException("simulated DB failure") }
        return salesFlow.value
            .filter { it.fkClient == clientId && it.fkTenant == tenantId && it.status in setOf("pending", "partial") }
            .sortedByDescending { it.createdAt }
    }

    override fun getOrdersForTenantAsFlow(
        tenantId: String,
        statusFilter: String?,
        normalizedQuery: String,
    ): Flow<List<OrderSummaryRow>> =
        salesFlow.map { sales ->
            sales
                .filter { it.fkTenant == tenantId }
                .filter { statusFilter == null || it.status == statusFilter }
                .filter { sale ->
                    if (normalizedQuery.isBlank()) {
                        true
                    } else {
                        val name = clientNames[sale.fkClient].orEmpty()
                        sale.folio.lowercase().contains(normalizedQuery) ||
                            SearchNormalizer.normalize(name).contains(normalizedQuery)
                    }
                }
                .sortedWith(compareByDescending<SaleEntity> { it.createdAt }.thenByDescending { it.id })
                .map { sale ->
                    OrderSummaryRow(
                        id = sale.id,
                        folio = sale.folio,
                        total = sale.total,
                        status = sale.status,
                        clientName = clientNames[sale.fkClient] ?: "(cliente eliminado)",
                        createdAt = sale.createdAt,
                        syncStatus = sale.syncStatus,
                    )
                }
        }

    override suspend fun countSalesForTenant(tenantId: String): Int =
        salesFlow.value.count { it.fkTenant == tenantId }

    // Simula getSaleDetail/GenerateTicketUseCase devolviendo null pese a que la venta se creó
    // exitosamente — usado para probar el fallback de PaymentViewModel.loadTicket ante ese caso.
    var forceGetByIdNull: Boolean = false

    override suspend fun getById(id: String, tenantId: String): SaleEntity? {
        if (forceGetByIdNull) return null
        return salesFlow.value.find { it.id == id && it.fkTenant == tenantId }
    }

    override suspend fun upsertAll(sales: List<SaleEntity>) {
        val byId = salesFlow.value.associateBy { it.id }.toMutableMap()
        sales.forEach { byId[it.id] = it }
        salesFlow.value = byId.values.toList()
    }

    fun setSales(entities: List<SaleEntity>) {
        salesFlow.value = entities
    }

    fun setClientNames(names: Map<String, String>) {
        clientNames.clear()
        clientNames.putAll(names)
    }
}
