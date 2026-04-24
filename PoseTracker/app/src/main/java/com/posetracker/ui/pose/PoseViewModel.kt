package com.posetracker.ui.pose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posetracker.network.CommandEncoder
import com.posetracker.network.CommandEncoder.Landmark
import com.posetracker.network.SocketManager
import com.posetracker.utils.PoseLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PoseViewModel : ViewModel() {

    private val _sendStatus = MutableStateFlow("Waiting for pose…")
    val sendStatus: StateFlow<String> = _sendStatus

    private var sequenceId = 0L

    fun initialize(address: String, port: Int) {
        // Socket already connected in ConnectionFragment.
    }

    fun sendPoseData(result: PoseLandmarkerHelper.PoseResult) {
        val landmarks = result.landmarks
        if (landmarks.size < 17) return

        viewModelScope.launch {
            val leftWrist  = landmarks[CommandEncoder.IDX_LEFT_WRIST]
            val leftElbow  = landmarks[CommandEncoder.IDX_LEFT_ELBOW]
            val leftFrame  = CommandEncoder.encode(
                wrist       = Landmark(leftWrist.x(),  leftWrist.y(),  leftWrist.z()),
                elbow       = Landmark(leftElbow.x(),  leftElbow.y(),  leftElbow.z()),
                sequenceId  = sequenceId++,
                timestampMs = result.timestampMs
            )
            val leftOk = SocketManager.sendBytes(leftFrame)

            val rightWrist = landmarks[CommandEncoder.IDX_RIGHT_WRIST]
            val rightElbow = landmarks[CommandEncoder.IDX_RIGHT_ELBOW]
            val rightFrame = CommandEncoder.encode(
                wrist       = Landmark(rightWrist.x(), rightWrist.y(), rightWrist.z()),
                elbow       = Landmark(rightElbow.x(), rightElbow.y(), rightElbow.z()),
                sequenceId  = sequenceId++,
                timestampMs = result.timestampMs
            )
            val rightOk = SocketManager.sendBytes(rightFrame)

            _sendStatus.value = when {
                leftOk && rightOk -> "Sent frame #${sequenceId / 2} (L+R)"
                else              -> "Send failed (frame #${sequenceId / 2})"
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch { SocketManager.disconnect() }
    }
}
