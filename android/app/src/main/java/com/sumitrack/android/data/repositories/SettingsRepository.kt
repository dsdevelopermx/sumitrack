package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val settingsApiService: SettingsApiService,
) {
    suspend fun downloadAndCacheSettings(token: String) {
        val dtos = settingsApiService.getSettings("Bearer $token")
        val entities = dtos.map { SettingsEntity(key = it.key, value = it.value) }
        settingsDao.upsertAll(entities)
    }

    suspend fun clearLocalSettings() {
        settingsDao.deleteAll()
    }

    suspend fun getValue(key: String): String? = settingsDao.getValue(key)
}
