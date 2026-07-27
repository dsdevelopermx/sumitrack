package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant

data class TicketFiscalData(
    val businessName: String,
    val rfc: String,
    val address: String,
    val phone: String,
)

sealed class TicketPaymentCondition {
    data class SinglePayment(val paidAt: Instant) : TicketPaymentCondition()
    data class InstallmentPlan(val installments: List<Installment>) : TicketPaymentCondition()
}

data class TicketLineItem(
    val description: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal,
)

data class TicketData(
    val fiscal: TicketFiscalData,
    val clientName: String,
    val folio: String,
    val createdAt: Instant,
    val lineItems: List<TicketLineItem>,
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
    val paymentCondition: TicketPaymentCondition,
)
