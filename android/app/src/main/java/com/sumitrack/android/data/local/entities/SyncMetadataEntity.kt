package com.sumitrack.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// Tabla dedicada al estado interno del motor de sync (Historia 4.2) — deliberadamente separada de
// `settings` para que estos valores nunca se marquen `pending` ni se suban al servidor junto con
// los settings de negocio del tenant.
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val entity: String,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Instant,
)
