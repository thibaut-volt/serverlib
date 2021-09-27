package com.volt.server.utils

import android.util.Log
import com.volt.server.exceptions.InternalRouteException
import com.volt.server.extensions.sliceRange
import com.volt.server.extensions.toHex
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

private const val bufferSize = 262144
internal const val longReadTimeout = 15000L
private const val TAG = "HttpStreamReader"
private const val returnCarrier = 13.toByte()
private const val lineFeed = 10.toByte()

internal fun ByteBuffer.putWithAllocation(buffer: ByteBuffer): ByteBuffer {
    val buf = if (position() + buffer.limit() > capacity()) {
        // Increase size of output buffer
        ByteBuffer.allocate(capacity() + bufferSize).also {
            it.put(sliceRange(0, position()))
        }
    } else {
        this
    }

    buf.put(buffer)

    return buf
}

class StreamBufferReader(private val inputStream: InputStream, private val scope: CoroutineScope) {

    private var buffer = ByteBuffer.allocate(bufferSize)

    init {
        buffer.position(buffer.limit())
    }

    fun close() {
        @Suppress("BlockingMethodInNonBlockingContext")
        inputStream.close()
    }

    suspend fun readBytesWithTimeout(bytesToRead: Int? = null, timeout: Long = longReadTimeout)
            = readBytes(bytesToRead, timeout, null, null)
    suspend fun readBytes(bytesToRead: Int? = null)
            = readBytes(bytesToRead, null, null, null)
    suspend fun readLine() = readBytesUntil(byteArrayOf(returnCarrier, lineFeed))

    suspend fun readBytes(
            bytesToRead: Int?,
            timeout: Long?,
            outputStream: OutputStream?,
            onPartialRead: ((buffer: ByteBuffer) -> Unit)?
    ): ByteBuffer? {
//        Log.d(TAG, "Reading $bytesToRead bytes")

        if (bytesToRead != null && bytesToRead <= 0) {
            Log.w(TAG, "Calling readBytes with 0 bytes to read")
            return null
        }

        var totalBytesRead = 0
        var bufBytesRead: ByteBuffer? = null
        var startPosition = buffer.position()

        while(true) {
            if (buffer.position() >= buffer.limit()) {
                if (outputStream == null && startPosition != buffer.position()) {
                    if (bufBytesRead == null) {
                        bufBytesRead = ByteBuffer.allocate(bufferSize)
                    }

                    bufBytesRead = bufBytesRead?.putWithAllocation(buffer.sliceRange(startPosition))
                }

                startPosition = 0

                try {
                    readBuffer(timeout)
                } catch (e: TimeoutException) {
                    Log.d(TAG, "Timeout reading socket. Cannot read $bytesToRead bytes")
                    throw e
                }

                if (buffer.position() >= buffer.limit()) {
                    if (bytesToRead == null) {
                        return bufBytesRead
                    }

                    throw InternalRouteException("Could not read $bytesToRead bytes")
                }
            }

            if (bytesToRead != null && totalBytesRead + buffer.limit() - buffer.position() >= bytesToRead) {
                if (outputStream == null) {
//                    Log.d(TAG, "startPosition: $startPosition - totalBytesRead: $totalBytesRead - bytesToRead: $bytesToRead")
                    return buffer.sliceRange(startPosition, startPosition + bytesToRead - totalBytesRead).let { slice ->
//                        Log.d(TAG, "sliceRange 1: pos = ${it.position()}, limit = ${it.limit()}")

                        if (bufBytesRead == null) {
                            onPartialRead?.let { it(slice.sliceRange()) }
                            slice
                        } else {
                            bufBytesRead.putWithAllocation(slice).also { newBuffer ->
                                newBuffer.limit(newBuffer.position())
                                newBuffer.position(0)
                                onPartialRead?.let { it(newBuffer.sliceRange()) }
                            }
                        }
                    }
                }

                buffer.sliceRange(startPosition, startPosition + bytesToRead - totalBytesRead).let { slice ->
                    onPartialRead?.let {
//                        Log.d(TAG, "Sending last partial data: ${slice.limit() - slice.position()} bytes")
                        it(slice)
                    }
                    @Suppress("BlockingMethodInNonBlockingContext")
                    outputStream.write(slice.array(), slice.position(), slice.limit() - slice.position())
                }

                return null
            }

            val remainingBytesInBuffer = buffer.sliceRange()
            onPartialRead?.let {
//                val savedPos = remainingBytesInBuffer.position()
//                Log.d(TAG, "Sending partial data: ${remainingBytesInBuffer.limit() - remainingBytesInBuffer.position()} bytes, First 20 Bytes = ${remainingBytesInBuffer.sliceRange(remainingBytesInBuffer.position(), remainingBytesInBuffer.position() + 20).array().toHex(20)}")
//                remainingBytesInBuffer.position(savedPos)

                it(remainingBytesInBuffer)
            }

            if (outputStream != null) {
                @Suppress("BlockingMethodInNonBlockingContext")
                outputStream.write(remainingBytesInBuffer.array(), remainingBytesInBuffer.position(), remainingBytesInBuffer.limit() - remainingBytesInBuffer.position())
            }

            totalBytesRead += remainingBytesInBuffer.limit()

            buffer.position(buffer.limit())
        }
    }

    suspend fun readBytesUntil(sequence: ByteArray, includeSequence: Boolean = true): ByteBuffer {
        var testByteIndex = 0
        var bufBytesRead: ByteBuffer? = null
        var startPosition = buffer.position()

        while(testByteIndex < sequence.size) {
            if (buffer.position() >= buffer.limit()) {
                if (startPosition !=  buffer.position()) {
                    if (bufBytesRead == null) {
                        bufBytesRead = ByteBuffer.allocate(bufferSize)
                    }

                    bufBytesRead = bufBytesRead!!.putWithAllocation(buffer.sliceRange(startPosition))
                }

                startPosition = 0

                try {
                    readBuffer(longReadTimeout)
                } catch (e: TimeoutException) {
                    Log.d(TAG, "Timeout reading socket. Cannot read bytes until: ${sequence.toHex()}")
                    throw e
                }

                if (buffer.limit() <= 0) {
                    throw InternalRouteException("Cannot find byte")
                }
            }

            if (buffer.get() == sequence[testByteIndex]) {
                ++testByteIndex
            } else {
                testByteIndex = 0
            }
        }

        return buffer.sliceRange(startPosition, buffer.position()).let {
//            Log.d(TAG, "sliceRange 0: pos = ${it.position()}, limit = ${it.limit()}")
            bufBytesRead?.putWithAllocation(it)?.also { newBuffer ->
                if (includeSequence) {
                    newBuffer.limit(newBuffer.position())
                } else {
                    newBuffer.limit(newBuffer.limit() - sequence.size)
                }
                newBuffer.position(0)
            } ?: if (includeSequence) {
                it
            } else {
                it.sliceRange(it.position(), it.limit() - sequence.size)
            }
        }
    }

    private suspend fun readBuffer(timeout: Long?) {
        val startOperationTime = System.currentTimeMillis()

        if (!scope.isActive) {
            throw Exception("Scope inactive")
        }

        if (timeout != null) {
            @Suppress("BlockingMethodInNonBlockingContext")
            while (inputStream.available() == 0) {
                if (System.currentTimeMillis() - startOperationTime > timeout) {
                    throw TimeoutException("Timeout reading socket")
                }

                delay(10L)
            }
        }

        buffer.position(0)
        buffer.limit(0)

//        val start = System.currentTimeMillis()

        val bytesRead = try {
            @Suppress("BlockingMethodInNonBlockingContext")
            inputStream.read(buffer.array(), 0, bufferSize)
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read from socket", e)
            0
        }

//        val end = System.currentTimeMillis()
//        Log.d(TAG, "Read $bytesRead bytes in: ${end - start}ms.")

        if (!scope.isActive) {
            Log.d(TAG, "Scope no longer active.")
            return
        }

        if (bytesRead > 0) {
            buffer.limit(bytesRead)
        } else {
            buffer.limit(0)
        }

        if (bytesRead > 0) {
//            Log.d(TAG, "str: ${buffer.sliceRange(0, bytesRead.coerceAtMost(500)).toStr()}")
//            Log.d(TAG, "data: ${buffer.sliceRange(0, bytesRead.coerceAtMost(500)).array().toHex(bytesRead.coerceAtMost(500))}")
            buffer.position(0)
        }
    }
}