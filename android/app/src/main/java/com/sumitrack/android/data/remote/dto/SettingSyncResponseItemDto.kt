package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SettingSyncResponseItemDto(
    val key: String,
    val success: Boolean,
    val error: String? = null,
)
