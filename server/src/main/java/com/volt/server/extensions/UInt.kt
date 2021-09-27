package com.volt.server.extensions

internal fun UInt.toByteArray(isBigEndian: Boolean = true): ByteArray {
    var bytes = byteArrayOf()

    var n = this

    if (n == 0x00u) {
        bytes += n.toByte()
    } else {
        while (n != 0x00u) {
            val b = n.toByte()

            bytes += b

            n = n.shr(Byte.SIZE_BITS)
        }
    }

    val padding = 0x00u.toByte()
    var paddings = byteArrayOf()
    repeat(UInt.SIZE_BYTES / 2 - bytes.count()) {
        paddings += padding
    }

    return if (isBigEndian) {
        paddings + bytes.reversedArray()
    } else {
        paddings + bytes
    }
}