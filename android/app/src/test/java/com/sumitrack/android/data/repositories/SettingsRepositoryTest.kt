package com.sumitrack.android.data.repositories

import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var fakeDao: FakeSettingsDao
    private lateinit var repository: SettingsRepository

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    @Before
    fun setUp() {
        fakeDao = FakeSettingsDao()
        repository = SettingsRepository(fakeDao, noOpApiService)
    }

    @Test
    fun `updateSetting inserta el valor con sync_status pending`() = runTest {
        repository.updateSetting("negocio_nombre", "Tortillería Don Beto")

        val stored = fakeDao.getByKey("negocio_nombre")
        assertEquals("Tortillería Don Beto", stored?.value)
        assertEquals("pending", stored?.syncStatus)
    }

    @Test
    fun `updateSetting sobre un valor ya synced lo vuelve a marcar pending y avanza createdAt`() = runTest {
        fakeDao.upsertAll(
            listOf(
                com.sumitrack.android.data.local.entities.SettingsEntity(
                    key = "serie_folio",
                    value = "A",
                    createdAt = java.time.Instant.ofEpochMilli(1_000_000L),
                    syncStatus = "synced",
                ),
            ),
        )

        repository.updateSetting("serie_folio", "B")

        val stored = fakeDao.getByKey("serie_folio")
        assertEquals("B", stored?.value)
        assertEquals("pending", stored?.syncStatus)
        assertTrue(stored!!.createdAt.isAfter(java.time.Instant.ofEpochMilli(1_000_000L)))
    }

    @Test
    fun `getAllValues devuelve un mapa key a value de todos los settings presentes`() = runTest {
        repository.updateSetting("max_parcialidades", "15")
        repository.updateSetting("serie_folio", "A")

        val values = repository.getAllValues()

        assertEquals(mapOf("max_parcialidades" to "15", "serie_folio" to "A"), values)
    }

    @Test
    fun `getAllValues no incluye keys que nunca se guardaron`() = runTest {
        repository.updateSetting("max_parcialidades", "15")

        val values = repository.getAllValues()

        assertTrue("negocio_nombre" !in values)
        assertEquals(1, values.size)
    }

    @Test
    fun `updateSettings guarda varias keys en un solo lote, todas pending`() = runTest {
        repository.updateSettings(
            mapOf(
                "negocio_nombre" to "Tortillería Don Beto",
                "negocio_rfc" to "XAXX010101000",
            ),
        )

        assertEquals("Tortillería Don Beto", fakeDao.getByKey("negocio_nombre")?.value)
        assertEquals("pending", fakeDao.getByKey("negocio_nombre")?.syncStatus)
        assertEquals("XAXX010101000", fakeDao.getByKey("negocio_rfc")?.value)
        assertEquals("pending", fakeDao.getByKey("negocio_rfc")?.syncStatus)
    }
}
