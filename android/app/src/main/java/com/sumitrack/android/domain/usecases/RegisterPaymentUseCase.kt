package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.InstallmentStatus
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.SaleStatus
import java.time.Instant
import javax.inject.Inject

class RegisterPaymentUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    suspend operator fun invoke(
        tenantId: String,
        saleId: String,
        installmentId: String?,
        method: PaymentMethodType,
        now: Instant = Instant.now(),
    ): Boolean {
        val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return false
        if (detail.sale.status == SaleStatus.CANCELLED || detail.sale.status == SaleStatus.PAID) return false

        val amount = if (installmentId == null) {
            if (detail.installments.isNotEmpty()) return false
            detail.sale.total
        } else {
            val installment = detail.installments.find { it.id == installmentId } ?: return false
            if (installment.status == InstallmentStatus.PAID) return false
            installment.amount
        }

        saleRepository.registerPayment(tenantId, saleId, installmentId, method, amount, now)
        return true
    }
}
