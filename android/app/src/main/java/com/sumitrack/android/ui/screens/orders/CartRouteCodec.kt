package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.domain.models.OrderDraftItem

// Codifica el carrito como string compacto para reenviarlo por argumentos de ruta de
// S-04 (ItemListScreen) a S-06 (OrderSummaryScreen) a S-07 (PaymentScreen), evitando introducir
// el primer ViewModel compartido entre pantallas del proyecto (ver Dev Notes de Historia 3.3).
// Cada pantalla resuelve los Product/ProductVariant completos vía ProductRepository.
object CartRouteCodec {

    fun encode(cart: List<OrderDraftItem>): String =
        cart.joinToString("|") { "${it.product.id},${it.variant?.id.orEmpty()},${it.quantity}" }

    fun decode(encoded: String): List<Triple<String, String?, Int>> =
        if (encoded.isBlank()) {
            emptyList()
        } else {
            encoded.split("|").map { part ->
                val fields = part.split(",")
                Triple(fields[0], fields[1].ifBlank { null }, fields[2].toInt())
            }
        }
}
