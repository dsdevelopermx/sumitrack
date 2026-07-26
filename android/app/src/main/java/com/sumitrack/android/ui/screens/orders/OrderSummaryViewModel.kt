package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.calculateOrderTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class OrderSummaryUiState(
    val isLoading: Boolean = true,
    val clientName: String = "",
    val items: List<OrderDraftItem> = emptyList(),
    val errorMessage: String? = null,
) {
    private val totals get() = calculateOrderTotals(items)
    val subtotal: BigDecimal get() = totals.subtotal
    val tax: BigDecimal get() = totals.tax
    val total: BigDecimal get() = totals.total
}

@HiltViewModel
class OrderSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val productRepository: ProductRepository,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    val clientId: String = checkNotNull(savedStateHandle["clientId"])
    private val cartEncoded: String = checkNotNull(savedStateHandle["cart"])

    private val _uiState = MutableStateFlow(OrderSummaryUiState())
    val uiState: StateFlow<OrderSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tenant = tenantId.first()
            if (tenant.isNullOrBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.")
                }
                return@launch
            }
            val client = runCatching { clientRepository.getClientById(clientId) }.getOrNull()
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
            _uiState.update { it.copy(isLoading = false, clientName = client?.name.orEmpty(), items = items) }
        }
    }
}
