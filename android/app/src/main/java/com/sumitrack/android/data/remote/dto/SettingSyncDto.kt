package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SettingSyncDto(
    val key: String,
    val value: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
