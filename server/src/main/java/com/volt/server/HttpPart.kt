package com.volt.server

import com.volt.server.utils.StreamBufferReader
import com.volt.server.utils.longReadTimeout
import java.io.OutputStream
import java.nio.ByteBuffer

class HttpPart(
        val contentType: String?,
        val contentDisposition: String?,
        val contentLength: Int,
        val contentData: Map<String, String>,
        private val reader: StreamBufferReader) {

    suspend fun getBody(outputStream: OutputStream, onPartialRead: ((buffer: ByteBuffer) -> Unit)?)
        = reader.readBytes(contentLength, longReadTimeout, outputStream, onPartialRead)
}