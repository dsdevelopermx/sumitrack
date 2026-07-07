package com.sumitrack.android.domain.usecases

import java.math.BigDecimal
import javax.inject.Inject

class CalculateClientBalanceUseCase @Inject constructor() {
    // TODO Historia 3.x: inyectar SaleRepository y sumar ventas en estado PENDING/PARTIAL
    operator fun invoke(clientId: String): BigDecimal = BigDecimal.ZERO
}
