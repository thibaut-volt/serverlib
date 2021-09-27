package com.volt.server.interfaces

import com.volt.server.enums.HttpCode
import kotlinx.serialization.KSerializer

interface IResponse {
    fun getHttpCode(): HttpCode?
    fun getData(): String?
    fun <T> sendJson(data: T, serializer: KSerializer<T>, httpCode: HttpCode = HttpCode.OK)
    fun sendText(data: String, httpCode: HttpCode = HttpCode.OK)
    fun sendEmpty(httpCode: HttpCode = HttpCode.OK)
    fun sendInvalidResponse(restApi: String?)
    fun sendMultiParts(parts: (IMultiPartResponse) -> Unit)
}
