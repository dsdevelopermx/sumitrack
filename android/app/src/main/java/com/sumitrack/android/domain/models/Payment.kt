package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class Payment(
    val id: String,
    val fkTenant: String,
    val fkSale: String,
    val fkInstallment: String?,
    val method: PaymentMethodType,
    val amount: BigDecimal,
    val paidAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
