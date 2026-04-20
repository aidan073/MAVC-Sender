package com.posetracker.ui.pose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posetracker.network.SocketManager
import com.posetracker.utils.PoseLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PoseViewModel : ViewModel() {

    private val _sendStatus = MutableStateFlow("Waiting for pose…")
    val sendStatus: StateFlow<String> = _sendStatus

    private var frameCount = 0

    fun initialize(address: String, port: Int) {
        // Socket was already connected in ConnectionFragment; nothing extra needed here.
        // If you want to reconnect on rotation, call SocketManager.connect(address, port) here.
    }

    fun sendPoseData(result: PoseLandmarkerHelper.PoseResult) {
        viewModelScope.launch {
            val json = result.toJson()
            val success = SocketManager.send(json)
            frameCount++
            _sendStatus.value = if (success) "Sent frame #$frameCount" else "Send failed (frame #$frameCount)"
        }
    }

    fun disconnect() {
        SocketManager.disconnect()
    }
}
