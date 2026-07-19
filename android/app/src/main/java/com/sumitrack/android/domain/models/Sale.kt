package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class Sale(
    val id: String,
    val fkTenant: String,
    val fkClient: String,
    val folio: String,
    val total: BigDecimal,
    val status: SaleStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
