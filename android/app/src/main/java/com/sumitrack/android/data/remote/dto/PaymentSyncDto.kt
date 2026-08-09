package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaymentSyncDto(
    val id: String,
    val fkTenant: String,
    val fkSale: String,
    val fkInstallment: String? = null,
    val method: String,
    val amount: String,
    val paidAt: String,
    val createdAt: String,
    val updatedAt: String,
)
