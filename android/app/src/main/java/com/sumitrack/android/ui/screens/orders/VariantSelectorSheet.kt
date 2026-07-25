package com.sumitrack.android.ui.screens.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sumitrack.android.domain.models.Product
import com.sumitrack.android.domain.models.ProductVariant
import com.sumitrack.android.ui.components.FilterChipData
import com.sumitrack.android.ui.components.FilterChipRow
import com.sumitrack.android.ui.components.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantSelectorSheet(
    product: Product,
    variants: List<ProductVariant>,
    onDismiss: () -> Unit,
    onConfirm: (variant: ProductVariant, quantity: Int) -> Unit,
) {
    var selectedVariantId by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableIntStateOf(1) }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            FilterChipRow(
                chips = variants.map { FilterChipData(it.id, it.name) },
                selectedChip = selectedVariantId,
                onChipSelected = { selectedVariantId = it },
            )
            Spacer(Modifier.height(16.dp))
            QuantityStepper(quantity = quantity, onQuantityChange = { quantity = it })
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    // firstOrNull en vez de first: defensa adicional si variants cambia bajo el
                    // sheet (ver ItemListViewModel.onProductClick, ya cancela la carga anterior,
                    // pero esto evita un crash si algún caller futuro no lo hace).
                    val variant = variants.firstOrNull { it.id == selectedVariantId } ?: return@Button
                    onConfirm(variant, quantity)
                },
                enabled = selectedVariantId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Agregar a la orden")
            }
        }
    }
}
