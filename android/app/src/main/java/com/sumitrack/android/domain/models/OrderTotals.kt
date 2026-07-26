package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.math.RoundingMode

data class OrderTotals(val subtotal: BigDecimal, val tax: BigDecimal, val total: BigDecimal)

// Único lugar donde se calcula subtotal/impuestos/total a partir de un carrito — antes duplicado
// de forma idéntica en SaleRepository.createSale, OrderSummaryUiState y PaymentUiState, con riesgo
// de que uno redondeara distinto a los otros. Redondear aquí a 2 decimales es lo que garantiza que
// comparaciones exactas como "remaining == 0" (que habilitan "Confirmar Pago") sean satisfacibles:
// sin este redondeo, una tasa de impuesto que produce más de 2 decimales podía dejar el total real
// con precisión que ningún monto de 2 decimales ingresado por el usuario podía igualar exactamente.
fun calculateOrderTotals(items: List<OrderDraftItem>): OrderTotals {
    val subtotal = items.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
        .setScale(2, RoundingMode.HALF_UP)
    val tax = items.fold(BigDecimal.ZERO) { acc, item ->
        acc + item.subtotal.multiply(item.product.taxRate).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }.setScale(2, RoundingMode.HALF_UP)
    return OrderTotals(subtotal, tax, subtotal + tax)
}
