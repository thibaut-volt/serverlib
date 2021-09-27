package com.volt.server.interfaces

import com.volt.server.HttpPart
import kotlinx.serialization.KSerializer

interface IRequest {
    val headers: Map<String, String>
    val params: Map<String, String>
    val arguments: Map<String, String>

    suspend fun getBody(): String
    suspend fun <T> getBody(deserializer: KSerializer<T>): T
    suspend fun readNextPart(): HttpPart?
}