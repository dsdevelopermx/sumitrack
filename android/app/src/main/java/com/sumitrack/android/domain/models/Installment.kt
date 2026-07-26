package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class Installment(
    val id: String,
    val fkTenant: String,
    val fkSale: String,
    val amount: BigDecimal,
    val dueDate: Instant,
    val status: InstallmentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
