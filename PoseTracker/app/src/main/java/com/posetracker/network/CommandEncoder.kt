package com.posetracker.network

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
     * Encodes arm pose + hand state into a 44-byte Command frame.
     *
     * grip_amount encodes the hand boolean:
     *   1.0f = hand closed
     *   0.0f = hand open
     */
    fun encode(
        shoulder: Landmark,
        elbow: Landmark,
        wrist: Landmark,
        imageWidth: Int,
        imageHeight: Int,
        handClosed: Boolean,
        sequenceId: Long,
        timestampMs: Long
    ): ByteArray {
        val ss = scaleLandmark(shoulder, imageWidth, imageHeight)
        val es = scaleLandmark(elbow,    imageWidth, imageHeight)
        val ws = scaleLandmark(wrist,    imageWidth, imageHeight)

        val armLength = dist(ss, es) + dist(es, ws)
        val scale = if (armLength > 0f) 1f / armLength else 1f

        val px = (ws.x - ss.x) * scale
        val py = (ws.y - ss.y) * scale
        val pz = (ws.z - ss.z) * scale

        val ox = (ws.x - es.x) * scale
        val oy = (ws.y - es.y) * scale
        val oz = (ws.z - es.z) * scale

        val buf = ByteBuffer.allocate(FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(MAGIC.toShort())
        buf.put(VERSION.toByte())
        buf.putInt((sequenceId and 0xFFFFFFFFL).toInt())
        buf.putDouble(timestampMs / 1000.0)
        buf.putFloat(px)
        buf.putFloat(py)
        buf.putFloat(pz)
        buf.putFloat(ox)
        buf.putFloat(oy)
        buf.putFloat(oz)
        buf.putFloat(if (handClosed) 1.0f else 0.0f)  // grip_amount: 1.0=closed, 0.0=open

        val payload = buf.array().copyOf(FRAME_SIZE - 1)
        val checksum = payload.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }.toByte()
        buf.put(checksum)

        return buf.array()
    }
}
