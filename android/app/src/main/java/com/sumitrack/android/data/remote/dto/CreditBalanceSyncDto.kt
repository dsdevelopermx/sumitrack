package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreditBalanceSyncDto(
    val id: String,
    val fkTenant: String,
    val fkClient: String,
    val amount: String,
    val origin: String,
    val fkOriginSale: String? = null,
    val appliedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
