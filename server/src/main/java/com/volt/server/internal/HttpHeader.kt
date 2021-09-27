package com.volt.server.internal

import android.net.Uri
import com.volt.server.exceptions.HttpException
import com.volt.server.extensions.toStr
import com.volt.server.utils.StreamBufferReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val httpMethodPathRegExp = Regex("(.+) (.+) HTTP/\\d.\\d\\r\\n")
internal val httpHeaderLineRegExp = Regex("([^:]+):[ ]*(.+)\\r\\n")

internal data class HttpHeaderUrl(val method: String, val url: String)

internal suspend fun parseHttpUrlHeader(
        reader: StreamBufferReader
): HttpHeaderUrl = withContext(Dispatchers.IO) {
    val line =  reader.readLine().toStr()
    val methodPathMatch = httpMethodPathRegExp.matchEntire(line)

    if (methodPathMatch == null || methodPathMatch.groups.size <= 2) {
        throw HttpException("Invalid HTTP header")
    }

    return@withContext HttpHeaderUrl(
            methodPathMatch.groups[1]!!.value,
            Uri.parse(methodPathMatch.groups[2]!!.value).toString()
    )
}

suspend fun parseHttpHeaders(
        reader: StreamBufferReader
): Map<String, String> = withContext(Dispatchers.IO) {
    val headers = HashMap<String, String>()

    while(true) {
        val headerLine = reader.readLine().toStr()

        if (headerLine.length == 2) { // headerLine == \r\n
            break
        }

        httpHeaderLineRegExp.matchEntire(headerLine)?.let {
            if (it.groups.size > 1) {
                headers[it.groups[1]!!.value] = it.groups[2]!!.value
            }
        }
    }

    return@withContext headers
}