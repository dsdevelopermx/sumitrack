package com.sumitrack.android.domain.models

enum class PaymentMethodType {
    EFECTIVO,
    TRANSFERENCIA,
    TARJETA,
    CREDITO_A_FAVOR;

    companion object {
        fun fromString(value: String): PaymentMethodType = when (value.lowercase()) {
            "transferencia" -> TRANSFERENCIA
            "tarjeta" -> TARJETA
            "credito_a_favor" -> CREDITO_A_FAVOR
            else -> EFECTIVO
        }
    }
}
