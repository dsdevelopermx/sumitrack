package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SaleItemSyncDto(
    val id: String,
    val fkTenant: String,
    val fkSale: String,
    val fkProduct: String,
    val fkVariant: String? = null,
    val productName: String,
    val variantName: String? = null,
    val quantity: Int,
    val unitPrice: String,
    val taxRate: String,
    val createdAt: String,
    val updatedAt: String,
)
