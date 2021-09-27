package com.volt.server.extensions

import java.security.MessageDigest

/**
 * Supported algorithms on Android:
 *
 * Algorithm	Supported API Levels
 * MD5          1+
 * SHA-1	    1+
 * SHA-224	    1-8,22+
 * SHA-256	    1+
 * SHA-384	    1+
 * SHA-512	    1+
 */
private fun String.hash(type: String) = MessageDigest
        .getInstance(type)
        .digest(toByteArray())

internal fun String.sha1() = hash("SHA-1")
