package com.volt.server.interfaces

import com.volt.server.enums.ContentType
import kotlinx.serialization.KSerializer

interface IMultiPartResponse {
    fun <T> sendJson(data: T, serializer: KSerializer<T>)
    fun sendText(data: String)
    fun sendData(contentType: ContentType, data: ByteArray)
    fun flush()
}
