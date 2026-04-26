package com.posetracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class HandLandmarkerHelper(
    private val context: Context,
    private val targetHandSide: String,   // "Left" or "Right"
    private val listener: HandListener
) {

    private var handLandmarker: HandLandmarker? = null

    /**
     * ZYX Euler angles in radians derived from the hand's rotation matrix.
     *   roll  — rotation around the hand's forward axis (finger-pointing direction)
     *   pitch — rotation around the hand's lateral axis (tilt forward/back)
     *   yaw   — rotation around the hand's normal axis (rotate left/right)
     */
    data class HandEuler(val roll: Float, val pitch: Float, val yaw: Float)

    init {
        setupHandLandmarker()
    }

    private fun buildOptions(delegate: Delegate): HandLandmarker.HandLandmarkerOptions {
        val assetList = context.assets.list("") ?: emptyArray()
        if (MODEL_HAND_LANDMARKER !in assetList) {
            throw IllegalStateException(
                "Model file '$MODEL_HAND_LANDMARKER' not found in assets/."
            )
        }
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_HAND_LANDMARKER)
            .setDelegate(delegate)
            .build()
        return HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> returnResult(result) }
            .setErrorListener { error ->
                Log.e(TAG, "Hand landmarker error: ${error.message}", error)
                listener.onHandError("Hand landmarker error: ${error.message}")
            }
            .build()
    }

    private fun setupHandLandmarker() {
        handLandmarker = try {
            HandLandmarker.createFromOptions(context, buildOptions(Delegate.GPU))
                .also { Log.d(TAG, "Hand landmarker GPU init OK") }
        } catch (e: Exception) {
            Log.w(TAG, "Hand GPU failed, trying CPU: ${e.message}")
            try {
                HandLandmarker.createFromOptions(context, buildOptions(Delegate.CPU))
                    .also { Log.d(TAG, "Hand landmarker CPU init OK") }
            } catch (e2: Exception) {
                Log.e(TAG, "Hand landmarker init failed", e2)
                listener.onHandError("Hand landmarker init failed: ${e2.message}")
                null
            }
        }
    }

    fun detectFromBitmap(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker?.detectAsync(mpImage, timestampMs)
    }

    private fun returnResult(result: HandLandmarkerResult) {
        // MediaPipe labels are mirrored for the front camera — flip to match user's hand
        val targetLabel = if (targetHandSide == "Left") "Right" else "Left"

        val handIndex = result.handedness().indexOfFirst { categories ->
            categories.firstOrNull()?.categoryName() == targetLabel
        }

        if (handIndex >= 0 && handIndex < result.landmarks().size) {
            val landmarks = result.landmarks()[handIndex]
            val isClosed = isHandClosed(landmarks)
            val euler    = computeEuler(landmarks)
            listener.onHandResult(isClosed, euler)
        } else {
            listener.onHandResult(false, null)
        }
    }

    /**
     * Computes ZYX Euler angles (roll, pitch, yaw) from the hand landmark positions.
     *
     * Uses two vectors to build a full rotation matrix:
     *   forward (F) = wrist(0) → middle MCP(9)   — along the hand
     *   lateral (L) = index MCP(5) → pinky MCP(17) — across the palm width
     *   normal  (N) = F × L                       — out of the palm
     *
     * L is then re-orthogonalised as N × F so the three axes are exactly perpendicular.
     * Euler angles are extracted from R = [L | N | F] using ZYX decomposition.
     */
    private fun computeEuler(landmarks: List<NormalizedLandmark>): HandEuler? {
        if (landmarks.size < 18) return null

        val wrist     = landmarks[0]
        val indexMcp  = landmarks[5]
        val pinkyMcp  = landmarks[17]
        val middleMcp = landmarks[9]

        // Build forward and rough lateral vectors
        var fx = middleMcp.x() - wrist.x()
        var fy = middleMcp.y() - wrist.y()
        var fz = middleMcp.z() - wrist.z()

        var lx = pinkyMcp.x() - indexMcp.x()
        var ly = pinkyMcp.y() - indexMcp.y()
        var lz = pinkyMcp.z() - indexMcp.z()

        // Normalise forward
        val fLen = sqrt(fx*fx + fy*fy + fz*fz)
        if (fLen == 0f) return null
        fx /= fLen; fy /= fLen; fz /= fLen

        // Normalise lateral
        val lLen = sqrt(lx*lx + ly*ly + lz*lz)
        if (lLen == 0f) return null
        lx /= lLen; ly /= lLen; lz /= lLen

        // Normal = forward × lateral
        var nx = fy*lz - fz*ly
        var ny = fz*lx - fx*lz
        var nz = fx*ly - fy*lx
        val nLen = sqrt(nx*nx + ny*ny + nz*nz)
        if (nLen == 0f) return null
        nx /= nLen; ny /= nLen; nz /= nLen

        // Re-orthogonalise lateral = normal × forward
        lx = ny*fz - nz*fy
        ly = nz*fx - nx*fz
        lz = nx*fy - ny*fx

        // Rotation matrix R = [L | N | F] (column vectors)
        // R[row][col]:
        // R[0][0]=lx  R[0][1]=nx  R[0][2]=fx
        // R[1][0]=ly  R[1][1]=ny  R[1][2]=fy
        // R[2][0]=lz  R[2][1]=nz  R[2][2]=fz

        // ZYX Euler extraction
        val pitch = asin(-lz.coerceIn(-1f, 1f))
        val yaw   = atan2(ly, lx)
        val roll  = atan2(nz, fz)

        return HandEuler(roll, pitch, yaw)
    }

    /**
     * Closed fist: 3 or more fingers have their tip closer to the wrist
     * than their MCP knuckle.
     */
    private fun isHandClosed(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size < 21) return false
        val wrist = landmarks[0]
        val fingerPairs = listOf(5 to 8, 9 to 12, 13 to 16, 17 to 20)
        val curledCount = fingerPairs.count { (mcpIdx, tipIdx) ->
            dist(landmarks[tipIdx], wrist) < dist(landmarks[mcpIdx], wrist)
        }
        return curledCount >= 3
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        val dz = a.z() - b.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }

    interface HandListener {
        fun onHandResult(isClosed: Boolean, euler: HandEuler?)
        fun onHandError(error: String)
    }

    companion object {
        private const val TAG = "HandLandmarkerHelper"
        const val MODEL_HAND_LANDMARKER = "hand_landmarker.task"
    }
}
