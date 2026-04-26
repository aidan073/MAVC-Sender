package com.posetracker.network

import com.posetracker.utils.HandLandmarkerHelper.HandEuler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object CommandEncoder {

    private const val MAGIC: Int      = 0x073CD
    private const val VERSION: Int    = 1
    private const val FRAME_SIZE: Int = 44

    const val IDX_LEFT_SHOULDER  = 11
    const val IDX_LEFT_ELBOW     = 13
    const val IDX_LEFT_WRIST     = 15
    const val IDX_RIGHT_SHOULDER = 12
    const val IDX_RIGHT_ELBOW    = 14
    const val IDX_RIGHT_WRIST    = 16

    data class Landmark(val x: Float, val y: Float, val z: Float)

    private fun dist(a: Landmark, b: Landmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun scaleLandmark(lm: Landmark, imageWidth: Int, imageHeight: Int): Landmark {
        val aspect = imageWidth.toFloat() / imageHeight.toFloat()
        return Landmark(lm.x * aspect, lm.y, lm.z * aspect)
    }

    /**
     * Encodes one arm frame into 44 bytes.
     *
     * palm_position    = (wrist - shoulder) / armLength  — shoulder-relative, arm-normalised
     * palm_orientation = (roll, pitch, yaw) in radians from hand landmarker ZYX Euler angles.
     *                    Sends (0, 0, 0) if hand is not visible.
     * grip_amount      = 1.0 if hand closed, 0.0 if open
     */
    fun encode(
        shoulder: Landmark,
        elbow: Landmark,
        wrist: Landmark,
        imageWidth: Int,
        imageHeight: Int,
        handClosed: Boolean,
        handEuler: HandEuler?,
        sequenceId: Long,
        timestampMs: Long
    ): ByteArray {
        val ss = scaleLandmark(shoulder, imageWidth, imageHeight)
        val es = scaleLandmark(elbow,    imageWidth, imageHeight)
        val ws = scaleLandmark(wrist,    imageWidth, imageHeight)

        val armLength = dist(ss, es) + dist(es, ws)
        val scale = if (armLength > 0f) 1f / armLength else 1f

        // Palm position: wrist relative to shoulder, normalised by arm length
        val px = (ws.x - ss.x) * scale
        val py = (ws.y - ss.y) * scale
        val pz = (ws.z - ss.z) * scale

        // Palm orientation: ZYX Euler angles in radians.
        // (0, 0, 0) when hand is not visible.
        val ox = handEuler?.roll  ?: 0f
        val oy = handEuler?.pitch ?: 0f
        val oz = handEuler?.yaw   ?: 0f

        val buf = ByteBuffer.allocate(FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(MAGIC.toShort())
        buf.put(VERSION.toByte())
        buf.putInt((sequenceId and 0xFFFFFFFFL).toInt())
        buf.putDouble(timestampMs / 1000.0)
        buf.putFloat(px)
        buf.putFloat(py)
        buf.putFloat(pz)
        buf.putFloat(ox)   // roll
        buf.putFloat(oy)   // pitch
        buf.putFloat(oz)   // yaw
        buf.putFloat(if (handClosed) 1.0f else 0.0f)

        val payload = buf.array().copyOf(FRAME_SIZE - 1)
        val checksum = payload.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }.toByte()
        buf.put(checksum)

        return buf.array()
    }
}
