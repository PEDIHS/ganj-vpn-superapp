package com.ganj.vpn.composition

private const val BASE64_URL_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/**
 * RFC 4648 Base64URL encoding without padding.
 *
 * Kept independent of java.util.Base64 so PKCE remains available on the supported Android 24+
 * range and in local JVM tests without Android framework shadows.
 */
internal fun ByteArray.toBase64UrlWithoutPadding(): String {
    if (isEmpty()) return ""

    val output = StringBuilder((size * 4 + 2) / 3)
    var offset = 0
    while (offset + 2 < size) {
        val value =
            ((this[offset].toInt() and 0xff) shl 16) or
                ((this[offset + 1].toInt() and 0xff) shl 8) or
                (this[offset + 2].toInt() and 0xff)
        output.append(BASE64_URL_ALPHABET[(value ushr 18) and 0x3f])
        output.append(BASE64_URL_ALPHABET[(value ushr 12) and 0x3f])
        output.append(BASE64_URL_ALPHABET[(value ushr 6) and 0x3f])
        output.append(BASE64_URL_ALPHABET[value and 0x3f])
        offset += 3
    }

    when (size - offset) {
        1 -> {
            val first = this[offset].toInt() and 0xff
            output.append(BASE64_URL_ALPHABET[first ushr 2])
            output.append(BASE64_URL_ALPHABET[(first and 0x03) shl 4])
        }
        2 -> {
            val first = this[offset].toInt() and 0xff
            val second = this[offset + 1].toInt() and 0xff
            output.append(BASE64_URL_ALPHABET[first ushr 2])
            output.append(BASE64_URL_ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
            output.append(BASE64_URL_ALPHABET[(second and 0x0f) shl 2])
        }
    }

    return output.toString()
}
