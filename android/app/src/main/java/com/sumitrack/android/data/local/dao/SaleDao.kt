package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.SaleEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class OrderSummaryRow(
    val id: String,
    val folio: String,
    val total: BigDecimal,
    val status: String,
    val clientName: String,
    val createdAt: Instant,
    val syncStatus: String,
)

@Dao
interface SaleDao {

    @Query(
        "SELECT * FROM sales WHERE fk_client = :clientId AND fk_tenant = :tenantId " +
            "AND status IN ('pending', 'partial') ORDER BY created_at DESC"
    )
    suspend fun getOpenSalesForClient(clientId: String, tenantId: String): List<SaleEntity>

    @Query(
        "SELECT s.id AS id, s.folio AS folio, s.total AS total, s.status AS status, " +
            "COALESCE(c.name, '(cliente eliminado)') AS clientName, s.created_at AS createdAt, s.sync_status AS syncStatus " +
            "FROM sales s LEFT JOIN clients c ON c.id = s.fk_client " +
            "WHERE s.fk_tenant = :tenantId " +
            "AND (:statusFilter IS NULL OR s.status = :statusFilter) " +
            "AND (:normalizedQuery = '' OR LOWER(s.folio) LIKE '%' || :normalizedQuery || '%' ESCAPE '\\' " +
            "OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(c.name)," +
            "'á','a'),'Á','a'),'é','e'),'É','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ñ','n'),'Ñ','n') " +
            "LIKE '%' || :normalizedQuery || '%' ESCAPE '\\') " +
            "ORDER BY s.created_at DESC, s.id DESC"
    )
    fun getOrdersForTenantAsFlow(
        tenantId: String,
        statusFilter: String?,
        normalizedQuery: String,
    ): Flow<List<OrderSummaryRow>>

    @Query("SELECT COUNT(*) FROM sales WHERE fk_tenant = :tenantId")
    suspend fun countSalesForTenant(tenantId: String): Int

    @Upsert
    suspend fun upsertAll(sales: List<SaleEntity>)
}
