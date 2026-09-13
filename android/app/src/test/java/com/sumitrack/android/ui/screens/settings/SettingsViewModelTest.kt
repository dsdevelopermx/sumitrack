package com.sumitrack.android.ui.screens.settings

import com.sumitrack.android.data.local.dao.SettingsDao
import com.sumitrack.android.data.local.entities.SettingsEntity
import com.sumitrack.android.data.remote.api.SettingsApiService
import com.sumitrack.android.data.remote.dto.SettingDto
import com.sumitrack.android.data.repositories.SessionClearer
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.sync.PullSyncTrigger
import com.sumitrack.android.ui.screens.orders.FakeSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeSettingsDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fakeSessionClearer: FakeSessionClearer
    private lateinit var fakePullSyncTrigger: FakePullSyncTrigger

    private val noOpApiService = object : SettingsApiService {
        override suspend fun getSettings(token: String): List<SettingDto> = emptyList()
    }

    private class FakeSessionClearer : SessionClearer {
        var cleared = false
        override suspend fun clearToken() {
            cleared = true
        }
    }

    private class FakePullSyncTrigger : PullSyncTrigger {
        var invocations = 0
        override fun invoke() {
            invocations++
        }
    }

    // Delega todo en un FakeSettingsDao real salvo upsertAll, que siempre lanza — simula un error
    // de Room (p. ej. disco lleno) durante un guardado desde SettingsViewModel.
    private class ThrowingSettingsDao(private val delegate: FakeSettingsDao) : SettingsDao by delegate {
        override suspend fun upsertAll(settings: List<SettingsEntity>) {
            throw RuntimeException("boom")
        }
    }

    // Delega todo en un FakeSettingsDao real, contando cuántas veces se invoca upsertAll — usado
    // para probar que un doble-tap antes de que la corrutina avance no dispara un segundo guardado.
    private class CountingSettingsDao(private val delegate: FakeSettingsDao) : SettingsDao by delegate {
        var upsertAllCallCount = 0
        override suspend fun upsertAll(settings: List<SettingsEntity>) {
            upsertAllCallCount++
            delegate.upsertAll(settings)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeSettingsDao()
        settingsRepository = SettingsRepository(fakeDao, noOpApiService)
        fakeSessionClearer = FakeSessionClearer()
        fakePullSyncTrigger = FakePullSyncTrigger()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(fakeSessionClearer, settingsRepository, fakePullSyncTrigger)

    @Test
    fun `carga los 7 valores existentes al iniciar`() = runTest {
        fakeDao.upsertAll(
            listOf(
                SettingsEntity(key = "negocio_nombre", value = "Tortillería Don Beto", syncStatus = "synced"),
                SettingsEntity(key = "negocio_rfc", value = "XAXX010101000", syncStatus = "synced"),
                SettingsEntity(key = "negocio_direccion", value = "Calle 1 #23", syncStatus = "synced"),
                SettingsEntity(key = "negocio_telefono", value = "5512345678", syncStatus = "synced"),
                SettingsEntity(key = "max_parcialidades", value = "15", syncStatus = "synced"),
                SettingsEntity(key = "serie_folio", value = "A", syncStatus = "synced"),
                SettingsEntity(key = "dias_anticipacion_recordatorio", value = "3", syncStatus = "synced"),
            ),
        )

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("Tortillería Don Beto", state.businessName)
        assertEquals("XAXX010101000", state.rfc)
        assertEquals("Calle 1 #23", state.address)
        assertEquals("5512345678", state.phone)
        assertEquals("15", state.maxParcialidades)
        assertEquals("A", state.serieFolio)
        assertEquals("3", state.diasAnticipacion)
    }

    @Test
    fun `guardar Datos Fiscales persiste las 4 keys como pending`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBusinessNameChange("Tortillería Don Beto")
        vm.onRfcChange("XAXX010101000")
        vm.onAddressChange("Calle 1 #23")
        vm.onPhoneChange("5512345678")
        vm.onSaveFiscalClick()
        advanceUntilIdle()

        assertEquals("Tortillería Don Beto", fakeDao.getByKey("negocio_nombre")?.value)
        assertEquals("pending", fakeDao.getByKey("negocio_nombre")?.syncStatus)
        assertEquals("XAXX010101000", fakeDao.getByKey("negocio_rfc")?.value)
        assertEquals("Calle 1 #23", fakeDao.getByKey("negocio_direccion")?.value)
        assertEquals("5512345678", fakeDao.getByKey("negocio_telefono")?.value)
        assertEquals(true, vm.uiState.value.fiscalSaved)
    }

    @Test
    fun `guardar Parametros de Venta con valores validos persiste las 3 keys`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("B")
        vm.onDiasAnticipacionChange("7")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("10", fakeDao.getByKey("max_parcialidades")?.value)
        assertEquals("pending", fakeDao.getByKey("max_parcialidades")?.syncStatus)
        assertEquals("B", fakeDao.getByKey("serie_folio")?.value)
        assertEquals("7", fakeDao.getByKey("dias_anticipacion_recordatorio")?.value)
        assertEquals(true, vm.uiState.value.paramsSaved)
        assertNull(vm.uiState.value.maxParcialidadesError)
        assertNull(vm.uiState.value.serieFolioError)
        assertNull(vm.uiState.value.diasAnticipacionError)
    }

    @Test
    fun `max_parcialidades en 0 no guarda y muestra error de rango`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("0")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("Debe ser un número entre 1 y 15", vm.uiState.value.maxParcialidadesError)
        assertEquals(null, fakeDao.getByKey("max_parcialidades"))
        assertEquals(false, vm.uiState.value.paramsSaved)
    }

    @Test
    fun `max_parcialidades en 16 no guarda y muestra error de rango`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("16")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("Debe ser un número entre 1 y 15", vm.uiState.value.maxParcialidadesError)
        assertEquals(null, fakeDao.getByKey("max_parcialidades"))
    }

    @Test
    fun `serie_folio vacio no guarda y muestra error de longitud`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("Debe tener entre 1 y 5 caracteres", vm.uiState.value.serieFolioError)
        assertEquals(null, fakeDao.getByKey("serie_folio"))
    }

    @Test
    fun `serie_folio de 6 caracteres no guarda y muestra error de longitud`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("ABCDEF")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("Debe tener entre 1 y 5 caracteres", vm.uiState.value.serieFolioError)
        assertEquals(null, fakeDao.getByKey("serie_folio"))
    }

    @Test
    fun `dias_anticipacion_recordatorio en 0 no guarda y muestra error de rango`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("0")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("Debe ser un número entre 1 y 30", vm.uiState.value.diasAnticipacionError)
        assertEquals(null, fakeDao.getByKey("dias_anticipacion_recordatorio"))
    }

    @Test
    fun `dias_anticipacion_recordatorio en 31 no guarda y muestra error de rango`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("31")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals("Debe ser un número entre 1 y 30", vm.uiState.value.diasAnticipacionError)
        assertEquals(null, fakeDao.getByKey("dias_anticipacion_recordatorio"))
    }

    @Test
    fun `isParamsSaveEnabled se deshabilita tras un error de validacion y se rehabilita al corregir (AC-6)`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.isParamsSaveEnabled)

        vm.onMaxParcialidadesChange("0")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()

        assertEquals(false, vm.uiState.value.isParamsSaveEnabled)

        vm.onMaxParcialidadesChange("10")

        assertEquals(true, vm.uiState.value.isParamsSaveEnabled)
    }

    @Test
    fun `onSyncNowClick invoca el PullSyncTrigger exactamente una vez`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSyncNowClick()

        assertEquals(1, fakePullSyncTrigger.invocations)
    }

    @Test
    fun `onLogoutConfirm limpia settings locales y el token de sesion`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLogoutClick()
        assertEquals(true, vm.uiState.value.showLogoutDialog)

        vm.onLogoutConfirm()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.showLogoutDialog)
        assertEquals(true, fakeSessionClearer.cleared)
    }

    @Test
    fun `doble tap en onSaveFiscalClick antes de que la corrutina avance no dispara un segundo guardado`() = runTest {
        val countingDao = CountingSettingsDao(fakeDao)
        val vm = SettingsViewModel(
            fakeSessionClearer,
            SettingsRepository(countingDao, noOpApiService),
            fakePullSyncTrigger,
        )
        advanceUntilIdle()

        vm.onBusinessNameChange("Tortillería Don Beto")
        // Sin advanceUntilIdle() entre ambas llamadas — simula el usuario tocando "Guardar" dos
        // veces antes de que la primera corrutina alcance a ejecutarse. isSavingFiscal se fija de
        // forma SÍNCRONA en onSaveFiscalClick(), así que la segunda llamada debe verla ya en true
        // y retornar sin encolar un segundo guardado.
        vm.onSaveFiscalClick()
        assertEquals(true, vm.uiState.value.isSavingFiscal)
        vm.onSaveFiscalClick()
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.fiscalSaved)
        assertEquals(false, vm.uiState.value.isSavingFiscal)
        // Un solo upsertAll en lote (updateSettings) — 2 indicaría que el doble-tap disparó dos
        // guardados concurrentes.
        assertEquals(1, countingDao.upsertAllCallCount)
    }

    @Test
    fun `doble tap en onSaveParamsClick antes de que la corrutina avance no dispara un segundo guardado`() = runTest {
        val countingDao = CountingSettingsDao(fakeDao)
        val vm = SettingsViewModel(
            fakeSessionClearer,
            SettingsRepository(countingDao, noOpApiService),
            fakePullSyncTrigger,
        )
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()
        assertEquals(true, vm.uiState.value.isSavingParams)
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.paramsSaved)
        assertEquals(false, vm.uiState.value.isSavingParams)
        assertEquals(1, countingDao.upsertAllCallCount)
    }

    @Test
    fun `onSaveFiscalClick libera isSavingFiscal y muestra error si el guardado falla`() = runTest {
        val throwingDao = ThrowingSettingsDao(fakeDao)
        val vm = SettingsViewModel(
            fakeSessionClearer,
            SettingsRepository(throwingDao, noOpApiService),
            fakePullSyncTrigger,
        )
        advanceUntilIdle()

        vm.onBusinessNameChange("Tortillería Don Beto")
        vm.onSaveFiscalClick()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isSavingFiscal)
        assertEquals(false, vm.uiState.value.fiscalSaved)
        assertEquals("Algo salió mal. Inténtalo de nuevo.", vm.uiState.value.fiscalError)
    }

    @Test
    fun `onSaveParamsClick libera isSavingParams y muestra error si el guardado falla`() = runTest {
        val throwingDao = ThrowingSettingsDao(fakeDao)
        val vm = SettingsViewModel(
            fakeSessionClearer,
            SettingsRepository(throwingDao, noOpApiService),
            fakePullSyncTrigger,
        )
        advanceUntilIdle()

        vm.onMaxParcialidadesChange("10")
        vm.onSerieFolioChange("A")
        vm.onDiasAnticipacionChange("3")
        vm.onSaveParamsClick()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isSavingParams)
        assertEquals(false, vm.uiState.value.paramsSaved)
        assertEquals("Algo salió mal. Inténtalo de nuevo.", vm.uiState.value.paramsError)
    }
}
