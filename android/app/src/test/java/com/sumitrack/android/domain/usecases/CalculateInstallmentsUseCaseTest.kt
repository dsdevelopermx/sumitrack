package com.sumitrack.android.domain.usecases

import com.sumitrack.android.domain.models.InstallmentPeriodicity
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateInstallmentsUseCaseTest {

    private lateinit var useCase: CalculateInstallmentsUseCase
    private val startDate: ZonedDateTime = ZonedDateTime.parse("2026-01-01T00:00:00-06:00[America/Mexico_City]")

    @Before
    fun setUp() {
        useCase = CalculateInstallmentsUseCase()
    }

    @Test
    fun `sum of suggested amounts equals exactly the total, including rounding adjustment`() {
        val result = useCase(BigDecimal("100.00"), 3, InstallmentPeriodicity.MONTHLY, startDate)

        val sum = result.fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }
        assertEquals(BigDecimal("100.00"), sum)
    }

    @Test
    fun `weekly periodicity suggests dates one week apart`() {
        val result = useCase(BigDecimal("300.00"), 3, InstallmentPeriodicity.WEEKLY, startDate)

        assertEquals(startDate.plusWeeks(1).toInstant(), result[0].dueDate)
        assertEquals(startDate.plusWeeks(2).toInstant(), result[1].dueDate)
        assertEquals(startDate.plusWeeks(3).toInstant(), result[2].dueDate)
    }

    @Test
    fun `biweekly periodicity suggests dates 15 days apart`() {
        val result = useCase(BigDecimal("300.00"), 3, InstallmentPeriodicity.BIWEEKLY, startDate)

        assertEquals(startDate.plusDays(15).toInstant(), result[0].dueDate)
        assertEquals(startDate.plusDays(30).toInstant(), result[1].dueDate)
        assertEquals(startDate.plusDays(45).toInstant(), result[2].dueDate)
    }

    @Test
    fun `monthly periodicity suggests dates one month apart`() {
        val result = useCase(BigDecimal("300.00"), 3, InstallmentPeriodicity.MONTHLY, startDate)

        assertEquals(startDate.plusMonths(1).toInstant(), result[0].dueDate)
        assertEquals(startDate.plusMonths(2).toInstant(), result[1].dueDate)
        assertEquals(startDate.plusMonths(3).toInstant(), result[2].dueDate)
    }

    @Test
    fun `count of zero throws`() {
        assertThrowsIllegalArgument { useCase(BigDecimal("100.00"), 0, InstallmentPeriodicity.MONTHLY, startDate) }
    }

    @Test
    fun `count above the hard limit of 15 throws`() {
        assertThrowsIllegalArgument { useCase(BigDecimal("100.00"), 16, InstallmentPeriodicity.MONTHLY, startDate) }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
