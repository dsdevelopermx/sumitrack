package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val settingsApiService: SettingsApiService,
) {
    suspend fun downloadAndCacheSettings(token: String) {
        val dtos = settingsApiService.getSettings("Bearer $token")
        val entities = dtos.map { SettingsEntity(key = it.key, value = it.value, syncStatus = "synced") }
        settingsDao.upsertAll(entities)
    }

    suspend fun clearLocalSettings() {
        settingsDao.deleteAll()
    }

    suspend fun getValue(key: String): String? = settingsDao.getValue(key)

    // SettingsEntity no tiene updatedAt propio (ver Dev Notes de Historia 4.4) — createdAt es el
    // campo reutilizado como timestamp de sincronización en SyncManager.toSyncDto(), así que debe
    // avanzar en cada edición para que la detección de conflictos de Historia 4.4 siga funcionando
    // sobre settings editados desde S-14.
    suspend fun updateSetting(key: String, value: String) {
        settingsDao.upsertAll(listOf(SettingsEntity(key = key, value = value, createdAt = Instant.now(), syncStatus = "pending")))
    }

    // Variante en lote de updateSetting: un único upsertAll(lista) — Room ejecuta los @Upsert de
    // una lista dentro de una sola transacción — así un guardado de varios campos a la vez (p. ej.
    // los 4 de Datos Fiscales) es atómico: si algo falla, no quedan escrituras parciales.
    suspend fun updateSettings(values: Map<String, String>) {
        val now = Instant.now()
        settingsDao.upsertAll(values.map { (key, value) -> SettingsEntity(key = key, value = value, createdAt = now, syncStatus = "pending") })
    }

    // Historia 5.2: el reconciliador de recordatorios observa este valor para reprogramar solo cuando
    // el proveedor lo cambia en S-14. Default 3 = el valor sembrado en backend.
    fun observeDiasAnticipacion(): Flow<Int> =
        settingsDao.getAll()
            .map { list -> list.find { it.key == "dias_anticipacion_recordatorio" }?.value?.toIntOrNull()?.coerceIn(1, 30) ?: 3 }
            .distinctUntilChanged()

    suspend fun getAllValues(): Map<String, String> =
        settingsDao.getAll().first().associate { it.key to it.value.orEmpty() }
}
