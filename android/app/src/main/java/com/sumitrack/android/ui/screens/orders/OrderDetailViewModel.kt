package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.bluetooth.BluetoothTicketPrinter
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.Installment
import com.sumitrack.android.domain.models.InstallmentStatus
import com.sumitrack.android.domain.models.Payment
import com.sumitrack.android.domain.models.SaleItem
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.domain.models.TicketData
import com.sumitrack.android.domain.models.TicketPaymentCondition
import com.sumitrack.android.domain.usecases.GenerateTicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

// "Vencida" no es un InstallmentStatus persistido (Historia 3.3 solo tiene PENDING/PAID) — es una
// comparación de fecha al momento de mostrarla, no un hecho que valga la pena reescribir en SQLite
// constantemente. `now` con default pero parametrizable permite tests deterministas sin mockear el
// reloj del sistema, mismo criterio que CalculateInstallmentsUseCase (Historia 3.3).
// No reutiliza SaleUiStatus/StatusBadge: ese enum describe el estado de una VENTA completa (4
// valores, contexto S-02); "vencida" aquí es un concepto distinto a nivel de PARCIALIDAD individual.
enum class InstallmentUiStatus { PENDING, PAID, OVERDUE }

fun Installment.toUiStatus(now: Instant = Instant.now()): InstallmentUiStatus = when {
    status == InstallmentStatus.PAID -> InstallmentUiStatus.PAID
    dueDate.isBefore(now) -> InstallmentUiStatus.OVERDUE
    else -> InstallmentUiStatus.PENDING
}

data class OrderDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val folio: String = "",
    val createdAt: Instant = Instant.EPOCH,
    val clientName: String = "",
    val status: SaleStatus = SaleStatus.PENDING,
    val items: List<SaleItem> = emptyList(),
    val subtotal: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal = BigDecimal.ZERO,
    val paymentCondition: TicketPaymentCondition? = null,
    val installments: List<Installment> = emptyList(),
    val paymentHistory: List<Payment> = emptyList(),
    val ticketData: TicketData? = null,
    val isPrinting: Boolean = false,
    val isSharing: Boolean = false,
    val printError: String? = null,
    val cancelPlaceholderMessage: String? = null,
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saleRepository: SaleRepository,
    private val clientRepository: ClientRepository,
    private val generateTicketUseCase: GenerateTicketUseCase,
    private val bluetoothTicketPrinter: BluetoothTicketPrinter,
    private val ticketFileWriter: TicketFileWriter,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    private val saleId: String = checkNotNull(savedStateHandle["saleId"])

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    // Uri del PNG ya escrito en caché — OrderDetailScreen lo colecta para disparar el
    // Intent.ACTION_SEND real (necesita un Context de UI, no debe vivir en el ViewModel).
    private val _shareEvent = Channel<String>()
    val shareEvent = _shareEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            val tenant = tenantId.first()
            if (tenant.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val detail = runCatching { saleRepository.getSaleDetail(saleId, tenant) }.getOrNull()
            if (detail == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val client = runCatching { clientRepository.getClientById(detail.sale.fkClient) }.getOrNull()
            val paymentCondition = if (detail.installments.isEmpty()) {
                TicketPaymentCondition.SinglePayment(detail.payments.firstOrNull()?.paidAt ?: detail.sale.createdAt)
            } else {
                TicketPaymentCondition.InstallmentPlan(detail.installments.sortedBy { it.dueDate })
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    folio = detail.sale.folio,
                    createdAt = detail.sale.createdAt,
                    clientName = client?.name.orEmpty(),
                    status = detail.sale.status,
                    items = detail.items,
                    subtotal = detail.sale.subtotal,
                    tax = detail.sale.tax,
                    total = detail.sale.total,
                    paymentCondition = paymentCondition,
                    installments = detail.installments.sortedBy { it.dueDate },
                    paymentHistory = detail.payments.sortedByDescending { it.paidAt },
                )
            }
        }
    }

    // isSharing se marca de forma síncrona antes del launch (no dentro de él) para cerrar la
    // ventana de doble-tap: dos llamadas seguidas antes de que resuelva tenantId.first() verían
    // ambas ticketData == null y dispararían generateTicketUseCase/shareEvent por duplicado
    // (Review Finding). Se resetea a false justo antes de invocar onShareClick() para que su
    // propio guard/estado (idéntico a PaymentViewModel, sin modificar) siga funcionando tal cual.
    fun onShareTicketClick() {
        if (_uiState.value.isSharing) return
        _uiState.update { it.copy(isSharing = true, printError = null) }
        viewModelScope.launch {
            val tenant = tenantId.first()
            val ticket = _uiState.value.ticketData
                ?: tenant?.let { runCatching { generateTicketUseCase(saleId, it) }.getOrNull() }
            if (ticket == null) {
                _uiState.update {
                    it.copy(isSharing = false, printError = "No se pudo generar el ticket. Inténtalo de nuevo.")
                }
                return@launch
            }
            _uiState.update { it.copy(ticketData = ticket, isSharing = false) }
            onShareClick()
        }
    }

    // Misma lógica que PaymentViewModel (Historia 3.4) — duplicada deliberadamente, no extraída a
    // un delegate/use case compartido porque ningún AC de esta historia lo exige y evita tocar
    // PaymentViewModel.kt, ya estable y cubierto por su propia suite de tests (ver Dev Notes).
    fun onPrintClick() {
        val ticket = _uiState.value.ticketData ?: return
        if (_uiState.value.isPrinting) return
        _uiState.update { it.copy(isPrinting = true, printError = null) }
        viewModelScope.launch {
            val result = bluetoothTicketPrinter.printTicket(ticket)
            result.onSuccess {
                _uiState.update { it.copy(isPrinting = false) }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isPrinting = false,
                        printError = "No encontramos la impresora. La orden ya está guardada — puedes compartir el ticket después.",
                    )
                }
            }
        }
    }

    fun onBluetoothPermissionDenied() {
        _uiState.update {
            it.copy(printError = "Se necesita permiso de Bluetooth para imprimir. Puedes compartir el ticket en su lugar.")
        }
    }

    private fun onShareClick() {
        val ticket = _uiState.value.ticketData ?: return
        if (_uiState.value.isSharing) return
        _uiState.update { it.copy(isSharing = true, printError = null) }
        viewModelScope.launch {
            runCatching { ticketFileWriter.writeToCacheAndGetUri(ticket, "ticket_${ticket.folio}.png") }
                .onSuccess { uri ->
                    _uiState.update { it.copy(isSharing = false) }
                    _shareEvent.send(uri)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isSharing = false, printError = "No se pudo preparar el ticket para compartir. Inténtalo de nuevo.")
                    }
                }
        }
    }

    fun onTicketDismiss() {
        _uiState.update { it.copy(ticketData = null, printError = null) }
    }

    fun onCancelOrderConfirm() {
        _uiState.update { it.copy(cancelPlaceholderMessage = "Cancelación de orden — disponible próximamente") }
    }

    fun onCancelPlaceholderShown() {
        _uiState.update { it.copy(cancelPlaceholderMessage = null) }
    }
}
