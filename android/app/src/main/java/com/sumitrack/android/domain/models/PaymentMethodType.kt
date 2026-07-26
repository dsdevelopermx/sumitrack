package com.sumitrack.android.domain.models

enum class PaymentMethodType {
    EFECTIVO,
    TRANSFERENCIA,
    TARJETA;

    companion object {
        fun fromString(value: String): PaymentMethodType = when (value.lowercase()) {
            "transferencia" -> TRANSFERENCIA
            "tarjeta" -> TARJETA
            else -> EFECTIVO
        }
    }
}
