package com.sumitrack.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "fk_tenant")
    val fkTenant: String,

    @ColumnInfo(name = "fk_client")
    val fkClient: String,

    @ColumnInfo(name = "folio")
    val folio: String,

    @ColumnInfo(name = "total")
    val total: BigDecimal,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending",
)
