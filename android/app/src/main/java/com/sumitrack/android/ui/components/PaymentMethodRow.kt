package com.sumitrack.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.sumitrack.android.domain.models.PaymentMethodType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodRow(
    type: PaymentMethodType,
    amountText: String,
    onTypeChange: (PaymentMethodType) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it },
            modifier = Modifier.widthIn(min = 120.dp),
        ) {
            OutlinedTextField(
                value = paymentMethodLabel(type),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                PaymentMethodType.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(paymentMethodLabel(option)) },
                        onClick = {
                            onTypeChange(option)
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Eliminar método de pago ${paymentMethodLabel(type)}")
        }
    }
}

private fun paymentMethodLabel(type: PaymentMethodType): String = when (type) {
    PaymentMethodType.EFECTIVO -> "Efectivo"
    PaymentMethodType.TRANSFERENCIA -> "Transferencia"
    PaymentMethodType.TARJETA -> "Tarjeta"
}
