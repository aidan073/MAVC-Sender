package com.posetracker.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException

/**
 * Singleton that manages a single TCP socket connection.
 * All I/O is dispatched on [Dispatchers.IO].
 */
object SocketManager {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    /**
     * Opens a TCP connection to [address]:[port].
     * Returns [Result.success] on success, [Result.failure] with the exception on failure.
     */
    suspend fun connect(address: String, port: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                disconnect()                          // close any existing connection
                val s = Socket(address, port)
                s.keepAlive = true
                s.soTimeout = 0                       // no read timeout for streaming
                writer = PrintWriter(s.getOutputStream(), true)
                socket = s
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Sends a newline-terminated JSON string over the socket.
     * Returns true if sent successfully.
     */
    suspend fun send(json: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val w = writer ?: return@withContext false
                w.println(json)          // println appends '\n' — easy to parse server-side
                !w.checkError()
            } catch (e: SocketException) {
                false
            }
        }

    /** Closes the socket and resets state. Safe to call multiple times. */
    fun disconnect() {
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false
}
