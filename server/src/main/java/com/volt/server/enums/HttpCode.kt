package com.volt.server.enums

enum class HttpCode(val code: Int, val str: String) {
    OK(200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    SWITCHING_PROTOCOLS(101, "Switching Protocols")
}