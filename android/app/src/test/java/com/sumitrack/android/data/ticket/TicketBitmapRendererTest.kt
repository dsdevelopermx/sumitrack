package com.sumitrack.android.data.ticket

import com.sumitrack.android.domain.models.Installment
import com.sumitrack.android.domain.models.InstallmentStatus
import com.sumitrack.android.domain.models.SyncStatus
import com.sumitrack.android.domain.models.TicketData
import com.sumitrack.android.domain.models.TicketFiscalData
import com.sumitrack.android.domain.models.TicketLineItem
import com.sumitrack.android.domain.models.TicketPaymentCondition
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketBitmapRendererTest {

    private fun ticket(
        fiscal: TicketFiscalData = TicketFiscalData("", "", "", ""),
        paymentCondition: TicketPaymentCondition = TicketPaymentCondition.SinglePayment(Instant.now()),
    ) = TicketData(
        fiscal = fiscal,
        clientName = "Ana López",
        folio = "A1",
        createdAt = Instant.now(),
        lineItems = listOf(TicketLineItem("Refresco", 2, BigDecimal("10.00"), BigDecimal("20.00"))),
        subtotal = BigDecimal("20.00"),
        tax = BigDecimal("0.00"),
        total = BigDecimal("20.00"),
        paymentCondition = paymentCondition,
    )

    @Test
    fun `blank fiscal data shows the placeholder line`() {
        val lines = buildTicketLines(ticket())
        assertEquals("(Sin datos fiscales configurados)", lines.first())
    }

    @Test
    fun `fiscal data with values renders name, rfc, address and phone in order`() {
        val fiscal = TicketFiscalData("Ferretería El Clavo", "XAXX010101000", "Calle 1", "555-0000")
        val lines = buildTicketLines(ticket(fiscal = fiscal))

        assertEquals("Ferretería El Clavo", lines[0])
        assertEquals("RFC: XAXX010101000", lines[1])
        assertEquals("Calle 1", lines[2])
        assertEquals("Tel: 555-0000", lines[3])
    }

    @Test
    fun `folio and client name lines are present`() {
        val lines = buildTicketLines(ticket())
        assertTrue(lines.contains("Folio: A1"))
        assertTrue(lines.contains("Cliente: Ana López"))
    }

    @Test
    fun `line items render description quantity and amounts`() {
        val lines = buildTicketLines(ticket())
        assertTrue(lines.contains("Refresco x2"))
        assertTrue(lines.contains("  \$10.00 c/u = \$20.00"))
    }

    @Test
    fun `totals section shows subtotal tax and total`() {
        val lines = buildTicketLines(ticket())
        assertTrue(lines.contains("Subtotal: \$20.00"))
        assertTrue(lines.contains("Impuestos: \$0.00"))
        assertTrue(lines.contains("Total: \$20.00"))
    }

    @Test
    fun `SinglePayment condition renders Pago de contado`() {
        val lines = buildTicketLines(ticket(paymentCondition = TicketPaymentCondition.SinglePayment(Instant.now())))
        assertTrue(lines.contains("Pago de contado"))
    }

    @Test
    fun `InstallmentPlan condition renders one line per installment with date and amount`() {
        val installment = Installment(
            id = "i1", fkTenant = "tenant-1", fkSale = "s1", amount = BigDecimal("10.00"),
            dueDate = Instant.parse("2026-08-15T00:00:00Z"), status = InstallmentStatus.PENDING,
            createdAt = Instant.now(), updatedAt = Instant.now(), syncStatus = SyncStatus.PENDING,
        )
        val lines = buildTicketLines(ticket(paymentCondition = TicketPaymentCondition.InstallmentPlan(listOf(installment))))

        assertTrue(lines.contains("Parcialidades:"))
        assertTrue(lines.any { it.contains("\$10.00") && it.contains("/") })
    }
}
