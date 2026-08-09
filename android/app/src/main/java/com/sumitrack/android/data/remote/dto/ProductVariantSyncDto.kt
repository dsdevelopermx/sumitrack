package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductVariantSyncDto(
    val id: String,
    val fkTenant: String,
    val fkProduct: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
)
