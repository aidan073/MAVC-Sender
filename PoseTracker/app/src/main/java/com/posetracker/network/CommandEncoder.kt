package com.posetracker.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes arm/hand pose data into the server's binary Command format.
 *
 * Wire format (little-endian, 44 bytes total):
 *   H  magic          2 bytes  — fixed 0x073CD (29645)
 *   B  version        1 byte
 *   I  sequence_id    4 bytes
 *   d  timestamp      8 bytes  — seconds as double
 *   f  palm_pos x     4 bytes  \
 *   f  palm_pos y     4 bytes   } wrist landmark (normalised 0..1)
 *   f  palm_pos z     4 bytes  /
 *   f  palm_ori x     4 bytes  \
 *   f  palm_ori y     4 bytes   } elbow→wrist direction vector
 *   f  palm_ori z     4 bytes  /
 *   f  grip_amount    4 bytes  — always 0.0 (Pose doesn't track finger curl)
 *   B  checksum       1 byte   — XOR of all preceding 43 bytes
 *
 * MediaPipe landmark indices used:
 *   15 = left wrist   16 = right wrist
 *   13 = left elbow   14 = right elbow
 */
object CommandEncoder {

    private const val MAGIC: Int      = 0x073CD   // 29645 — fits in UShort
    private const val VERSION: Int    = 1
    private const val FRAME_SIZE: Int = 44

    // Use left arm by default; call encode() with isRight=true for right arm.
    const val IDX_LEFT_WRIST  = 15
    const val IDX_LEFT_ELBOW  = 13
    const val IDX_RIGHT_WRIST = 16
    const val IDX_RIGHT_ELBOW = 14

    data class Landmark(val x: Float, val y: Float, val z: Float)

    /**
     * Encodes one arm's data into a 44-byte Command frame.
     *
     * @param wrist       Wrist landmark (used as palm position).
     * @param elbow       Elbow landmark (used to derive palm orientation).
     * @param sequenceId  Monotonically increasing frame counter.
     * @param timestampMs Frame timestamp in milliseconds.
     */
    fun encode(
        wrist: Landmark,
        elbow: Landmark,
        sequenceId: Long,
        timestampMs: Long
    ): ByteArray {
        val buf = ByteBuffer.allocate(FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // palm_position — wrist coords
        val px = wrist.x
        val py = wrist.y
        val pz = wrist.z

        // palm_orientation — elbow→wrist direction vector (not normalised; matches server expectation)
        val ox = wrist.x - elbow.x
        val oy = wrist.y - elbow.y
        val oz = wrist.z - elbow.z

        val timestampSec = timestampMs / 1000.0

        // Pack fields (43 bytes of payload)
        buf.putShort(MAGIC.toShort())                  // H  magic
        buf.put(VERSION.toByte())                      // B  version
        buf.putInt((sequenceId and 0xFFFFFFFFL).toInt()) // I  sequence_id
        buf.putDouble(timestampSec)                    // d  timestamp
        buf.putFloat(px)                               // f  palm_pos x
        buf.putFloat(py)                               // f  palm_pos y
        buf.putFloat(pz)                               // f  palm_pos z
        buf.putFloat(ox)                               // f  palm_ori x
        buf.putFloat(oy)                               // f  palm_ori y
        buf.putFloat(oz)                               // f  palm_ori z
        buf.putFloat(0.0f)                             // f  grip_amount

        // XOR checksum over the 43 payload bytes
        val payload = buf.array().copyOf(FRAME_SIZE - 1)
        val checksum = payload.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }.toByte()
        buf.put(checksum)                              // B  checksum

        return buf.array()
    }
}
