package com.posetracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseLandmarkerHelper(
    private val context: Context,
    private val minPoseDetectionConfidence: Float = 0.5f,
    private val minPoseTrackingConfidence: Float = 0.5f,
    private val minPosePresenceConfidence: Float = 0.5f,
    private val listener: LandmarkerListener
) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private fun buildOptions(delegate: Delegate): PoseLandmarker.PoseLandmarkerOptions {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_POSE_LANDMARKER_FULL)
            .setDelegate(delegate)
            .build()
        return PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinTrackingConfidence(minPoseTrackingConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setNumPoses(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, input -> returnLivestreamResult(result, input) }
            .setErrorListener { error -> listener.onError(error.message ?: "MediaPipe error") }
            .build()
    }

    private fun setupPoseLandmarker() {
        // Try GPU first; fall back to CPU if the device doesn't support it
        poseLandmarker = try {
            PoseLandmarker.createFromOptions(context, buildOptions(Delegate.GPU))
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU: ${e.message}")
            try {
                PoseLandmarker.createFromOptions(context, buildOptions(Delegate.CPU))
            } catch (e2: Exception) {
                listener.onError("Failed to initialise MediaPipe: ${e2.message}")
                null
            }
        }
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
    )

    interface LandmarkerListener {
        fun onResults(result: PoseResult)
        fun onError(error: String)
    }

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        const val MODEL_POSE_LANDMARKER_FULL = "pose_landmarker_full.task"
    }
}
