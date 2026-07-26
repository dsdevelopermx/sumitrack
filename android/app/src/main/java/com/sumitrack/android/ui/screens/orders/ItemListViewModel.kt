package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.OrderDraftItem
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.ProductVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ItemListUiState(
    val isLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val productIdsWithVariants: Set<String> = emptySet(),
    val cart: List<OrderDraftItem> = emptyList(),
    val variantSheetProduct: Product? = null,
    val variantSheetVariants: List<ProductVariant> = emptyList(),
) {
    val subtotal: BigDecimal get() = cart.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
    val quantityByProductId: Map<String, Int> get() =
        cart.groupBy { it.product.id }.mapValues { (_, items) -> items.sumOf { it.quantity } }
}

@HiltViewModel
class ItemListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _uiState = MutableStateFlow(ItemListUiState())
    val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

    // Cancelado y relanzado en cada toque de onProductClick para que, si el proveedor toca dos
    // productos con variantes en sucesión rápida, solo la última petición pueda actualizar el
    // sheet — evita que una respuesta lenta sobreescriba el producto/variantes visibles a medio
    // interactuar (Review Finding del code review de esta historia).
    private var variantLoadJob: Job? = null

    init {
        viewModelScope.launch {
            val tenant = tenantId.first() ?: return@launch
            launch {
                productRepository.getActiveProducts(tenant)
                    .catch { emit(emptyList()) }
                    .collect { products ->
                        _uiState.update { it.copy(products = products, isLoading = false) }
                    }
            }
            val idsWithVariants = runCatching { productRepository.getProductIdsWithVariants(tenant) }.getOrDefault(emptySet())
            _uiState.update { it.copy(productIdsWithVariants = idsWithVariants) }
        }
    }

    fun onProductClick(product: Product) {
        if (product.id in _uiState.value.productIdsWithVariants) {
            variantLoadJob?.cancel()
            variantLoadJob = viewModelScope.launch {
                val tenant = tenantId.first() ?: return@launch
                val variants = runCatching { productRepository.getVariantsForProduct(product.id, tenant) }.getOrDefault(emptyList())
                if (variants.isEmpty()) return@launch
                _uiState.update { it.copy(variantSheetProduct = product, variantSheetVariants = variants) }
            }
        } else {
            addOrIncrementItem(product, variant = null)
        }
    }

    fun onVariantSheetDismiss() {
        variantLoadJob?.cancel()
        _uiState.update { it.copy(variantSheetProduct = null, variantSheetVariants = emptyList()) }
    }

    fun onVariantConfirmed(variant: ProductVariant, quantity: Int) {
        val product = _uiState.value.variantSheetProduct ?: return
        addOrIncrementItem(product, variant, quantity)
        onVariantSheetDismiss()
    }

    private fun addOrIncrementItem(product: Product, variant: ProductVariant?, addQuantity: Int = 1) {
        _uiState.update { state ->
            val cart = state.cart
            val existingIndex = cart.indexOfFirst { it.product.id == product.id && it.variant?.id == variant?.id }
            val newCart = if (existingIndex >= 0) {
                cart.toMutableList().also {
                    val existing = it[existingIndex]
                    it[existingIndex] = existing.copy(quantity = existing.quantity + addQuantity)
                }
            } else {
                cart + OrderDraftItem(product, variant, addQuantity)
            }
            state.copy(cart = newCart)
        }
    }
}
