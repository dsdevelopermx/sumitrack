package com.sumitrack.android.ui.screens.clients

import androidx.lifecycle.SavedStateHandle
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.usecases.CalculateClientBalanceUseCase
import com.sumitrack.android.ui.screens.orders.FakeCreditBalanceDao
import com.sumitrack.android.ui.screens.orders.FakeInstallmentDao
import com.sumitrack.android.ui.screens.orders.FakePaymentDao
import com.sumitrack.android.ui.screens.orders.FakeSaleItemDao
import com.sumitrack.android.ui.screens.products.FakeTransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeClientDao
    private lateinit var repository: ClientRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeClientDao()
        repository = ClientRepository(
            fakeDao,
            CalculateClientBalanceUseCase(
                SaleRepository(FakeTransactionRunner(), FakeSaleDao(), FakeSaleItemDao(), FakeInstallmentDao(), FakePaymentDao(), FakeCreditBalanceDao())
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clientId: String? = null, tenantId: String? = "tenant-1") = ClientFormViewModel(
        savedStateHandle = SavedStateHandle(mapOf("clientId" to clientId)),
        clientRepository = repository,
        tenantId = flowOf(tenantId),
    )

    @Test
    fun `alta mode starts with isEditMode false and empty fields`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.isEditMode)
        assertEquals("", vm.uiState.value.name)
    }

    @Test
    fun `isSaveEnabled is false until name and phone are filled`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.isSaveEnabled)

        vm.onNameChange("Ana López")
        assertFalse(vm.uiState.value.isSaveEnabled)

        vm.onPhoneChange("555-0001")
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun `onSaveClick with blank name marks nameError`() {
        val vm = viewModel()
        vm.onPhoneChange("555-0001")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.nameError)
        assertEquals("El nombre es obligatorio", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onSaveClick with blank phone marks phoneError`() {
        val vm = viewModel()
        vm.onNameChange("Ana López")

        vm.onSaveClick()

        assertTrue(vm.uiState.value.phoneError)
        assertEquals("El teléfono es obligatorio", vm.uiState.value.errorMessage)
    }

    @Test
    fun `typing after an error clears that field's error`() {
        val vm = viewModel()
        vm.onSaveClick()
        assertTrue(vm.uiState.value.nameError)

        vm.onNameChange("Ana")
        assertFalse(vm.uiState.value.nameError)
    }

    @Test
    fun `editing any field clears a stale generic errorMessage`() = runTest {
        val vm = viewModel(tenantId = null)
        vm.onNameChange("Ana López")
        vm.onPhoneChange("555-0001")
        vm.onSaveClick()
        advanceUntilIdle()
        assertEquals("No se pudo determinar tu negocio. Vuelve a iniciar sesión.", vm.uiState.value.errorMessage)

        vm.onRfcChange("XAXX010101000")

        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `blank clientId is treated as alta mode, not edit mode`() {
        val vm = viewModel(clientId = "")
        assertFalse(vm.uiState.value.isEditMode)
    }

    @Test
    fun `onSaveClick trims name and phone before persisting`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        vm.onNameChange("  Ana López  ")
        vm.onPhoneChange("  555-0001  ")
        vm.onSaveClick()
        advanceUntilIdle()

        val all = mutableListOf<com.sumitrack.android.domain.models.Client>()
        val job = launch { repository.getAllClients().collect { all.addAll(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals("Ana López", all.first().name)
        assertEquals("555-0001", all.first().phone)
    }

    @Test
    fun `alta mode creates client with resolved tenantId and emits navEvent`() = runTest {
        val vm = viewModel(tenantId = "tenant-1")
        val navEvents = mutableListOf<String>()
        val job = launch { vm.navEvent.collect { navEvents.add(it) } }

        vm.onNameChange("Ana López")
        vm.onPhoneChange("555-0001")
        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals(1, navEvents.size)
        val createdId = navEvents.first()
        assertEquals("Ana López", repository.getClientById(createdId)?.name)
        job.cancel()
    }

    @Test
    fun `alta mode with null tenantId shows error and does not save`() = runTest {
        val vm = viewModel(tenantId = null)
        vm.onNameChange("Ana López")
        vm.onPhoneChange("555-0001")

        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals("No se pudo determinar tu negocio. Vuelve a iniciar sesión.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `edit mode preloads fields from existing client`() = runTest {
        val id = repository.createClient("Ana López", "555-0001", "XAXX010101000", "Calle 1", "Nota", "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(clientId = id)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isEditMode)
        assertEquals("Ana López", state.name)
        assertEquals("555-0001", state.phone)
        assertEquals("XAXX010101000", state.rfc)
        assertEquals("Calle 1", state.address)
        assertEquals("Nota", state.notes)
    }

    @Test
    fun `edit mode on a deleted client shows an error and does not emit navEvent`() = runTest {
        val vm = viewModel(clientId = "ghost-id")
        advanceUntilIdle()
        assertEquals("No pudimos cargar los datos del cliente.", vm.uiState.value.errorMessage)

        val navEvents = mutableListOf<String>()
        val job = launch { vm.navEvent.collect { navEvents.add(it) } }

        vm.onNameChange("Ana López")
        vm.onPhoneChange("555-0001")
        vm.onSaveClick()
        advanceUntilIdle()

        assertEquals(0, navEvents.size)
        assertEquals("Este cliente ya no existe. Puede que se haya eliminado en otro dispositivo.", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSaving)
        job.cancel()
    }

    @Test
    fun `edit mode saves updates to the same client id`() = runTest {
        val id = repository.createClient("Ana López", "555-0001", null, null, null, "tenant-1")
        advanceUntilIdle()

        val vm = viewModel(clientId = id)
        advanceUntilIdle()

        vm.onNameChange("Ana López Ruiz")
        vm.onSaveClick()
        advanceUntilIdle()

        val updated = repository.getClientById(id)
        assertEquals(id, updated?.id)
        assertEquals("Ana López Ruiz", updated?.name)
    }
}
