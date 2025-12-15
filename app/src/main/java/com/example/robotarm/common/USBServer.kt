package com.example.robotarm.common

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

object UsbServer {
    private const val PORT = 6000
    private var serverSocket: ServerSocket? = null

    // Volatile ensures all threads see the latest writer immediately
    @Volatile private var writer: PrintWriter? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

    suspend fun start() {
        if (isRunning) return
        isRunning = true

        withContext(Dispatchers.IO) {
            try {
                Log.d("USB", "Starting Server on Port $PORT...")
                // Cleanup old socket if exists
                if (serverSocket != null && !serverSocket!!.isClosed) {
                    serverSocket?.close()
                }
                serverSocket = ServerSocket(PORT)

                // --- THE FIX: LOOP TO ACCEPT RECONNECTIONS ---
                while (isActive && isRunning) {
                    try {
                        Log.d("USB", "Waiting for Laptop...")
                        // 1. Block until a client connects
                        val socket = serverSocket?.accept()

                        Log.d("USB", "Laptop Connected!")
                        socket?.tcpNoDelay = true

                        // 2. Assign the new writer (Overwriting any old one)
                        writer = PrintWriter(socket!!.getOutputStream(), true)

                        // 3. Loop back immediately to accept() again.
                        // This means if you restart Python, 'accept' will wake up
                        // and give us the new connection instantly.

                    } catch (e: Exception) {
                        if (isRunning) Log.e("USB", "Connection Error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("USB", "Server Error", e)
            } finally {
                stop()
            }
        }
    }

    fun sendData(x: Float, y: Float) {
        scope.launch {
            try {
                // Only send if writer exists and no error is detected
                if (writer != null && !writer!!.checkError()) {
                    writer?.println("${x.toInt()},${y.toInt()}")
                }
            } catch (e: Exception) {
                Log.e("USB", "Send Error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            writer?.close()
            serverSocket?.close()
            writer = null
            serverSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}