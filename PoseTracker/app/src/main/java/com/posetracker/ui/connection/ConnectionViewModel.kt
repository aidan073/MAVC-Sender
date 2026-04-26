package com.posetracker.ui.connection

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.posetracker.network.SocketManager
import com.posetracker.ui.pose.ArmSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ConnectionUiState {
    object Idle : ConnectionUiState()
    object Connecting : ConnectionUiState()
    data class Connected(val address: String, val port: Int, val armSide: ArmSide) : ConnectionUiState()
    data class Error(val message: String) : ConnectionUiState()
}

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState: StateFlow<ConnectionUiState> = _uiState

    var lastAddress: String? = prefs.getString("address", null)
    var lastPort: String?    = prefs.getString("port", null)
    var lastArmSide: ArmSide = ArmSide.valueOf(prefs.getString("arm_side", ArmSide.LEFT.name)!!)

    fun connect(address: String, port: Int, armSide: ArmSide) {
        lastAddress = address
        lastPort    = port.toString()
        lastArmSide = armSide

        prefs.edit()
            .putString("address",  address)
            .putString("port",     port.toString())
            .putString("arm_side", armSide.name)
            .apply()

        viewModelScope.launch {
            _uiState.value = ConnectionUiState.Connecting
            val result = SocketManager.connect(address, port)
            _uiState.value = if (result.isSuccess) {
                ConnectionUiState.Connected(address, port, armSide)
            } else {
                ConnectionUiState.Error(result.exceptionOrNull()?.message ?: "Connection failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = ConnectionUiState.Idle
    }
}
