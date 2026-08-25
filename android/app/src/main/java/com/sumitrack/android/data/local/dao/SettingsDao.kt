package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Upsert
    suspend fun upsertAll(settings: List<SettingsEntity>)

    @Query("SELECT * FROM settings")
    fun getAll(): Flow<List<SettingsEntity>>

    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM settings WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): SettingsEntity?

    @Query("DELETE FROM settings")
    suspend fun deleteAll()

    @Query("SELECT * FROM settings WHERE sync_status = 'pending'")
    suspend fun getPending(): List<SettingsEntity>

    @Query("SELECT * FROM settings WHERE sync_status = 'conflict'")
    suspend fun getConflicted(): List<SettingsEntity>

    @Query("UPDATE settings SET sync_status = 'synced' WHERE key IN (:keys)")
    suspend fun markSynced(keys: List<String>)

    @Query("UPDATE settings SET sync_status = 'conflict' WHERE key IN (:keys)")
    suspend fun markConflict(keys: List<String>)
}
