package com.sumitrack.android.ui.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.di.TenantId
import com.sumitrack.android.domain.models.OrderSummary
import com.sumitrack.android.domain.models.SaleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<SaleStatus?>(null)
    val statusFilter: StateFlow<SaleStatus?> = _statusFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val orders: StateFlow<List<OrderSummary>> = combine(
        tenantId, _searchQuery.debounce(200), _statusFilter,
    ) { tenant, query, filter -> Triple(tenant, query, filter) }
        .flatMapLatest { (tenant, query, filter) ->
            if (tenant.isNullOrBlank()) flowOf(emptyList())
            else saleRepository.getOrdersForTenant(tenant, filter, query)
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSearchClear() {
        _searchQuery.value = ""
    }

    fun onStatusFilterSelected(status: SaleStatus?) {
        _statusFilter.value = status
    }
}
