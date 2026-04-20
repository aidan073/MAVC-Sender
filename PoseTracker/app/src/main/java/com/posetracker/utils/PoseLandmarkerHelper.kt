package com.posetracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.gson.Gson
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseLandmarkerHelper(
    private val context: Context,
    private val minPoseDetectionConfidence: Float = 0.5f,
    private val minPoseTrackingConfidence: Float = 0.5f,
    private val minPosePresenceConfidence: Float = 0.5f,
    private val listener: LandmarkerListener
) {

    private var poseLandmarker: PoseLandmarker? = null
    private val gson = Gson()

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_POSE_LANDMARKER_FULL)
            .setDelegate(Delegate.GPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinTrackingConfidence(minPoseTrackingConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setNumPoses(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, input ->
                returnLivestreamResult(result, input)
            }
            .setErrorListener { error ->
                listener.onError(error.message ?: "MediaPipe error")
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            // Front camera: mirror horizontally
            postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val frameTime = SystemClock.uptimeMillis()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val landmarks = result.landmarks().firstOrNull() ?: emptyList()
        val poseResult = PoseResult(
            landmarks = landmarks,
            imageWidth = input.width,
            imageHeight = input.height,
            timestampMs = result.timestampMs()
        )
        listener.onResults(poseResult)
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class PoseResult(
        val landmarks: List<NormalizedLandmark>,
        val imageWidth: Int,
        val imageHeight: Int,
        val timestampMs: Long
    ) {
        /** Serializes landmarks to a compact JSON string ready to send over the socket. */
        fun toJson(): String {
            val landmarkData = landmarks.mapIndexed { index, lm ->
                mapOf(
                    "i" to index,
                    "x" to lm.x(),
                    "y" to lm.y(),
                    "z" to lm.z(),
                    "v" to lm.visibility().orElse(0f)
                )
            }
            val payload = mapOf(
                "ts" to timestampMs,
                "w" to imageWidth,
                "h" to imageHeight,
                "landmarks" to landmarkData
            )
            return com.google.gson.Gson().toJson(payload)
        }
    }

    interface LandmarkerListener {
        fun onResults(result: PoseResult)
        fun onError(error: String)
    }

    companion object {
        // Download from: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
        // Place in app/src/main/assets/
        const val MODEL_POSE_LANDMARKER_FULL = "pose_landmarker_full.task"
    }
}
