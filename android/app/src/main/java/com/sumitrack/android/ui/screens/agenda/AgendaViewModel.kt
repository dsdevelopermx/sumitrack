package com.sumitrack.android.ui.screens.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.AgendaEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

// `isOverdue` usa la MISMA regla que Installment.toUiStatus (Historia 3.5): dueDate.isBefore(ahora),
// comparación de Instant — así S-09 y S-10 dicen lo mismo de una parcialidad que vence hoy.
data class AgendaEntryUi(val entry: AgendaEntry, val isOverdue: Boolean)

data class AgendaUiState(
    val isLoading: Boolean = true,
    val hasAnyEntries: Boolean = false,
    val month: YearMonth,
    val monthLabel: String,
    val cells: List<AgendaGridCell?> = emptyList(),
    val selectedDate: LocalDate? = null,
    val selectedEntries: List<AgendaEntryUi> = emptyList(),
    // Aviso "Tienes N cobros vencidos en meses anteriores" (los vencidos viven fuera del mes visible).
    val overdueBeforeMonthCount: Int = 0,
    val earliestOverdueMonth: YearMonth? = null,
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    settingsRepository: SettingsRepository,
    @TenantId private val tenantId: Flow<String?>,
    private val clock: Clock,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now(clock))
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _refresh = MutableStateFlow(0)
    private var lastToday = LocalDate.now(clock)
    private val firstDayOfWeek = AgendaCalculator.defaultFirstDayOfWeek()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val entries: Flow<List<AgendaEntry>> = tenantId
        .flatMapLatest { tenant ->
            if (tenant.isNullOrBlank()) flowOf(emptyList()) else saleRepository.getAgendaForTenant(tenant)
        }
        .catch { emit(emptyList()) }

    val uiState: StateFlow<AgendaUiState> = combine(
        entries,
        settingsRepository.observeDiasAnticipacion(),
        _month,
        _selectedDate,
        _refresh,
    ) { entries, dias, month, selectedDate, _ ->
        val zone = clock.zone
        val now = Instant.now(clock)
        val byDay = AgendaCalculator.groupByDay(entries, zone)
        // La selección solo es válida si es del mes visible Y ese día aún tiene cobros (un cobro pudo
        // pagarse/cancelarse/sincronizarse mientras la lista estaba abierta).
        val selected = selectedDate?.takeIf { YearMonth.from(it) == month && byDay.containsKey(it) }
        val overdue = AgendaCalculator.overdueBeforeMonth(entries, month, now, zone)
        AgendaUiState(
            isLoading = false,
            hasAnyEntries = entries.isNotEmpty(),
            month = month,
            monthLabel = AgendaCalculator.monthLabel(month),
            cells = AgendaCalculator.buildMonthGrid(month, byDay, LocalDate.now(clock), now, dias, firstDayOfWeek),
            selectedDate = selected,
            selectedEntries = selected?.let { byDay[it] }.orEmpty().map { AgendaEntryUi(it, it.dueDate.isBefore(now)) },
            overdueBeforeMonthCount = overdue.count,
            earliestOverdueMonth = overdue.earliestMonth,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgendaUiState(month = _month.value, monthLabel = AgendaCalculator.monthLabel(_month.value)),
    )

    // "Hoy", "ahora", Vencida/Pendiente y la campana se calculan al emitir; sin un flujo que cambie no se
    // refrescarían al volver a la app otro día u hora. La pantalla llama esto en cada ON_RESUME. Si el
    // DÍA cambió mientras la agenda seguía viva, vuelve al mes actual (no queda abierta en un mes viejo).
    fun onResume() {
        val today = LocalDate.now(clock)
        if (today != lastToday) {
            lastToday = today
            _month.value = YearMonth.from(today)
            _selectedDate.value = null
        }
        _refresh.value += 1
    }

    fun onPreviousMonth() = changeMonth(-1)

    fun onNextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        _month.value = _month.value.plusMonths(delta)
        _selectedDate.value = null
    }

    // Solo se puede seleccionar un día CON cobros; tocar el mismo día otra vez lo deselecciona.
    fun onDayClick(date: LocalDate) {
        val hasEntries = uiState.value.cells.any { it != null && it.date == date && it.count > 0 }
        if (!hasEntries) return
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    // Salta al mes del cobro vencido más antiguo.
    fun onOverdueNoticeClick() {
        val target = uiState.value.earliestOverdueMonth ?: return
        _month.value = target
        _selectedDate.value = null
    }
}
