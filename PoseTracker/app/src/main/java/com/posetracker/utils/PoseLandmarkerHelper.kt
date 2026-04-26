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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseLandmarkerHelper(
    private val context: Context,
    private val isFrontCamera: Boolean = true,
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
        val assetList = context.assets.list("") ?: emptyArray()
        if (MODEL_POSE_LANDMARKER_FULL !in assetList) {
            throw IllegalStateException(
                "Model file '$MODEL_POSE_LANDMARKER_FULL' not found in assets/."
            )
        }
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
            .setErrorListener { error ->
                Log.e(TAG, "MediaPipe error: ${error.message}", error)
                listener.onError("MediaPipe error: ${error.message}")
            }
            .build()
    }

    private fun setupPoseLandmarker() {
        poseLandmarker = try {
            PoseLandmarker.createFromOptions(context, buildOptions(Delegate.GPU))
                .also { Log.d(TAG, "GPU delegate initialised OK") }
        } catch (e: Exception) {
            Log.w(TAG, "GPU failed, trying CPU: ${e.message}")
            try {
                PoseLandmarker.createFromOptions(context, buildOptions(Delegate.CPU))
                    .also { Log.d(TAG, "CPU delegate initialised OK") }
            } catch (e2: Exception) {
                Log.e(TAG, "CPU also failed", e2)
                listener.onError("MediaPipe init failed: ${e2.message}")
                null
            }
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy): Bitmap? {
        if (poseLandmarker == null) {
            imageProxy.close()
            return null
        }

        val bitmap = imageProxy.use { proxy ->
            val raw = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(proxy.planes[0].buffer)

            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                // Only mirror horizontally for the front camera
                if (isFrontCamera) {
                    postScale(-1f, 1f, proxy.width / 2f, proxy.height / 2f)
                }
            }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        poseLandmarker?.detectAsync(mpImage, SystemClock.uptimeMillis())
        return bitmap
    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: com.google.mediapipe.framework.image.MPImage
    ) {
        val landmarks = result.landmarks().firstOrNull() ?: emptyList()
        listener.onResults(
            PoseResult(
                landmarks   = landmarks,
                imageWidth  = input.width,
                imageHeight = input.height,
                timestampMs = result.timestampMs()
            )
        )
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

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
