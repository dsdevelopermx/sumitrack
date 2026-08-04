package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.SaleStatus
import java.math.BigDecimal
import javax.inject.Inject

// El nombre sigue el AC-3 de Historia 3.7 al pie de la letra: se ejecuta al CANCELAR una venta con
// cobros (Opción B) para OTORGAR Crédito a Favor — no consume crédito en una venta nueva (eso lo
// resuelve SaleRepository.createSale cuando el método de pago es CREDITO_A_FAVOR).
class ApplyCreditBalanceUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    // Mismo guard que CancelSaleUseCase (Review Finding: faltaba aquí) — sin él, una segunda
    // invocación sobre una venta ya cancelada otorgaría Crédito a Favor duplicado por el mismo
    // monto, ya que cancelSale no valida por sí mismo el estado previo de la venta.
    suspend operator fun invoke(tenantId: String, saleId: String, amount: BigDecimal): Boolean {
        val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return false
        if (detail.sale.status == SaleStatus.CANCELLED) return false
        return saleRepository.cancelSale(tenantId, saleId, creditAmount = amount)
    }
}
