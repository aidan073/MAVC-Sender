package com.posetracker.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Transparent overlay drawn on top of the camera preview.
 * Renders pose landmark dots and connecting skeleton lines.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 180
    }

    fun setResults(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.landmarks = landmarks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (landmarks.isEmpty()) return

        val scaleX = width.toFloat()
        val scaleY = height.toFloat()

        fun lx(index: Int) = landmarks[index].x() * scaleX
        fun ly(index: Int) = landmarks[index].y() * scaleY
        fun visible(index: Int) = landmarks[index].visibility().orElse(0f) > 0.5f

        // Draw skeleton connections
        POSE_CONNECTIONS.forEach { (a, b) ->
            if (a < landmarks.size && b < landmarks.size && visible(a) && visible(b)) {
                canvas.drawLine(lx(a), ly(a), lx(b), ly(b), linePaint)
            }
        }

        // Draw landmark dots
        landmarks.forEachIndexed { index, lm ->
            if (lm.visibility().orElse(0f) > 0.5f) {
                canvas.drawCircle(lx(index), ly(index), 8f, pointPaint)
            }
        }
    }

    companion object {
        // MediaPipe Pose landmark connections (index pairs)
        val POSE_CONNECTIONS = listOf(
            // Face
            0 to 1, 1 to 2, 2 to 3, 3 to 7,
            0 to 4, 4 to 5, 5 to 6, 6 to 8,
            // Torso
            11 to 12, 11 to 23, 12 to 24, 23 to 24,
            // Left arm
            11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
            // Right arm
            12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
            // Left leg
            23 to 25, 25 to 27, 27 to 29, 27 to 31, 29 to 31,
            // Right leg
            24 to 26, 26 to 28, 28 to 30, 28 to 32, 30 to 32
        )
    }
}
