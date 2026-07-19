package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class Product(
    val id: String,
    val fkTenant: String,
    val name: String,
    val price: BigDecimal,
    val taxRate: BigDecimal,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
