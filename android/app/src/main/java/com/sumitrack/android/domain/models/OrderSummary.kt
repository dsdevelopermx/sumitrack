package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class OrderSummary(
    val id: String,
    val folio: String,
    val clientName: String,
    val total: BigDecimal,
    val status: SaleStatus,
    val createdAt: Instant,
    val syncStatus: SyncStatus,
)
