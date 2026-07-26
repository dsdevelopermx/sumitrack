package com.sumitrack.android.domain.models

enum class InstallmentStatus {
    PENDING,
    PAID;

    companion object {
        fun fromString(value: String): InstallmentStatus = when (value.lowercase()) {
            "paid" -> PAID
            else -> PENDING
        }
    }
}
