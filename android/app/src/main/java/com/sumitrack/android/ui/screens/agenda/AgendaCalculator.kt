package com.sumitrack.android.ui.screens.agenda

import com.sumitrack.android.domain.models.AgendaEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

data class AgendaGridCell(
    val date: LocalDate,
    val count: Int,
    val hasAlert: Boolean,
    val isToday: Boolean,
)

// Lógica pura del calendario de S-10 (sin android.*): `zone`/`today` son parámetros, mismo criterio que
// ReminderPlanner (Historia 5.2), para tests deterministas sin reloj de sistema.
// Cobros vencidos en meses ANTERIORES al visible, y el mes del más antiguo (destino del aviso de S-10).
data class OverdueSummary(val count: Int, val earliestMonth: YearMonth?)

object AgendaCalculator {

    private val locale = Locale.forLanguageTag("es-MX")

    fun defaultFirstDayOfWeek(): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    // El día es la fecha LOCAL de dueDate — la que el proveedor ve en pantalla (formatDate usa la zona
    // del sistema) — no la fecha UTC.
    fun groupByDay(entries: List<AgendaEntry>, zone: ZoneId): Map<LocalDate, List<AgendaEntry>> =
        entries
            .groupBy { it.dueDate.atZone(zone).toLocalDate() }
            .mapValues { (_, day) -> day.sortedWith(compareBy({ it.dueDate }, { it.installmentId })) }

    // `null` = celda de relleno antes del día 1 para alinear con `firstDayOfWeek`; no se rellena al final.
    // La campana (hasAlert) marca días con cobros dentro del umbral de dias_anticipacion_recordatorio
    // [hoy, hoy + dias]; una fecha pasada NO la lleva — lo vencido se comunica con "Vencida" en la lista.
    // Un cobro de HOY que ya venció (dueDate antes de `now`) tampoco cuenta: ya está "Vencida", no "próximo".
    fun buildMonthGrid(
        month: YearMonth,
        byDay: Map<LocalDate, List<AgendaEntry>>,
        today: LocalDate,
        now: Instant,
        diasAnticipacion: Int,
        firstDayOfWeek: DayOfWeek,
    ): List<AgendaGridCell?> {
        val padding = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = MutableList<AgendaGridCell?>(padding) { null }
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val date = month.atDay(dayOfMonth)
            val count = byDay[date]?.size ?: 0
            val daysUntil = ChronoUnit.DAYS.between(today, date)
            cells += AgendaGridCell(
                date = date,
                count = count,
                hasAlert = count > 0 &&
                    daysUntil in 0..diasAnticipacion.toLong() &&
                    byDay[date].orEmpty().any { !it.dueDate.isBefore(now) },
                isToday = date == today,
            )
        }
        return cells
    }

    // Cobros vencidos (dueDate antes de `now`) cuya fecha local cae antes del mes visible.
    fun overdueBeforeMonth(entries: List<AgendaEntry>, month: YearMonth, now: Instant, zone: ZoneId): OverdueSummary {
        val monthStart = month.atDay(1)
        val overdue = entries.filter { it.dueDate.isBefore(now) && it.dueDate.atZone(zone).toLocalDate().isBefore(monthStart) }
        val earliest = overdue.minByOrNull { it.dueDate }
        return OverdueSummary(overdue.size, earliest?.let { YearMonth.from(it.dueDate.atZone(zone)) })
    }

    fun overdueNotice(count: Int): String =
        if (count == 1) "Tienes 1 cobro vencido en meses anteriores" else "Tienes $count cobros vencidos en meses anteriores"

    // "Lunes 29 de junio, 3 cobros pendientes" / "1 cobro pendiente" / "Martes 30 de junio, sin cobros"
    // (UX-DR22). Adición deliberada de accesibilidad: con alerta se agrega ", con cobros próximos a
    // vencer" — la celda reemplaza su árbol de semantics, así que la campana no llegaría a TalkBack.
    fun dayDescription(date: LocalDate, count: Int, hasAlert: Boolean): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
        val month = date.month.getDisplayName(TextStyle.FULL, locale)
        val base = "$weekday ${date.dayOfMonth} de $month"
        if (count == 0) return "$base, sin cobros"
        val pending = if (count == 1) "1 cobro pendiente" else "$count cobros pendientes"
        return if (hasAlert) "$base, $pending, con cobros próximos a vencer" else "$base, $pending"
    }

    // "21 de septiembre" — encabezado de la lista de cobros del día seleccionado.
    fun dayLabel(date: LocalDate): String =
        "${date.dayOfMonth} de ${date.month.getDisplayName(TextStyle.FULL, locale)}"

    fun monthLabel(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}".replaceFirstChar { it.titlecase(locale) }
}
