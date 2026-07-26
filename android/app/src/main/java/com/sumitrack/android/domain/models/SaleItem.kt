package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class SaleItem(
    val id: String,
    val fkTenant: String,
    val fkSale: String,
    val fkProduct: String,
    val fkVariant: String?,
    val productName: String,
    val variantName: String?,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
) {
    val subtotal: BigDecimal get() = unitPrice.multiply(BigDecimal(quantity))
    val tax: BigDecimal get() = subtotal.multiply(taxRate).divide(BigDecimal(100))
}
