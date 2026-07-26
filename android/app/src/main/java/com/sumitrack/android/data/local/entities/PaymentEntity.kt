package com.sumitrack.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "fk_tenant")
    val fkTenant: String,

    @ColumnInfo(name = "fk_sale")
    val fkSale: String,

    @ColumnInfo(name = "fk_installment")
    val fkInstallment: String?,

    @ColumnInfo(name = "method")
    val method: String,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "paid_at")
    val paidAt: Instant,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending",
)
