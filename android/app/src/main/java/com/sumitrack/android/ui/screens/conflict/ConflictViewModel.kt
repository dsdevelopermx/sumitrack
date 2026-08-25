package com.sumitrack.android.ui.screens.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.local.dao.ClientDao
import com.sumitrack.android.data.local.dao.ConflictLogDao
import com.sumitrack.android.data.local.dao.CreditBalanceDao
import com.sumitrack.android.data.local.dao.InstallmentDao
import com.sumitrack.android.data.local.dao.PaymentDao
import com.sumitrack.android.data.local.dao.ProductDao
import com.sumitrack.android.data.local.dao.ProductVariantDao
import com.sumitrack.android.data.local.dao.SaleDao
import com.sumitrack.android.data.local.dao.SaleItemDao
import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.ConflictLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConflictUiState(
    val logEntry: ConflictLogEntity? = null,
    val isLoading: Boolean = true,
    val isResolving: Boolean = false,
    val isResolved: Boolean = false,
)

// Resultado de aplicar una resolución al registro real — distingue "el registro ya no existe" de
// "se aplicó y no se pidió duplicado", que antes eran indistinguibles (ambos retornaban null) y
// causaban que un registro faltante igual se marcara como conflicto resuelto sin haber cambiado
// nada (Review Finding).
private sealed class ResolutionOutcome {
    data class Applied(val duplicateId: String?) : ResolutionOutcome()
    data object RecordNotFound : ResolutionOutcome()
}

// Dispatch explícito por entityType (when con los 9 casos) — mismo patrón ya usado en
// SyncManager.pushPending/PullService, no una abstracción genérica entre las 9 entidades.
@HiltViewModel
class ConflictViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conflictLogDao: ConflictLogDao,
    private val clientDao: ClientDao,
    private val productDao: ProductDao,
    private val productVariantDao: ProductVariantDao,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val installmentDao: InstallmentDao,
    private val paymentDao: PaymentDao,
    private val creditBalanceDao: CreditBalanceDao,
    private val settingsDao: SettingsDao,
) : ViewModel() {

    private val entityType: String = checkNotNull(savedStateHandle["entityType"])
    private val recordId: String = checkNotNull(savedStateHandle["recordId"])

    private val _uiState = MutableStateFlow(ConflictUiState())
    val uiState: StateFlow<ConflictUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = conflictLogDao.getLatestUnresolvedFor(entityType, recordId)
            _uiState.value = ConflictUiState(logEntry = entry, isLoading = false)
        }
    }

    fun resolveKeepLocal() = resolve(keepBoth = false)

    fun resolveKeepBoth() = resolve(keepBoth = true)

    // Guard de re-entrada: un doble-tap antes de que _uiState se actualice ya no dispara
    // applyResolution dos veces (Review Finding — creaba dos registros duplicados para un mismo
    // conflicto). isResolving se setea de forma síncrona, antes de lanzar la corrutina.
    private fun resolve(keepBoth: Boolean) {
        val state = _uiState.value
        if (state.isResolving || state.isResolved) return
        val entry = state.logEntry ?: return
        _uiState.value = state.copy(isResolving = true)
        viewModelScope.launch {
            val now = Instant.now()
            when (val outcome = applyResolution(entry, now, keepBoth)) {
                is ResolutionOutcome.Applied -> {
                    conflictLogDao.update(
                        entry.copy(
                            resolvedAt = now,
                            resolution = if (keepBoth) "kept_both" else "local_wins",
                            duplicateRecordId = outcome.duplicateId,
                        )
                    )
                    _uiState.value = _uiState.value.copy(isResolving = false, isResolved = true)
                }
                ResolutionOutcome.RecordNotFound -> {
                    // El registro subyacente ya no existe — NO se marca el log como resuelto (antes
                    // sí se marcaba, dejando el conflicto "resuelto" sin haber cambiado nada real).
                    // El diálogo se re-habilita; el usuario puede cerrarlo (AC-6, queda en cola).
                    _uiState.value = _uiState.value.copy(isResolving = false)
                }
            }
        }
    }

    // "Usar versión local": bumpea updatedAt a ahora y vuelve a pending — en el siguiente push el
    // cliente ya es más nuevo que el servidor, así que sobrescribe normalmente (ver Dev Notes de
    // la historia: sin este bump, el próximo push volvería a chocar con el mismo timestamp).
    // "Conservar ambas": hace lo mismo con el original Y ADEMÁS crea una copia con id nuevo,
    // también pending — la versión del servidor que se pierde en el original queda preservada
    // únicamente en el snapshot del log (auditoría), no en un tercer registro vivo.
    private suspend fun applyResolution(entry: ConflictLogEntity, now: Instant, keepBoth: Boolean): ResolutionOutcome {
        return when (entry.entityType) {
            "clientes" -> {
                val current = clientDao.getById(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                clientDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                clientDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "productos" -> {
                val current = productDao.getById(entry.recordId, entry.fkTenant) ?: return ResolutionOutcome.RecordNotFound
                productDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                productDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "variantes" -> {
                val current = productVariantDao.getById(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                productVariantDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                productVariantDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "ventas" -> {
                val current = saleDao.getById(entry.recordId, entry.fkTenant) ?: return ResolutionOutcome.RecordNotFound
                saleDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                saleDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "items_venta" -> {
                val current = saleItemDao.getById(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                saleItemDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                saleItemDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "parcialidades" -> {
                val current = installmentDao.getById(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                installmentDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                installmentDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "cobros" -> {
                val current = paymentDao.getById(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                paymentDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                paymentDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "creditos_a_favor" -> {
                val current = creditBalanceDao.getById(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                creditBalanceDao.upsertAll(listOf(current.copy(syncStatus = "pending", updatedAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now, syncStatus = "pending")
                creditBalanceDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.id)
            }
            "settings" -> {
                // SettingsEntity no tiene updatedAt propio (ver Dev Notes) — se bumpea createdAt,
                // consistente con toSyncDto() reutilizándolo para ambos campos del DTO.
                val current = settingsDao.getByKey(entry.recordId) ?: return ResolutionOutcome.RecordNotFound
                settingsDao.upsertAll(listOf(current.copy(syncStatus = "pending", createdAt = now)))
                if (!keepBoth) return ResolutionOutcome.Applied(null)
                val duplicate = current.copy(key = UUID.randomUUID().toString(), createdAt = now, syncStatus = "pending")
                settingsDao.upsertAll(listOf(duplicate))
                ResolutionOutcome.Applied(duplicate.key)
            }
            else -> ResolutionOutcome.RecordNotFound
        }
    }
}
