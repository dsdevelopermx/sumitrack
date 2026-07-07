package com.sumitrack.android.ui.screens.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.AuthRepository
import com.sumitrack.android.domain.exceptions.InvalidCredentialsException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val usernameError: Boolean = false,
    val passwordError: Boolean = false,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Navigation events — Channel garantiza entrega única incluso después de recomposición
    private val _navEvent = Channel<Unit>(Channel.CONFLATED)
    val navEvent = _navEvent.receiveAsFlow()

    init {
        checkConnectivity()
    }

    private fun checkConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val hasInternet = cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
        if (!hasInternet) {
            _uiState.value = _uiState.value.copy(isOffline = true)
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(
            username = value,
            usernameError = false,
            errorMessage = null,
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(
            password = value,
            passwordError = false,
            errorMessage = null,
        )
    }

    fun onLoginClick() {
        val state = _uiState.value
        if (state.isLoading) return  // guard contra race condition teclado+botón

        if (state.username.isBlank()) {
            _uiState.value = state.copy(usernameError = true, errorMessage = "Usuario y contraseña son requeridos")
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = state.copy(passwordError = true, errorMessage = "Usuario y contraseña son requeridos")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null, usernameError = false, passwordError = false)
            authRepository.login(state.username, state.password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _navEvent.send(Unit)
                }
                .onFailure { e ->
                    val message = when (e) {
                        is InvalidCredentialsException -> "Usuario o contraseña incorrectos. Inténtalo de nuevo."
                        else -> "Error de conexión. Verifique su red."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        usernameError = e is InvalidCredentialsException,
                        passwordError = e is InvalidCredentialsException,
                        errorMessage = message,
                    )
                }
        }
    }
}
