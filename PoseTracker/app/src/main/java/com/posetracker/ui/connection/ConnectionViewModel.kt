package com.posetracker.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posetracker.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ConnectionUiState {
    object Idle : ConnectionUiState()
    object Connecting : ConnectionUiState()
    data class Connected(val address: String, val port: Int) : ConnectionUiState()
    data class Error(val message: String) : ConnectionUiState()
}

class ConnectionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState: StateFlow<ConnectionUiState> = _uiState

    var lastAddress: String? = null
        private set
    var lastPort: String? = null
        private set

    fun connect(address: String, port: Int) {
        lastAddress = address
        lastPort = port.toString()

        viewModelScope.launch {
            _uiState.value = ConnectionUiState.Connecting
            val result = SocketManager.connect(address, port)
            _uiState.value = if (result.isSuccess) {
                ConnectionUiState.Connected(address, port)
            } else {
                ConnectionUiState.Error(
                    result.exceptionOrNull()?.message ?: "Connection failed"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = ConnectionUiState.Idle
    }
}
