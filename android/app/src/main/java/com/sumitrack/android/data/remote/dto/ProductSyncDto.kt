package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductSyncDto(
    val id: String,
    val fkTenant: String,
    val name: String,
    val price: String,
    val taxRate: String,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
