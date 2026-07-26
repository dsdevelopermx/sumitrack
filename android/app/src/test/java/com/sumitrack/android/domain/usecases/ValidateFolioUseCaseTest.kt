package com.sumitrack.android.domain.usecases

import com.sumitrack.android.data.local.entities.SaleEntity
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.ui.screens.clients.FakeSaleDao
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ValidateFolioUseCaseTest {

    private lateinit var fakeSaleDao: FakeSaleDao
    private lateinit var fakeSettingsDao: FakeSettingsDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ValidateFolioUseCase

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    @Before
    fun setUp() {
        fakeSaleDao = FakeSaleDao()
        fakeSettingsDao = FakeSettingsDao()
        settingsRepository = SettingsRepository(fakeSettingsDao, noOpApiService)
        useCase = ValidateFolioUseCase(fakeSaleDao, settingsRepository)
    }

    private fun sale(id: String, tenantId: String = "tenant-1") = SaleEntity(
        id = id,
        fkTenant = tenantId,
        fkClient = "client-1",
        folio = "A$id",
        total = BigDecimal("10.00"),
        status = "paid",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = "synced",
    )

    @Test
    fun `folio uses the serie from Settings`() = runTest {
        fakeSettingsDao.upsertAll(listOf(SettingsEntity(key = "serie_folio", value = "B")))

        val folio = useCase("tenant-1")

        assertEquals("B1", folio)
    }

    @Test
    fun `folio defaults to serie A when Settings has no value`() = runTest {
        val folio = useCase("tenant-1")
        assertEquals("A1", folio)
    }

    @Test
    fun `folio counts existing sales for the tenant correctly`() = runTest {
        fakeSaleDao.setSales(listOf(sale("s1"), sale("s2"), sale("s3")))

        val folio = useCase("tenant-1")

        assertEquals("A4", folio)
    }

    @Test
    fun `tenant without previous sales gets the first folio`() = runTest {
        val folio = useCase("tenant-1")
        assertEquals("A1", folio)
    }

    @Test
    fun `folio excludes sales from a different tenant from the count`() = runTest {
        fakeSaleDao.setSales(listOf(sale("s1", tenantId = "tenant-2"), sale("s2", tenantId = "tenant-2")))

        val folio = useCase("tenant-1")

        assertEquals("A1", folio)
    }
}
