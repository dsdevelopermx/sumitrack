package com.sumitrack.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumitrack.android.data.repositories.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val showLogoutDialog: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onLogoutClick() {
        _uiState.value = _uiState.value.copy(showLogoutDialog = true)
    }

    fun onLogoutDismiss() {
        _uiState.value = _uiState.value.copy(showLogoutDialog = false)
    }

    fun onLogoutConfirm() {
        _uiState.value = _uiState.value.copy(showLogoutDialog = false)
        viewModelScope.launch {
            sessionManager.clearToken()
            // AppViewModel observa isLoggedIn → reactivamente navega a Login
        }
    }
}
