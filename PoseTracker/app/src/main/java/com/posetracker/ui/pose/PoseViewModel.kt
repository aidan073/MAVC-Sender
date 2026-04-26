package com.posetracker.ui.pose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posetracker.network.CommandEncoder
import com.posetracker.network.CommandEncoder.Landmark
import com.posetracker.network.SocketManager
import com.posetracker.utils.HandLandmarkerHelper.HandEuler
import com.posetracker.utils.PoseLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ArmSide { LEFT, RIGHT }

class PoseViewModel : ViewModel() {

    private val _sendStatus = MutableStateFlow("Waiting for pose…")
    val sendStatus: StateFlow<String> = _sendStatus

    var armSide: ArmSide = ArmSide.LEFT

    @Volatile private var isHandClosed: Boolean = false
    @Volatile private var handEuler: HandEuler? = null

    private var sequenceId = 0L

    fun initialize(address: String, port: Int) { }

    fun updateHandState(closed: Boolean, euler: HandEuler?) {
        isHandClosed = closed
        handEuler    = euler
    }

    fun sendPoseData(result: PoseLandmarkerHelper.PoseResult) {
        val landmarks = result.landmarks
        if (landmarks.size < 17) return

        val handClosed = isHandClosed
        val euler      = handEuler

        viewModelScope.launch {
            val shoulderIdx: Int
            val elbowIdx: Int
            val wristIdx: Int

            when (armSide) {
                ArmSide.LEFT -> {
                    shoulderIdx = CommandEncoder.IDX_LEFT_SHOULDER
                    elbowIdx    = CommandEncoder.IDX_LEFT_ELBOW
                    wristIdx    = CommandEncoder.IDX_LEFT_WRIST
                }
                ArmSide.RIGHT -> {
                    shoulderIdx = CommandEncoder.IDX_RIGHT_SHOULDER
                    elbowIdx    = CommandEncoder.IDX_RIGHT_ELBOW
                    wristIdx    = CommandEncoder.IDX_RIGHT_WRIST
                }
            }

            val shoulder = landmarks[shoulderIdx]
            val elbow    = landmarks[elbowIdx]
            val wrist    = landmarks[wristIdx]

            val frame = CommandEncoder.encode(
                shoulder    = Landmark(shoulder.x(), shoulder.y(), shoulder.z()),
                elbow       = Landmark(elbow.x(),    elbow.y(),    elbow.z()),
                wrist       = Landmark(wrist.x(),    wrist.y(),    wrist.z()),
                imageWidth  = result.imageWidth,
                imageHeight = result.imageHeight,
                handClosed  = handClosed,
                handEuler   = euler,
                sequenceId  = sequenceId++,
                timestampMs = result.timestampMs
            )

            val ok = SocketManager.sendBytes(frame)
            _sendStatus.value = if (ok)
                "Sent frame #$sequenceId (${armSide.name.lowercase()}, ${if (handClosed) "closed" else "open"})"
            else
                "Send failed (frame #$sequenceId)"
        }
    }

    fun disconnect() {
        viewModelScope.launch { SocketManager.disconnect() }
    }
}
