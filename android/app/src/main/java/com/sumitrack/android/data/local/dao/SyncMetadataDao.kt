package com.sumitrack.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sumitrack.android.data.local.entities.SyncMetadataEntity
import java.time.Instant

@Dao
interface SyncMetadataDao {

    @Query("SELECT last_sync_at FROM sync_metadata WHERE entity = :entity LIMIT 1")
    suspend fun getLastSyncAt(entity: String): Instant?

    @Upsert
    suspend fun setLastSyncAt(row: SyncMetadataEntity)
}
