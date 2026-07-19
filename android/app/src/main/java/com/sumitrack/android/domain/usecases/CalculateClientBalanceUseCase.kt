package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.SaleRepository
import java.math.BigDecimal
import javax.inject.Inject

class CalculateClientBalanceUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    suspend operator fun invoke(clientId: String, tenantId: String): BigDecimal =
        saleRepository.getOpenSalesForClient(clientId, tenantId).fold(BigDecimal.ZERO) { acc, sale -> acc + sale.total }
}
