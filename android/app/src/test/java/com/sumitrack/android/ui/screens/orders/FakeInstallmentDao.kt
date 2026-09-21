package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.dao.AgendaRow
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.entities.InstallmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeInstallmentDao : InstallmentDao {

    private val installmentsFlow = MutableStateFlow<Map<String, InstallmentEntity>>(emptyMap())
    private val installments: Map<String, InstallmentEntity> get() = installmentsFlow.value

    override suspend fun getForSale(saleId: String, tenantId: String): List<InstallmentEntity> =
        installments.values
            .filter { it.fkSale == saleId && it.fkTenant == tenantId }
            .sortedBy { it.dueDate }

    override suspend fun upsertAll(installments: List<InstallmentEntity>) {
        installmentsFlow.value = this.installments + installments.associateBy { it.id }
    }

    override suspend fun getPending(tenantId: String): List<InstallmentEntity> =
        installments.values.filter { it.fkTenant == tenantId && it.syncStatus == "pending" }

    override suspend fun getConflicted(tenantId: String): List<InstallmentEntity> =
        installments.values.filter { it.fkTenant == tenantId && it.syncStatus == "conflict" }

    override suspend fun markSynced(ids: List<String>) {
        installmentsFlow.value = installments.mapValues { (id, e) -> if (id in ids) e.copy(syncStatus = "synced") else e }
    }

    override suspend fun markConflict(ids: List<String>) {
        installmentsFlow.value = installments.mapValues { (id, e) -> if (id in ids) e.copy(syncStatus = "conflict") else e }
    }

    override suspend fun getById(id: String): InstallmentEntity? = installments[id]

    // Contexto (folio + cliente) de cada venta, para simular el join de observeAgendaForTenant — mismo
    // criterio que FakeSaleDao.clientNames. No replica el filtro por estado de la venta ni el SQL.
    private val saleContext = mutableMapOf<String, Pair<String, String>>()

    fun setSaleContext(saleId: String, folio: String, clientName: String) {
        saleContext[saleId] = folio to clientName
    }

    override fun observeAgendaForTenant(tenantId: String): Flow<List<AgendaRow>> =
        installmentsFlow.map { map ->
            map.values
                .filter { it.fkTenant == tenantId && it.status == "pending" }
                .sortedWith(compareBy({ it.dueDate }, { it.id }))
                .map { i ->
                    val (folio, client) = saleContext[i.fkSale] ?: ("(sin folio)" to "(cliente eliminado)")
                    AgendaRow(i.id, i.fkSale, folio, client, i.amount, i.dueDate)
                }
        }

    override fun observeOpenForTenant(tenantId: String): Flow<List<InstallmentEntity>> =
        installmentsFlow.map { map -> map.values.filter { it.fkTenant == tenantId && it.status == "pending" } }
}
