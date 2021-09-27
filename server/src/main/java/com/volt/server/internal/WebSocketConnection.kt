package com.volt.server.internal

import android.util.Log
import com.volt.server.extensions.toByteArray
import com.volt.server.extensions.toHex
import com.volt.server.utils.StreamBufferReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.experimental.and

private const val maxPayloadSize = 524288
private const val TAG = "WebSocketConnection"

internal enum class WebSocketConnectionStatus {
    DISCONNECTED,
    CONNECTED
}

internal class WebSocketConnection(
        private val scope: CoroutineScope,
        private val clientSocket: Socket,
        private val readStream: StreamBufferReader,
        private val outputStream: OutputStream
) {
    private val _connectionStatus = MutableStateFlow(WebSocketConnectionStatus.CONNECTED)
    val connectionStatus: StateFlow<WebSocketConnectionStatus> = _connectionStatus

    init {
        waitForIncomingData()
    }

    private fun close() {
        Log.d(TAG, "Closing connection with ${clientSocket.inetAddress.hostAddress}")
        _connectionStatus.value = WebSocketConnectionStatus.DISCONNECTED
        outputStream.flush()
        readStream.close()
    }

    private fun waitForIncomingData() {
        scope.launch(Dispatchers.IO) {
            while (connectionStatus.value == WebSocketConnectionStatus.CONNECTED && isActive) {
                val byteBuffer = try {
                    readStream.readBytes(2)
                } catch(e: Exception) {
                    Log.d(TAG, "Exception reading web socket.", e)
                    close()
                    return@launch
                }?: continue

                if (byteBuffer.position() < byteBuffer.limit()) {
                    parseIncomingData(byteBuffer)
                }
            }
        }
    }

    private fun parseIncomingData(data: ByteBuffer) {
        Log.d(TAG, "WebSocket incoming data: ${data.limit() - data.position()} bytes. First ${40.coerceAtMost(data.limit() - data.position())} bytes: ${data.toHex(40.coerceAtMost(data.limit() - data.position()), false)}")

        if (data.get().and(0x9) == 0x9.toByte()) {
            // Ping frame
            val applicationLengthByte = data.get().and(0x7F)

            when(applicationLengthByte) {
                0x00.toByte() -> {
                    Log.d(TAG, "PING Application Length: empty")
                }

                0xFE.toByte() -> {
                    Log.d(TAG, "PING Application Length: 0xFE")
                }

                0xFF.toByte() -> {
                    Log.d(TAG, "PING Application Length: 0xFF")
                }

                else -> {
                    Log.d(TAG, "PING ApplicationLength: $applicationLengthByte")
                }
            }

            val frame = byteArrayOf(0x8A.toByte(), applicationLengthByte)

            Log.d(TAG, "Send PONG frame to ${clientSocket.inetAddress.hostAddress}")
            Log.d(TAG, "First ${40.coerceAtMost(frame.size)} bytes: ${frame.toHex(40.coerceAtMost(frame.size))}")

            // Send Pong frame
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                outputStream.write(frame)
                @Suppress("BlockingMethodInNonBlockingContext")
                outputStream.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write pong frame to webSocket", e)
                close()
                return
            }
        }

    }

    suspend fun sendData(data: ByteArray, isBinary: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (_connectionStatus.value != WebSocketConnectionStatus.CONNECTED) {
            Log.d(TAG, "Message not sent to ${clientSocket.inetAddress.hostAddress}. Disconnected")
            return@withContext false
        }

        if (data.isEmpty()) {
            return@withContext true
        }

        var offset = 0

        while(offset < data.size) {
            var fin = 0x8
            val opcode = when {
                offset > 0 -> 0x0
                isBinary -> 0x2
                else -> 0x1 // Text frame
            }

            val frame: ByteArray

            when {
                data.size - offset < 126 -> {
                    frame = ByteArray(2 + data.size - offset)
                    frame[1] = data.size.toByte()
                    data.copyInto(frame, 2, offset)
                }

                data.size - offset <= 65536 -> {
                    frame = ByteArray(2 + 2 + data.size - offset)
                    frame[1] = 0x7E.toByte()
                    val lengthBytes = (data.size - offset).toUInt().toByteArray()
                    lengthBytes.copyInto(frame, 4 - lengthBytes.size)
                    data.copyInto(frame, 4, offset)
                }

                data.size - offset <= maxPayloadSize -> {
                    frame = ByteArray(2 + 8 + data.size - offset)
                    frame[1] = 0x7F.toByte()
                    val lengthBytes = (data.size - offset).toUInt().toByteArray()
                    lengthBytes.copyInto(frame, 10 - lengthBytes.size)
                    data.copyInto(frame, 10, offset)
                }

                else -> {
                    frame = ByteArray(2 + 8 + maxPayloadSize)
                    fin = 0x0 // Not an end frame
                    frame[1] = 0x7F.toByte()
                    val lengthBytes = maxPayloadSize.toUInt().toByteArray()
                    lengthBytes.copyInto(frame, 10 - lengthBytes.size)
                    data.copyInto(frame, 10, offset, offset + maxPayloadSize)
                }
            }

            frame[0] = ((fin shl 4) + opcode).toByte()

            Log.d(TAG, "Sending ${data.size} Bytes to client. Frame size: ${frame.size} Bytes")
            Log.d(TAG, "First ${40.coerceAtMost(frame.size)} bytes: ${frame.toHex(40.coerceAtMost(frame.size))}")

            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                outputStream.write(frame)
                @Suppress("BlockingMethodInNonBlockingContext")
                outputStream.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to webSocket", e)
                close()
                return@withContext false
            }

            // Last frame?
            if (fin == 0x8) {
                return@withContext true
            }

            offset += maxPayloadSize
        }

        return@withContext true
    }

}