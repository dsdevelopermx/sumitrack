package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sumitrack.android.data.local.entities.ConflictLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictLogDao {

    @Insert
    suspend fun insert(entry: ConflictLogEntity)

    @Query("SELECT * FROM conflict_log WHERE id = :id")
    suspend fun getById(id: String): ConflictLogEntity?

    // Usado por S-15 al abrirse desde el ícono de conflicto de una card: la card solo conoce
    // entityType+recordId (no el id propio del log entry), así que se resuelve aquí la entrada NO
    // resuelta más reciente para ese registro. ORDER BY + LIMIT 1 en vez de asumir unicidad —
    // en teoría un registro podría acumular más de un conflicto detectado si se reintenta el push
    // varias veces sin resolver el anterior.
    @Query(
        "SELECT * FROM conflict_log WHERE entity_type = :entityType AND record_id = :recordId " +
            "AND resolved_at IS NULL ORDER BY detected_at DESC LIMIT 1"
    )
    suspend fun getLatestUnresolvedFor(entityType: String, recordId: String): ConflictLogEntity?

    @Query("SELECT * FROM conflict_log WHERE fk_tenant = :tenantId AND resolved_at IS NULL")
    fun getUnresolvedAsFlow(tenantId: String): Flow<List<ConflictLogEntity>>

    @Query("SELECT * FROM conflict_log WHERE fk_tenant = :tenantId AND resolved_at IS NOT NULL ORDER BY resolved_at DESC")
    fun getResolvedAsFlow(tenantId: String): Flow<List<ConflictLogEntity>>

    @Update
    suspend fun update(entry: ConflictLogEntity)
}
