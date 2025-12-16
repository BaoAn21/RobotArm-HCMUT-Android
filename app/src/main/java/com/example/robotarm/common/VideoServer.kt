package com.example.robotarm.common

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

object VideoServer {
    private const val PORT = 6001
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                Log.d("VideoServer", "Opening Video Server on $PORT...")
                serverSocket = ServerSocket(PORT)

                // This blocks until the Laptop script connects!
                clientSocket = serverSocket?.accept()
                outputStream = DataOutputStream(clientSocket?.getOutputStream())
                Log.d("VideoServer", "Laptop Connected for Video!")
            } catch (e: Exception) {
                Log.e("VideoServer", "Start Error", e)
                isRunning = false
            }
        }
    }

    fun sendFrame(bitmap: Bitmap) {
        // If nobody is connected, don't waste CPU compressing images
        if (outputStream == null || !isRunning) return

        scope.launch {
            try {
                // 1. Compress to JPEG
                val byteStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteStream)
                val jpegBytes = byteStream.toByteArray()

                // 2. Send Size (4 bytes)
                outputStream?.writeInt(jpegBytes.size)

                // 3. Send Image Data
                outputStream?.write(jpegBytes)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e("VideoServer", "Send Error", e)
                // Optional: Stop server or try to reconnect logic could go here
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            outputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
            outputStream = null
            clientSocket = null
            serverSocket = null
        } catch (e: Exception) {
            Log.e("VideoServer", "Stop Error", e)
        }
    }
}