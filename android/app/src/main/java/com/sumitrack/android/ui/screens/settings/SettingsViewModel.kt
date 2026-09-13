package com.sumitrack.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.SessionClearer
import com.sumitrack.android.data.repositories.SettingsRepository
import com.sumitrack.android.sync.PullSyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val showLogoutDialog: Boolean = false,
    val isLoading: Boolean = true,

    // Datos Fiscales (AC-2)
    val businessName: String = "",
    val rfc: String = "",
    val address: String = "",
    val phone: String = "",
    val isSavingFiscal: Boolean = false,
    val fiscalSaved: Boolean = false,
    val fiscalError: String? = null,

    // Parámetros de Venta (AC-3/AC-4/AC-6)
    val maxParcialidades: String = "",
    val serieFolio: String = "",
    val diasAnticipacion: String = "",
    val maxParcialidadesError: String? = null,
    val serieFolioError: String? = null,
    val diasAnticipacionError: String? = null,
    val isSavingParams: Boolean = false,
    val paramsSaved: Boolean = false,
    val paramsError: String? = null,
) {
    // Datos Fiscales no tiene validación de contenido (ver Dev Notes de la historia) — solo se
    // deshabilita mientras una operación de guardado está en curso.
    val isFiscalSaveEnabled: Boolean get() = !isSavingFiscal

    // La validación de Parámetros de Venta ocurre al tocar Guardar, pero AC-6 exige que el botón
    // quede deshabilitado mientras exista un error activo — se recomputa aquí en vez de depender
    // de un flag aparte, así se limpia solo en cuanto onXxxChange borra el error del campo.
    val isParamsSaveEnabled: Boolean
        get() = !isSavingParams && maxParcialidadesError == null && serieFolioError == null && diasAnticipacionError == null
}

private const val KEY_NEGOCIO_NOMBRE = "negocio_nombre"
private const val KEY_NEGOCIO_RFC = "negocio_rfc"
private const val KEY_NEGOCIO_DIRECCION = "negocio_direccion"
private const val KEY_NEGOCIO_TELEFONO = "negocio_telefono"
private const val KEY_MAX_PARCIALIDADES = "max_parcialidades"
private const val KEY_SERIE_FOLIO = "serie_folio"
private const val KEY_DIAS_ANTICIPACION = "dias_anticipacion_recordatorio"
private const val GENERIC_ERROR = "Algo salió mal. Inténtalo de nuevo."

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionClearer: SessionClearer,
    private val settingsRepository: SettingsRepository,
    private val pullSyncTrigger: PullSyncTrigger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Los campos quedan deshabilitados (ver SettingsScreen.kt, isLoading) hasta que esto
            // termina, para que el tipeo del usuario no pueda ser sobrescrito por la carga inicial.
            // runCatching evita que un error de Room al abrir la pantalla la tumbe — la pantalla
            // simplemente arranca con los 7 campos vacíos si la carga falla.
            val values = runCatching { settingsRepository.getAllValues() }.getOrDefault(emptyMap())
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                businessName = values[KEY_NEGOCIO_NOMBRE].orEmpty(),
                rfc = values[KEY_NEGOCIO_RFC].orEmpty(),
                address = values[KEY_NEGOCIO_DIRECCION].orEmpty(),
                phone = values[KEY_NEGOCIO_TELEFONO].orEmpty(),
                maxParcialidades = values[KEY_MAX_PARCIALIDADES].orEmpty(),
                serieFolio = values[KEY_SERIE_FOLIO].orEmpty(),
                diasAnticipacion = values[KEY_DIAS_ANTICIPACION].orEmpty(),
            )
        }
    }

    fun onBusinessNameChange(value: String) {
        _uiState.value = _uiState.value.copy(businessName = value, fiscalSaved = false, fiscalError = null)
    }

    fun onRfcChange(value: String) {
        _uiState.value = _uiState.value.copy(rfc = value, fiscalSaved = false, fiscalError = null)
    }

    fun onAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(address = value, fiscalSaved = false, fiscalError = null)
    }

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(phone = value, fiscalSaved = false, fiscalError = null)
    }

    fun onSaveFiscalClick() {
        val state = _uiState.value
        if (state.isSavingFiscal) return

        // isSavingFiscal se fija SÍNCRONAMENTE (antes de .launch) — mismo patrón que
        // ConflictViewModel.resolve() — para que un doble-tap antes de que la corrutina alcance a
        // ejecutarse no pase el guard de arriba dos veces.
        _uiState.value = state.copy(isSavingFiscal = true, fiscalSaved = false, fiscalError = null)
        viewModelScope.launch {
            // Un solo updateSettings (lote) en vez de 4 updateSetting secuenciales — Room ejecuta
            // el upsertAll de la lista en una sola transacción, así que si algo falla no quedan
            // 2 de 4 keys guardadas y las otras 2 no.
            val result = runCatching {
                settingsRepository.updateSettings(
                    mapOf(
                        KEY_NEGOCIO_NOMBRE to state.businessName.trim(),
                        KEY_NEGOCIO_RFC to state.rfc.trim(),
                        KEY_NEGOCIO_DIRECCION to state.address.trim(),
                        KEY_NEGOCIO_TELEFONO to state.phone.trim(),
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(
                isSavingFiscal = false,
                fiscalSaved = result.isSuccess,
                fiscalError = if (result.isFailure) GENERIC_ERROR else null,
            )
        }
    }

    fun onMaxParcialidadesChange(value: String) {
        _uiState.value = _uiState.value.copy(maxParcialidades = value, maxParcialidadesError = null, paramsSaved = false, paramsError = null)
    }

    fun onSerieFolioChange(value: String) {
        _uiState.value = _uiState.value.copy(serieFolio = value, serieFolioError = null, paramsSaved = false, paramsError = null)
    }

    fun onDiasAnticipacionChange(value: String) {
        _uiState.value = _uiState.value.copy(diasAnticipacion = value, diasAnticipacionError = null, paramsSaved = false, paramsError = null)
    }

    fun onSaveParamsClick() {
        val state = _uiState.value
        if (state.isSavingParams) return

        val maxParcialidades = state.maxParcialidades.trim().toIntOrNull()
        val maxParcialidadesError = if (maxParcialidades == null || maxParcialidades !in 1..15) {
            "Debe ser un número entre 1 y 15"
        } else {
            null
        }

        val serieFolio = state.serieFolio.trim()
        val serieFolioError = if (serieFolio.isEmpty() || serieFolio.length > 5) {
            "Debe tener entre 1 y 5 caracteres"
        } else {
            null
        }

        val diasAnticipacion = state.diasAnticipacion.trim().toIntOrNull()
        val diasAnticipacionError = if (diasAnticipacion == null || diasAnticipacion !in 1..30) {
            "Debe ser un número entre 1 y 30"
        } else {
            null
        }

        // Se valida todo antes de guardar cualquiera de los 3 — ningún campo se guarda parcialmente.
        if (maxParcialidadesError != null || serieFolioError != null || diasAnticipacionError != null) {
            _uiState.value = state.copy(
                maxParcialidadesError = maxParcialidadesError,
                serieFolioError = serieFolioError,
                diasAnticipacionError = diasAnticipacionError,
                paramsError = null,
            )
            return
        }

        // isSavingParams se fija SÍNCRONAMENTE (antes de .launch) — mismo motivo que onSaveFiscalClick.
        _uiState.value = _uiState.value.copy(isSavingParams = true, paramsSaved = false, paramsError = null)
        viewModelScope.launch {
            // Un solo updateSettings (lote) — mismo motivo que en onSaveFiscalClick.
            val result = runCatching {
                settingsRepository.updateSettings(
                    mapOf(
                        KEY_MAX_PARCIALIDADES to maxParcialidades.toString(),
                        KEY_SERIE_FOLIO to serieFolio,
                        KEY_DIAS_ANTICIPACION to diasAnticipacion.toString(),
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(
                isSavingParams = false,
                paramsSaved = result.isSuccess,
                paramsError = if (result.isFailure) GENERIC_ERROR else null,
            )
        }
    }

    fun onSyncNowClick() {
        runCatching { pullSyncTrigger() }
    }

    fun onLogoutClick() {
        _uiState.value = _uiState.value.copy(showLogoutDialog = true)
    }

    fun onLogoutDismiss() {
        _uiState.value = _uiState.value.copy(showLogoutDialog = false)
    }

    fun onLogoutConfirm() {
        _uiState.value = _uiState.value.copy(showLogoutDialog = false)
        viewModelScope.launch {
            settingsRepository.clearLocalSettings()
            sessionClearer.clearToken()
        }
    }
}
