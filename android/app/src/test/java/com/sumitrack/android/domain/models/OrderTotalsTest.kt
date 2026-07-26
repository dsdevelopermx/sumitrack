package com.sumitrack.android.domain.models

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderTotalsTest {

    private fun product(price: BigDecimal, taxRate: BigDecimal) = Product(
        id = "p1",
        fkTenant = "tenant-1",
        name = "Refresco",
        price = price,
        taxRate = taxRate,
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = SyncStatus.SYNCED,
    )

    @Test
    fun `totals are rounded to 2 decimals even when tax math produces more precision`() {
        // 33.33 * 15% = 4.9995 -> sin redondear, remaining==0 sería imposible de satisfacer con
        // un monto de 2 decimales ingresado por el usuario.
        val items = listOf(OrderDraftItem(product(BigDecimal("33.33"), BigDecimal("15")), null, 1))

        val totals = calculateOrderTotals(items)

        assertEquals(BigDecimal("33.33"), totals.subtotal)
        assertEquals(BigDecimal("5.00"), totals.tax)
        assertEquals(BigDecimal("38.33"), totals.total)
    }

    @Test
    fun `total equals subtotal plus tax across multiple items`() {
        val items = listOf(
            OrderDraftItem(product(BigDecimal("100.00"), BigDecimal("16")), null, 1),
            OrderDraftItem(product(BigDecimal("50.00"), BigDecimal.ZERO), null, 2),
        )

        val totals = calculateOrderTotals(items)

        assertEquals(BigDecimal("200.00"), totals.subtotal)
        assertEquals(BigDecimal("16.00"), totals.tax)
        assertEquals(BigDecimal("216.00"), totals.total)
    }

    @Test
    fun `empty item list yields zero totals`() {
        val totals = calculateOrderTotals(emptyList())

        assertEquals(0, BigDecimal.ZERO.compareTo(totals.subtotal))
        assertEquals(0, BigDecimal.ZERO.compareTo(totals.tax))
        assertEquals(0, BigDecimal.ZERO.compareTo(totals.total))
    }
}
