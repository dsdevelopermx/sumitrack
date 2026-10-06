package com.sumitrack.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sumitrack.android.domain.format.formatMoney
import com.sumitrack.android.domain.models.OrderSummary
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.ui.theme.PrimaryVariant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderCard(
    order: OrderSummary,
    onClick: () -> Unit,
    onConflictClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = order.folio,
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryVariant,
                )
                Text(
                    text = formatDate(order.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = order.clientName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            // FlowRow en vez de Row: a escala de fuente 2.0 el monto, el badge y el ícono no caben en una línea y el
            // monto (con weight) se partía en "$300." / "00". Ahora el monto no se parte y el grupo badge+ícono baja
            // de línea cuando no hay espacio.
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = formatMoney(order.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    softWrap = false,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusBadge(order.status.toUiStatus())
                    SyncIcon(status = order.syncStatus, onConflictClick = onConflictClick)
                }
            }
        }
    }
}

private fun SaleStatus.toUiStatus(): SaleUiStatus = when (this) {
    SaleStatus.PENDING, SaleStatus.PARTIAL -> SaleUiStatus.PARTIAL
    SaleStatus.PAID -> SaleUiStatus.PAID
    SaleStatus.CANCELLED -> SaleUiStatus.CANCELLED
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX"))

private fun formatDate(instant: Instant): String =
    dateFormatter.format(instant.atZone(ZoneId.systemDefault()))
