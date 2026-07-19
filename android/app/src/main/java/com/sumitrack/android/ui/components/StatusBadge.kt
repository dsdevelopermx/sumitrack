package com.sumitrack.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sumitrack.android.ui.theme.StatusCancelled
import com.sumitrack.android.ui.theme.StatusOverdue
import com.sumitrack.android.ui.theme.StatusPaid
import com.sumitrack.android.ui.theme.StatusPending

enum class SaleUiStatus {
    PAID,
    PARTIAL,
    OVERDUE,
    CANCELLED,
}

@Composable
fun StatusBadge(status: SaleUiStatus, modifier: Modifier = Modifier) {
    val (label, color) = statusLabelAndColor(status)

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun statusLabelAndColor(status: SaleUiStatus): Pair<String, Color> = when (status) {
    SaleUiStatus.PAID -> "Pagada" to StatusPaid
    SaleUiStatus.PARTIAL -> "Parcialidades" to StatusPending
    SaleUiStatus.OVERDUE -> "Atraso" to StatusOverdue
    SaleUiStatus.CANCELLED -> "Cancelada" to StatusCancelled
}
