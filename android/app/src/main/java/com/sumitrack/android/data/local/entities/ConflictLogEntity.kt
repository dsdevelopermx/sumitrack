package com.sumitrack.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// Historia 4.4 — un registro por conflicto detectado en el push. `id` es propio del log entry
// (no el id del registro real en conflicto, que vive en `recordId`). Los snapshots se guardan como
// JSON crudo porque cada `entityType` tiene una forma distinta (9 tipos) — evita 9 esquemas de
// columnas separados solo para auditoría/visualización en S-15/S-14.
@Entity(tableName = "conflict_log")
data class ConflictLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "fk_tenant") val fkTenant: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "local_snapshot") val localSnapshotJson: String,
    @ColumnInfo(name = "server_snapshot") val serverSnapshotJson: String,
    @ColumnInfo(name = "detected_at") val detectedAt: Instant,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Instant? = null,
    val resolution: String? = null,
    @ColumnInfo(name = "duplicate_record_id") val duplicateRecordId: String? = null,
)
