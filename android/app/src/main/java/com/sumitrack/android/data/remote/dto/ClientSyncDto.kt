package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClientSyncDto(
    val id: String,
    val fkTenant: String,
    val name: String,
    val phone: String,
    val rfc: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
