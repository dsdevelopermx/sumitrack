package com.sumitrack.android.sync

import com.sumitrack.android.data.local.dao.SyncMetadataDao
import com.sumitrack.android.data.local.entities.SyncMetadataEntity
import java.time.Instant

class FakeSyncMetadataDao : SyncMetadataDao {

    private val rows = mutableMapOf<String, Instant>()

    override suspend fun getLastSyncAt(entity: String): Instant? = rows[entity]

    override suspend fun setLastSyncAt(row: SyncMetadataEntity) {
        rows[row.entity] = row.lastSyncAt
    }
}
