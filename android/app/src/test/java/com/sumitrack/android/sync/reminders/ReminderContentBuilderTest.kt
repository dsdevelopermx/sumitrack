package com.sumitrack.android.sync.reminders

import com.sumitrack.android.data.local.entities.InstallmentEntity
import com.sumitrack.android.data.local.entities.SaleEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderContentBuilderTest {

    private val zone = ZoneId.of("America/Mexico_City")

    private fun installment(status: String = "pending") = InstallmentEntity(
        id = "i1",
        fkTenant = "t1",
        fkSale = "s1",
        amount = BigDecimal("1250"),
        dueDate = ZonedDateTime.of(2026, 10, 1, 15, 0, 0, 0, zone).toInstant(),
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun sale(status: String = "pending") = SaleEntity(
        id = "s1",
        fkTenant = "t1",
        fkClient = "c1",
        folio = "A1",
        total = BigDecimal("2500"),
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `parcialidad pending con cliente arma titulo con el nombre y texto con monto y fecha`() {
        val content = ReminderContentBuilder.build(installment(), sale(), "Juan Pérez", zone)

        assertNotNull(content)
        assertEquals("Juan Pérez", content!!.title)
        assertTrue(content.text.startsWith("$1250.00 · vence el "))
        assertTrue(content.text.endsWith("2026"))
    }

    @Test
    fun `parcialidad paid no genera notificacion`() {
        assertNull(ReminderContentBuilder.build(installment("paid"), sale(), "Juan Pérez", zone))
    }

    @Test
    fun `venta cancelled no genera notificacion`() {
        assertNull(ReminderContentBuilder.build(installment(), sale("cancelled"), "Juan Pérez", zone))
    }

    @Test
    fun `venta paid no genera notificacion`() {
        assertNull(ReminderContentBuilder.build(installment(), sale("paid"), "Juan Pérez", zone))
    }

    @Test
    fun `parcialidad inexistente no genera notificacion`() {
        assertNull(ReminderContentBuilder.build(null, sale(), "Juan Pérez", zone))
    }

    @Test
    fun `cliente ausente usa el titulo generico sin perder el recordatorio`() {
        val content = ReminderContentBuilder.build(installment(), sale(), null, zone)

        assertEquals("Cobro próximo a vencer", content!!.title)
    }

    @Test
    fun `venta ausente todavia notifica con titulo generico`() {
        val content = ReminderContentBuilder.build(installment(), null, null, zone)

        assertEquals("Cobro próximo a vencer", content!!.title)
    }
}
