package com.sumitrack.android.ui.screens.conflict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.local.dao.ConflictLogDao
import com.sumitrack.android.data.local.entities.ConflictLogEntity
import com.sumitrack.android.di.TenantId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

// AC-2 requiere que S-14 § Sincronización sea también una vía de descubrimiento para conflictos
// SIN resolver (no solo historial de resueltos, AC-7) — corregido en la ronda de revisión: los no
// resueltos ahora también se exponen aquí, clickeables, para navegar a S-15.
@HiltViewModel
class ConflictLogViewModel @Inject constructor(
    private val conflictLogDao: ConflictLogDao,
    @TenantId tenantId: Flow<String?>,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val unresolvedEntries: StateFlow<List<ConflictLogEntity>> = tenantId
        .flatMapLatest { tenant ->
            if (tenant.isNullOrBlank()) flowOf(emptyList()) else conflictLogDao.getUnresolvedAsFlow(tenant)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val resolvedEntries: StateFlow<List<ConflictLogEntity>> = tenantId
        .flatMapLatest { tenant ->
            if (tenant.isNullOrBlank()) flowOf(emptyList()) else conflictLogDao.getResolvedAsFlow(tenant)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
