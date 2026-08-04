package com.sumitrack.android.domain.models

enum class InstallmentStatus {
    PENDING,
    PAID,
    CANCELLED;

    companion object {
        fun fromString(value: String): InstallmentStatus = when (value.lowercase()) {
            "paid" -> PAID
            "cancelled" -> CANCELLED
            else -> PENDING
        }
    }
}
