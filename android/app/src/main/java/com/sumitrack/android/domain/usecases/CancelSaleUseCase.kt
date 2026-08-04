package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.SaleStatus
import java.math.BigDecimal
import javax.inject.Inject

class CancelSaleUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    // generateCredit solo importa cuando la venta tiene cobros (Opción A/B); si no tiene cobros
    // se ignora silenciosamente para evitar crear una CreditBalanceEntity de $0.00 sin sentido.
    // Permite cancelar cualquier estado != CANCELLED (incluyendo PAID) — FR-16: "independientemente
    // de su Estatus". El AC-2 menciona "estado Parcial" como caso ilustrativo, pero el criterio real
    // que decide si hay cobros que resolver es si la venta TIENE pagos, no su status literal.
    suspend operator fun invoke(tenantId: String, saleId: String, generateCredit: Boolean): Boolean {
        val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return false
        if (detail.sale.status == SaleStatus.CANCELLED) return false
        val totalCollected = detail.payments.fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
        val creditAmount = if (generateCredit && totalCollected > BigDecimal.ZERO) totalCollected else null
        return saleRepository.cancelSale(tenantId, saleId, creditAmount)
    }
}
