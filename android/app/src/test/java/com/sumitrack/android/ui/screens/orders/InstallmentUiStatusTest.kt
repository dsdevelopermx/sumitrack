package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.domain.models.Installment
import com.sumitrack.android.domain.models.InstallmentStatus
import com.sumitrack.android.domain.models.SyncStatus
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallmentUiStatusTest {

    private fun installment(status: InstallmentStatus, dueDate: Instant) = Installment(
        id = "i1", fkTenant = "tenant-1", fkSale = "s1", amount = BigDecimal("10.00"), dueDate = dueDate,
        status = status, createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = SyncStatus.PENDING,
    )

    private val now = Instant.parse("2026-08-15T00:00:00Z")

    @Test
    fun `paid installment is PAID regardless of due date`() {
        val installment = installment(InstallmentStatus.PAID, now.minusSeconds(86_400))
        assertEquals(InstallmentUiStatus.PAID, installment.toUiStatus(now))
    }

    @Test
    fun `pending installment with a future due date is PENDING`() {
        val installment = installment(InstallmentStatus.PENDING, now.plusSeconds(86_400))
        assertEquals(InstallmentUiStatus.PENDING, installment.toUiStatus(now))
    }

    @Test
    fun `pending installment with a past due date is OVERDUE`() {
        val installment = installment(InstallmentStatus.PENDING, now.minusSeconds(86_400))
        assertEquals(InstallmentUiStatus.OVERDUE, installment.toUiStatus(now))
    }

    @Test
    fun `pending installment due exactly now is PENDING, not OVERDUE`() {
        val installment = installment(InstallmentStatus.PENDING, now)
        assertEquals(InstallmentUiStatus.PENDING, installment.toUiStatus(now))
    }
}
