package com.sumitrack.android.ui.screens.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.di.TenantId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ProductFormUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val price: String = "",
    val taxRate: String = "",
    val isActive: Boolean = true,
    val variantNames: List<String> = emptyList(),
    val newVariantName: String = "",
    val nameError: Boolean = false,
    val priceError: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSaveEnabled: Boolean get() = name.isNotBlank() && price.isNotBlank() && !isSaving
}

private const val GENERIC_ERROR = "Algo salió mal. Inténtalo de nuevo."
private const val NO_TENANT_ERROR = "No se pudo determinar tu negocio. Vuelve a iniciar sesión."
private val MAX_TAX_RATE = BigDecimal(100)

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    // Normaliza productId="" (alcanzable vía ruta product_form?productId=) a null — modo alta.
    private val productId: String? = savedStateHandle.get<String>("productId")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(ProductFormUiState(isEditMode = productId != null))
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<Unit>(Channel.CONFLATED)
    val navEvent = _navEvent.receiveAsFlow()

    init {
        productId?.let { id ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val fkTenant = runCatching { tenantId.first() }.getOrNull()
                if (fkTenant.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = NO_TENANT_ERROR)
                    return@launch
                }
                val product = runCatching { productRepository.getProductById(id, fkTenant) }.getOrNull()
                if (product == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No pudimos cargar los datos del producto.",
                    )
                    return@launch
                }
                val variants = runCatching { productRepository.getVariantsForProduct(id, fkTenant) }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    name = product.name,
                    price = product.price.toPlainString(),
                    taxRate = product.taxRate.toPlainString(),
                    isActive = product.isActive,
                    variantNames = variants.map { it.name },
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameError = false, errorMessage = null)
    }

    fun onPriceChange(value: String) {
        _uiState.value = _uiState.value.copy(price = value, priceError = false, errorMessage = null)
    }

    fun onTaxRateChange(value: String) {
        _uiState.value = _uiState.value.copy(taxRate = value, errorMessage = null)
    }

    fun onActiveToggle(value: Boolean) {
        _uiState.value = _uiState.value.copy(isActive = value)
    }

    fun onNewVariantNameChange(value: String) {
        _uiState.value = _uiState.value.copy(newVariantName = value)
    }

    fun onAddVariantClick() {
        val name = _uiState.value.newVariantName.trim()
        if (name.isBlank()) return
        _uiState.value = _uiState.value.copy(
            variantNames = _uiState.value.variantNames + name,
            newVariantName = "",
        )
    }

    fun onRemoveVariantClick(index: Int) {
        _uiState.value = _uiState.value.copy(
            variantNames = _uiState.value.variantNames.filterIndexed { i, _ -> i != index },
        )
    }

    // Rechaza notación científica y valores fuera de la precisión/escala de NUMERIC(18,6) —
    // Review Finding: el precio/impuesto no tenía cota alguna, contradiciendo el AC-2.
    private fun parseBoundedDecimal(input: String): BigDecimal? {
        if (input.contains('e', ignoreCase = true)) return null
        val value = runCatching { BigDecimal(input) }.getOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        if (value.scale() > 6) return null
        if (value.precision() > 18) return null
        return value
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isSaving) return

        val name = state.name.trim()
        val priceInput = state.price.trim()
        val nameErr = name.isBlank()
        val price = if (priceInput.isBlank()) null else parseBoundedDecimal(priceInput)
        val priceErr = price == null

        if (nameErr || priceErr) {
            _uiState.value = state.copy(
                nameError = nameErr,
                priceError = priceErr,
                errorMessage = when {
                    nameErr -> "El nombre es obligatorio"
                    else -> "Ingresa un precio válido"
                },
            )
            return
        }

        val taxRateInput = state.taxRate.trim()
        val taxRate = if (taxRateInput.isBlank()) {
            BigDecimal.ZERO
        } else {
            parseBoundedDecimal(taxRateInput)?.takeIf { it <= MAX_TAX_RATE }
        }
        if (taxRate == null) {
            _uiState.value = state.copy(errorMessage = "Ingresa un impuesto válido (0-100%)")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)

            val fkTenant = runCatching { tenantId.first() }.getOrNull()
            if (fkTenant.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = NO_TENANT_ERROR)
                return@launch
            }

            if (state.isEditMode && productId != null) {
                val saved = runCatching {
                    productRepository.updateProduct(
                        id = productId,
                        tenantId = fkTenant,
                        name = name,
                        price = price!!,
                        taxRate = taxRate,
                        isActive = state.isActive,
                        variantNames = state.variantNames,
                    )
                }.getOrNull()
                when (saved) {
                    true -> {
                        _uiState.value = _uiState.value.copy(isSaving = false)
                        _navEvent.send(Unit)
                    }
                    false -> _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "Este producto ya no existe. Puede que se haya eliminado en otro dispositivo.",
                    )
                    null -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = GENERIC_ERROR)
                }
                return@launch
            }

            val created = runCatching {
                productRepository.createProduct(name, price!!, taxRate, state.variantNames, fkTenant)
            }
            created.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                _navEvent.send(Unit)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = GENERIC_ERROR)
            }
        }
    }
}
