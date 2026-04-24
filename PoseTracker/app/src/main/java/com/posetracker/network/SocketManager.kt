package com.posetracker.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException

object SocketManager {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    suspend fun connect(address: String, port: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                disconnectInternal()
                val s = Socket(address, port)
                s.keepAlive = true
                s.soTimeout = 0
                outputStream = s.getOutputStream()
                socket = s
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun sendBytes(data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val os = outputStream ?: return@withContext false
                os.write(data)
                os.flush()
                true
            } catch (e: Exception) {
                false
            }
        }

    /** Call from a coroutine — safely dispatches to IO thread. */
    suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectInternal() }

    private fun disconnectInternal() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false
}
