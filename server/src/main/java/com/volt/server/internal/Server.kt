package com.volt.server.internal

import android.util.Log
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "Server"

internal class Server(
        private val scope: CoroutineScope,
        private val onStarted: (port: Int) -> Unit,
        private val onStopped: () -> Unit,
        private val onClientConnected: suspend (client: Socket) -> Unit,
) {
    private val isServerRunning = AtomicBoolean(false)
    private var shouldStopServer = false
    private var serverSocket: ServerSocket? = null
    var port: Int? = null
        private set

    fun start(port: Int) {
        if (isServerRunning.get()) {
            return
        }

        this.port = port
        serverSocket = ServerSocket(port)
        isServerRunning.set(true)
        Log.d(TAG, "Start listening on port: $port")

        Thread {
            if (!scope.isActive) {
                return@Thread
            }

            var disconnect = false

            onStarted(port)

            while (true) {
                val client: Socket? = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    Log.e(TAG, "Socket exception", e)
                    null
                }

                synchronized(shouldStopServer) {
                    if (shouldStopServer) {
                        disconnect = true
                    }
                }

                if (disconnect) {
                    break
                }

                if (client == null) {
                    continue
                }

                Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress} on server port: $port")

                if (!scope.isActive) {
                    break
                }

                scope.launch {
                    try {
                        onClientConnected(client)
                    } catch(e: Exception) {
                        Log.e(TAG, "Exception when processing connected client", e)
                    }
                }
            }

            Log.d(TAG, "Server listening on port $port stopped")

            onStopped()
            isServerRunning.set(false)
        }.start()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun stop() = withContext(Dispatchers.IO) {
        if (!isServerRunning.get()) {
            return@withContext
        }

        Log.d(TAG, "Stop listening on port: $port")

        synchronized(shouldStopServer) {
            shouldStopServer = true
            serverSocket?.close()
        }

        while(true) {
            delay(200)

            if (!isServerRunning.get()) {
                break
            }
        }

        synchronized(shouldStopServer) {
            shouldStopServer = false
        }
    }

    fun isServerStarted() = isServerRunning.get()
}
