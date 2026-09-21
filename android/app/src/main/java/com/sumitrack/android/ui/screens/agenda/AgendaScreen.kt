package com.sumitrack.android.ui.screens.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.ui.components.EmptyState
import com.sumitrack.android.ui.screens.orders.InstallmentUiStatus
import com.sumitrack.android.ui.screens.orders.installmentStatusLabelAndColor
import com.sumitrack.android.ui.theme.StatusOverdue
import com.sumitrack.android.ui.theme.StatusPending
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ES_MX = Locale.forLanguageTag("es-MX")

// S-10 — Agenda de Cobros (Historia 5.3). Sin banner propio de offline (AC-7): los datos son locales.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onBackClick: () -> Unit,
    onEntryClick: (saleId: String) -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // "Hoy", Vencida/Pendiente y la campana se recalculan al volver a la app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agenda de cobros") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            !uiState.hasAnyEntries -> EmptyState(
                icon = Icons.Outlined.CalendarMonth,
                message = "No hay cobros programados. Crea una orden con parcialidades para verlos aquí.",
                modifier = Modifier.padding(innerPadding).padding(horizontal = 32.dp),
            )

            else -> AgendaContent(
                uiState = uiState,
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
                onDayClick = viewModel::onDayClick,
                onEntryClick = onEntryClick,
                onOverdueNoticeClick = viewModel::onOverdueNoticeClick,
                // Padding horizontal de 8dp (no 16dp): a 360dp de ancho cada celda mide (360-16)/7 ≈ 49dp,
                // cumpliendo el mínimo táctil de 48dp (UX-DR20); con 16dp serían ≈46.9dp.
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun AgendaContent(
    uiState: AgendaUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onEntryClick: (saleId: String) -> Unit,
    onOverdueNoticeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = uiState.monthLabel,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
            }
        }

        // Los cobros vencidos (los más urgentes) viven en meses anteriores; sin este aviso un mes actual
        // sin cobros se vería como un calendario en blanco. Texto + color (nunca solo color).
        if (uiState.overdueBeforeMonthCount > 0) {
            TextButton(onClick = onOverdueNoticeClick, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = AgendaCalculator.overdueNotice(uiState.overdueBeforeMonthCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusOverdue,
                )
            }
        }

        WeekdayHeader()

        uiState.cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isSelected = cell != null && cell.date == uiState.selectedDate,
                        onDayClick = onDayClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        val selectedDate = uiState.selectedDate
        if (selectedDate == null) {
            Text(
                text = "Toca un día para ver sus cobros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        } else {
            Text(
                text = "Cobros del ${AgendaCalculator.dayLabel(selectedDate)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            uiState.selectedEntries.forEach { item ->
                EntryRow(item = item, onClick = { onEntryClick(item.entry.saleId) })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WeekdayHeader() {
    val first = AgendaCalculator.defaultFirstDayOfWeek()
    // Decorativo: cada celda de día ya se describe completa a TalkBack.
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        for (offset in 0L until 7L) {
            Text(
                text = first.plus(offset).getDisplayName(TextStyle.NARROW, ES_MX).uppercase(ES_MX),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DayCell(
    cell: AgendaGridCell?,
    isSelected: Boolean,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cell == null) {
        Box(modifier = modifier.heightIn(min = 48.dp))
        return
    }

    val hasEntries = cell.count > 0
    val description = AgendaCalculator.dayDescription(cell.date, cell.count, cell.hasAlert)
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        hasEntries -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        hasEntries -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(8.dp)

    // Alto mínimo 48dp en el Box EXTERNO (el clickable) — el padding visual va en el Box interno, para
    // no encoger el área táctil. Solo los días con cobros son clicables.
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(
                if (hasEntries) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Ver cobros del día") { onDayClick(cell.date) }
                } else {
                    Modifier
                },
            )
            // Reemplaza el árbol interno (número, conteo, campana) por UNA descripción (UX-DR22). Rol, acción
            // de clic y `selected` se declaran AQUÍ (no se dejan al merge con `clickable`) para que TalkBack
            // los anuncie sin depender del orden de los modifiers.
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                if (hasEntries) {
                    role = Role.Button
                    onClick(label = "Ver cobros del día") {
                        onDayClick(cell.date)
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(2.dp)
                .clip(shape)
                .background(background)
                .then(if (cell.isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        )
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            // Canal no dependiente del color para el día seleccionado: número en negritas.
            fontWeight = if (isSelected) FontWeight.Bold else null,
            color = contentColor,
        )
        if (hasEntries) {
            // Canal secundario no dependiente del color: el número de cobros superpuesto.
            Text(
                text = cell.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 6.dp),
            )
        }
        if (cell.hasAlert) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = StatusPending,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 4.dp).size(14.dp),
            )
        }
    }
}

@Composable
private fun EntryRow(item: AgendaEntryUi, onClick: () -> Unit) {
    val (statusLabel, statusColor) = installmentStatusLabelAndColor(
        if (item.isOverdue) InstallmentUiStatus.OVERDUE else InstallmentUiStatus.PENDING,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.entry.clientName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Folio ${item.entry.folio}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatAmount(item.entry.amount), style = MaterialTheme.typography.bodyLarge)
            // Color + texto (nunca solo color).
            Text(text = statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
        }
    }
}

private fun formatAmount(amount: BigDecimal): String = "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
