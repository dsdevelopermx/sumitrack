package com.sumitrack.android.sync.reminders

import com.sumitrack.android.data.local.entities.InstallmentEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

    private val zone = ZoneId.of("America/Mexico_City")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

    private fun installment(id: String, dueDate: Instant, status: String = "pending") = InstallmentEntity(
        id = id,
        fkTenant = "t1",
        fkSale = "s1",
        amount = BigDecimal("100.00"),
        dueDate = dueDate,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `dueDate a 10 dias con dias=3 dispara el dia 7 a las 09_00 hora local`() {
        val plans = ReminderPlanner.plan(
            installments = listOf(installment("i1", at(2026, 10, 1, 15))),
            diasAnticipacion = 3,
            now = at(2026, 9, 21, 8),
            zone = zone,
        )

        assertEquals(listOf(ReminderPlan("i1", at(2026, 9, 28, 9))), plans)
    }

    @Test
    fun `si el momento del recordatorio ya paso no se programa nada`() {
        val plans = ReminderPlanner.plan(
            installments = listOf(installment("i1", at(2026, 9, 22, 15))),
            diasAnticipacion = 3,
            now = at(2026, 9, 21, 8),
            zone = zone,
        )

        assertTrue(plans.isEmpty())
    }

    @Test
    fun `trigger hoy a las 09_00 se incluye a las 08_59 y se excluye a las 09_00 exactas`() {
        val due = installment("i1", at(2026, 9, 24, 12))

        val before = ReminderPlanner.plan(listOf(due), 3, at(2026, 9, 21, 8, 59), zone)
        val exact = ReminderPlanner.plan(listOf(due), 3, at(2026, 9, 21, 9, 0), zone)

        assertEquals(listOf(ReminderPlan("i1", at(2026, 9, 21, 9))), before)
        assertTrue(exact.isEmpty())
    }

    @Test
    fun `parcialidades paid o cancelled no se programan`() {
        val plans = ReminderPlanner.plan(
            installments = listOf(
                installment("paid", at(2026, 10, 1, 15), status = "paid"),
                installment("cancelled", at(2026, 10, 1, 15), status = "cancelled"),
            ),
            diasAnticipacion = 3,
            now = at(2026, 9, 21, 8),
            zone = zone,
        )

        assertTrue(plans.isEmpty())
    }

    @Test
    fun `bordes del rango dias=1 y dias=30`() {
        val one = ReminderPlanner.plan(listOf(installment("i1", at(2026, 9, 25, 15))), 1, at(2026, 9, 21, 8), zone)
        val thirty = ReminderPlanner.plan(listOf(installment("i2", at(2026, 11, 30, 15))), 30, at(2026, 9, 21, 8), zone)

        assertEquals(listOf(ReminderPlan("i1", at(2026, 9, 24, 9))), one)
        assertEquals(listOf(ReminderPlan("i2", at(2026, 10, 31, 9))), thirty)
    }

    @Test
    fun `varias parcialidades generan un plan por cada una`() {
        val plans = ReminderPlanner.plan(
            installments = listOf(
                installment("i1", at(2026, 10, 1, 15)),
                installment("i2", at(2026, 10, 15, 15)),
            ),
            diasAnticipacion = 3,
            now = at(2026, 9, 21, 8),
            zone = zone,
        )

        assertEquals(
            listOf(ReminderPlan("i1", at(2026, 9, 28, 9)), ReminderPlan("i2", at(2026, 10, 12, 9))),
            plans,
        )
    }
}
