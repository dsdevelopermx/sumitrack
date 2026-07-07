package com.sumitrack.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SettingDto(
    val key: String,
    val value: String? = null,
)
