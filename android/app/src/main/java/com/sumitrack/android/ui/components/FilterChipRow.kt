package com.sumitrack.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FilterChipData<T>(val id: T, val label: String)

@Composable
fun <T> FilterChipRow(
    chips: List<FilterChipData<T>>,
    selectedChip: T?,
    onChipSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips) { chip ->
            val isSelected = selectedChip == chip.id
            FilterChip(
                selected = isSelected,
                onClick = { onChipSelected(if (isSelected) null else chip.id) },
                label = { Text(chip.label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
}
