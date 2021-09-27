package com.volt.server.internal

import com.volt.server.HttpPart
import com.volt.server.extensions.toStr
import com.volt.server.interfaces.IRequest
import com.volt.server.internal.*
import com.volt.server.utils.StreamBufferReader
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.ByteBuffer

private val multipartHeaderRegExp = Regex("multipart/form-data; ?boundary=(.+)")
private const val TAG = "HttpRequest"

internal class HttpRequest(
        override val headers: Map<String, String>,
        override val params: Map<String, String>,
        override val arguments: Map<String, String>,
        private val reader: StreamBufferReader
): IRequest {

    private val multiPartBoundary = headers["Content-Type"]?.let {
        val matches = multipartHeaderRegExp.matchEntire(it) ?: return@let null

        if (matches.groups.size > 1) {
            return@let "--" + matches.groups[1]!!.value
        }

        return@let null
    }

    override suspend fun getBody(): String {
        val bytesToRead = headers["Content-Length"]?.toInt()
                ?: throw Exception("Missing Content-Length header")
        return reader.readBytesWithTimeout(bytesToRead)?.toStr()
                ?: throw Exception("Cannot read request body")
    }

    override suspend fun <T> getBody(deserializer: KSerializer<T>): T {
        return Json.decodeFromString(deserializer, getBody())
            ?: throw Exception("Cannot deserialize body")
    }

    override suspend fun readNextPart(): HttpPart? {
        val boundary = multiPartBoundary ?: return null
        val boundaryLine = reader.readLine().toStr()

        if (boundaryLine != "$boundary\r\n") {
            return null
        }

        var contentType: String? = null
        var contentDisposition: String? = null
        var contentLength = 0
        var bufferLine: ByteBuffer?

        while(true) {
            bufferLine = reader.readLine()

            if (bufferLine.limit() - bufferLine.position() <= crlf.length) {
                break
            }

            val line = bufferLine.toStr()
            val matchHeader = httpHeaderLineRegExp.matchEntire(line) ?: continue

            when (matchHeader.groups[1]!!.value) {
                "Content-Disposition" -> contentDisposition = matchHeader.groups[2]!!.value
                "Content-Type" -> contentType = matchHeader.groups[2]!!.value
                "Content-Length" -> contentLength = matchHeader.groups[2]!!.value.toInt()
            }
        }

        val contentData = if (contentLength != 0 && contentDisposition?.startsWith("form-data;") == true) {
            val map = HashMap<String, String>()

            contentDisposition
                    .trim()
                    .split(Regex("; ?"))
                    .drop(1)
                    .forEach {
                        val pair = it.split("=")

                        if (pair.size == 2) {
                            map[pair[0]] = URLDecoder.decode(pair[1].replace("\"", ""), "UTF-8")
                        }
                    }

            map
        } else {
            emptyMap()
        }

        return HttpPart(contentType, contentDisposition, contentLength, contentData, reader)
    }
}
