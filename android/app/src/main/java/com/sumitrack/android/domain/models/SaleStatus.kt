package com.sumitrack.android.domain.models

enum class SaleStatus {
    PENDING,
    PARTIAL,
    PAID,
    CANCELLED;

    companion object {
        fun fromString(value: String): SaleStatus = when (value.lowercase()) {
            "partial" -> PARTIAL
            "paid" -> PAID
            "cancelled" -> CANCELLED
            else -> PENDING
        }
    }
}
