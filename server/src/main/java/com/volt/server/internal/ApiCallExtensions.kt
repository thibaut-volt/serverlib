package com.volt.server.internal

import com.volt.server.models.ApiCall

fun ApiCall.withResponse(responseCode: Int, responseData: String?) = ApiCall(
        id,
        requestTime,
        requestIp,
        requestType,
        requestUrl,
        connection,
        System.currentTimeMillis(),
        responseCode,
        responseData
)