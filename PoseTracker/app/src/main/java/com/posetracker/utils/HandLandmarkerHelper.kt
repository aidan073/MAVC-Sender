package com.posetracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

class HandLandmarkerHelper(
    private val context: Context,
    private val targetHandSide: String,   // "Left" or "Right" — matches MediaPipe's label
    private val listener: HandListener
) {

    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun buildOptions(delegate: Delegate): HandLandmarker.HandLandmarkerOptions {
        val assetList = context.assets.list("") ?: emptyArray()
        if (MODEL_HAND_LANDMARKER !in assetList) {
            throw IllegalStateException(
                "Model file '$MODEL_HAND_LANDMARKER' not found in assets/. " +
                "Download it from the MediaPipe releases page and place it in app/src/main/assets/"
            )
        }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_HAND_LANDMARKER)
            .setDelegate(delegate)
            .build()

        return HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(2)          // detect both so we can filter to the right one
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

    /**
     * Accepts the same rotated bitmap that PoseLandmarkerHelper already produced,
     * so we don't need to re-decode the ImageProxy.
     */
    fun detectFromBitmap(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker?.detectAsync(mpImage, timestampMs)
    }

    private fun returnResult(result: HandLandmarkerResult) {
        // MediaPipe reports handedness as seen from the camera (mirrored for front cam),
        // so "Left" in the result corresponds to the user's right hand and vice versa.
        // We flip the label here to match the user's actual hand.
        val targetLabel = if (targetHandSide == "Left") "Right" else "Left"

        val handIndex = result.handedness().indexOfFirst { categories ->
            categories.firstOrNull()?.categoryName() == targetLabel
        }

        val isClosed = if (handIndex >= 0 && handIndex < result.landmarks().size) {
            isHandClosed(result.landmarks()[handIndex])
        } else {
            false  // hand not visible — default to open
        }

        listener.onHandResult(isClosed)
    }

    /**
     * Detects a closed fist by checking whether each fingertip is closer to
     * the wrist than its MCP knuckle is. If 3 or more of the 4 fingers are
     * curled, the hand is considered closed.
     *
     * Landmark pairs: (MCP index, tip index)
     */
    private fun isHandClosed(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size < 21) return false

        val wrist = landmarks[0]

        val fingerPairs = listOf(
            5 to 8,   // index
            9 to 12,  // middle
            13 to 16, // ring
            17 to 20  // pinky
        )

        val curledCount = fingerPairs.count { (mcpIdx, tipIdx) ->
            val mcp = landmarks[mcpIdx]
            val tip = landmarks[tipIdx]
            dist(tip, wrist) < dist(mcp, wrist)
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
        fun onHandResult(isClosed: Boolean)
        fun onHandError(error: String)
    }

    companion object {
        private const val TAG = "HandLandmarkerHelper"
        const val MODEL_HAND_LANDMARKER = "hand_landmarker.task"
    }
}
