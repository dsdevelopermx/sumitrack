package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class InstallmentSyncDto(
    val id: String,
    val fkTenant: String,
    val fkSale: String,
    val amount: String,
    val dueDate: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)
