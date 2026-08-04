package com.sumitrack.android.ui.screens.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.ClientRepository
import com.sumitrack.android.data.repositories.SaleRepository
import com.sumitrack.android.domain.models.Client
import com.sumitrack.android.domain.models.Sale
import com.sumitrack.android.domain.usecases.CalculateAvailableCreditUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientProfileUiState(
    val isLoading: Boolean = true,
    val client: Client? = null,
    val openSales: List<Sale> = emptyList(),
    val errorMessage: String? = null,
    val creditBalance: BigDecimal? = null,
)

@HiltViewModel
class ClientProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val saleRepository: SaleRepository,
    private val calculateAvailableCreditUseCase: CalculateAvailableCreditUseCase,
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"])

    private val _uiState = MutableStateFlow(ClientProfileUiState())
    val uiState: StateFlow<ClientProfileUiState> = _uiState.asStateFlow()

    // Evita que init{} y el refresco en ON_RESUME (ClientProfileScreen) carguen en paralelo:
    // cancela cualquier load() en curso antes de lanzar uno nuevo.
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val client = try {
                clientRepository.getClientById(clientId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (client == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No pudimos cargar los datos del cliente.",
                )
                return@launch
            }

            val openSales = try {
                saleRepository.getOpenSalesForClient(clientId, client.fkTenant)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (openSales == null) {
                // Distinto del caso AC-5 (sin ventas abiertas): aquí la carga falló, no que
                // el cliente esté al corriente. errorMessage no-nulo con openSales vacío es
                // la señal que ClientProfileScreen usa para diferenciar ambos casos.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    client = client,
                    openSales = emptyList(),
                    errorMessage = "No pudimos cargar las ventas abiertas de este cliente.",
                )
                return@launch
            }

            // Mismo criterio try/catch(CancellationException) rethrow ya usado arriba para
            // client/openSales — un fallo al cargar el crédito no debe bloquear el resto del
            // perfil, el banner simplemente no se muestra (creditBalance = null).
            val creditBalance = try {
                calculateAvailableCreditUseCase(clientId, client.fkTenant)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            _uiState.value = ClientProfileUiState(isLoading = false, client = client, openSales = openSales, creditBalance = creditBalance)
        }
    }
}
