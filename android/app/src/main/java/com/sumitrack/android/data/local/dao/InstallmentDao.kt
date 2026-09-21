package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.InstallmentEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

// Fila de la Agenda de Cobros (S-10, Historia 5.3): parcialidad pendiente + folio y cliente de su venta.
data class AgendaRow(
    val installmentId: String,
    val saleId: String,
    val folio: String,
    val clientName: String,
    val amount: BigDecimal,
    val dueDate: Instant,
)

@Dao
interface InstallmentDao {

    // Sin join con `sales`: cancelSale marca `cancelled` las parcialidades pending de la venta y
    // registerPayment con installmentId marca `paid` la cobrada. registerPayment con installmentId = null
    // (que marca la VENTA paid sin tocar parcialidades) solo es alcanzable para ventas SIN parcialidades:
    // RegisterPaymentUseCase lo rechaza si la venta tiene alguna. Si algún día llegara una parcialidad
    // pending de una venta ya cerrada, ReminderContentBuilder no notifica (guard de venta paid/cancelled).
    @Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND status = 'pending'")
    fun observeOpenForTenant(tenantId: String): Flow<List<InstallmentEntity>>

    // A diferencia de observeOpenForTenant, aquí SÍ se une con `sales` (folio + saleId para navegar a
    // S-09), así que filtrar por estado de la venta es gratis: defensa en profundidad.
    @Query(
        "SELECT i.id AS installmentId, s.id AS saleId, s.folio AS folio, " +
            "COALESCE(c.name, '(cliente eliminado)') AS clientName, i.amount AS amount, i.due_date AS dueDate " +
            "FROM installments i JOIN sales s ON s.id = i.fk_sale LEFT JOIN clients c ON c.id = s.fk_client " +
            "WHERE i.fk_tenant = :tenantId AND i.status = 'pending' AND s.status IN ('pending', 'partial') " +
            "ORDER BY i.due_date ASC, i.id ASC"
    )
    fun observeAgendaForTenant(tenantId: String): Flow<List<AgendaRow>>

    @Query("SELECT * FROM installments WHERE fk_sale = :saleId AND fk_tenant = :tenantId ORDER BY due_date ASC")
    suspend fun getForSale(saleId: String, tenantId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun getById(id: String): InstallmentEntity?

    @Upsert
    suspend fun upsertAll(installments: List<InstallmentEntity>)

    @Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND sync_status = 'pending'")
    suspend fun getPending(tenantId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE fk_tenant = :tenantId AND sync_status = 'conflict'")
    suspend fun getConflicted(tenantId: String): List<InstallmentEntity>

    @Query("UPDATE installments SET sync_status = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE installments SET sync_status = 'conflict' WHERE id IN (:ids)")
    suspend fun markConflict(ids: List<String>)
}
