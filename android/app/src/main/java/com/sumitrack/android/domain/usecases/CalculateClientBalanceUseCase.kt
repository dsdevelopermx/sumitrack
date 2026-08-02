package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.SaleRepository
import java.math.BigDecimal
import javax.inject.Inject

class CalculateClientBalanceUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    // Historia 3.6: una venta "partial" ya puede tener cobros parciales registrados
    // (RegisterPaymentUseCase) — el saldo debe reflejar total - ya cobrado, no el total completo.
    suspend operator fun invoke(clientId: String, tenantId: String): BigDecimal =
        saleRepository.getOpenSalesForClient(clientId, tenantId).fold(BigDecimal.ZERO) { acc, sale ->
            val paid = saleRepository.getPaymentsForSale(sale.id, tenantId).fold(BigDecimal.ZERO) { sum, payment -> sum + payment.amount }
            // coerceAtLeast(ZERO): un sobrepago no debe restar del saldo agregado del resto de
            // las ventas abiertas del cliente (Review Finding).
            acc + (sale.total - paid).coerceAtLeast(BigDecimal.ZERO)
        }
}
