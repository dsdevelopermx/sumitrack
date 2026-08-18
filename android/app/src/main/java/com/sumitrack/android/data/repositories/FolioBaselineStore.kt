package com.sumitrack.android.data.repositories

import kotlinx.coroutines.flow.Flow

// Interfaz angosta sobre el piso del contador de folios (SessionManager.folioBaseline) — permite
// que PullService y ValidateFolioUseCase sean testeables en JVM puro con un fake, sin depender de
// SessionManager (que envuelve un Context/DataStore real).
interface FolioBaselineStore {
    val folioBaseline: Flow<Int?>
    suspend fun saveFolioBaseline(count: Int)
}
