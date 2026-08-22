package com.sumitrack.android.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Canal explícito de eventos de sync (Historia 4.3) — WorkManager no expone SUCCEEDED/FAILED como
// estados observables para trabajo periódico (confirmado en el bytecode de WorkerWrapper: ambos
// resultados pasan por resetPeriodic(), que reinicia a ENQUEUED de inmediato), así que PushWorker
// emite aquí directamente en vez de que la UI intente inferir el resultado desde WorkInfo.
//
// onBufferOverflow = DROP_OLDEST (en vez del SUSPEND por default) — sin colector activo (app en
// background, o el colector de MainScreen ocupado mostrando un Snackbar Indefinite), emit() nunca
// debe bloquear a PushWorker.doWork(): un Worker suspendido indefinidamente esperando espacio en
// el buffer arriesga que el sistema lo mate por exceder su tiempo de ejecución permitido. Perder
// un evento no consumido es aceptable — son notificaciones de UI efímeras, no estado crítico.
@Singleton
class SyncEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<SyncEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    fun emit(event: SyncEvent) {
        _events.tryEmit(event)
    }
}
