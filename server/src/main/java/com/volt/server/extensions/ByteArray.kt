package com.volt.server.extensions

import android.util.Base64

private const val hexChars = "0123456789ABCDEF"

fun ByteArray.toHex(limit: Int = size): String {
    val result = StringBuilder(limit * 2)

    for(i in 0 until limit) {
        val v = this[i].toInt()
        result.append(hexChars[v shr 4 and 0x0f])
        result.append(hexChars[v and 0x0f])
        result.append(" ")
    }

    return result.toString()
}

internal fun ByteArray.base64() = Base64.encodeToString(this, Base64.NO_WRAP)
