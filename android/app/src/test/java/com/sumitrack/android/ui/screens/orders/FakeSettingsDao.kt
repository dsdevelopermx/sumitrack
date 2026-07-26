package com.sumitrack.android.ui.screens.orders

import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsDao : SettingsDao {

    private val allFlow = MutableStateFlow<List<SettingsEntity>>(emptyList())

    override suspend fun upsertAll(settings: List<SettingsEntity>) {
        val byKey = allFlow.value.associateBy { it.key }.toMutableMap()
        settings.forEach { byKey[it.key] = it }
        allFlow.value = byKey.values.toList()
    }

    override fun getAll(): Flow<List<SettingsEntity>> = allFlow

    override suspend fun getValue(key: String): String? = allFlow.value.find { it.key == key }?.value

    override suspend fun deleteAll() {
        allFlow.value = emptyList()
    }
}
