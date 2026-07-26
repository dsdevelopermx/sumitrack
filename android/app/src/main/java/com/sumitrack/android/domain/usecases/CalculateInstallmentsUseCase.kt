package com.sumitrack.android.domain.usecases

import com.sumitrack.android.domain.models.InstallmentPeriodicity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

data class InstallmentSuggestion(val amount: BigDecimal, val dueDate: Instant)

class CalculateInstallmentsUseCase @Inject constructor() {

    operator fun invoke(
        total: BigDecimal,
        count: Int,
        periodicity: InstallmentPeriodicity,
        startDate: ZonedDateTime = ZonedDateTime.now(),
    ): List<InstallmentSuggestion> {
        require(count in 1..MAX_INSTALLMENTS_HARD_LIMIT) { "count fuera de rango" }

        val baseAmount = total.divide(BigDecimal(count), 2, RoundingMode.HALF_UP)
        val amounts = MutableList(count) { baseAmount }
        // La última parcialidad absorbe la diferencia de redondeo para que la suma sea
        // EXACTAMENTE el total (AC-4 lo exige antes de habilitar "Confirmar Pago").
        val roundingDiff = total.subtract(baseAmount.multiply(BigDecimal(count)))
        amounts[count - 1] = amounts[count - 1].add(roundingDiff)

        return amounts.mapIndexed { index, amount ->
            val dueDate = when (periodicity) {
                InstallmentPeriodicity.WEEKLY -> startDate.plusWeeks((index + 1).toLong())
                InstallmentPeriodicity.BIWEEKLY -> startDate.plusDays((index + 1) * 15L)
                InstallmentPeriodicity.MONTHLY -> startDate.plusMonths((index + 1).toLong())
            }
            InstallmentSuggestion(amount, dueDate.toInstant())
        }
    }

    companion object {
        const val MAX_INSTALLMENTS_HARD_LIMIT = 15
    }
}
