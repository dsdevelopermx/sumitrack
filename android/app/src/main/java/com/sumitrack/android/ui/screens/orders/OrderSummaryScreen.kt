package com.sumitrack.android.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.domain.models.OrderDraftItem
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSummaryScreen(
    onEditClick: () -> Unit,
    onGoToPaymentClick: (clientId: String, cartEncoded: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.clientName.ifBlank { "Resumen de orden" }) },
                navigationIcon = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Editar")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { "${it.product.id}-${it.variant?.id}" }) { item ->
                        OrderSummaryItemRow(item)
                    }
                }

                HorizontalDivider()

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    SummaryLine("Subtotal", uiState.subtotal)
                    SummaryLine("Impuestos", uiState.tax)
                    SummaryLine("Total", uiState.total, emphasize = true)
                    Button(
                        onClick = { onGoToPaymentClick(viewModel.clientId, CartRouteCodec.encode(uiState.items)) },
                        enabled = uiState.items.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text("Ir a Pagar")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryItemRow(item: OrderDraftItem) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.name, style = MaterialTheme.typography.bodyLarge)
            val variantName = item.variant?.name
            Text(
                text = if (variantName != null) "$variantName · x${item.quantity}" else "x${item.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatAmount(item.subtotal), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SummaryLine(label: String, amount: BigDecimal, emphasize: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
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

private fun formatAmount(amount: BigDecimal): String =
    "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
