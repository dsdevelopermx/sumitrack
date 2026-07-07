package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class Client(
    val id: String,
    val fkTenant: String,
    val name: String,
    val phone: String,
    val rfc: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val balance: BigDecimal = BigDecimal.ZERO,
)
