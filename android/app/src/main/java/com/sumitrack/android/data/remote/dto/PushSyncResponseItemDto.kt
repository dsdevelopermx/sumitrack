package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PushSyncResponseItemDto(
    val id: String,
    val success: Boolean,
    val error: String? = null,
)
