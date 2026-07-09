package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.ClientEntity
import kotlinx.coroutines.flow.Flow

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

    @Upsert
    suspend fun upsertAll(clients: List<ClientEntity>)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?
}
