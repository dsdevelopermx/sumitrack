package com.sumitrack.android.ui.screens.orders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.domain.models.SaleStatus
import com.sumitrack.android.ui.components.EmptyState
import com.sumitrack.android.ui.components.FilterChipData
import com.sumitrack.android.ui.components.FilterChipRow
import com.sumitrack.android.ui.components.OrderCard
import kotlinx.coroutines.launch

private val STATUS_CHIPS = listOf(
    FilterChipData(SaleStatus.PENDING, "Pendiente"),
    FilterChipData(SaleStatus.PARTIAL, "Parcial"),
    FilterChipData(SaleStatus.PAID, "Pagada"),
    FilterChipData(SaleStatus.CANCELLED, "Cancelada"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    modifier: Modifier = Modifier,
    viewModel: OrderListViewModel = hiltViewModel(),
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()

    var searchActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = searchActive) { searchActive = false }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("+") },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Nueva Orden") },
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Nueva orden — disponible próximamente") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        onSearch = { viewModel.onSearchQueryChange(it) },
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        placeholder = { Text("Buscar por folio o cliente...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Buscar",
                            )
                        },
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            {
                                IconButton(
                                    onClick = {
                                        viewModel.onSearchClear()
                                        searchActive = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Limpiar búsqueda",
                                    )
                                }
                            }
                        } else null,
                    )
                },
                expanded = searchActive,
                onExpandedChange = { searchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { }

            FilterChipRow(
                chips = STATUS_CHIPS,
                selectedChip = statusFilter,
                onChipSelected = { viewModel.onStatusFilterSelected(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { /* TODO Historia 4.x: trigger sync */ },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (orders.isEmpty()) {
                    val emptyMessage = if (searchQuery.isBlank() && statusFilter == null) {
                        "Aún no hay órdenes. Toca + para empezar."
                    } else {
                        "No se encontraron órdenes con esos criterios."
                    }
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        message = emptyMessage,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 64.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(orders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onClick = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Detalle de orden — disponible próximamente")
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
