package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SaleSyncDto(
    val id: String,
    val fkTenant: String,
    val fkClient: String,
    val folio: String,
    val total: String,
    val subtotal: String,
    val tax: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)
