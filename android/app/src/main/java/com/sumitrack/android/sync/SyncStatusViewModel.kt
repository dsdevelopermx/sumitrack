package com.sumitrack.android.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.sumitrack.android.di.TenantId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncWorkObserver: SyncWorkObserver,
    private val connectivityObserver: ConnectivityObserver,
    private val syncEventBus: SyncEventBus,
    private val pushSyncTrigger: PushSyncTrigger,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    // push-sync es periódico: ENQUEUED es su reposo normal entre corridas de 15 min (resetPeriodic()
    // lo deja ENQUEUED de inmediato tras cada corrida, exitosa o no — ver Dev Notes), no una
    // ejecución inminente como sí lo es para pull-sync/push-sync-now (trabajo one-time). Por eso
    // push-sync solo cuenta como "sincronizando" cuando está realmente RUNNING.
    val isSyncing: StateFlow<Boolean> = combine(
        syncWorkObserver.observe("pull-sync"),
        syncWorkObserver.observe("push-sync"),
        syncWorkObserver.observe("push-sync-now"),
    ) { pull, push, pushNow ->
        val oneTimeActive = (pull + pushNow).any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        val periodicRunning = push.any { it.state == WorkInfo.State.RUNNING }
        oneTimeActive || periodicRunning
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isOffline: StateFlow<Boolean> = connectivityObserver.isOnline
        .map { online -> !online }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // SyncEventBus es global al proceso, sin noción de tenant — se filtra aquí por el tenant activo
    // para evitar que un resultado de push de una sesión anterior (logout/login sin cancelar
    // trabajos de WorkManager en curso) aparezca como Snackbar bajo la sesión de un tenant distinto.
    val syncEvents: Flow<SyncEvent> = combine(syncEventBus.events, tenantId) { event, currentTenant ->
        event.takeIf { it.tenantId == currentTenant }
    }.filterNotNull()

    fun retryPush() = pushSyncTrigger()
}
