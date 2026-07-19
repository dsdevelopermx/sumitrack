package com.sumitrack.android.ui.screens.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitrack.android.domain.models.Sale
import com.sumitrack.android.ui.components.SaleUiStatus
import com.sumitrack.android.ui.components.StatusBadge
import com.sumitrack.android.ui.theme.PrimaryVariant
import com.sumitrack.android.ui.theme.StatusOverdue
import com.sumitrack.android.ui.theme.SyncOk
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientProfileScreen(
    onBackClick: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: ClientProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // S-12 permanece en el back stack al navegar a S-13 (editar) — Compose Navigation no
    // destruye su ViewModel, así que sin este refresco el header mostraría datos obsoletos
    // al volver de editar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.client?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    uiState.client?.let { client ->
                        IconButton(onClick = { onEditClick(client.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar cliente")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.client == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.errorMessage ?: "No pudimos cargar los datos del cliente.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            else -> {
                val client = uiState.client!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = formatAmount(client.balance),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // Fuera de alcance en esta historia: overdueAmount/creditAmount siempre
                    // null — ver "Fuera de alcance" en la historia 2.3.
                    FinancialAlertBanner(overdueAmount = null, creditAmount = null)

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Datos de contacto", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = client.phone, style = MaterialTheme.typography.bodyLarge)
                    client.address?.takeIf { it.isNotBlank() }?.let { address ->
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    client.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Órdenes abiertas", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // uiState.errorMessage no-nulo aquí (con client != null) significa que
                    // falló la carga de ventas abiertas — distinto del AC-5 "sin adeudos"
                    // real, que es openSales vacío SIN error.
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else if (uiState.openSales.isEmpty()) {
                        OpenSalesEmptyMessage()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.openSales.forEach { sale ->
                                OpenSaleRow(sale = sale)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSaleRow(sale: Sale, modifier: Modifier = Modifier) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = sale.folio,
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryVariant,
            )
            Text(
                text = formatAmount(sale.total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // Sin datos de Cobros/vencimiento disponibles todavía (Epic 3) — toda venta
            // abierta se muestra como "Parcialidades", ver Fuera de alcance en la historia.
            StatusBadge(status = SaleUiStatus.PARTIAL)
        }
    }
}

/**
 * Mensaje vacío para la sección "Órdenes abiertas". No reutiliza el componente compartido
 * `EmptyState` porque este último usa `fillMaxSize()`, lo que rompe dentro del `Column` con
 * `verticalScroll` de esta pantalla (altura no acotada).
 */
@Composable
private fun OpenSalesEmptyMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin adeudos. ¡Todo al corriente!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Banner de alerta financiera (deuda vencida / Crédito a Favor). Implementado completo para
 * que Épica 3 solo tenga que pasarle montos reales — en esta historia ambos parámetros son
 * siempre `null` (no hay datos de vencimiento ni de Crédito a Favor todavía, ver "Fuera de
 * alcance" en la historia 2.3) por lo que nunca renderiza nada en producción.
 */
@Composable
private fun FinancialAlertBanner(
    overdueAmount: BigDecimal?,
    creditAmount: BigDecimal?,
    modifier: Modifier = Modifier,
) {
    if (overdueAmount == null && creditAmount == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        overdueAmount?.let { amount ->
            Surface(
                color = StatusOverdue.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = "Tiene ${formatAmount(amount)} vencido.",
                    color = StatusOverdue,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        creditAmount?.let { amount ->
            Surface(
                color = SyncOk.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = "Tiene ${formatAmount(amount)} a su favor. Puedes aplicarlo al pago.",
                    color = SyncOk,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private fun formatAmount(amount: BigDecimal): String =
    "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
