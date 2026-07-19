package com.sumitrack.android.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.ProductRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    @TenantId tenantId: Flow<String?>,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<Product>> = tenantId
        .flatMapLatest { tenant ->
            if (tenant.isNullOrBlank()) flowOf(emptyList()) else productRepository.getAllProducts(tenant)
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
