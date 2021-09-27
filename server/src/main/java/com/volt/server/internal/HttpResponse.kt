package com.volt.server.internal

import android.util.Log
import com.volt.server.enums.ContentType
import com.volt.server.enums.HttpCode
import com.volt.server.extensions.toHex
import com.volt.server.interfaces.IMultiPartResponse
import com.volt.server.interfaces.IResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.*

private const val responseInvalidApi = "Invalid api"
internal const val crlf = "\r\n"
internal val crlfBytes = crlf.toByteArray()
private const val TAG = "HttpResponse"

private data class PartResponse(val contentType: ContentType, val data: ByteArray)

internal class HttpResponse(
        private val outputStream: OutputStream,
        private val overrideHeader: ((code: HttpCode) -> String)?
): IResponse {

    private var httpCode: HttpCode? = null
    private var data: String? = null

    private var totalResponseSize = 0

    override fun getHttpCode() = httpCode
    override fun getData() = data

    override fun <T> sendJson(data: T, serializer: KSerializer<T>, httpCode: HttpCode)
            = sendResponse(Json.encodeToString(serializer, data), ContentType.APPLICATION_JSON, httpCode)
    override fun sendText(data: String, httpCode: HttpCode)
            = sendResponse(data, ContentType.TEXT_PLAIN, httpCode)
    override fun sendEmpty(httpCode: HttpCode)
            = sendResponse(null, ContentType.TEXT_PLAIN, httpCode)

    private fun sendResponse(data: String?, contentType: ContentType, httpCode: HttpCode) {
        this.httpCode = httpCode
        this.data = data

        data?.let { totalResponseSize += it.length }

        Log.d(TAG, "Sending data $totalResponseSize bytes: $data")

        overrideHeader?.let {
            outputStream.write("${it(httpCode)}$crlf".toByteArray())
        } ?: outputStream.write("HTTP/1.1 ${httpCode.code} ${httpCode.str}$crlf".toByteArray())

        outputStream.write("Server: TVolt 1.0$crlf".toByteArray())
        outputStream.write("Content-Type: ${contentType.str}$crlf".toByteArray())
        outputStream.write("Content-Length: $totalResponseSize$crlf".toByteArray())
        outputStream.write(crlfBytes) // blank line between headers and content
        data?.let { outputStream.write(it.toByteArray()) }
    }

    override fun sendInvalidResponse(restApi: String?) {
        Log.d(TAG, "404 $restApi")

        overrideHeader?.let {
            outputStream.write("${it(HttpCode.NOT_FOUND)}$crlf".toByteArray())
        } ?: outputStream.write("HTTP/1.1 404 Not Found$crlf".toByteArray())

        outputStream.write("Server: TVolt 1.0$crlf".toByteArray())
        outputStream.write("Date: ${Date()}$crlf".toByteArray())
        outputStream.write("Connection: Close$crlf".toByteArray())
        outputStream.write("Content-Type: text/html; charset=iso-8859-1$crlf".toByteArray())
        outputStream.write("Content-length: ${responseInvalidApi.length}$crlf".toByteArray())
        outputStream.write(crlf.toByteArray()) // blank line between headers and content, very important !
        outputStream.write(responseInvalidApi.toByteArray())
    }

    private inner class MultiPartResponse(
            private val outputStream: OutputStream
    ): IMultiPartResponse {

        private var size = 0L
        private val parts = ArrayList<PartResponse>()


        override fun <T> sendJson(data: T, serializer: KSerializer<T>) {
            val bytes = Json.encodeToString(serializer, data).toByteArray()
            size += bytes.size
            parts.add(PartResponse(ContentType.APPLICATION_JSON, bytes))
        }

        override fun sendText(data: String) {
            val bytes = data.toByteArray()
            size += bytes.size
            parts.add(PartResponse(ContentType.TEXT_PLAIN, bytes))
        }

        override fun sendData(contentType: ContentType, data: ByteArray) {
            size += data.size
            parts.add(PartResponse(contentType, data))
        }

        override fun flush() {
            val multiPartBoundary = UUID.randomUUID().toString()

            overrideHeader?.let {
                outputStream.write("${it(HttpCode.OK)}$crlf".toByteArray())
            } ?: outputStream.write("HTTP/1.1 ${HttpCode.OK.code} ${HttpCode.OK.str}$crlf".toByteArray())

            outputStream.write("Server: TVolt 1.0$crlf".toByteArray())
            outputStream.write("Date: ${Date()}$crlf".toByteArray())
            outputStream.write("Connection: Close$crlf".toByteArray())
            outputStream.write("Content-Type: multipart/mixed; boundary=\"$multiPartBoundary\"$crlf".toByteArray())
            outputStream.write("Content-Length: ${size}$crlf".toByteArray())

            parts.forEach {
                outputStream.write("$crlf--$multiPartBoundary$crlf".toByteArray())
                outputStream.write("Content-Type: ${it.contentType.str}$crlf".toByteArray())

                if (it.contentType.isBinary) {
                    outputStream.write("Content-Length: ${it.data.size}$crlf".toByteArray())
                }
Log.d(TAG, "Sending multi part data. Current part: ${it.data.size} bytes. First ${40.coerceAtMost(it.data.size)} bytes: ${it.data.toHex(40.coerceAtMost(it.data.size))}")
                outputStream.write(crlfBytes)
                outputStream.write(it.data)
                outputStream.write(crlfBytes)
            }

            outputStream.write("--$multiPartBoundary--".toByteArray())
        }
    }

    override fun sendMultiParts(parts: (IMultiPartResponse) -> Unit) {
        val multiPartResponse = MultiPartResponse(outputStream)
        parts(multiPartResponse)
        multiPartResponse.flush()
    }
}