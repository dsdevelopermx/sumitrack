package com.sumitrack.android.ui.screens.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.di.TenantId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientFormUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val rfc: String = "",
    val address: String = "",
    val notes: String = "",
    val nameError: Boolean = false,
    val phoneError: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSaveEnabled: Boolean get() = name.isNotBlank() && phone.isNotBlank() && !isSaving
}

private const val GENERIC_ERROR = "Algo salió mal. Inténtalo de nuevo."

@HiltViewModel
class ClientFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    @TenantId private val tenantId: Flow<String?>,
) : ViewModel() {

    // Normaliza clientId="" (alcanzable vía ruta client_form?clientId=) a null — modo alta, no edición.
    private val clientId: String? = savedStateHandle.get<String>("clientId")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(ClientFormUiState(isEditMode = clientId != null))
    val uiState: StateFlow<ClientFormUiState> = _uiState.asStateFlow()

    // Navegación única — mismo patrón que LoginViewModel (Historia 1.4)
    private val _navEvent = Channel<Unit>(Channel.CONFLATED)
    val navEvent = _navEvent.receiveAsFlow()

    init {
        clientId?.let { id ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val client = runCatching { clientRepository.getClientById(id) }.getOrNull()
                _uiState.value = if (client != null) {
                    _uiState.value.copy(
                        isLoading = false,
                        name = client.name,
                        phone = client.phone,
                        rfc = client.rfc.orEmpty(),
                        address = client.address.orEmpty(),
                        notes = client.notes.orEmpty(),
                    )
                } else {
                    _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No pudimos cargar los datos del cliente.",
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameError = false, errorMessage = null)
    }

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(phone = value, phoneError = false, errorMessage = null)
    }

    fun onRfcChange(value: String) {
        _uiState.value = _uiState.value.copy(rfc = value, errorMessage = null)
    }

    fun onAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(address = value, errorMessage = null)
    }

    fun onNotesChange(value: String) {
        _uiState.value = _uiState.value.copy(notes = value, errorMessage = null)
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isSaving) return

        val name = state.name.trim()
        val phone = state.phone.trim()
        val nameErr = name.isBlank()
        val phoneErr = phone.isBlank()
        if (nameErr || phoneErr) {
            _uiState.value = state.copy(
                nameError = nameErr,
                phoneError = phoneErr,
                errorMessage = if (nameErr) "El nombre es obligatorio" else "El teléfono es obligatorio",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            val rfc = state.rfc.trim().ifBlank { null }
            val address = state.address.trim().ifBlank { null }
            val notes = state.notes.trim().ifBlank { null }

            if (state.isEditMode && clientId != null) {
                val saved = runCatching {
                    clientRepository.updateClient(clientId, name, phone, rfc, address, notes)
                }.getOrNull()
                when (saved) {
                    true -> {
                        _uiState.value = _uiState.value.copy(isSaving = false)
                        _navEvent.send(Unit)
                    }
                    false -> _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "Este cliente ya no existe. Puede que se haya eliminado en otro dispositivo.",
                    )
                    null -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = GENERIC_ERROR)
                }
                return@launch
            }

            val fkTenant = runCatching { tenantId.first() }.getOrNull()
            if (fkTenant.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "No se pudo determinar tu negocio. Vuelve a iniciar sesión.",
                )
                return@launch
            }
            val created = runCatching {
                clientRepository.createClient(name, phone, rfc, address, notes, fkTenant)
            }
            created.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false)
                _navEvent.send(Unit)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = GENERIC_ERROR)
            }
        }
    }
}
