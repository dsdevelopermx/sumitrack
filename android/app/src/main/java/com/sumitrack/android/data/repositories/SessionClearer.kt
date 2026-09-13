package com.sumitrack.android.data.repositories

// Interfaz angosta sobre SessionManager.clearToken() — mismo criterio que FolioBaselineStore:
// permite que SettingsViewModel (botón "Cerrar sesión") sea testeable en JVM puro con un fake,
// sin depender de SessionManager (que envuelve un Context/DataStore real).
interface SessionClearer {
    suspend fun clearToken()
}
