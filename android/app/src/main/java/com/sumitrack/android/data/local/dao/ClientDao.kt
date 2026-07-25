package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.ClientEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

data class ClientSearchRow(
    val id: String,
    val name: String,
    val phone: String,
    val balance: BigDecimal,
)

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllAsFlow(): Flow<List<ClientEntity>>

    @Query(
        "SELECT * FROM clients WHERE " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(name)," +
            "'á','a'),'Á','a'),'é','e'),'É','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ñ','n'),'Ñ','n') " +
            "LIKE '%' || :normalizedQuery || '%' ESCAPE '\\' " +
            "ORDER BY name ASC"
    )
    fun searchByNameAsFlow(normalizedQuery: String): Flow<List<ClientEntity>>

    @Query(
        "SELECT c.id AS id, c.name AS name, c.phone AS phone, " +
            "COALESCE(SUM(s.total), 0) AS balance " +
            "FROM clients c " +
            "LEFT JOIN sales s ON s.fk_client = c.id AND s.fk_tenant = :tenantId AND s.status IN ('pending', 'partial') " +
            "WHERE c.fk_tenant = :tenantId " +
            "AND (:normalizedQuery = '' OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(c.name)," +
            "'á','a'),'Á','a'),'é','e'),'É','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ñ','n'),'Ñ','n') " +
            "LIKE '%' || :normalizedQuery || '%' ESCAPE '\\') " +
            "GROUP BY c.id " +
            "ORDER BY c.name ASC"
    )
    fun searchWithBalanceAsFlow(tenantId: String, normalizedQuery: String): Flow<List<ClientSearchRow>>

    @Upsert
    suspend fun upsertAll(clients: List<ClientEntity>)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?
}
