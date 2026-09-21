package com.sumitrack.android.ui.screens.agenda

import com.sumitrack.android.domain.models.AgendaEntry
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaCalculatorTest {

    private val zone = ZoneId.of("America/Mexico_City")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Instant =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

    private fun entry(id: String, dueDate: Instant) = AgendaEntry(
        installmentId = id,
        saleId = "s-$id",
        folio = "A1",
        clientName = "Cliente $id",
        amount = BigDecimal("100.00"),
        dueDate = dueDate,
    )

    private fun day(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    // Lunes 21 de septiembre de 2026, 08:00 hora local.
    private val now = at(2026, 9, 21, 8)

    @Test
    fun `groupByDay usa el dia local, no el dia UTC`() {
        // 23:30 hora de la Ciudad de México = 05:30 UTC del día siguiente.
        val grouped = AgendaCalculator.groupByDay(listOf(entry("i1", at(2026, 9, 30, 23, 30))), zone)

        assertEquals(setOf(day(2026, 9, 30)), grouped.keys)
    }

    @Test
    fun `groupByDay ordena las parcialidades de un dia por hora y luego por id`() {
        val grouped = AgendaCalculator.groupByDay(
            listOf(
                entry("b", at(2026, 9, 21, 15)),
                entry("z", at(2026, 9, 21, 9)),
                entry("a", at(2026, 9, 21, 15)),
            ),
            zone,
        )

        assertEquals(listOf("z", "a", "b"), grouped.getValue(day(2026, 9, 21)).map { it.installmentId })
    }

    @Test
    fun `buildMonthGrid rellena antes del dia 1 segun el primer dia de la semana`() {
        // 1 de septiembre de 2026 es martes.
        val monday = AgendaCalculator.buildMonthGrid(YearMonth.of(2026, 9), emptyMap(), day(2026, 9, 21), now, 3, DayOfWeek.MONDAY)
        val sunday = AgendaCalculator.buildMonthGrid(YearMonth.of(2026, 9), emptyMap(), day(2026, 9, 21), now, 3, DayOfWeek.SUNDAY)

        assertEquals(1, monday.takeWhile { it == null }.size)
        assertEquals(2, sunday.takeWhile { it == null }.size)
        assertEquals(day(2026, 9, 1), monday.first { it != null }!!.date)
    }

    @Test
    fun `buildMonthGrid tiene una celda por dia del mes en meses de 28, 30 y 31 dias`() {
        fun daysIn(month: YearMonth) =
            AgendaCalculator.buildMonthGrid(month, emptyMap(), day(2026, 9, 21), now, 3, DayOfWeek.MONDAY).filterNotNull().size

        assertEquals(28, daysIn(YearMonth.of(2026, 2)))
        assertEquals(30, daysIn(YearMonth.of(2026, 9)))
        assertEquals(31, daysIn(YearMonth.of(2026, 10)))
    }

    @Test
    fun `buildMonthGrid no rellena al final`() {
        // Febrero 2026 empieza en domingo: con lunes como primer día son 6 nulos + 28 = 34 celdas.
        val grid = AgendaCalculator.buildMonthGrid(YearMonth.of(2026, 2), emptyMap(), day(2026, 9, 21), now, 3, DayOfWeek.MONDAY)

        assertEquals(34, grid.size)
        assertEquals(day(2026, 2, 28), grid.last()!!.date)
    }

    @Test
    fun `buildMonthGrid calcula count e isToday`() {
        val byDay = AgendaCalculator.groupByDay(
            listOf(entry("i1", at(2026, 9, 21, 9)), entry("i2", at(2026, 9, 21, 12)), entry("i3", at(2026, 9, 21, 15))),
            zone,
        )

        val cells = AgendaCalculator.buildMonthGrid(YearMonth.of(2026, 9), byDay, day(2026, 9, 21), now, 3, DayOfWeek.MONDAY)
            .filterNotNull().associateBy { it.date }

        assertEquals(3, cells.getValue(day(2026, 9, 21)).count)
        assertTrue(cells.getValue(day(2026, 9, 21)).isToday)
        assertEquals(0, cells.getValue(day(2026, 9, 22)).count)
        assertFalse(cells.getValue(day(2026, 9, 22)).isToday)
    }

    @Test
    fun `hasAlert incluye hoy y el borde del umbral, y excluye mas alla, el pasado y dias sin cobros`() {
        val byDay = AgendaCalculator.groupByDay(
            listOf(
                entry("pasado", at(2026, 9, 20)),
                entry("hoy", at(2026, 9, 21)),
                entry("borde", at(2026, 9, 24)),
                entry("fuera", at(2026, 9, 25)),
            ),
            zone,
        )

        val cells = AgendaCalculator.buildMonthGrid(YearMonth.of(2026, 9), byDay, day(2026, 9, 21), now, 3, DayOfWeek.MONDAY)
            .filterNotNull().associateBy { it.date }

        assertFalse(cells.getValue(day(2026, 9, 20)).hasAlert)
        assertTrue(cells.getValue(day(2026, 9, 21)).hasAlert)
        assertTrue(cells.getValue(day(2026, 9, 24)).hasAlert)
        assertFalse(cells.getValue(day(2026, 9, 25)).hasAlert)
        assertFalse(cells.getValue(day(2026, 9, 22)).hasAlert)
    }

    @Test
    fun `dayDescription con varios cobros, uno solo y ninguno`() {
        assertEquals("Lunes 29 de junio, 3 cobros pendientes", AgendaCalculator.dayDescription(day(2026, 6, 29), 3, false))
        assertEquals("Lunes 29 de junio, 1 cobro pendiente", AgendaCalculator.dayDescription(day(2026, 6, 29), 1, false))
        assertEquals("Martes 30 de junio, sin cobros", AgendaCalculator.dayDescription(day(2026, 6, 30), 0, false))
    }

    @Test
    fun `dayDescription agrega el aviso de cobros proximos solo cuando hay alerta`() {
        assertEquals(
            "Lunes 29 de junio, 3 cobros pendientes, con cobros próximos a vencer",
            AgendaCalculator.dayDescription(day(2026, 6, 29), 3, true),
        )
    }

    @Test
    fun `monthLabel capitaliza el mes en espanol`() {
        assertEquals("Septiembre 2026", AgendaCalculator.monthLabel(YearMonth.of(2026, 9)))
    }

    @Test
    fun `dayLabel arma el encabezado de la lista del dia`() {
        assertEquals("21 de septiembre", AgendaCalculator.dayLabel(day(2026, 9, 21)))
    }

    @Test
    fun `un cobro de hoy que ya vencio no cuenta para la campana pero uno de hoy aun vigente si`() {
        val vencidoHoy = AgendaCalculator.groupByDay(listOf(entry("v", at(2026, 9, 21, 7))), zone)
        val vigenteHoy = AgendaCalculator.groupByDay(listOf(entry("h", at(2026, 9, 21, 15))), zone)
        val mezcla = AgendaCalculator.groupByDay(listOf(entry("v", at(2026, 9, 21, 7)), entry("h", at(2026, 9, 21, 15))), zone)

        fun alert(byDay: Map<LocalDate, List<AgendaEntry>>) =
            AgendaCalculator.buildMonthGrid(YearMonth.of(2026, 9), byDay, day(2026, 9, 21), now, 3, DayOfWeek.MONDAY)
                .filterNotNull().first { it.date == day(2026, 9, 21) }.hasAlert

        assertFalse(alert(vencidoHoy))
        assertTrue(alert(vigenteHoy))
        assertTrue(alert(mezcla))
    }

    @Test
    fun `overdueBeforeMonth cuenta solo vencidos de meses anteriores y devuelve el mes del mas antiguo`() {
        val entries = listOf(
            entry("julio", at(2026, 7, 10)),
            entry("agosto", at(2026, 8, 15)),
            entry("septiembre-vencido", at(2026, 9, 5)),
            entry("septiembre-futuro", at(2026, 9, 28)),
            entry("octubre", at(2026, 10, 3)),
        )

        val enSeptiembre = AgendaCalculator.overdueBeforeMonth(entries, YearMonth.of(2026, 9), now, zone)
        val enAgosto = AgendaCalculator.overdueBeforeMonth(entries, YearMonth.of(2026, 8), now, zone)

        assertEquals(OverdueSummary(2, YearMonth.of(2026, 7)), enSeptiembre)
        assertEquals(OverdueSummary(1, YearMonth.of(2026, 7)), enAgosto)
        assertEquals(OverdueSummary(0, null), AgendaCalculator.overdueBeforeMonth(entries, YearMonth.of(2026, 7), now, zone))
    }

    @Test
    fun `overdueNotice en singular y plural`() {
        assertEquals("Tienes 1 cobro vencido en meses anteriores", AgendaCalculator.overdueNotice(1))
        assertEquals("Tienes 4 cobros vencidos en meses anteriores", AgendaCalculator.overdueNotice(4))
    }

    @Test
    fun `dayDescription con alerta en singular, y sin cobros ignora la alerta`() {
        assertEquals(
            "Lunes 29 de junio, 1 cobro pendiente, con cobros próximos a vencer",
            AgendaCalculator.dayDescription(day(2026, 6, 29), 1, true),
        )
        assertEquals("Martes 30 de junio, sin cobros", AgendaCalculator.dayDescription(day(2026, 6, 30), 0, true))
    }
}
