package com.sumitrack.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "fk_tenant")
    val fkTenant: String,

    @ColumnInfo(name = "fk_sale")
    val fkSale: String,

    @ColumnInfo(name = "fk_product")
    val fkProduct: String,

    @ColumnInfo(name = "fk_variant")
    val fkVariant: String?,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "variant_name")
    val variantName: String?,

    @ColumnInfo(name = "quantity")
    val quantity: Int,

    @ColumnInfo(name = "unit_price")
    val unitPrice: BigDecimal,

    @ColumnInfo(name = "tax_rate")
    val taxRate: BigDecimal,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "pending",
)
