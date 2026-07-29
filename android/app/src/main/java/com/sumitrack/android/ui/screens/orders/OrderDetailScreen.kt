package com.sumitrack.android.ui.screens.orders

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.domain.models.Installment
import com.sumitrack.android.domain.models.Payment
import com.sumitrack.android.domain.models.SaleItem
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.TicketPaymentCondition
import com.sumitrack.android.ui.components.EmptyState
import com.sumitrack.android.ui.components.SaleUiStatus
import com.sumitrack.android.ui.components.StatusBadge
import com.sumitrack.android.ui.theme.StatusOverdue
import com.sumitrack.android.ui.theme.StatusPaid
import com.sumitrack.android.ui.theme.StatusPending
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { uriString ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                .onFailure { snackbarHostState.showSnackbar("No hay ninguna app instalada para compartir") }
        }
    }

    LaunchedEffect(uiState.cancelPlaceholderMessage) {
        uiState.cancelPlaceholderMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onCancelPlaceholderShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.folio.ifBlank { "Detalle de orden" }) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.notFound -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    message = "No pudimos cargar esta orden.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(formatDate(uiState.createdAt), style = MaterialTheme.typography.bodyMedium)
                            Text(uiState.clientName, style = MaterialTheme.typography.titleMedium)
                        }
                        StatusBadge(uiState.status.toUiStatus())
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Ítems", style = MaterialTheme.typography.titleSmall)
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.items.forEach { item -> OrderDetailItemRow(item) }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SummaryLine("Subtotal", uiState.subtotal)
                        SummaryLine("Impuestos", uiState.tax)
                        SummaryLine("Total", uiState.total, emphasize = true)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Condición de pago", style = MaterialTheme.typography.titleSmall)
                    when (val condition = uiState.paymentCondition) {
                        is TicketPaymentCondition.SinglePayment -> {
                            Text(
                                "Pago de contado — ${formatDate(condition.paidAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        is TicketPaymentCondition.InstallmentPlan -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.installments.forEach { installment -> InstallmentRow(installment) }
                            }
                        }
                        null -> Unit
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Historial de cobros", style = MaterialTheme.typography.titleSmall)
                    if (uiState.paymentHistory.isEmpty()) {
                        Text(
                            "Sin cobros registrados todavía.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.paymentHistory.forEach { payment -> PaymentHistoryRow(payment) }
                        }
                    }

                    Button(
                        onClick = viewModel::onShareTicketClick,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) {
                        Text("Compartir Ticket")
                    }

                    if (uiState.status != SaleStatus.CANCELLED) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text("Cancelar Orden")
                        }
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("¿Cancelar esta orden?") },
            text = { Text("Esto no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.onCancelOrderConfirm()
                }) {
                    Text("Sí, cancelar orden", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("No, mantenerla")
                }
            },
        )
    }

    val ticketData = uiState.ticketData
    if (ticketData != null) {
        TicketSheet(
            ticketData = ticketData,
            isPrinting = uiState.isPrinting,
            isSharing = uiState.isSharing,
            printError = uiState.printError,
            onPrintClick = viewModel::onPrintClick,
            onPermissionDenied = viewModel::onBluetoothPermissionDenied,
            onShareClick = viewModel::onShareTicketClick,
            onDismiss = viewModel::onTicketDismiss,
        )
    }
}

@Composable
private fun OrderDetailItemRow(item: SaleItem) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            val name = if (item.variantName != null) "${item.productName} (${item.variantName})" else item.productName
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "x${item.quantity} · ${formatAmount(item.unitPrice)} c/u",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatAmount(item.subtotal), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SummaryLine(label: String, amount: BigDecimal, emphasize: Boolean = false) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = formatAmount(amount),
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InstallmentRow(installment: Installment) {
    val (label, color) = installmentStatusLabelAndColor(installment.toUiStatus())
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDate(installment.dueDate), style = MaterialTheme.typography.bodyMedium)
            Text(formatAmount(installment.amount), style = MaterialTheme.typography.bodyLarge)
        }
        Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PaymentHistoryRow(payment: Payment) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(formatDate(payment.paidAt), style = MaterialTheme.typography.bodyMedium)
        Text(formatAmount(payment.amount), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun installmentStatusLabelAndColor(status: InstallmentUiStatus): Pair<String, Color> = when (status) {
    InstallmentUiStatus.PAID -> "Pagada" to StatusPaid
    InstallmentUiStatus.PENDING -> "Pendiente" to StatusPending
    InstallmentUiStatus.OVERDUE -> "Vencida" to StatusOverdue
}

private fun SaleStatus.toUiStatus(): SaleUiStatus = when (this) {
    SaleStatus.PENDING, SaleStatus.PARTIAL -> SaleUiStatus.PARTIAL
    SaleStatus.PAID -> SaleUiStatus.PAID
    SaleStatus.CANCELLED -> SaleUiStatus.CANCELLED
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX"))

private fun formatDate(instant: Instant): String =
    dateFormatter.format(instant.atZone(ZoneId.systemDefault()))

private fun formatAmount(amount: BigDecimal): String =
    "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
