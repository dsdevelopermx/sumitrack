package com.sumitrack.android.ui.screens.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.data.local.entities.ConflictLogEntity
import com.sumitrack.android.ui.components.EmptyState
import com.sumitrack.android.ui.theme.Error
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-MX"))

private fun entityTypeLabel(entityType: String): String = when (entityType) {
    "clientes" -> "Cliente"
    "productos" -> "Producto"
    "variantes" -> "Variante de producto"
    "ventas" -> "Venta"
    "items_venta" -> "Ítem de venta"
    "parcialidades" -> "Parcialidad"
    "cobros" -> "Cobro"
    "creditos_a_favor" -> "Crédito a favor"
    "settings" -> "Configuración"
    else -> entityType
}

private fun resolutionLabel(resolution: String?): String = when (resolution) {
    "local_wins" -> "Versión local"
    "kept_both" -> "Conservadas ambas"
    else -> "—"
}

// AC-2 requiere descubrir un conflicto "por el ícono o accediendo a S-14 § Sincronización" — esta
// pantalla es la segunda vía: lista los NO resueltos (clickeables, abren S-15) además del
// historial de resueltos que pide AC-7. También es, para las 7 entidades sin ícono en ninguna
// card (todas menos clientes/ventas), la ÚNICA vía de acceso a la resolución de un conflicto.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictLogScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onConflictClick: (entityType: String, recordId: String) -> Unit = { _, _ -> },
    viewModel: ConflictLogViewModel = hiltViewModel(),
) {
    val unresolved by viewModel.unresolvedEntries.collectAsStateWithLifecycle()
    val resolved by viewModel.resolvedEntries.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sincronización") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (unresolved.isEmpty() && resolved.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Sync,
                message = "Aún no hay conflictos de sincronización.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp, vertical = 64.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (unresolved.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sin resolver",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    items(unresolved, key = { it.id }) { entry ->
                        ConflictLogRow(
                            entry = entry,
                            onClick = { onConflictClick(entry.entityType, entry.recordId) },
                        )
                    }
                }
                if (resolved.isNotEmpty()) {
                    item {
                        Text(
                            text = "Resueltos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(resolved, key = { it.id }) { entry ->
                        ConflictLogRow(entry = entry, onClick = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictLogRow(entry: ConflictLogEntity, onClick: (() -> Unit)?) {
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            ConflictLogRowContent(entry)
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            ConflictLogRowContent(entry)
        }
    }
}

@Composable
private fun ConflictLogRowContent(entry: ConflictLogEntity) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = entityTypeLabel(entry.entityType), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Detectado: ${dateFormatter.format(entry.detectedAt.atZone(ZoneId.systemDefault()))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.resolvedAt != null) {
            Text(
                text = "Resolución: ${resolutionLabel(entry.resolution)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Toca para resolver",
                style = MaterialTheme.typography.bodySmall,
                color = Error,
            )
        }
    }
}
