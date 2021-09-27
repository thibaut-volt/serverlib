package com.volt.server.extensions

import java.nio.ByteBuffer
import java.nio.charset.Charset

fun ByteBuffer.toStr(encoding: Charset = Charsets.UTF_8)
        = String(array(), position(), limit() - position(), encoding)

fun ByteBuffer.sliceRange(start: Int = position(), end: Int = limit(), movePositionToEnd: Boolean = true): ByteBuffer {
    position(0)
    val slicedBuffer = slice()
    slicedBuffer.position(start)
    slicedBuffer.limit(end)

    if (movePositionToEnd) {
        position(end)
    }

    return slicedBuffer
}

fun ByteBuffer.toHex(size: Int = limit() - position(), movePositionToEnd: Boolean = true)
        = sliceRange(position(), position() + size, movePositionToEnd).array().toHex(limit() - position())
