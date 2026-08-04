package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.SaleRepository
import java.math.BigDecimal
import javax.inject.Inject

// Deliberadamente separado de CalculateClientBalanceUseCase: "saldo" (lo que el cliente debe) y
// "crédito a favor" (lo que se le debe al cliente) son conceptos opuestos, mostrados como dos
// banners independientes en S-12 — nunca fusionados en un solo número neto.
class CalculateAvailableCreditUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    suspend operator fun invoke(clientId: String, tenantId: String): BigDecimal =
        saleRepository.getAvailableCredit(clientId, tenantId)
}
