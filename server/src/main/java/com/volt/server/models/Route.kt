package com.volt.server.models

import com.volt.server.interfaces.IRequest
import com.volt.server.interfaces.IResponse

data class Route(
        val callback: suspend (request: IRequest, response: IResponse) -> Unit,
        val params: Map<String, String>,
        val arguments: Map<String, String>
)