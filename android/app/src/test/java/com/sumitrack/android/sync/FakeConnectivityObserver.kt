package com.sumitrack.android.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeConnectivityObserver(initiallyOnline: Boolean = true) : ConnectivityObserver {
    private val _isOnline = MutableStateFlow(initiallyOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
