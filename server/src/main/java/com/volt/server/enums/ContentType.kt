package com.volt.server.enums

enum class ContentType(val str: String, val isBinary: Boolean) {
    TEXT_PLAIN("text/plain", false),
    APPLICATION_JSON("application/json", false),
    IMAGE("image", true)
}