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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.domain.models.InstallmentPeriodicity
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.ui.components.PaymentMethodRow
import com.sumitrack.android.ui.theme.SyncOk
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onConfirmed: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // navEvent ya no navega de inmediato (S-08/TicketSheet reemplaza el placeholder de Historia
    // 3.3) — loadTicket ya corre dentro del ViewModel al recibir este evento; el collector solo
    // existe para no bloquear el `send()` del Channel (rendezvous, sin buffer).
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { /* no-op: ticketData ya se actualiza reactivamente en uiState */ }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { uriString ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Sin app instalada capaz de manejar image/png (poco común, pero posible en un
            // dispositivo muy restringido o un emulador sin apps de mensajería/correo).
            runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                .onFailure { snackbarHostState.showSnackbar("No hay ninguna app instalada para compartir") }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configurar pago") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.mode == PaymentMode.IMMEDIATE,
                    onClick = { viewModel.onModeChange(PaymentMode.IMMEDIATE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Pago inmediato")
                }
                SegmentedButton(
                    selected = uiState.mode == PaymentMode.INSTALLMENTS,
                    onClick = { viewModel.onModeChange(PaymentMode.INSTALLMENTS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Parcialidades")
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            when (uiState.mode) {
                PaymentMode.IMMEDIATE -> ImmediatePaymentSection(
                    paymentMethods = uiState.paymentMethods,
                    remaining = uiState.remaining,
                    isConfirmEnabled = uiState.isImmediateConfirmEnabled,
                    availableCredit = uiState.availableCredit,
                    onTypeChange = viewModel::onPaymentMethodTypeChange,
                    onAmountChange = viewModel::onPaymentMethodAmountChange,
                    onRemove = viewModel::onRemovePaymentMethod,
                    onAddMethod = viewModel::onAddPaymentMethod,
                    onApplyCredit = viewModel::onApplyCreditClick,
                    onConfirm = viewModel::onConfirmClick,
                )
                PaymentMode.INSTALLMENTS -> InstallmentsSection(
                    countText = uiState.installmentCountText,
                    periodicity = uiState.periodicity,
                    installments = uiState.installments,
                    installmentsSum = uiState.installmentsSum,
                    isConfirmEnabled = uiState.isInstallmentsConfirmEnabled,
                    onCountChange = viewModel::onInstallmentCountChange,
                    onPeriodicityChange = viewModel::onPeriodicityChange,
                    onAmountChange = viewModel::onInstallmentAmountChange,
                    onDateChange = viewModel::onInstallmentDateChange,
                    onConfirm = viewModel::onConfirmClick,
                )
            }
        }
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
            onShareClick = viewModel::onShareClick,
            onDismiss = {
                viewModel.onTicketDismiss()
                onConfirmed()
            },
        )
    }

    // GenerateTicketUseCase devolvió null (venta no encontrada u otro fallo interno) — sin esto
    // el proveedor quedaba varado en PaymentScreen pese a que la venta ya se guardó exitosamente
    // (Review Finding del code review de esta historia); TicketSheet nunca llega a mostrarse
    // porque requiere ticketData no nulo, así que el swipe-down/tap-fuera tampoco es una opción.
    if (uiState.ticketLoadFailed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("No se pudo generar el ticket") },
            text = { Text("La venta ya se guardó. Puedes ver el detalle desde el Historial.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.onTicketDismiss()
                    onConfirmed()
                }) {
                    Text("Ir a Historial")
                }
            },
        )
    }
}

@Composable
private fun ImmediatePaymentSection(
    paymentMethods: List<PaymentMethodDraft>,
    remaining: BigDecimal,
    isConfirmEnabled: Boolean,
    availableCredit: BigDecimal,
    onTypeChange: (String, PaymentMethodType) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddMethod: () -> Unit,
    onApplyCredit: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        // Mismo texto ya establecido en ClientProfileScreen.kt/EXPERIENCE.md para Crédito a
        // Favor — solo visible si hay crédito disponible y todavía no se aplicó a esta venta.
        if (availableCredit > BigDecimal.ZERO && paymentMethods.none { it.type == PaymentMethodType.CREDITO_A_FAVOR }) {
            Surface(
                color = SyncOk.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Text(
                        text = "Tiene ${formatAmount(availableCredit)} a su favor. Puedes aplicarlo al pago.",
                        color = SyncOk,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onApplyCredit) { Text("Aplicar") }
                }
            }
        }
        paymentMethods.forEach { method ->
            PaymentMethodRow(
                type = method.type,
                amountText = method.amountText,
                onTypeChange = { onTypeChange(method.localId, it) },
                onAmountChange = { onAmountChange(method.localId, it) },
                onRemove = { onRemove(method.localId) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        OutlinedButton(onClick = onAddMethod, modifier = Modifier.padding(top = 8.dp)) {
            Text("+ Agregar método")
        }
        Text(
            text = "Restante por asignar: ${formatAmount(remaining)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(
            onClick = onConfirm,
            enabled = isConfirmEnabled,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Confirmar Pago")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentsSection(
    countText: String,
    periodicity: InstallmentPeriodicity,
    installments: List<InstallmentDraftUi>,
    installmentsSum: BigDecimal,
    isConfirmEnabled: Boolean,
    onCountChange: (String) -> Unit,
    onPeriodicityChange: (InstallmentPeriodicity) -> Unit,
    onAmountChange: (Int, String) -> Unit,
    onDateChange: (Int, Instant) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        OutlinedTextField(
            value = countText,
            onValueChange = onCountChange,
            label = { Text("Número de parcialidades") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            val periodicities = InstallmentPeriodicity.entries
            periodicities.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = periodicity == option,
                    onClick = { onPeriodicityChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = periodicities.size),
                ) {
                    Text(periodicityLabel(option))
                }
            }
        }

        // Column simple (no LazyColumn) — el máximo de 15 parcialidades es lo bastante chico para
        // no necesitar composición perezosa, y anidar un LazyColumn dentro del Column con
        // verticalScroll del contenedor padre causaría un conflicto de medición (dos contenedores
        // scrolleables verticales anidados).
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            installments.forEachIndexed { index, installment ->
                InstallmentRow(
                    installment = installment,
                    onAmountChange = { onAmountChange(index, it) },
                    onDateChange = { onDateChange(index, it) },
                )
            }
        }

        Text(
            text = "Suma de parcialidades: ${formatAmount(installmentsSum)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = onConfirm,
            enabled = isConfirmEnabled,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Confirmar Pago")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallmentRow(
    installment: InstallmentDraftUi,
    onAmountChange: (String) -> Unit,
    onDateChange: (Instant) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val zoneId = ZoneId.systemDefault()
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = installment.amountText,
            onValueChange = onAmountChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(start = 8.dp)) {
            Text(formatter.format(installment.dueDate.atZone(zoneId).toLocalDate()))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = installment.dueDate.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it)) }
                    showDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun periodicityLabel(periodicity: InstallmentPeriodicity): String = when (periodicity) {
    InstallmentPeriodicity.WEEKLY -> "Semanal"
    InstallmentPeriodicity.BIWEEKLY -> "Quincenal"
    InstallmentPeriodicity.MONTHLY -> "Mensual"
}

private fun formatAmount(amount: BigDecimal): String =
    "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
