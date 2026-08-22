package com.sumitrack.android.sync

// Cada evento lleva el tenantId al que pertenece — SyncEventBus es un singleton global al
// proceso, sin scope de tenant; sin esto, un resultado de push de una sesión anterior (logout
// seguido de login como otro tenant, sin que WorkManager cancele el trabajo en curso) podría
// aparecer como Snackbar bajo la sesión de un tenant distinto (ver Review Findings, Historia 4.3).
sealed class SyncEvent {
    abstract val tenantId: String

    data class Success(override val tenantId: String) : SyncEvent()
    data class Failure(override val tenantId: String) : SyncEvent()
}
