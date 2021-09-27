package com.volt.server

import android.content.Context
import android.util.Log
import com.volt.server.enums.HttpCode
import com.volt.server.extensions.base64
import com.volt.server.extensions.sha1
import com.volt.server.internal.*
import com.volt.server.internal.Server
import com.volt.server.internal.parseHttpHeaders
import com.volt.server.internal.parseHttpUrlHeader
import com.volt.server.models.ApiCall
import com.volt.server.utils.StreamBufferReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedInputStream
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList

private const val TAG = "WebSocketServer"

class WebSocketServer(context: Context, private val scope: CoroutineScope) {

    private var nextApiCall = 0
    private val wsConnections = ArrayList<WebSocketConnection>()
    private val _devApiCallFlow = MutableStateFlow<ApiCall?>(null)

    val devApiCallFlow : StateFlow<ApiCall?> = _devApiCallFlow

    private val server = Server(scope, {
        Log.d(TAG, "WebSocket Server listening at: ${getLocalIpAddress(context)}:$it")
    }, {
        Log.d(TAG, "WebSocket Server stopped")
    }, {
        handshake(it)
    })

    fun start(port: Int) {
        if (server.isServerStarted()) {
            return
        }

        Log.d(TAG, "Starting WebSocket server on port: $port")
        server.start(port)
    }

    suspend fun stop() {
        if (!server.isServerStarted()) {
            return
        }

        Log.d(TAG, "Stopping WebSocket server")
        server.stop()
    }

    suspend fun restart(port: Int) {
        stop()
        start(port)
    }

    fun getPort() = server.port

    fun broadcastMessage(message: String)
        = broadcastData(message.toByteArray(Charsets.UTF_8), false)

    fun broadcastData(data: ByteArray, isBinary: Boolean = true) {
        scope.launch {
            wsConnections.forEach { it.sendData(data, isBinary) }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun handshake(clientSocket: Socket) = withContext(Dispatchers.IO) {
        val localScope = scope + Dispatchers.IO
        @Suppress("BlockingMethodInNonBlockingContext")
        val streamReader = StreamBufferReader(BufferedInputStream(clientSocket.getInputStream()), localScope)
        @Suppress("BlockingMethodInNonBlockingContext")
        val outputStream = clientSocket.getOutputStream()

        val httpUrlHeader = try {
            parseHttpUrlHeader(streamReader)
        } catch (e: Exception) {
            streamReader.close()
            Log.e(TAG, "Cannot complete handshake", e)
            return@withContext
        }

        val headers = try {
            parseHttpHeaders(streamReader)
        } catch (e: Exception) {
            streamReader.close()
            Log.e(TAG, "Cannot complete handshake", e)
            return@withContext
        }

        val apiCall = --nextApiCall

        val apiRequestCall = ApiCall(
                apiCall,
                System.currentTimeMillis(),
                clientSocket.inetAddress.hostAddress,
                httpUrlHeader.method,
                httpUrlHeader.url,
                "WEBSOCKET"
        )

        _devApiCallFlow.value = apiRequestCall

        val webSocketVersion = headers["Sec-WebSocket-Version"]

        if (webSocketVersion != "13") {
            Log.d(TAG, "Invalid webSocket version ($webSocketVersion)")
            streamReader.close()
            return@withContext
        }

        val webSocketKey = headers["Sec-WebSocket-Key"]

        if (webSocketKey == null) {
            Log.d(TAG, "Missing webSocket key")
            streamReader.close()
            return@withContext
        }

        try {
            outputStream.write("HTTP/1.1 ${HttpCode.SWITCHING_PROTOCOLS.code} ${HttpCode.SWITCHING_PROTOCOLS.str}$crlf".toByteArray())
            outputStream.write("Server: WebSocket Server by TVolt : 1.0$crlf".toByteArray())
            outputStream.write("Date: ${Date()}$crlf".toByteArray())
            outputStream.write("Upgrade: websocket$crlf".toByteArray())
            outputStream.write("Connection: Upgrade$crlf".toByteArray())
            outputStream.write("Sec-WebSocket-Accept: ${(webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").sha1().base64()}$crlf".toByteArray())
            outputStream.write(crlfBytes)
        } catch(e: Exception) {
            Log.e(TAG, "Cannot send handshake response", e)
            _devApiCallFlow.value = apiRequestCall.withResponse(-1, null)
            streamReader.close()
            @Suppress("BlockingMethodInNonBlockingContext")
            outputStream.flush()
            return@withContext
        }

        val wsConnection = WebSocketConnection(localScope, clientSocket, streamReader, outputStream)
        wsConnections.add(wsConnection)

        // Remove connection if disconnected
        scope.launch {
            wsConnection.connectionStatus
                    .filter { it == WebSocketConnectionStatus.DISCONNECTED }
                    .firstOrNull()
                    ?.let {
                        Log.d(TAG, "Removing connection. ${clientSocket.inetAddress.hostAddress}")
                        wsConnections.remove(wsConnection)
                    }
        }

        // Notify developer listener of response
        _devApiCallFlow.value = apiRequestCall.withResponse(HttpCode.SWITCHING_PROTOCOLS.code, null)
    }
}