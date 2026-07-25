package com.sumitrack.android.domain.models

import java.math.BigDecimal

data class OrderDraftItem(
    val product: Product,
    val variant: ProductVariant?,
    val quantity: Int,
) {
    val subtotal: BigDecimal get() = product.price.multiply(BigDecimal(quantity))
}
