package com.sumitrack.android.domain.models

import java.time.Instant

data class ProductVariant(
    val id: String,
    val fkTenant: String,
    val fkProduct: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
)
