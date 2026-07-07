package com.sumitrack.android.domain.models

enum class SyncStatus {
    SYNCED,
    PENDING,
    CONFLICT;

    companion object {
        fun fromString(value: String): SyncStatus = when (value.lowercase()) {
            "synced" -> SYNCED
            "conflict" -> CONFLICT
            else -> PENDING
        }
    }
}
