package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.PaymentConfig
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.InstallmentPeriodicity
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.PaymentMethodType
import com.sumitrack.android.domain.models.calculateOrderTotals
import com.sumitrack.android.domain.usecases.CalculateInstallmentsUseCase
import com.sumitrack.android.domain.usecases.InstallmentSuggestion
import com.sumitrack.android.domain.usecases.ValidateFolioUseCase
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
import java.util.UUID
import javax.inject.Inject

enum class PaymentMode { IMMEDIATE, INSTALLMENTS }

data class PaymentMethodDraft(
    val localId: String = UUID.randomUUID().toString(),
    val type: PaymentMethodType = PaymentMethodType.EFECTIVO,
    val amountText: String = "",
)

data class InstallmentDraftUi(val amountText: String, val dueDate: Instant)

data class PaymentUiState(
    val isLoading: Boolean = true,
    val items: List<OrderDraftItem> = emptyList(),
    val mode: PaymentMode = PaymentMode.IMMEDIATE,
    val paymentMethods: List<PaymentMethodDraft> = listOf(PaymentMethodDraft()),
    val installmentCountText: String = "",
    val periodicity: InstallmentPeriodicity = InstallmentPeriodicity.MONTHLY,
    val installments: List<InstallmentDraftUi> = emptyList(),
    val maxParcialidades: Int = 15,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val total: BigDecimal get() = calculateOrderTotals(items).total

    // Solo montos positivos cuentan como "asignados" — un monto negativo/cero jamás se persiste
    // como Payment/Installment (ver onConfirmClick), así que tampoco debe poder satisfacer
    // remaining==0 / installmentsSum==total aquí; de lo contrario el total realmente cobrado
    // divergiría del total mostrado como "cubierto".
    private fun String.toPositiveBigDecimalOrZero(): BigDecimal =
        toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ZERO

    val assignedAmount: BigDecimal get() =
        paymentMethods.fold(BigDecimal.ZERO) { acc, m -> acc + m.amountText.toPositiveBigDecimalOrZero() }

    val remaining: BigDecimal get() = total.subtract(assignedAmount)

    val isImmediateConfirmEnabled: Boolean get() = !isSaving && remaining.compareTo(BigDecimal.ZERO) == 0

    val installmentsSum: BigDecimal get() =
        installments.fold(BigDecimal.ZERO) { acc, i -> acc + i.amountText.toPositiveBigDecimalOrZero() }

    val isInstallmentsConfirmEnabled: Boolean get() =
        !isSaving && installments.isNotEmpty() &&
            installments.all { it.amountText.toBigDecimalOrNull()?.let { amount -> amount > BigDecimal.ZERO } == true } &&
            installmentsSum.compareTo(total) == 0
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    private val saleRepository: SaleRepository,
    private val validateFolioUseCase: ValidateFolioUseCase,
    private val calculateInstallmentsUseCase: CalculateInstallmentsUseCase,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])
    private val cartEncoded: String = checkNotNull(savedStateHandle["cart"])

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<Unit>()
    val navEvent = _navEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            val tenant = tenantId.first()
            if (tenant.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.")
                }
                return@launch
            }
            val items = CartRouteCodec.decode(cartEncoded).mapNotNull { (productId, variantId, quantity) ->
                val product = runCatching { productRepository.getProductById(productId, tenant) }.getOrNull()
                    ?: return@mapNotNull null
                val variant = variantId?.let { vid ->
                    runCatching { productRepository.getVariantsForProduct(productId, tenant) }
                        .getOrDefault(emptyList())
                        .find { it.id == vid }
                }
                OrderDraftItem(product, variant, quantity)
            }
            // Acotado al límite duro de CalculateInstallmentsUseCase: un valor de Settings mayor a
            // 15 permitiría que onInstallmentCountChange acepte un count que luego hace explotar
            // el require() del use case con una IllegalArgumentException no capturada.
            val maxParcialidades = (
                runCatching { settingsRepository.getValue("max_parcialidades") }
                    .getOrNull()
                    ?.toIntOrNull()
                    ?: 15
                ).coerceAtMost(CalculateInstallmentsUseCase.MAX_INSTALLMENTS_HARD_LIMIT)
            _uiState.update { it.copy(isLoading = false, items = items, maxParcialidades = maxParcialidades) }
        }
    }

    fun onModeChange(mode: PaymentMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun onAddPaymentMethod() {
        _uiState.update { state ->
            val hasEfectivo = state.paymentMethods.any { it.type == PaymentMethodType.EFECTIVO }
            val newMethod = PaymentMethodDraft(type = if (hasEfectivo) PaymentMethodType.TRANSFERENCIA else PaymentMethodType.EFECTIVO)
            state.copy(paymentMethods = state.paymentMethods + newMethod)
        }
    }

    fun onRemovePaymentMethod(localId: String) {
        _uiState.update { it.copy(paymentMethods = it.paymentMethods.filterNot { m -> m.localId == localId }) }
    }

    fun onPaymentMethodTypeChange(localId: String, type: PaymentMethodType) {
        _uiState.update { state ->
            val conflict = type == PaymentMethodType.EFECTIVO &&
                state.paymentMethods.any { it.localId != localId && it.type == PaymentMethodType.EFECTIVO }
            if (conflict) {
                state.copy(errorMessage = "Ya agregaste un método de pago en efectivo")
            } else {
                state.copy(
                    paymentMethods = state.paymentMethods.map { if (it.localId == localId) it.copy(type = type) else it },
                    errorMessage = null,
                )
            }
        }
    }

    fun onPaymentMethodAmountChange(localId: String, text: String) {
        _uiState.update { state ->
            state.copy(paymentMethods = state.paymentMethods.map { if (it.localId == localId) it.copy(amountText = text) else it })
        }
    }

    fun onInstallmentCountChange(text: String) {
        val count = text.toIntOrNull()
        val state = _uiState.value
        if (count == null || count !in 1..state.maxParcialidades) {
            // Limpia el plan anterior — de lo contrario un plan válido previo seguiría satisfaciendo
            // isInstallmentsConfirmEnabled pese al error visible, permitiendo confirmar un plan que
            // ya no corresponde al número mostrado en el campo.
            _uiState.update {
                it.copy(installmentCountText = text, installments = emptyList(), errorMessage = "Número de parcialidades inválido")
            }
            return
        }
        val suggestions = calculateInstallmentsUseCase(state.total, count, state.periodicity)
        _uiState.update {
            it.copy(
                installmentCountText = text,
                installments = suggestions.map { s -> InstallmentDraftUi(s.amount.toPlainString(), s.dueDate) },
                errorMessage = null,
            )
        }
    }

    fun onPeriodicityChange(periodicity: InstallmentPeriodicity) {
        val state = _uiState.value
        val count = state.installmentCountText.toIntOrNull()
        if (count == null || count !in 1..state.maxParcialidades) {
            _uiState.update { it.copy(periodicity = periodicity) }
            return
        }
        val suggestions = calculateInstallmentsUseCase(state.total, count, periodicity)
        _uiState.update {
            it.copy(
                periodicity = periodicity,
                installments = suggestions.map { s -> InstallmentDraftUi(s.amount.toPlainString(), s.dueDate) },
            )
        }
    }

    fun onInstallmentAmountChange(index: Int, text: String) {
        _uiState.update { state ->
            state.copy(
                installments = state.installments.mapIndexed { i, installment ->
                    if (i == index) installment.copy(amountText = text) else installment
                }
            )
        }
    }

    fun onInstallmentDateChange(index: Int, newDate: Instant) {
        _uiState.update { state ->
            state.copy(
                installments = state.installments.mapIndexed { i, installment ->
                    if (i == index) installment.copy(dueDate = newDate) else installment
                }
            )
        }
    }

    fun onConfirmClick() {
        val currentState = _uiState.value
        if (currentState.isSaving) return
        val confirmable = when (currentState.mode) {
            PaymentMode.IMMEDIATE -> currentState.isImmediateConfirmEnabled
            PaymentMode.INSTALLMENTS -> currentState.isInstallmentsConfirmEnabled
        }
        if (!confirmable) return

        // isSaving se marca de forma síncrona, antes del punto de suspensión de viewModelScope.launch,
        // para que dos taps disparados en el mismo frame (antes de que la UI recomponga el botón
        // deshabilitado) no puedan ambos pasar el guard de arriba y crear dos ventas duplicadas.
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        val state = _uiState.value

        viewModelScope.launch {
            val tenant = runCatching { tenantId.first() }.getOrNull()
            if (tenant.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.")
                }
                return@launch
            }
            val paymentConfig = when (state.mode) {
                PaymentMode.IMMEDIATE -> PaymentConfig.Immediate(
                    state.paymentMethods.mapNotNull { m ->
                        m.amountText.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let { m.type to it }
                    }
                )
                PaymentMode.INSTALLMENTS -> PaymentConfig.Installments(
                    state.installments.mapNotNull { i ->
                        i.amountText.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let { InstallmentSuggestion(it, i.dueDate) }
                    }
                )
            }
            val result = runCatching {
                val folio = validateFolioUseCase(tenant)
                saleRepository.createSale(tenant, clientId, folio, state.items, paymentConfig)
            }
            result.onSuccess {
                // isSaving NO se resetea a false aquí: la pantalla está a punto de navegar fuera
                // (ver PaymentScreen/NavGraph), así que reactivar el botón solo abriría una ventana
                // para un segundo tap antes de que la navegación realmente ocurra.
                _navEvent.send(Unit)
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Algo salió mal. Inténtalo de nuevo.") }
            }
        }
    }
}
